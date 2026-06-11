package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.encore.common.ErrorCode;
import com.encore.dto.CreateOrderRequest;
import com.encore.dto.OrderResponse;
import com.encore.dto.RefundOrderRequest;
import com.encore.dto.RefundRequestSummary;
import com.encore.dto.TicketItemResponse;
import com.encore.entity.RefundRequest;
import com.encore.entity.RefundRequestTicket;
import com.encore.entity.ScheduleSeat;
import com.encore.entity.ShowEntity;
import com.encore.entity.ShowSchedule;
import com.encore.entity.TicketItem;
import com.encore.entity.TicketOrder;
import com.encore.entity.ScheduleAreaInventory;
import com.encore.entity.UserAccount;
import com.encore.entity.VenueArea;
import com.encore.exception.BusinessException;
import com.encore.mapper.RefundRequestMapper;
import com.encore.mapper.RefundRequestTicketMapper;
import com.encore.mapper.ScheduleSeatMapper;
import com.encore.mapper.ShowMapper;
import com.encore.mapper.ShowScheduleMapper;
import com.encore.mapper.TicketItemMapper;
import com.encore.mapper.TicketOrderMapper;
import com.encore.mapper.ScheduleAreaInventoryMapper;
import com.encore.mapper.UserAccountMapper;
import com.encore.mapper.VenueAreaMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {
    private static final Duration PAYMENT_TTL = Duration.ofMinutes(15);
    private static final Duration SELF_SERVICE_REFUND_DEADLINE = Duration.ofHours(2);

    public record GroupSeatHolder(String seatId, String userId, String displayName) {
    }

    private record RefundScope(String scope, List<TicketItem> tickets, boolean wholeOrder) {
    }

    private final TicketOrderMapper ticketOrderMapper;
    private final TicketItemMapper ticketItemMapper;
    private final ScheduleSeatMapper scheduleSeatMapper;
    private final SeatService seatService;
    private final SeatStatusPublisher seatStatusPublisher;
    private final DashboardRefreshPublisher dashboardRefreshPublisher;
    private final ScheduleAreaInventoryMapper scheduleAreaInventoryMapper;
    private final VenueAreaMapper venueAreaMapper;
    private final ShowScheduleMapper showScheduleMapper;
    private final ShowMapper showMapper;
    private final UserAccountMapper userAccountMapper;
    private final RefundRequestMapper refundRequestMapper;
    private final RefundRequestTicketMapper refundRequestTicketMapper;

    public OrderService(
            TicketOrderMapper ticketOrderMapper,
            TicketItemMapper ticketItemMapper,
            ScheduleSeatMapper scheduleSeatMapper,
            SeatService seatService,
            SeatStatusPublisher seatStatusPublisher,
            DashboardRefreshPublisher dashboardRefreshPublisher,
            ScheduleAreaInventoryMapper scheduleAreaInventoryMapper,
            VenueAreaMapper venueAreaMapper,
            ShowScheduleMapper showScheduleMapper,
            ShowMapper showMapper,
            UserAccountMapper userAccountMapper,
            RefundRequestMapper refundRequestMapper,
            RefundRequestTicketMapper refundRequestTicketMapper
    ) {
        this.ticketOrderMapper = ticketOrderMapper;
        this.ticketItemMapper = ticketItemMapper;
        this.scheduleSeatMapper = scheduleSeatMapper;
        this.seatService = seatService;
        this.seatStatusPublisher = seatStatusPublisher;
        this.dashboardRefreshPublisher = dashboardRefreshPublisher;
        this.scheduleAreaInventoryMapper = scheduleAreaInventoryMapper;
        this.venueAreaMapper = venueAreaMapper;
        this.showScheduleMapper = showScheduleMapper;
        this.showMapper = showMapper;
        this.userAccountMapper = userAccountMapper;
        this.refundRequestMapper = refundRequestMapper;
        this.refundRequestTicketMapper = refundRequestTicketMapper;
    }

    @Transactional
    public String createOrder(CreateOrderRequest request) {
        String userId = StpUtil.getLoginIdAsString();
        String holderDisplayName = currentDisplayName(userId);
        seatService.ensureOnSaleSchedule(request.scheduleId());

        // ZONED / standing area mode
        if (request.areaInventoryId() != null && !request.areaInventoryId().isBlank()) {
            Integer quantity = request.quantity();
            if (quantity == null || quantity < 1 || quantity > 4) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "购票数量必须在 1 到 4 张之间");
            }
            ScheduleAreaInventory inventory = scheduleAreaInventoryMapper.selectById(request.areaInventoryId());
            if (inventory == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "区域库存不存在");
            }
            if (!inventory.getScheduleId().equals(request.scheduleId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "场次与区域不匹配");
            }

            String existingOrderId = findSamePendingZonedOrder(userId, request.scheduleId(), request.areaInventoryId(), quantity);
            if (existingOrderId != null) {
                return existingOrderId;
            }

            int updated = scheduleAreaInventoryMapper.lockInventory(request.areaInventoryId(), quantity);
            if (updated == 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "区域余票不足，锁定库存失败");
            }

            BigDecimal totalAmount = inventory.getPrice().multiply(BigDecimal.valueOf(quantity));
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiresAt = now.plus(PAYMENT_TTL);
            String orderId = "ord-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

            TicketOrder order = new TicketOrder();
            order.setId(orderId);
            order.setUserId(userId);
            order.setScheduleId(request.scheduleId());
            order.setTotalAmount(totalAmount);
            order.setStatus("PENDING_PAYMENT");
            order.setCreatedAt(now);
            order.setExpiresAt(expiresAt);
            ticketOrderMapper.insert(order);

            for (int i = 0; i < quantity; i++) {
                TicketItem ticket = new TicketItem();
                ticket.setId("tk-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18));
                ticket.setOrderId(orderId);
                ticket.setScheduleId(request.scheduleId());
                ticket.setSeatId(null);
                ticket.setAreaInventoryId(request.areaInventoryId());
                ticket.setTicketCode("T" + Long.toString(System.currentTimeMillis(), 36).toUpperCase() + "Z" + i + "XYZ");
                ticket.setStatus("RESERVED");
                ticket.setHolderUserId(userId);
                ticket.setHolderDisplayName(holderDisplayName);
                ticket.setCreatedAt(now);
                ticket.setUpdatedAt(now);
                ticketItemMapper.insert(ticket);
            }
            dashboardRefreshPublisher.publish("ORDER_CREATED", orderId);
            seatService.publishAreaInventory(request.scheduleId(), "AREA_LOCKED", request.areaInventoryId());
            return orderId;
        }

        // SEATED mode
        List<String> seatIds = normalizeSeatIds(request.seatIds());
        List<ScheduleSeat> seats = seatService.findSeats(request.scheduleId(), seatIds);
        seats.forEach(seat -> seatService.ensureSeatAvailableForOrder(request.scheduleId(), seat));

        String existingOrderId = findSamePendingOrder(userId, request.scheduleId(), seatIds);
        if (existingOrderId != null) {
            return existingOrderId;
        }

        for (String seatId : seatIds) {
            if (!seatService.isLockedByCurrentUser(request.scheduleId(), seatId)) {
                seatService.lockSeats(request.scheduleId(), List.of(seatId));
            }
        }

        BigDecimal totalAmount = seats.stream()
                .map(ScheduleSeat::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(PAYMENT_TTL);
        String orderId = "ord-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        TicketOrder order = new TicketOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setScheduleId(request.scheduleId());
        order.setTotalAmount(totalAmount);
        order.setStatus("PENDING_PAYMENT");
        order.setCreatedAt(now);
        order.setExpiresAt(expiresAt);
        ticketOrderMapper.insert(order);

        List<TicketItem> tickets = seatIds.stream()
                .sorted()
                .map(seatId -> createReservedTicket(orderId, request.scheduleId(), seatId, userId, holderDisplayName))
                .toList();
        tickets.forEach(ticketItemMapper::insert);
        seatService.attachOrderToLocks(request.scheduleId(), seatIds, orderId, PAYMENT_TTL);
        releaseSeatLocksOnRollback(request.scheduleId(), seatIds);
        return orderId;
    }

    @Transactional
    public String createGroupOrder(String scheduleId, List<GroupSeatHolder> requestedSeatHolders, String lockOwner) {
        String userId = StpUtil.getLoginIdAsString();
        seatService.ensureOnSaleSchedule(scheduleId);
        List<GroupSeatHolder> seatHolders = normalizeGroupSeatHolders(requestedSeatHolders);
        List<String> seatIds = seatHolders.stream().map(GroupSeatHolder::seatId).toList();
        Map<String, GroupSeatHolder> holderBySeatId = seatHolders.stream()
                .collect(Collectors.toMap(GroupSeatHolder::seatId, holder -> holder, (left, right) -> left, LinkedHashMap::new));
        List<ScheduleSeat> seats = seatService.findSeats(scheduleId, seatIds);
        seats.forEach(seat -> seatService.ensureSeatAvailableForOrder(scheduleId, seat));
        seatService.ensureLocksOwnedBy(scheduleId, seatIds, lockOwner);

        BigDecimal totalAmount = seats.stream()
                .map(ScheduleSeat::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(PAYMENT_TTL);
        String orderId = "ord-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        TicketOrder order = new TicketOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setScheduleId(scheduleId);
        order.setTotalAmount(totalAmount);
        order.setStatus("PENDING_PAYMENT");
        order.setCreatedAt(now);
        order.setExpiresAt(expiresAt);
        ticketOrderMapper.insert(order);

        List<TicketItem> tickets = seatIds.stream()
                .sorted()
                .map(seatId -> {
                    GroupSeatHolder holder = holderBySeatId.get(seatId);
                    return createReservedTicket(orderId, scheduleId, seatId, holder.userId(), holder.displayName());
                })
                .toList();
        tickets.forEach(ticketItemMapper::insert);
        seatService.transferLocksOwner(scheduleId, seatIds, lockOwner, orderId, PAYMENT_TTL);
        revertGroupLocksOnRollback(scheduleId, seatIds, orderId, lockOwner);
        return orderId;
    }

    // Redis 锁不受数据库事务管控：下单事务回滚后，指向该订单的座位锁会变成孤儿锁。
    // 注册事务同步，在确实回滚(STATUS_ROLLED_BACK)时删除这些锁，让座位立即恢复可售。
    private void releaseSeatLocksOnRollback(String scheduleId, List<String> seatIds) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    seatService.releaseLocks(scheduleId, seatIds);
                } catch (RuntimeException ignored) {
                    // 回收失败时残留锁由 SeatService.cleanupExpiredSeatLocks 定时兜底
                }
            }
        });
    }

    // 拼座下单已把座位锁从发起人转移到 orderId；若订单事务回滚，需把锁退回发起人，
    // 否则拼座会话会丢失这些座位。并发改动导致回退失败时放弃，交由定时任务回收。
    private void revertGroupLocksOnRollback(String scheduleId, List<String> seatIds, String orderId, String lockOwner) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    seatService.transferLocksOwner(scheduleId, seatIds, orderId, lockOwner, SeatService.SEAT_LOCK_TTL);
                } catch (RuntimeException ignored) {
                    // 锁已被并发改动或失效时放弃回退，残留锁由定时任务回收
                }
            }
        });
    }

    public OrderResponse getOrderDetail(String orderId) {
        TicketOrder order = getOrder(orderId);
        ensureCanViewOrder(order);
        expireIfNeeded(order);
        return toOrderResponse(getOrder(orderId));
    }

    public String getOrderStatus(String orderId) {
        TicketOrder order = ticketOrderMapper.selectById(orderId);
        return order == null ? null : order.getStatus();
    }

    @Transactional
    public void repairGroupTicketHolders(String orderId, List<GroupSeatHolder> requestedSeatHolders) {
        if (orderId == null || orderId.isBlank() || requestedSeatHolders == null || requestedSeatHolders.isEmpty()) {
            return;
        }
        TicketOrder order = ticketOrderMapper.selectById(orderId);
        if (order == null) {
            return;
        }
        Map<String, GroupSeatHolder> holderBySeatId = normalizeGroupSeatHolders(requestedSeatHolders).stream()
                .collect(Collectors.toMap(GroupSeatHolder::seatId, holder -> holder, (left, right) -> left, LinkedHashMap::new));
        if (holderBySeatId.isEmpty()) {
            return;
        }

        for (TicketItem ticket : listTickets(orderId)) {
            if (ticket.getAreaInventoryId() != null || ticket.getSeatId() == null) {
                continue;
            }
            GroupSeatHolder holder = holderBySeatId.get(ticket.getSeatId());
            if (holder == null) {
                continue;
            }
            String displayName = holder.displayName() == null || holder.displayName().isBlank()
                    ? holder.userId()
                    : holder.displayName();
            boolean holderChanged = !holder.userId().equals(ticket.getHolderUserId());
            boolean displayNameChanged = !displayName.equals(ticket.getHolderDisplayName());
            if (!holderChanged && !displayNameChanged) {
                continue;
            }
            ticket.setHolderUserId(holder.userId());
            ticket.setHolderDisplayName(displayName);
            ticket.setUpdatedAt(LocalDateTime.now());
            ticketItemMapper.updateById(ticket);
        }
    }

    @Transactional
    public List<OrderResponse> listMyOrders() {
        String userId = StpUtil.getLoginIdAsString();
        Map<String, TicketOrder> ordersById = new LinkedHashMap<>();
        safeList(ticketOrderMapper.selectList(new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getUserId, userId)))
                .forEach(order -> ordersById.put(order.getId(), order));

        Set<String> heldOrderIds = safeList(ticketItemMapper.selectList(new LambdaQueryWrapper<TicketItem>()
                        .eq(TicketItem::getHolderUserId, userId)))
                .stream()
                .map(TicketItem::getOrderId)
                .collect(Collectors.toSet());
        for (String heldOrderId : heldOrderIds) {
            if (ordersById.containsKey(heldOrderId)) {
                continue;
            }
            TicketOrder order = ticketOrderMapper.selectById(heldOrderId);
            if (order != null && !"PENDING_PAYMENT".equals(order.getStatus())) {
                ordersById.put(order.getId(), order);
            }
        }

        return ordersById.values().stream()
                .sorted((left, right) -> Comparator.nullsLast(LocalDateTime::compareTo)
                        .compare(right.getCreatedAt(), left.getCreatedAt()))
                .map(order -> {
                    expireIfNeeded(order);
                    return toOrderResponse(getOrder(order.getId()));
                })
                .toList();
    }

    @Transactional
    public boolean simulatePayment(String orderId) {
        TicketOrder order = getOrder(orderId);
        ensureOrderOwner(order);
        expireIfNeeded(order);
        order = getOrder(orderId);
        if ("PAID".equals(order.getStatus())) {
            return true;
        }
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            return false;
        }

        List<TicketItem> tickets = listTickets(orderId);
        boolean isZoned = !tickets.isEmpty() && tickets.get(0).getAreaInventoryId() != null;
        if (isZoned) {
            String areaInventoryId = tickets.get(0).getAreaInventoryId();
            int quantity = tickets.size();
            int updated = scheduleAreaInventoryMapper.sellInventory(areaInventoryId, quantity);
            if (updated == 0) {
                markExpired(order, tickets);
                return false;
            }
            for (TicketItem ticket : tickets) {
                ticket.setStatus("UNUSED");
                ticketItemMapper.updateById(ticket);
            }
        } else {
            for (TicketItem ticket : tickets) {
                if (!seatService.isLockedByOrder(order.getScheduleId(), ticket.getSeatId(), orderId)) {
                    markExpired(order, tickets);
                    return false;
                }
            }

            for (TicketItem ticket : tickets) {
                ScheduleSeat seat = scheduleSeatMapper.selectOne(new LambdaQueryWrapper<ScheduleSeat>()
                        .eq(ScheduleSeat::getScheduleId, order.getScheduleId())
                        .eq(ScheduleSeat::getSeatCode, ticket.getSeatId())
                        .last("limit 1"));
                if (seat == null || !"AVAILABLE".equals(seat.getStatus())) {
                    throw new BusinessException(ErrorCode.CONFLICT, "座位状态已变化，无法支付");
                }
                seat.setStatus("SOLD");
                scheduleSeatMapper.updateById(seat);

                ticket.setStatus("UNUSED");
                ticketItemMapper.updateById(ticket);
            }
        }

        order.setStatus("PAID");
        order.setPaidAt(LocalDateTime.now());
        ticketOrderMapper.updateById(order);

        if (!isZoned) {
            List<String> seatIds = tickets.stream().map(TicketItem::getSeatId).toList();
            seatService.releaseLocks(order.getScheduleId(), seatIds);
            seatStatusPublisher.publishSeatStatus(order.getScheduleId(), "SOLD", "SOLD", seatIds);
        } else {
            seatService.publishAreaInventory(order.getScheduleId(), "AREA_SOLD", tickets.get(0).getAreaInventoryId());
        }
        dashboardRefreshPublisher.publish("ORDER_PAID", order.getId());
        return true;
    }

    @Transactional
    public OrderResponse cancelOrder(String orderId) {
        TicketOrder order = getOrder(orderId);
        ensureOrderOwner(order);
        expireIfNeeded(order);
        order = getOrder(orderId);
        if ("CANCELLED".equals(order.getStatus()) || "EXPIRED".equals(order.getStatus())) {
            return toOrderResponse(order);
        }
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅待支付预约可取消");
        }
        markCancelled(order, listTickets(orderId));
        return toOrderResponse(getOrder(orderId));
    }

    @Transactional
    public OrderResponse refundOrder(String orderId) {
        return refundOrder(orderId, null);
    }

    @Transactional
    public OrderResponse refundOrder(String orderId, RefundOrderRequest request) {
        TicketOrder order = getOrder(orderId);
        String currentUserId = StpUtil.getLoginIdAsString();
        expireIfNeeded(order);
        order = getOrder(orderId);
        if ("REFUNDED".equals(order.getStatus())) {
            return toOrderResponse(order);
        }
        if ("PENDING_REFUND".equals(order.getStatus())) {
            return toOrderResponse(order);
        }
        if (!"PAID".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅已支付订单可申请退票");
        }
        ShowSchedule schedule = showScheduleMapper.selectById(order.getScheduleId());
        if (schedule == null || schedule.getStartTime() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "场次信息异常，无法自助退票");
        }
        LocalDateTime now = LocalDateTime.now();
        if (schedule.getEndTime() != null && now.isAfter(schedule.getEndTime())) {
            throw new BusinessException(ErrorCode.CONFLICT, "演出已结束，无法申请退票");
        }
        LocalDateTime refundDeadline = schedule.getStartTime().minus(SELF_SERVICE_REFUND_DEADLINE);
        List<TicketItem> tickets = listTickets(orderId);
        RefundScope refundScope = resolveRefundScope(order, tickets, request, currentUserId);
        if (hasPendingRefundForTickets(order.getId(), refundScope.tickets())) {
            return toOrderResponse(getOrder(orderId));
        }
        String reason = cleanRefundText(request == null ? null : request.reason());
        if (now.isBefore(refundDeadline)) {
            createRefundRequest(order, "APPROVED", "USER_AUTO", refundScope.scope(), reason, null, null, currentUserId, refundScope.tickets());
            markRefunded(order, refundScope.tickets(), "USER_REFUNDED");
        } else {
            createRefundRequest(order, "PENDING", "USER_REVIEW", refundScope.scope(), reason, null, null, currentUserId, refundScope.tickets());
            markTicketsPendingRefund(order, refundScope.tickets(), refundScope.wholeOrder());
            dashboardRefreshPublisher.publish("ORDER_REFUND_REQUESTED", order.getId());
        }
        return toOrderResponse(getOrder(orderId));
    }

    private TicketOrder getOrder(String orderId) {
        TicketOrder order = ticketOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private RefundRequest findPendingRefundRequest(String orderId) {
        List<RefundRequest> rows = refundRequestMapper.selectList(new LambdaQueryWrapper<RefundRequest>()
                .eq(RefundRequest::getOrderId, orderId)
                .eq(RefundRequest::getStatus, "PENDING")
                .orderByDesc(RefundRequest::getRequestedAt));
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    private RefundRequest latestRefundRequest(String orderId) {
        List<RefundRequest> rows = refundRequestMapper.selectList(new LambdaQueryWrapper<RefundRequest>()
                .eq(RefundRequest::getOrderId, orderId)
                .orderByDesc(RefundRequest::getRequestedAt));
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    private RefundRequest latestRefundRequest(String orderId, List<TicketItem> visibleTickets, boolean fullAccess) {
        if (fullAccess) {
            return latestRefundRequest(orderId);
        }
        List<String> ticketIds = visibleTickets.stream()
                .map(TicketItem::getId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        if (ticketIds.isEmpty()) {
            return null;
        }
        List<String> requestIds = safeList(refundRequestTicketMapper.selectList(new LambdaQueryWrapper<RefundRequestTicket>()
                        .eq(RefundRequestTicket::getOrderId, orderId)
                        .in(RefundRequestTicket::getTicketId, ticketIds)))
                .stream()
                .map(RefundRequestTicket::getRefundRequestId)
                .distinct()
                .toList();
        if (requestIds.isEmpty()) {
            return null;
        }
        List<RefundRequest> rows = refundRequestMapper.selectList(new LambdaQueryWrapper<RefundRequest>()
                .in(RefundRequest::getId, requestIds)
                .orderByDesc(RefundRequest::getRequestedAt));
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    private RefundRequest createRefundRequest(
            TicketOrder order,
            String status,
            String source,
            String scope,
            String reason,
            String reviewNote,
            UserAccount reviewer,
            String requesterId,
            List<TicketItem> tickets
    ) {
        LocalDateTime now = LocalDateTime.now();
        RefundRequest request = new RefundRequest();
        request.setId("rr-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18));
        request.setOrderId(order.getId());
        request.setUserId(order.getUserId());
        request.setRequesterId(StringUtils.hasText(requesterId) ? requesterId : order.getUserId());
        request.setStatus(status);
        request.setSource(source);
        request.setScope(StringUtils.hasText(scope) ? scope : "ORDER");
        request.setReason(cleanRefundText(reason));
        request.setReviewNote(cleanRefundText(reviewNote));
        request.setRefundAmount(calculateTicketAmount(order, tickets));
        request.setTicketCount(tickets == null ? 0 : tickets.size());
        if (reviewer != null) {
            request.setReviewerId(reviewer.getId());
            request.setReviewerUsername(reviewer.getUsername());
        }
        request.setRequestedAt(now);
        request.setReviewedAt("PENDING".equals(status) ? null : now);
        request.setUpdatedAt(now);
        refundRequestMapper.insert(request);
        for (TicketItem ticket : tickets == null ? List.<TicketItem>of() : tickets) {
            RefundRequestTicket row = new RefundRequestTicket();
            row.setId("rrt-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18));
            row.setRefundRequestId(request.getId());
            row.setOrderId(order.getId());
            row.setTicketId(ticket.getId());
            row.setHolderUserId(ticket.getHolderUserId());
            row.setAmount(calculateTicketAmount(order, List.of(ticket)));
            row.setCreatedAt(now);
            refundRequestTicketMapper.insert(row);
        }
        return request;
    }

    private RefundScope resolveRefundScope(
            TicketOrder order,
            List<TicketItem> tickets,
            RefundOrderRequest request,
            String currentUserId
    ) {
        boolean isOwner = order.getUserId().equals(currentUserId);
        List<String> requestedTicketIds = normalizeOptionalTicketIds(request == null ? null : request.ticketIds());
        if (!isOwner && !hasTicketForHolder(order.getId(), currentUserId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权操作该订单");
        }

        List<TicketItem> targetTickets;
        if (requestedTicketIds.isEmpty()) {
            targetTickets = tickets.stream()
                    .filter(ticket -> isOwner || currentUserId.equals(ticket.getHolderUserId()))
                    .filter(ticket -> !"VOID".equals(ticket.getStatus()))
                    .toList();
        } else {
            Set<String> requestedSet = new LinkedHashSet<>(requestedTicketIds);
            targetTickets = tickets.stream()
                    .filter(ticket -> requestedSet.contains(ticket.getId()))
                    .toList();
            if (targetTickets.size() != requestedSet.size()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "退票票据不存在");
            }
            if (!isOwner && targetTickets.stream().anyMatch(ticket -> !currentUserId.equals(ticket.getHolderUserId()))) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "只能申请退还自己持有的票据");
            }
        }

        if (targetTickets.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "没有可申请退票的票据");
        }
        if (targetTickets.stream().anyMatch(ticket -> "CHECKED_IN".equals(ticket.getStatus()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "已核销票据不可退票");
        }
        if (targetTickets.stream().anyMatch(ticket -> "PENDING_REFUND".equals(ticket.getStatus()))) {
            return new RefundScope("TICKET", targetTickets, false);
        }
        if (targetTickets.stream().anyMatch(ticket -> !"UNUSED".equals(ticket.getStatus()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅未使用票据可申请退票");
        }
        boolean allActiveTickets = tickets.stream()
                .filter(ticket -> !"VOID".equals(ticket.getStatus()))
                .allMatch(targetTickets::contains);
        boolean isTicketLevel = !isOwner || !requestedTicketIds.isEmpty() || !allActiveTickets;
        if (isTicketLevel && targetTickets.stream().anyMatch(ticket -> ticket.getAreaInventoryId() != null)) {
            throw new BusinessException(ErrorCode.CONFLICT, "区域票暂不支持票级退票，请由订单发起人处理整单退票");
        }
        return new RefundScope(isTicketLevel ? "TICKET" : "ORDER", targetTickets, !isTicketLevel);
    }

    private boolean hasPendingRefundForTickets(String orderId, List<TicketItem> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return false;
        }
        if (tickets.stream().anyMatch(ticket -> "PENDING_REFUND".equals(ticket.getStatus()))) {
            return true;
        }
        List<RefundRequest> pendingRequests = refundRequestMapper.selectList(new LambdaQueryWrapper<RefundRequest>()
                .eq(RefundRequest::getOrderId, orderId)
                .eq(RefundRequest::getStatus, "PENDING"));
        if (pendingRequests == null || pendingRequests.isEmpty()) {
            return false;
        }
        Set<String> targetTicketIds = tickets.stream()
                .map(TicketItem::getId)
                .collect(Collectors.toSet());
        for (RefundRequest request : pendingRequests) {
            List<RefundRequestTicket> rows = refundRequestTicketMapper.selectList(new LambdaQueryWrapper<RefundRequestTicket>()
                    .eq(RefundRequestTicket::getRefundRequestId, request.getId()));
            if (rows == null || rows.isEmpty()) {
                return true;
            }
            if (rows.stream().anyMatch(row -> targetTicketIds.contains(row.getTicketId()))) {
                return true;
            }
        }
        return false;
    }

    private List<String> normalizeOptionalTicketIds(List<String> ticketIds) {
        if (ticketIds == null) {
            return List.of();
        }
        return ticketIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String cleanRefundText(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > 500 ? cleaned.substring(0, 500) : cleaned;
    }

    private RefundRequestSummary toRefundSummary(RefundRequest request) {
        if (request == null) {
            return null;
        }
        List<String> ticketIds = refundRequestTicketMapper.selectList(new LambdaQueryWrapper<RefundRequestTicket>()
                        .eq(RefundRequestTicket::getRefundRequestId, request.getId()))
                .stream()
                .map(RefundRequestTicket::getTicketId)
                .toList();
        return new RefundRequestSummary(
                request.getId(),
                request.getStatus(),
                request.getSource(),
                request.getScope() == null ? "ORDER" : request.getScope(),
                request.getReason(),
                request.getReviewNote(),
                request.getReviewerUsername(),
                moneyOrZero(request.getRefundAmount()),
                request.getTicketCount() == null ? ticketIds.size() : request.getTicketCount(),
                ticketIds,
                request.getRequesterId(),
                request.getRequestedAt(),
                request.getReviewedAt()
        );
    }

    private void ensureCanViewOrder(TicketOrder order) {
        String userId = StpUtil.getLoginIdAsString();
        if (order.getUserId().equals(userId)) {
            return;
        }
        if (hasTicketForHolder(order.getId(), userId)) {
            return;
        }
        UserAccount user = userAccountMapper.selectById(userId);
        if (user != null && ("admin".equals(user.getRole()) || "sysadmin".equals(user.getRole()))) {
            return;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权查看该订单");
    }

    private void ensureOrderOwner(TicketOrder order) {
        String userId = StpUtil.getLoginIdAsString();
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权操作该订单");
        }
    }

    private void expireIfNeeded(TicketOrder order) {
        if ("PENDING_PAYMENT".equals(order.getStatus()) && order.getExpiresAt().isBefore(LocalDateTime.now())) {
            markExpired(order, listTickets(order.getId()));
        }
    }

    private void markExpired(TicketOrder order, List<TicketItem> tickets) {
        order.setStatus("EXPIRED");
        ticketOrderMapper.updateById(order);
        for (TicketItem ticket : tickets) {
            ticket.setStatus("VOID");
            ticketItemMapper.updateById(ticket);
        }
        boolean isZoned = !tickets.isEmpty() && tickets.get(0).getAreaInventoryId() != null;
        if (isZoned) {
            String areaInventoryId = tickets.get(0).getAreaInventoryId();
            int quantity = tickets.size();
            scheduleAreaInventoryMapper.unlockInventory(areaInventoryId, quantity);
            seatService.publishAreaInventory(order.getScheduleId(), "AREA_RELEASED", areaInventoryId);
        } else {
            List<String> seatIds = tickets.stream().map(TicketItem::getSeatId).toList();
            seatService.releaseLocks(order.getScheduleId(), seatIds);
            seatStatusPublisher.publishSeatStatus(order.getScheduleId(), "EXPIRED", "AVAILABLE", seatIds);
        }
        dashboardRefreshPublisher.publish("ORDER_EXPIRED", order.getId());
    }

    private void markCancelled(TicketOrder order, List<TicketItem> tickets) {
        order.setStatus("CANCELLED");
        ticketOrderMapper.updateById(order);
        for (TicketItem ticket : tickets) {
            ticket.setStatus("VOID");
            ticketItemMapper.updateById(ticket);
        }
        releaseReservedInventory(order, tickets, "ORDER_CANCELLED", "AVAILABLE", "AREA_RELEASED");
        dashboardRefreshPublisher.publish("ORDER_CANCELLED", order.getId());
    }

    private void markTicketsPendingRefund(TicketOrder order, List<TicketItem> tickets, boolean wholeOrder) {
        LocalDateTime now = LocalDateTime.now();
        if (!wholeOrder) {
            for (TicketItem ticket : tickets) {
                ticket.setStatus("PENDING_REFUND");
                ticket.setUpdatedAt(now);
                ticketItemMapper.updateById(ticket);
            }
        }
        if (wholeOrder) {
            order.setStatus("PENDING_REFUND");
        } else {
            order.setStatus("PAID");
        }
        order.setUpdatedAt(now);
        ticketOrderMapper.updateById(order);
    }

    private void markRefunded(TicketOrder order, List<TicketItem> tickets, String reason) {
        LocalDateTime now = LocalDateTime.now();
        for (TicketItem ticket : tickets) {
            ticket.setStatus("VOID");
            ticket.setUpdatedAt(now);
            ticketItemMapper.updateById(ticket);
        }
        boolean isZoned = !tickets.isEmpty() && tickets.get(0).getAreaInventoryId() != null;
        if (isZoned) {
            String areaInventoryId = tickets.get(0).getAreaInventoryId();
            scheduleAreaInventoryMapper.refundInventory(areaInventoryId, tickets.size());
            seatService.publishAreaInventory(order.getScheduleId(), "AREA_REFUNDED", areaInventoryId);
        } else {
            List<String> seatIds = tickets.stream().map(TicketItem::getSeatId).toList();
            for (String seatId : seatIds) {
                markSeatAvailable(order.getScheduleId(), seatId);
            }
            seatStatusPublisher.publishSeatStatus(order.getScheduleId(), reason, "AVAILABLE", seatIds);
        }
        List<TicketItem> latestTickets = listTickets(order.getId());
        boolean allVoided = !latestTickets.isEmpty()
                && latestTickets.stream().allMatch(ticket -> "VOID".equals(ticket.getStatus()));
        order.setStatus(allVoided ? "REFUNDED" : "PAID");
        order.setUpdatedAt(now);
        ticketOrderMapper.updateById(order);
        dashboardRefreshPublisher.publish("ORDER_REFUNDED", order.getId());
    }

    private void releaseReservedInventory(
            TicketOrder order,
            List<TicketItem> tickets,
            String seatReason,
            String seatStatus,
            String areaReason
    ) {
        boolean isZoned = !tickets.isEmpty() && tickets.get(0).getAreaInventoryId() != null;
        if (isZoned) {
            String areaInventoryId = tickets.get(0).getAreaInventoryId();
            scheduleAreaInventoryMapper.unlockInventory(areaInventoryId, tickets.size());
            seatService.publishAreaInventory(order.getScheduleId(), areaReason, areaInventoryId);
        } else {
            List<String> seatIds = tickets.stream().map(TicketItem::getSeatId).toList();
            seatService.releaseLocks(order.getScheduleId(), seatIds);
            seatStatusPublisher.publishSeatStatus(order.getScheduleId(), seatReason, seatStatus, seatIds);
        }
    }

    private void markSeatAvailable(String scheduleId, String seatId) {
        if (seatId == null) {
            return;
        }
        ScheduleSeat seat = scheduleSeatMapper.selectOne(new LambdaQueryWrapper<ScheduleSeat>()
                .eq(ScheduleSeat::getScheduleId, scheduleId)
                .eq(ScheduleSeat::getSeatCode, seatId)
                .last("limit 1"));
        if (seat != null) {
            seat.setStatus("AVAILABLE");
            scheduleSeatMapper.updateById(seat);
        }
    }

    private TicketItem createReservedTicket(
            String orderId,
            String scheduleId,
            String seatId,
            String holderUserId,
            String holderDisplayName
    ) {
        TicketItem ticket = new TicketItem();
        ticket.setId("tk-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18));
        ticket.setOrderId(orderId);
        ticket.setScheduleId(scheduleId);
        ticket.setSeatId(seatId);
        ticket.setTicketCode("T" + Long.toString(System.currentTimeMillis(), 36).toUpperCase() + seatId.replace("seat-", "") + "XYZ");
        ticket.setStatus("RESERVED");
        ticket.setHolderUserId(holderUserId);
        ticket.setHolderDisplayName(holderDisplayName == null || holderDisplayName.isBlank() ? holderUserId : holderDisplayName);
        return ticket;
    }

    private String findSamePendingOrder(String userId, String scheduleId, List<String> seatIds) {
        Set<String> requested = Set.copyOf(seatIds);
        List<TicketOrder> pendingOrders = ticketOrderMapper.selectList(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getUserId, userId)
                .eq(TicketOrder::getScheduleId, scheduleId)
                .eq(TicketOrder::getStatus, "PENDING_PAYMENT")
                .ge(TicketOrder::getExpiresAt, LocalDateTime.now())
                .orderByDesc(TicketOrder::getCreatedAt));

        for (TicketOrder order : pendingOrders) {
            Set<String> reserved = listTickets(order.getId()).stream()
                    .map(TicketItem::getSeatId)
                    .collect(Collectors.toSet());
            if (reserved.equals(requested)) {
                return order.getId();
            }
        }
        return null;
    }

    private String findSamePendingZonedOrder(String userId, String scheduleId, String areaInventoryId, int quantity) {
        List<TicketOrder> pendingOrders = ticketOrderMapper.selectList(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getUserId, userId)
                .eq(TicketOrder::getScheduleId, scheduleId)
                .eq(TicketOrder::getStatus, "PENDING_PAYMENT")
                .ge(TicketOrder::getExpiresAt, LocalDateTime.now())
                .orderByDesc(TicketOrder::getCreatedAt));

        for (TicketOrder order : pendingOrders) {
            List<TicketItem> tickets = listTickets(order.getId());
            if (tickets.size() == quantity && tickets.stream().allMatch(t -> areaInventoryId.equals(t.getAreaInventoryId()))) {
                return order.getId();
            }
        }
        return null;
    }

    private List<TicketItem> listTickets(String orderId) {
        return safeList(ticketItemMapper.selectList(new LambdaQueryWrapper<TicketItem>()
                .eq(TicketItem::getOrderId, orderId)));
    }

    private OrderResponse toOrderResponse(TicketOrder order) {
        String viewerId = StpUtil.getLoginIdAsString();
        boolean fullAccess = canViewAllTickets(order, viewerId);
        List<TicketItem> rawTickets = listTickets(order.getId());
        List<TicketItem> visibleTickets = fullAccess
                ? rawTickets
                : rawTickets.stream()
                        .filter(ticket -> viewerId.equals(ticket.getHolderUserId()))
                        .toList();
        List<TicketItem> billableVisibleTickets = visibleTickets.stream()
                .filter(ticket -> "UNUSED".equals(ticket.getStatus()) || "CHECKED_IN".equals(ticket.getStatus()))
                .toList();
        BigDecimal visibleAmount = fullAccess ? order.getTotalAmount() : calculateTicketAmount(order, billableVisibleTickets);

        List<TicketItemResponse> tickets = visibleTickets.stream()
                .sorted(Comparator.comparing(t -> t.getSeatId() == null ? "" : t.getSeatId()))
                .map(ticket -> {
                    String areaName = null;
                    String areaType = null;
                    String seatLabel = "不指定座位";
                    Integer rowNo = null;
                    Integer colNo = null;
                    BigDecimal price = BigDecimal.ZERO;

                    if (ticket.getAreaInventoryId() != null) {
                        ScheduleAreaInventory inv = scheduleAreaInventoryMapper.selectById(ticket.getAreaInventoryId());
                        if (inv != null) {
                            price = moneyOrZero(inv.getPrice());
                            VenueArea area = venueAreaMapper.selectById(inv.getAreaId());
                            if (area != null) {
                                areaName = area.getName();
                                areaType = area.getAreaType();
                            }
                        }
                    } else if (ticket.getSeatId() != null) {
                        ScheduleSeat seat = scheduleSeatMapper.selectOne(new LambdaQueryWrapper<ScheduleSeat>()
                                .eq(ScheduleSeat::getScheduleId, order.getScheduleId())
                                .eq(ScheduleSeat::getSeatCode, ticket.getSeatId())
                                .last("limit 1"));
                        if (seat != null) {
                            seatLabel = String.format("第 %s 排 %s 号", seat.getRowNo(), seat.getColNo());
                            rowNo = seat.getRowNo();
                            colNo = seat.getColNo();
                            if (seat.getAreaId() != null) {
                                VenueArea area = venueAreaMapper.selectById(seat.getAreaId());
                                if (area != null) {
                                    areaName = area.getName();
                                    areaType = area.getAreaType();
                                }
                            }
                            price = moneyOrZero(seat.getPrice());
                        }
                    }

                    return new TicketItemResponse(
                            ticket.getId(),
                            ticket.getOrderId(),
                            ticket.getScheduleId(),
                            ticket.getSeatId(),
                            ticket.getTicketCode(),
                            ticket.getStatus(),
                            ticket.getAreaInventoryId(),
                            areaName,
                            areaType,
                            seatLabel,
                            rowNo,
                            colNo,
                            price,
                            ticket.getHolderUserId(),
                            ticket.getHolderDisplayName()
                    );
                })
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getScheduleId(),
                showTitle(order.getScheduleId()),
                theaterName(order.getScheduleId()),
                startTime(order.getScheduleId()),
                endTime(order.getScheduleId()),
                visibleAmount,
                order.getStatus(),
                order.getCreatedAt(),
                order.getExpiresAt(),
                toRefundSummary(latestRefundRequest(order.getId(), visibleTickets, fullAccess)),
                tickets
        );
    }

    private String showTitle(String scheduleId) {
        ShowSchedule schedule = showScheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            return null;
        }
        ShowEntity show = showMapper.selectById(schedule.getShowId());
        return show == null ? null : show.getTitle();
    }

    private String theaterName(String scheduleId) {
        ShowSchedule schedule = showScheduleMapper.selectById(scheduleId);
        return schedule == null ? null : schedule.getTheaterName();
    }

    private LocalDateTime startTime(String scheduleId) {
        ShowSchedule schedule = showScheduleMapper.selectById(scheduleId);
        return schedule == null ? null : schedule.getStartTime();
    }

    private LocalDateTime endTime(String scheduleId) {
        ShowSchedule schedule = showScheduleMapper.selectById(scheduleId);
        return schedule == null ? null : schedule.getEndTime();
    }

    @Scheduled(fixedRate = 5000)
    @Transactional
    public void cleanupExpiredOrders() {
        List<TicketOrder> expiredOrders = ticketOrderMapper.selectList(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getStatus, "PENDING_PAYMENT")
                .lt(TicketOrder::getExpiresAt, LocalDateTime.now()));
        for (TicketOrder order : expiredOrders) {
            try {
                markExpired(order, listTickets(order.getId()));
            } catch (Exception e) {
                // ignore / log
            }
        }
    }

    private List<String> normalizeSeatIds(List<String> seatIds) {
        List<String> normalized = seatIds == null ? List.of() : seatIds.stream()
                .filter(seatId -> seatId != null && !seatId.isBlank())
                .distinct()
                .toList();
        if (normalized.isEmpty() || normalized.size() > 6) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择 1 到 6 个座位");
        }
        return normalized;
    }

    private List<GroupSeatHolder> normalizeGroupSeatHolders(List<GroupSeatHolder> seatHolders) {
        Map<String, GroupSeatHolder> normalized = new LinkedHashMap<>();
        if (seatHolders != null) {
            for (GroupSeatHolder holder : seatHolders) {
                if (holder == null || holder.seatId() == null || holder.seatId().isBlank()
                        || holder.userId() == null || holder.userId().isBlank()) {
                    continue;
                }
                normalized.putIfAbsent(holder.seatId(), new GroupSeatHolder(
                        holder.seatId(),
                        holder.userId(),
                        holder.displayName() == null || holder.displayName().isBlank()
                                ? holder.userId()
                                : holder.displayName()
                ));
            }
        }
        if (normalized.isEmpty() || normalized.size() > 6) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择 1 到 6 个座位");
        }
        return new ArrayList<>(normalized.values());
    }

    private boolean hasTicketForHolder(String orderId, String userId) {
        return !safeList(ticketItemMapper.selectList(new LambdaQueryWrapper<TicketItem>()
                .eq(TicketItem::getOrderId, orderId)
                .eq(TicketItem::getHolderUserId, userId)
                .last("limit 1"))).isEmpty();
    }

    private boolean canViewAllTickets(TicketOrder order, String viewerId) {
        if (order.getUserId().equals(viewerId)) {
            return true;
        }
        UserAccount user = userAccountMapper.selectById(viewerId);
        return user != null && ("admin".equals(user.getRole()) || "sysadmin".equals(user.getRole()));
    }

    private BigDecimal calculateTicketAmount(TicketOrder order, List<TicketItem> tickets) {
        if (tickets.isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (tickets.get(0).getAreaInventoryId() != null) {
            ScheduleAreaInventory inventory = scheduleAreaInventoryMapper.selectById(tickets.get(0).getAreaInventoryId());
            return inventory == null
                    ? BigDecimal.ZERO
                    : inventory.getPrice().multiply(BigDecimal.valueOf(tickets.size()));
        }
        return tickets.stream()
                .map(ticket -> ticketSeatPrice(order.getScheduleId(), ticket.getSeatId()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal moneyOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal ticketSeatPrice(String scheduleId, String seatId) {
        if (seatId == null) {
            return BigDecimal.ZERO;
        }
        ScheduleSeat seat = scheduleSeatMapper.selectOne(new LambdaQueryWrapper<ScheduleSeat>()
                .eq(ScheduleSeat::getScheduleId, scheduleId)
                .eq(ScheduleSeat::getSeatCode, seatId)
                .last("limit 1"));
        return seat == null || seat.getPrice() == null ? BigDecimal.ZERO : seat.getPrice();
    }

    private String currentDisplayName(String userId) {
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null) {
            return userId;
        }
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        return user.getUsername() == null || user.getUsername().isBlank() ? userId : user.getUsername();
    }

    private <T> List<T> safeList(List<T> rows) {
        return rows == null ? List.of() : rows;
    }
}
