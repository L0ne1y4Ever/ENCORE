package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.encore.common.ErrorCode;
import com.encore.dto.CreateOrderRequest;
import com.encore.dto.OrderResponse;
import com.encore.dto.TicketItemResponse;
import com.encore.entity.ScheduleSeat;
import com.encore.entity.ShowEntity;
import com.encore.entity.ShowSchedule;
import com.encore.entity.TicketItem;
import com.encore.entity.TicketOrder;
import com.encore.entity.ScheduleAreaInventory;
import com.encore.entity.UserAccount;
import com.encore.entity.VenueArea;
import com.encore.exception.BusinessException;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {
    private static final Duration PAYMENT_TTL = Duration.ofMinutes(15);
    private static final Duration SELF_SERVICE_REFUND_DEADLINE = Duration.ofHours(2);

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
            UserAccountMapper userAccountMapper
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
    }

    @Transactional
    public String createOrder(CreateOrderRequest request) {
        String userId = StpUtil.getLoginIdAsString();
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
                .map(seatId -> createReservedTicket(orderId, request.scheduleId(), seatId))
                .toList();
        tickets.forEach(ticketItemMapper::insert);
        seatService.attachOrderToLocks(request.scheduleId(), seatIds, orderId, PAYMENT_TTL);
        releaseSeatLocksOnRollback(request.scheduleId(), seatIds);
        return orderId;
    }

    @Transactional
    public String createGroupOrder(String scheduleId, List<String> requestedSeatIds, String lockOwner) {
        String userId = StpUtil.getLoginIdAsString();
        seatService.ensureOnSaleSchedule(scheduleId);
        List<String> seatIds = normalizeSeatIds(requestedSeatIds);
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
                .map(seatId -> createReservedTicket(orderId, scheduleId, seatId))
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

    @Transactional
    public List<OrderResponse> listMyOrders() {
        String userId = StpUtil.getLoginIdAsString();
        return ticketOrderMapper.selectList(new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getUserId, userId)
                        .orderByDesc(TicketOrder::getCreatedAt))
                .stream()
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
        TicketOrder order = getOrder(orderId);
        ensureOrderOwner(order);
        expireIfNeeded(order);
        order = getOrder(orderId);
        if ("REFUNDED".equals(order.getStatus())) {
            return toOrderResponse(order);
        }
        if (!"PAID".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅已支付订单可申请退票");
        }
        ShowSchedule schedule = showScheduleMapper.selectById(order.getScheduleId());
        if (schedule == null || schedule.getStartTime() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "场次信息异常，无法自助退票");
        }
        LocalDateTime refundDeadline = schedule.getStartTime().minus(SELF_SERVICE_REFUND_DEADLINE);
        if (!LocalDateTime.now().isBefore(refundDeadline)) {
            throw new BusinessException(ErrorCode.CONFLICT, "开演前 2 小时内不可自助退票，请联系管理员处理");
        }
        List<TicketItem> tickets = listTickets(orderId);
        if (tickets.stream().anyMatch(ticket -> "CHECKED_IN".equals(ticket.getStatus()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "已核销票据不可退票");
        }
        markRefunded(order, tickets, "USER_REFUNDED");
        return toOrderResponse(getOrder(orderId));
    }

    private TicketOrder getOrder(String orderId) {
        TicketOrder order = ticketOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private void ensureCanViewOrder(TicketOrder order) {
        String userId = StpUtil.getLoginIdAsString();
        if (order.getUserId().equals(userId)) {
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

    private void markRefunded(TicketOrder order, List<TicketItem> tickets, String reason) {
        order.setStatus("REFUNDED");
        ticketOrderMapper.updateById(order);
        for (TicketItem ticket : tickets) {
            ticket.setStatus("VOID");
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

    private TicketItem createReservedTicket(String orderId, String scheduleId, String seatId) {
        TicketItem ticket = new TicketItem();
        ticket.setId("tk-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18));
        ticket.setOrderId(orderId);
        ticket.setScheduleId(scheduleId);
        ticket.setSeatId(seatId);
        ticket.setTicketCode("T" + Long.toString(System.currentTimeMillis(), 36).toUpperCase() + seatId.replace("seat-", "") + "XYZ");
        ticket.setStatus("RESERVED");
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
        return ticketItemMapper.selectList(new LambdaQueryWrapper<TicketItem>()
                .eq(TicketItem::getOrderId, orderId));
    }

    private OrderResponse toOrderResponse(TicketOrder order) {
        List<TicketItemResponse> tickets = listTickets(order.getId()).stream()
                .sorted(Comparator.comparing(t -> t.getSeatId() == null ? "" : t.getSeatId()))
                .map(ticket -> {
                    String areaName = null;
                    String areaType = null;
                    String seatLabel = "不指定座位";
                    Integer rowNo = null;
                    Integer colNo = null;

                    if (ticket.getAreaInventoryId() != null) {
                        ScheduleAreaInventory inv = scheduleAreaInventoryMapper.selectById(ticket.getAreaInventoryId());
                        if (inv != null) {
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
                            colNo
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
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getExpiresAt(),
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
}
