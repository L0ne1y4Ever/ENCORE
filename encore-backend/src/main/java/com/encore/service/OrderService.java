package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.encore.common.ErrorCode;
import com.encore.dto.CreateOrderRequest;
import com.encore.dto.OrderResponse;
import com.encore.dto.TicketItemResponse;
import com.encore.entity.ScheduleSeat;
import com.encore.entity.TicketItem;
import com.encore.entity.TicketOrder;
import com.encore.exception.BusinessException;
import com.encore.mapper.ScheduleSeatMapper;
import com.encore.mapper.TicketItemMapper;
import com.encore.mapper.TicketOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final TicketOrderMapper ticketOrderMapper;
    private final TicketItemMapper ticketItemMapper;
    private final ScheduleSeatMapper scheduleSeatMapper;
    private final SeatService seatService;
    private final SeatStatusPublisher seatStatusPublisher;

    public OrderService(
            TicketOrderMapper ticketOrderMapper,
            TicketItemMapper ticketItemMapper,
            ScheduleSeatMapper scheduleSeatMapper,
            SeatService seatService,
            SeatStatusPublisher seatStatusPublisher
    ) {
        this.ticketOrderMapper = ticketOrderMapper;
        this.ticketItemMapper = ticketItemMapper;
        this.scheduleSeatMapper = scheduleSeatMapper;
        this.seatService = seatService;
        this.seatStatusPublisher = seatStatusPublisher;
    }

    @Transactional
    public String createOrder(CreateOrderRequest request) {
        String userId = StpUtil.getLoginIdAsString();
        seatService.ensureOnSaleSchedule(request.scheduleId());
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
        return orderId;
    }

    public OrderResponse getOrderDetail(String orderId) {
        TicketOrder order = getOrder(orderId);
        expireIfNeeded(order);
        return toOrderResponse(getOrder(orderId));
    }

    @Transactional
    public boolean simulatePayment(String orderId) {
        TicketOrder order = getOrder(orderId);
        expireIfNeeded(order);
        order = getOrder(orderId);
        if ("PAID".equals(order.getStatus())) {
            return true;
        }
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            return false;
        }

        List<TicketItem> tickets = listTickets(orderId);
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

        order.setStatus("PAID");
        order.setPaidAt(LocalDateTime.now());
        ticketOrderMapper.updateById(order);
        List<String> seatIds = tickets.stream().map(TicketItem::getSeatId).toList();
        seatService.releaseLocks(order.getScheduleId(), seatIds);
        seatStatusPublisher.publishSeatStatus(order.getScheduleId(), "SOLD", "SOLD", seatIds);
        return true;
    }

    private TicketOrder getOrder(String orderId) {
        TicketOrder order = ticketOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return order;
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
        List<String> seatIds = tickets.stream().map(TicketItem::getSeatId).toList();
        seatService.releaseLocks(order.getScheduleId(), seatIds);
        seatStatusPublisher.publishSeatStatus(order.getScheduleId(), "EXPIRED", "AVAILABLE", seatIds);
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

    private List<TicketItem> listTickets(String orderId) {
        return ticketItemMapper.selectList(new LambdaQueryWrapper<TicketItem>()
                .eq(TicketItem::getOrderId, orderId)
                .orderByAsc(TicketItem::getSeatId));
    }

    private OrderResponse toOrderResponse(TicketOrder order) {
        List<TicketItemResponse> tickets = listTickets(order.getId()).stream()
                .sorted(Comparator.comparing(TicketItem::getSeatId))
                .map(ticket -> new TicketItemResponse(
                        ticket.getId(),
                        ticket.getOrderId(),
                        ticket.getScheduleId(),
                        ticket.getSeatId(),
                        ticket.getTicketCode(),
                        ticket.getStatus()
                ))
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getScheduleId(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getExpiresAt(),
                tickets
        );
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
