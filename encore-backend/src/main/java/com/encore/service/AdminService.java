package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.encore.common.ErrorCode;
import com.encore.dto.AdminOrderResponse;
import com.encore.dto.AdminScheduleResponse;
import com.encore.dto.AdminShowResponse;
import com.encore.dto.CreateShowRequest;
import com.encore.dto.UpdateShowRequest;
import com.encore.entity.ScheduleSeat;
import com.encore.entity.ShowEntity;
import com.encore.entity.ShowSchedule;
import com.encore.entity.TicketItem;
import com.encore.entity.TicketOrder;
import com.encore.entity.UserAccount;
import com.encore.exception.BusinessException;
import com.encore.mapper.ScheduleSeatMapper;
import com.encore.mapper.ShowMapper;
import com.encore.mapper.ShowScheduleMapper;
import com.encore.mapper.TicketItemMapper;
import com.encore.mapper.TicketOrderMapper;
import com.encore.mapper.UserAccountMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminService {
    private static final Set<String> ADMIN_ROLES = Set.of("admin", "sysadmin");
    private static final Set<String> SCHEDULE_STATUSES = Set.of(
            "COMING_SOON", "PREPARING", "ON_SALE", "SOLD_OUT", "CANCELLED"
    );
    private static final Set<String> SHOW_STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");

    private final ShowScheduleMapper showScheduleMapper;
    private final ShowMapper showMapper;
    private final ScheduleSeatMapper scheduleSeatMapper;
    private final TicketOrderMapper ticketOrderMapper;
    private final TicketItemMapper ticketItemMapper;
    private final UserAccountMapper userAccountMapper;
    private final StringRedisTemplate redisTemplate;

    public AdminService(
            ShowScheduleMapper showScheduleMapper,
            ShowMapper showMapper,
            ScheduleSeatMapper scheduleSeatMapper,
            TicketOrderMapper ticketOrderMapper,
            TicketItemMapper ticketItemMapper,
            UserAccountMapper userAccountMapper,
            StringRedisTemplate redisTemplate
    ) {
        this.showScheduleMapper = showScheduleMapper;
        this.showMapper = showMapper;
        this.scheduleSeatMapper = scheduleSeatMapper;
        this.ticketOrderMapper = ticketOrderMapper;
        this.ticketItemMapper = ticketItemMapper;
        this.userAccountMapper = userAccountMapper;
        this.redisTemplate = redisTemplate;
    }

    public List<AdminShowResponse> listShows() {
        ensureAdminRole();
        return showMapper.selectList(new LambdaQueryWrapper<ShowEntity>()
                        .orderByAsc(ShowEntity::getSortOrder)
                        .orderByDesc(ShowEntity::getCreatedAt))
                .stream()
                .map(this::toShowResponse)
                .toList();
    }

    @Transactional
    public AdminShowResponse createShow(CreateShowRequest request) {
        ensureAdminRole();
        ShowEntity show = new ShowEntity();
        show.setId(generateShowId());
        show.setTitle(clean(request.title()));
        show.setSubtitle(clean(request.subtitle()));
        show.setCoverUrl(clean(request.coverUrl()));
        show.setDescription(clean(request.description()));
        show.setDuration(request.duration());
        show.setCategory(clean(request.category()));
        show.setTags(normalizeTags(request.tags()));
        show.setStatus(StringUtils.hasText(request.status()) ? normalizeShowStatus(request.status()) : "DRAFT");
        show.setSortOrder(request.sortOrder() == null ? nextShowSortOrder() : request.sortOrder());
        showMapper.insert(show);
        return toShowResponse(showMapper.selectById(show.getId()));
    }

    @Transactional
    public AdminShowResponse updateShow(String showId, UpdateShowRequest request) {
        ensureAdminRole();
        ShowEntity show = getShow(showId);
        show.setTitle(clean(request.title()));
        show.setSubtitle(clean(request.subtitle()));
        show.setCoverUrl(clean(request.coverUrl()));
        show.setDescription(clean(request.description()));
        show.setDuration(request.duration());
        show.setCategory(clean(request.category()));
        show.setTags(normalizeTags(request.tags()));
        if (StringUtils.hasText(request.status())) {
            show.setStatus(normalizeShowStatus(request.status()));
        }
        if (request.sortOrder() != null) {
            show.setSortOrder(request.sortOrder());
        }
        showMapper.updateById(show);
        return toShowResponse(showMapper.selectById(showId));
    }

    @Transactional
    public AdminShowResponse updateShowStatus(String showId, String status) {
        ensureAdminRole();
        ShowEntity show = getShow(showId);
        show.setStatus(normalizeShowStatus(status));
        showMapper.updateById(show);
        return toShowResponse(showMapper.selectById(showId));
    }

    @Transactional
    public AdminShowResponse archiveShow(String showId) {
        ensureAdminRole();
        ShowEntity show = getShow(showId);
        show.setStatus("ARCHIVED");
        showMapper.updateById(show);
        return toShowResponse(showMapper.selectById(showId));
    }

    public List<AdminScheduleResponse> listSchedules() {
        ensureAdminRole();
        return showScheduleMapper.selectList(new LambdaQueryWrapper<ShowSchedule>()
                        .orderByAsc(ShowSchedule::getStartTime))
                .stream()
                .map(this::toScheduleResponse)
                .toList();
    }

    @Transactional
    public AdminScheduleResponse updateScheduleStatus(String scheduleId, String status) {
        ensureAdminRole();
        String normalizedStatus = normalizeScheduleStatus(status);
        ShowSchedule schedule = getSchedule(scheduleId);
        schedule.setStatus(normalizedStatus);
        showScheduleMapper.updateById(schedule);
        return toScheduleResponse(schedule);
    }

    public List<AdminOrderResponse> listOrders() {
        ensureAdminRole();
        return ticketOrderMapper.selectList(new LambdaQueryWrapper<TicketOrder>()
                        .orderByDesc(TicketOrder::getCreatedAt))
                .stream()
                .map(this::toOrderResponse)
                .toList();
    }

    @Transactional
    public AdminOrderResponse refundOrder(String orderId) {
        ensureAdminRole();
        TicketOrder order = getOrder(orderId);
        if ("REFUNDED".equals(order.getStatus())) {
            return toOrderResponse(order);
        }
        if (!"PAID".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅已支付订单可退款");
        }

        List<TicketItem> tickets = listTickets(orderId);
        boolean hasCheckedInTicket = tickets.stream().anyMatch(ticket -> "CHECKED_IN".equals(ticket.getStatus()));
        if (hasCheckedInTicket) {
            throw new BusinessException(ErrorCode.CONFLICT, "已核销订单不可退款");
        }

        order.setStatus("REFUNDED");
        ticketOrderMapper.updateById(order);
        for (TicketItem ticket : tickets) {
            ticket.setStatus("VOID");
            ticketItemMapper.updateById(ticket);
            markSeatAvailable(ticket.getScheduleId(), ticket.getSeatId());
        }
        return toOrderResponse(getOrder(orderId));
    }

    @Transactional
    public AdminOrderResponse forceCheckInOrder(String orderId) {
        ensureAdminRole();
        TicketOrder order = getOrder(orderId);
        if (!"PAID".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅已支付订单可核销");
        }

        LocalDateTime now = LocalDateTime.now();
        List<TicketItem> tickets = listTickets(orderId);
        for (TicketItem ticket : tickets) {
            if ("UNUSED".equals(ticket.getStatus())) {
                ticket.setStatus("CHECKED_IN");
                ticket.setUpdatedAt(now);
                ticketItemMapper.updateById(ticket);
            }
        }
        return toOrderResponse(getOrder(orderId));
    }

    private void ensureAdminRole() {
        String userId = StpUtil.getLoginIdAsString();
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null || !ADMIN_ROLES.contains(user.getRole())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "当前账号无后台管理权限");
        }
    }

    private String normalizeScheduleStatus(String status) {
        if (!StringUtils.hasText(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "场次状态不能为空");
        }
        String normalized = status.trim().toUpperCase();
        if (!SCHEDULE_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的场次状态");
        }
        return normalized;
    }

    private String normalizeShowStatus(String status) {
        if (!StringUtils.hasText(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "演出状态不能为空");
        }
        String normalized = status.trim().toUpperCase();
        if (!SHOW_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的演出状态");
        }
        return normalized;
    }

    private ShowSchedule getSchedule(String scheduleId) {
        ShowSchedule schedule = showScheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "场次不存在");
        }
        return schedule;
    }

    private ShowEntity getShow(String showId) {
        ShowEntity show = showMapper.selectById(showId);
        if (show == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "演出不存在");
        }
        return show;
    }

    private TicketOrder getOrder(String orderId) {
        TicketOrder order = ticketOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private String generateShowId() {
        String id;
        do {
            id = "s-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        } while (showMapper.selectById(id) != null);
        return id;
    }

    private int nextShowSortOrder() {
        List<ShowEntity> shows = showMapper.selectList(new LambdaQueryWrapper<ShowEntity>()
                .orderByDesc(ShowEntity::getSortOrder)
                .last("limit 1"));
        if (shows.isEmpty() || shows.get(0).getSortOrder() == null) {
            return 10;
        }
        return shows.get(0).getSortOrder() + 10;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private String clean(String value) {
        return value.trim();
    }

    private List<TicketItem> listTickets(String orderId) {
        return ticketItemMapper.selectList(new LambdaQueryWrapper<TicketItem>()
                .eq(TicketItem::getOrderId, orderId)
                .orderByAsc(TicketItem::getSeatId));
    }

    private List<TicketItem> listScheduleTickets(String scheduleId) {
        return ticketItemMapper.selectList(new LambdaQueryWrapper<TicketItem>()
                .eq(TicketItem::getScheduleId, scheduleId));
    }

    private void markSeatAvailable(String scheduleId, String seatId) {
        ScheduleSeat seat = scheduleSeatMapper.selectOne(new LambdaQueryWrapper<ScheduleSeat>()
                .eq(ScheduleSeat::getScheduleId, scheduleId)
                .eq(ScheduleSeat::getSeatCode, seatId)
                .last("limit 1"));
        if (seat != null && "SOLD".equals(seat.getStatus())) {
            seat.setStatus("AVAILABLE");
            scheduleSeatMapper.updateById(seat);
        }
    }

    private AdminShowResponse toShowResponse(ShowEntity show) {
        long scheduleCount = showScheduleMapper.selectCount(new LambdaQueryWrapper<ShowSchedule>()
                .eq(ShowSchedule::getShowId, show.getId()));
        return new AdminShowResponse(
                show.getId(),
                show.getTitle(),
                show.getSubtitle(),
                show.getCoverUrl(),
                show.getDescription(),
                show.getDuration(),
                show.getCategory(),
                show.getTags(),
                show.getStatus(),
                show.getSortOrder(),
                scheduleCount
        );
    }

    private AdminScheduleResponse toScheduleResponse(ShowSchedule schedule) {
        ShowEntity show = showMapper.selectById(schedule.getShowId());
        List<ScheduleSeat> seats = scheduleSeatMapper.selectList(new LambdaQueryWrapper<ScheduleSeat>()
                .eq(ScheduleSeat::getScheduleId, schedule.getId()));
        List<TicketItem> tickets = listScheduleTickets(schedule.getId());
        Set<String> lockKeys = redisTemplate.keys("encore:seat-lock:%s:*".formatted(schedule.getId()));
        long lockedSeats = lockKeys == null ? 0 : lockKeys.size();

        return new AdminScheduleResponse(
                schedule.getId(),
                schedule.getShowId(),
                show == null ? "Unknown Show" : show.getTitle(),
                schedule.getTheaterName(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getStatus(),
                schedule.getPriceRange(),
                seats.size(),
                countSeats(seats, "AVAILABLE"),
                lockedSeats,
                countSeats(seats, "SOLD"),
                countSeats(seats, "DISABLED"),
                tickets.stream().filter(ticket -> "UNUSED".equals(ticket.getStatus()) || "CHECKED_IN".equals(ticket.getStatus())).count(),
                tickets.stream().filter(ticket -> "CHECKED_IN".equals(ticket.getStatus())).count()
        );
    }

    private long countSeats(List<ScheduleSeat> seats, String status) {
        return seats.stream().filter(seat -> status.equals(seat.getStatus())).count();
    }

    private AdminOrderResponse toOrderResponse(TicketOrder order) {
        UserAccount user = userAccountMapper.selectById(order.getUserId());
        ShowSchedule schedule = showScheduleMapper.selectById(order.getScheduleId());
        ShowEntity show = schedule == null ? null : showMapper.selectById(schedule.getShowId());
        List<TicketItem> tickets = listTickets(order.getId()).stream()
                .sorted(Comparator.comparing(TicketItem::getSeatId))
                .toList();
        int checkedInCount = (int) tickets.stream()
                .filter(ticket -> "CHECKED_IN".equals(ticket.getStatus()))
                .count();
        return new AdminOrderResponse(
                order.getId(),
                order.getUserId(),
                user == null ? "Unknown User" : user.getUsername(),
                order.getScheduleId(),
                show == null ? "Unknown Show" : show.getTitle(),
                schedule == null ? "Unknown Theater" : schedule.getTheaterName(),
                schedule == null ? null : schedule.getStartTime(),
                order.getTotalAmount(),
                order.getStatus(),
                tickets.size(),
                checkedInCount,
                order.getCreatedAt(),
                order.getPaidAt()
        );
    }
}
