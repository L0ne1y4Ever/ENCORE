package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.encore.dto.OrderResponse;
import com.encore.entity.ScheduleSeat;
import com.encore.entity.TicketItem;
import com.encore.entity.TicketOrder;
import com.encore.exception.BusinessException;
import com.encore.mapper.ScheduleSeatMapper;
import com.encore.mapper.ScheduleAreaInventoryMapper;
import com.encore.mapper.ShowMapper;
import com.encore.mapper.ShowScheduleMapper;
import com.encore.mapper.TicketItemMapper;
import com.encore.mapper.TicketOrderMapper;
import com.encore.mapper.UserAccountMapper;
import com.encore.mapper.VenueAreaMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock
    private TicketOrderMapper ticketOrderMapper;
    @Mock
    private TicketItemMapper ticketItemMapper;
    @Mock
    private ScheduleSeatMapper scheduleSeatMapper;
    @Mock
    private SeatService seatService;
    @Mock
    private SeatStatusPublisher seatStatusPublisher;
    @Mock
    private DashboardRefreshPublisher dashboardRefreshPublisher;
    @Mock
    private ScheduleAreaInventoryMapper scheduleAreaInventoryMapper;
    @Mock
    private VenueAreaMapper venueAreaMapper;
    @Mock
    private ShowScheduleMapper showScheduleMapper;
    @Mock
    private ShowMapper showMapper;
    @Mock
    private UserAccountMapper userAccountMapper;

    @Test
    void simulatePaymentPublishesSoldEvent() {
        OrderService service = createService();
        TicketOrder order = pendingOrder(LocalDateTime.now().plusMinutes(10));
        TicketItem ticket = ticket();
        ScheduleSeat seat = availableSeat();

        when(ticketOrderMapper.selectById("ord-1")).thenReturn(order);
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(ticket));
        when(seatService.isLockedByOrder("sch-1", "seat-1-1", "ord-1")).thenReturn(true);
        when(scheduleSeatMapper.selectOne(any())).thenReturn(seat);

        boolean paid;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-1");
            paid = service.simulatePayment("ord-1");
        }

        assertThat(paid).isTrue();
        assertThat(order.getStatus()).isEqualTo("PAID");
        assertThat(seat.getStatus()).isEqualTo("SOLD");
        verify(seatService).releaseLocks("sch-1", List.of("seat-1-1"));
        verify(seatStatusPublisher).publishSeatStatus(
                "sch-1",
                "SOLD",
                "SOLD",
                List.of("seat-1-1")
        );
        verify(dashboardRefreshPublisher).publish("ORDER_PAID", "ord-1");
    }

    @Test
    void expiredOrderPublishesAvailableEvent() {
        OrderService service = createService();
        TicketOrder order = pendingOrder(LocalDateTime.now().minusMinutes(1));
        TicketItem ticket = ticket();

        when(ticketOrderMapper.selectById("ord-1")).thenReturn(order);
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(ticket));

        OrderResponse response;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-1");
            response = service.getOrderDetail("ord-1");
        }

        assertThat(response.status()).isEqualTo("EXPIRED");
        assertThat(ticket.getStatus()).isEqualTo("VOID");
        verify(seatService).releaseLocks("sch-1", List.of("seat-1-1"));
        verify(seatStatusPublisher).publishSeatStatus(
                "sch-1",
                "EXPIRED",
                "AVAILABLE",
                List.of("seat-1-1")
        );
        verify(dashboardRefreshPublisher).publish("ORDER_EXPIRED", "ord-1");
    }

    @Test
    void listMyOrdersReturnsCurrentUserOrders() {
        OrderService service = createService();
        TicketOrder order = pendingOrder(LocalDateTime.now().plusMinutes(10));
        order.setStatus("PAID");
        TicketItem ticket = ticket();
        ticket.setStatus("UNUSED");

        when(ticketOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(ticketOrderMapper.selectById("ord-1")).thenReturn(order);
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(ticket));

        List<OrderResponse> responses;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-1");
            responses = service.listMyOrders();
        }

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo("ord-1");
        assertThat(responses.get(0).tickets()).hasSize(1);
    }

    @Test
    void getOrderDetailRejectsOtherUserOrder() {
        OrderService service = createService();
        TicketOrder order = pendingOrder(LocalDateTime.now().plusMinutes(10));
        order.setUserId("u-2");

        when(ticketOrderMapper.selectById("ord-1")).thenReturn(order);
        when(userAccountMapper.selectById("u-1")).thenReturn(null);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-1");
            assertThatThrownBy(() -> service.getOrderDetail("ord-1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("无权查看该订单");
        }
    }

    private OrderService createService() {
        return new OrderService(
                ticketOrderMapper,
                ticketItemMapper,
                scheduleSeatMapper,
                seatService,
                seatStatusPublisher,
                dashboardRefreshPublisher,
                scheduleAreaInventoryMapper,
                venueAreaMapper,
                showScheduleMapper,
                showMapper,
                userAccountMapper
        );
    }

    private TicketOrder pendingOrder(LocalDateTime expiresAt) {
        TicketOrder order = new TicketOrder();
        order.setId("ord-1");
        order.setUserId("u-1");
        order.setScheduleId("sch-1");
        order.setTotalAmount(BigDecimal.valueOf(150));
        order.setStatus("PENDING_PAYMENT");
        order.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        order.setExpiresAt(expiresAt);
        return order;
    }

    private TicketItem ticket() {
        TicketItem ticket = new TicketItem();
        ticket.setId("tk-1");
        ticket.setOrderId("ord-1");
        ticket.setScheduleId("sch-1");
        ticket.setSeatId("seat-1-1");
        ticket.setTicketCode("TK-1");
        ticket.setStatus("RESERVED");
        return ticket;
    }

    private ScheduleSeat availableSeat() {
        ScheduleSeat seat = new ScheduleSeat();
        seat.setId("sch-1:seat-1-1");
        seat.setScheduleId("sch-1");
        seat.setSeatCode("seat-1-1");
        seat.setStatus("AVAILABLE");
        return seat;
    }
}
