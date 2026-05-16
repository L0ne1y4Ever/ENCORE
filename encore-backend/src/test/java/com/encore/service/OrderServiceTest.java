package com.encore.service;

import com.encore.dto.OrderResponse;
import com.encore.entity.ScheduleSeat;
import com.encore.entity.TicketItem;
import com.encore.entity.TicketOrder;
import com.encore.mapper.ScheduleSeatMapper;
import com.encore.mapper.TicketItemMapper;
import com.encore.mapper.TicketOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

        boolean paid = service.simulatePayment("ord-1");

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
    }

    @Test
    void expiredOrderPublishesAvailableEvent() {
        OrderService service = createService();
        TicketOrder order = pendingOrder(LocalDateTime.now().minusMinutes(1));
        TicketItem ticket = ticket();

        when(ticketOrderMapper.selectById("ord-1")).thenReturn(order);
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(ticket));

        OrderResponse response = service.getOrderDetail("ord-1");

        assertThat(response.status()).isEqualTo("EXPIRED");
        assertThat(ticket.getStatus()).isEqualTo("VOID");
        verify(seatService).releaseLocks("sch-1", List.of("seat-1-1"));
        verify(seatStatusPublisher).publishSeatStatus(
                "sch-1",
                "EXPIRED",
                "AVAILABLE",
                List.of("seat-1-1")
        );
    }

    private OrderService createService() {
        return new OrderService(
                ticketOrderMapper,
                ticketItemMapper,
                scheduleSeatMapper,
                seatService,
                seatStatusPublisher
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
