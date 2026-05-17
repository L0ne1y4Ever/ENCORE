package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.encore.common.ErrorCode;
import com.encore.dto.CreateGroupOrderRequest;
import com.encore.dto.GroupOrderMemberResponse;
import com.encore.dto.GroupOrderResponse;
import com.encore.dto.JoinGroupOrderRequest;
import com.encore.entity.ScheduleSeat;
import com.encore.entity.UserAccount;
import com.encore.exception.BusinessException;
import com.encore.mapper.UserAccountMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GroupOrderService {
    private static final Duration GROUP_TTL = Duration.ofMinutes(15);
    private static final Duration CLOSED_TTL = Duration.ofMinutes(1);
    private static final int MAX_SEATS = 6;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SeatService seatService;
    private final OrderService orderService;
    private final UserAccountMapper userAccountMapper;

    public GroupOrderService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            SeatService seatService,
            OrderService orderService,
            UserAccountMapper userAccountMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.seatService = seatService;
        this.orderService = orderService;
        this.userAccountMapper = userAccountMapper;
    }

    public GroupOrderResponse create(CreateGroupOrderRequest request) {
        UserAccount user = currentUser();
        List<String> seatIds = normalizeSeatIds(request.seatIds());
        String inviteCode = newInviteCode();
        String owner = groupOwner(inviteCode);

        try {
            seatService.lockSeatsForOwner(request.scheduleId(), seatIds, owner, GROUP_TTL, true);
            GroupOrderSession session = new GroupOrderSession();
            session.setInviteCode(inviteCode);
            session.setScheduleId(request.scheduleId());
            session.setHostUserId(user.getId());
            session.setHostDisplayName(user.getDisplayName());
            session.setStatus("OPEN");
            session.setExpiresAt(LocalDateTime.now().plus(GROUP_TTL));
            session.setMaxSeats(MAX_SEATS);
            session.setMembers(new ArrayList<>(List.of(member(user, seatIds))));
            saveSession(session, GROUP_TTL);
            return toResponse(session);
        } catch (RuntimeException exception) {
            seatService.releaseLocksOwnedBy(request.scheduleId(), seatIds, owner, true);
            throw exception;
        }
    }

    public GroupOrderResponse get(String inviteCode) {
        StpUtil.getLoginIdAsString();
        return toResponse(loadSession(inviteCode));
    }

    public GroupOrderResponse join(String inviteCode, JoinGroupOrderRequest request) {
        UserAccount user = currentUser();
        GroupOrderSession session = loadOpenSession(inviteCode);
        Duration ttl = remainingTtl(inviteCode);
        List<String> nextSeatIds = normalizeSeatIds(request.seatIds());
        String owner = groupOwner(inviteCode);

        List<String> oldSeatIds = session.getMembers().stream()
                .filter(member -> user.getId().equals(member.getUserId()))
                .findFirst()
                .map(GroupOrderSession.Member::getSeatIds)
                .orElse(List.of());
        Set<String> seatsClaimedByOthers = session.getMembers().stream()
                .filter(member -> !user.getId().equals(member.getUserId()))
                .flatMap(member -> member.getSeatIds().stream())
                .collect(Collectors.toSet());
        if (nextSeatIds.stream().anyMatch(seatsClaimedByOthers::contains)) {
            throw new BusinessException(ErrorCode.CONFLICT, "座位已被其他成员认领");
        }
        if (seatsClaimedByOthers.size() + nextSeatIds.size() > MAX_SEATS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "拼座最多支持 6 个座位");
        }

        seatService.lockSeatsForOwner(session.getScheduleId(), nextSeatIds, owner, ttl, true);
        List<String> removedSeatIds = oldSeatIds.stream()
                .filter(seatId -> !nextSeatIds.contains(seatId))
                .toList();
        if (!removedSeatIds.isEmpty()) {
            seatService.releaseLocksOwnedBy(session.getScheduleId(), removedSeatIds, owner, true);
        }

        session.getMembers().removeIf(member -> user.getId().equals(member.getUserId()));
        session.getMembers().add(member(user, nextSeatIds));
        saveSession(session, ttl);
        return toResponse(session);
    }

    public GroupOrderResponse leave(String inviteCode) {
        UserAccount user = currentUser();
        GroupOrderSession session = loadOpenSession(inviteCode);
        if (user.getId().equals(session.getHostUserId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "发起人不能退出，请取消拼座");
        }
        Duration ttl = remainingTtl(inviteCode);
        String owner = groupOwner(inviteCode);
        List<String> oldSeatIds = session.getMembers().stream()
                .filter(member -> user.getId().equals(member.getUserId()))
                .findFirst()
                .map(GroupOrderSession.Member::getSeatIds)
                .orElse(List.of());
        if (oldSeatIds.isEmpty()) {
            return toResponse(session);
        }
        session.getMembers().removeIf(member -> user.getId().equals(member.getUserId()));
        seatService.releaseLocksOwnedBy(session.getScheduleId(), oldSeatIds, owner, true);
        saveSession(session, ttl);
        return toResponse(session);
    }

    public GroupOrderResponse cancel(String inviteCode) {
        UserAccount user = currentUser();
        GroupOrderSession session = loadSession(inviteCode);
        if (!user.getId().equals(session.getHostUserId())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "仅发起人可取消拼座");
        }
        if ("CHECKED_OUT".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "拼座已结算，不能取消");
        }
        if (!"CANCELLED".equals(session.getStatus())) {
            List<String> seatIds = allSeatIds(session);
            if (!seatIds.isEmpty()) {
                seatService.releaseLocksOwnedBy(session.getScheduleId(), seatIds, groupOwner(inviteCode), true);
            }
            session.setStatus("CANCELLED");
            session.setExpiresAt(LocalDateTime.now());
            saveSession(session, CLOSED_TTL);
        }
        return toResponse(session);
    }

    public String checkout(String inviteCode) {
        UserAccount user = currentUser();
        GroupOrderSession session = loadSession(inviteCode);
        if (!user.getId().equals(session.getHostUserId())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "仅发起人可结算拼座");
        }
        if ("CHECKED_OUT".equals(session.getStatus()) && session.getOrderId() != null) {
            return session.getOrderId();
        }
        ensureOpen(session);
        Duration ttl = remainingTtl(inviteCode);
        List<String> seatIds = allSeatIds(session);
        if (seatIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "拼座暂无座位");
        }
        String owner = groupOwner(inviteCode);
        seatService.ensureLocksOwnedBy(session.getScheduleId(), seatIds, owner);
        String orderId = orderService.createGroupOrder(session.getScheduleId(), seatIds, owner);
        session.setStatus("CHECKED_OUT");
        session.setOrderId(orderId);
        saveSession(session, ttl);
        return orderId;
    }

    private GroupOrderSession loadOpenSession(String inviteCode) {
        GroupOrderSession session = loadSession(inviteCode);
        ensureOpen(session);
        return session;
    }

    private void ensureOpen(GroupOrderSession session) {
        if ("OPEN".equals(session.getStatus())) {
            return;
        }
        if ("CANCELLED".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "拼座已取消");
        }
        if ("CHECKED_OUT".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "拼座已结算");
        }
        throw new BusinessException(ErrorCode.CONFLICT, "拼座会话不可用");
    }

    private GroupOrderSession loadSession(String inviteCode) {
        String raw = redisTemplate.opsForValue().get(key(inviteCode));
        if (raw == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "拼座会话不存在或已过期");
        }
        try {
            return objectMapper.readValue(raw, GroupOrderSession.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "拼座会话读取失败");
        }
    }

    private void saveSession(GroupOrderSession session, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key(session.getInviteCode()), objectMapper.writeValueAsString(session), ttl);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "拼座会话保存失败");
        }
    }

    private Duration remainingTtl(String inviteCode) {
        Long seconds = redisTemplate.getExpire(key(inviteCode), TimeUnit.SECONDS);
        if (seconds == null || seconds <= 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "拼座会话不存在或已过期");
        }
        return Duration.ofSeconds(seconds);
    }

    private GroupOrderResponse toResponse(GroupOrderSession session) {
        List<String> seatIds = allSeatIds(session);
        Map<String, ScheduleSeat> seatById = seatIds.isEmpty()
                ? Map.of()
                : seatService.findSeats(session.getScheduleId(), seatIds).stream()
                        .collect(Collectors.toMap(ScheduleSeat::getSeatCode, Function.identity(), (left, right) -> left));
        List<GroupOrderMemberResponse> members = session.getMembers().stream()
                .map(member -> new GroupOrderMemberResponse(
                        member.getUserId(),
                        member.getDisplayName(),
                        member.getSeatIds(),
                        amountOf(member.getSeatIds(), seatById),
                        member.getJoinedAt()
                ))
                .toList();
        BigDecimal totalAmount = members.stream()
                .map(GroupOrderMemberResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new GroupOrderResponse(
                session.getInviteCode(),
                session.getScheduleId(),
                session.getHostUserId(),
                session.getHostDisplayName(),
                session.getStatus(),
                session.getExpiresAt(),
                session.getMaxSeats(),
                totalAmount,
                members
        );
    }

    private BigDecimal amountOf(List<String> seatIds, Map<String, ScheduleSeat> seatById) {
        return seatIds.stream()
                .map(seatById::get)
                .filter(seat -> seat != null)
                .map(ScheduleSeat::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private GroupOrderSession.Member member(UserAccount user, List<String> seatIds) {
        GroupOrderSession.Member member = new GroupOrderSession.Member();
        member.setUserId(user.getId());
        member.setDisplayName(user.getDisplayName());
        member.setSeatIds(seatIds);
        member.setJoinedAt(LocalDateTime.now());
        return member;
    }

    private UserAccount currentUser() {
        String userId = StpUtil.getLoginIdAsString();
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录用户不存在");
        }
        return user;
    }

    private List<String> normalizeSeatIds(List<String> seatIds) {
        List<String> normalized = seatIds == null ? List.of() : seatIds.stream()
                .filter(seatId -> seatId != null && !seatId.isBlank())
                .distinct()
                .toList();
        if (normalized.isEmpty() || normalized.size() > MAX_SEATS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择 1 到 6 个座位");
        }
        return normalized;
    }

    private List<String> allSeatIds(GroupOrderSession session) {
        Set<String> uniqueSeatIds = new HashSet<>();
        return session.getMembers().stream()
                .flatMap(member -> member.getSeatIds().stream())
                .filter(uniqueSeatIds::add)
                .toList();
    }

    private String newInviteCode() {
        for (int i = 0; i < 5; i++) {
            String inviteCode = "g-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(key(inviteCode)))) {
                return inviteCode;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "拼座邀请码生成失败");
    }

    private String key(String inviteCode) {
        return "encore:group-order:%s".formatted(inviteCode);
    }

    private String groupOwner(String inviteCode) {
        return "group:%s".formatted(inviteCode);
    }
}
