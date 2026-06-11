package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.encore.dto.OrderResponse;
import com.encore.entity.RefundRequest;
import com.encore.entity.ScheduleSeat;
import com.encore.entity.ShowSchedule;
import com.encore.entity.TicketItem;
import com.encore.entity.TicketOrder;
import com.encore.exception.BusinessException;
import com.encore.mapper.ScheduleSeatMapper;
import com.encore.mapper.ScheduleAreaInventoryMapper;
import com.encore.mapper.RefundRequestMapper;
import com.encore.mapper.RefundRequestTicketMapper;
import com.encore.mapper.ShowMapper;
import com.encore.mapper.ShowScheduleMapper;
import com.encore.mapper.TicketItemMapper;
import com.encore.mapper.TicketOrderMapper;
import com.encore.mapper.UserAccountMapper;
import com.encore.mapper.VenueAreaMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @Mock
    private RefundRequestMapper refundRequestMapper;
    @Mock
    private RefundRequestTicketMapper refundRequestTicketMapper;

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
    void createGroupOrderAssignsTicketHolders() {
        OrderService service = createService();
        List<OrderService.GroupSeatHolder> holders = List.of(
                new OrderService.GroupSeatHolder("seat-1-1", "u-101", "发起人"),
                new OrderService.GroupSeatHolder("seat-1-2", "u-102", "拼座好友")
        );
        when(seatService.findSeats("sch-1", List.of("seat-1-1", "seat-1-2")))
                .thenReturn(List.of(seat("seat-1-1", 150), seat("seat-1-2", 100)));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-101");
            service.createGroupOrder("sch-1", holders, "group:g-test");
        }

        verify(seatService).ensureLocksOwnedBy("sch-1", List.of("seat-1-1", "seat-1-2"), "group:g-test");
        verify(seatService).transferLocksOwner(
                eq("sch-1"),
                eq(List.of("seat-1-1", "seat-1-2")),
                eq("group:g-test"),
                any(),
                eq(Duration.ofMinutes(15))
        );
        ArgumentCaptor<TicketItem> ticketCaptor = ArgumentCaptor.forClass(TicketItem.class);
        verify(ticketItemMapper, times(2)).insert(ticketCaptor.capture());
        assertThat(ticketCaptor.getAllValues())
                .anySatisfy(ticket -> {
                    assertThat(ticket.getSeatId()).isEqualTo("seat-1-1");
                    assertThat(ticket.getHolderUserId()).isEqualTo("u-101");
                    assertThat(ticket.getHolderDisplayName()).isEqualTo("发起人");
                })
                .anySatisfy(ticket -> {
                    assertThat(ticket.getSeatId()).isEqualTo("seat-1-2");
                    assertThat(ticket.getHolderUserId()).isEqualTo("u-102");
                    assertThat(ticket.getHolderDisplayName()).isEqualTo("拼座好友");
                });
    }

    @Test
    void repairGroupTicketHoldersCorrectsInviteeTicketOwner() {
        OrderService service = createService();
        TicketOrder order = paidGroupOrder();
        TicketItem hostTicket = heldTicket("tk-1", "seat-1-1", "u-101", "发起人");
        TicketItem inviteeTicket = heldTicket("tk-2", "seat-1-2", "u-101", "发起人");
        List<OrderService.GroupSeatHolder> holders = List.of(
                new OrderService.GroupSeatHolder("seat-1-1", "u-101", "发起人"),
                new OrderService.GroupSeatHolder("seat-1-2", "u-102", "拼座好友")
        );

        when(ticketOrderMapper.selectById("ord-1")).thenReturn(order);
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(hostTicket, inviteeTicket));

        service.repairGroupTicketHolders("ord-1", holders);

        assertThat(hostTicket.getHolderUserId()).isEqualTo("u-101");
        assertThat(inviteeTicket.getHolderUserId()).isEqualTo("u-102");
        assertThat(inviteeTicket.getHolderDisplayName()).isEqualTo("拼座好友");
        verify(ticketItemMapper).updateById(inviteeTicket);
    }

    @Test
    void inviteeCanViewOnlyOwnHeldTicket() {
        OrderService service = createService();
        TicketOrder order = paidGroupOrder();
        TicketItem hostTicket = heldTicket("tk-1", "seat-1-1", "u-101", "发起人");
        TicketItem inviteeTicket = heldTicket("tk-2", "seat-1-2", "u-102", "拼座好友");
        ShowSchedule schedule = schedule(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2));

        when(ticketOrderMapper.selectById("ord-1")).thenReturn(order);
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(hostTicket, inviteeTicket));
        when(userAccountMapper.selectById("u-102")).thenReturn(user("u-102", "拼座好友", "user"));
        when(showScheduleMapper.selectById("sch-1")).thenReturn(schedule);
        when(scheduleSeatMapper.selectOne(any())).thenReturn(seat("seat-1-2", 100));

        OrderResponse response;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-102");
            response = service.getOrderDetail("ord-1");
        }

        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(response.endTime()).isEqualTo(schedule.getEndTime());
        assertThat(response.tickets()).singleElement().satisfies(ticket -> {
            assertThat(ticket.seatId()).isEqualTo("seat-1-2");
            assertThat(ticket.price()).isEqualByComparingTo(BigDecimal.valueOf(100));
            assertThat(ticket.holderUserId()).isEqualTo("u-102");
            assertThat(ticket.holderDisplayName()).isEqualTo("拼座好友");
        });
    }

    @Test
    void inviteeVisibleAmountExcludesPendingAndVoidedTickets() {
        OrderService service = createService();
        TicketOrder order = paidGroupOrder();
        TicketItem activeTicket = heldTicket("tk-2", "seat-1-2", "u-102", "拼座好友");
        TicketItem pendingRefundTicket = heldTicket("tk-3", "seat-1-3", "u-102", "拼座好友");
        pendingRefundTicket.setStatus("PENDING_REFUND");
        TicketItem voidedTicket = heldTicket("tk-4", "seat-1-4", "u-102", "拼座好友");
        voidedTicket.setStatus("VOID");

        when(ticketOrderMapper.selectById("ord-1")).thenReturn(order);
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(activeTicket, pendingRefundTicket, voidedTicket));
        when(userAccountMapper.selectById("u-102")).thenReturn(user("u-102", "拼座好友", "user"));
        when(scheduleSeatMapper.selectOne(any()))
                .thenReturn(
                        seat("seat-1-2", 100),
                        seat("seat-1-2", 100),
                        seat("seat-1-3", 80),
                        seat("seat-1-4", 60)
                );

        OrderResponse response;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-102");
            response = service.getOrderDetail("ord-1");
        }

        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(response.tickets()).hasSize(3);
    }

    @Test
    void groupOrderOwnerCanViewAllHeldTickets() {
        OrderService service = createService();
        TicketOrder order = paidGroupOrder();
        TicketItem hostTicket = heldTicket("tk-1", "seat-1-1", "u-101", "发起人");
        TicketItem inviteeTicket = heldTicket("tk-2", "seat-1-2", "u-102", "拼座好友");

        when(ticketOrderMapper.selectById("ord-1")).thenReturn(order);
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(hostTicket, inviteeTicket));
        when(scheduleSeatMapper.selectOne(any()))
                .thenReturn(seat("seat-1-1", 150), seat("seat-1-2", 100));

        OrderResponse response;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-101");
            response = service.getOrderDetail("ord-1");
        }

        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(250));
        assertThat(response.tickets()).hasSize(2);
        assertThat(response.tickets())
                .extracting(ticket -> ticket.holderUserId())
                .containsExactlyInAnyOrder("u-101", "u-102");
    }

    @Test
    void groupOrderRejectsViewerWithoutHeldTicket() {
        OrderService service = createService();
        TicketOrder order = paidGroupOrder();

        when(ticketOrderMapper.selectById("ord-1")).thenReturn(order);
        when(ticketItemMapper.selectList(any())).thenReturn(List.of());
        when(userAccountMapper.selectById("u-999")).thenReturn(user("u-999", "陌生用户", "user"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-999");
            assertThatThrownBy(() -> service.getOrderDetail("ord-1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("无权查看该订单");
        }
    }

    @Test
    void listMyOrdersIncludesPaidHeldGroupOrderForInvitee() {
        OrderService service = createService();
        TicketOrder order = paidGroupOrder();
        TicketItem inviteeTicket = heldTicket("tk-2", "seat-1-2", "u-102", "拼座好友");

        when(ticketOrderMapper.selectList(any())).thenReturn(List.of());
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(inviteeTicket));
        when(ticketOrderMapper.selectById("ord-1")).thenReturn(order);
        when(userAccountMapper.selectById("u-102")).thenReturn(user("u-102", "拼座好友", "user"));
        when(scheduleSeatMapper.selectOne(any())).thenReturn(seat("seat-1-2", 100));

        List<OrderResponse> responses;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-102");
            responses = service.listMyOrders();
        }

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo("ord-1");
        assertThat(responses.get(0).totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(responses.get(0).tickets()).singleElement()
                .extracting(ticket -> ticket.holderUserId())
                .isEqualTo("u-102");
    }

    @Test
    void cancelPendingOrderReleasesSeatLocks() {
        OrderService service = createService();
        TicketOrder order = pendingOrder(LocalDateTime.now().plusMinutes(10));
        TicketItem ticket = ticket();

        when(ticketOrderMapper.selectById("ord-1")).thenReturn(order);
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(ticket));

        OrderResponse response;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-1");
            response = service.cancelOrder("ord-1");
        }

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(ticket.getStatus()).isEqualTo("VOID");
        verify(seatService).releaseLocks("sch-1", List.of("seat-1-1"));
        verify(seatStatusPublisher).publishSeatStatus("sch-1", "ORDER_CANCELLED", "AVAILABLE", List.of("seat-1-1"));
        verify(dashboardRefreshPublisher).publish("ORDER_CANCELLED", "ord-1");
    }

    @Test
    void refundPaidOrderRestoresUnsoldSeatsBeforeDeadline() {
        OrderService service = createService();
        TicketOrder order = pendingOrder(LocalDateTime.now().plusMinutes(10));
        order.setStatus("PAID");
        TicketItem ticket = ticket();
        ticket.setStatus("UNUSED");
        ShowSchedule schedule = new ShowSchedule();
        schedule.setId("sch-1");
        schedule.setStartTime(LocalDateTime.now().plusDays(1));
        schedule.setTheaterName("Main Hall");
        ScheduleSeat soldSeat = availableSeat();
        soldSeat.setStatus("SOLD");

        when(ticketOrderMapper.selectById("ord-1")).thenReturn(order);
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(ticket));
        when(showScheduleMapper.selectById("sch-1")).thenReturn(schedule);
        when(scheduleSeatMapper.selectOne(any())).thenReturn(soldSeat);

        OrderResponse response;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-1");
            response = service.refundOrder("ord-1");
        }

        assertThat(response.status()).isEqualTo("REFUNDED");
        assertThat(ticket.getStatus()).isEqualTo("VOID");
        assertThat(soldSeat.getStatus()).isEqualTo("AVAILABLE");
        verify(scheduleSeatMapper).updateById(soldSeat);
        verify(seatStatusPublisher).publishSeatStatus("sch-1", "USER_REFUNDED", "AVAILABLE", List.of("seat-1-1"));
        verify(dashboardRefreshPublisher).publish("ORDER_REFUNDED", "ord-1");
    }

    @Test
    void lateRefundRequestBecomesPendingReviewWithoutReleasingSeat() {
        OrderService service = createService();
        TicketOrder order = pendingOrder(LocalDateTime.now().plusMinutes(10));
        order.setStatus("PAID");
        order.setPaidAt(LocalDateTime.now().minusMinutes(5));
        TicketItem ticket = ticket();
        ticket.setStatus("UNUSED");
        ShowSchedule schedule = new ShowSchedule();
        schedule.setId("sch-1");
        schedule.setStartTime(LocalDateTime.now().plusMinutes(90));
        schedule.setTheaterName("Main Hall");

        when(ticketOrderMapper.selectById("ord-1")).thenReturn(order);
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(ticket));
        when(showScheduleMapper.selectById("sch-1")).thenReturn(schedule);

        OrderResponse response;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-1");
            response = service.refundOrder("ord-1");
        }

        assertThat(response.status()).isEqualTo("PENDING_REFUND");
        assertThat(ticket.getStatus()).isEqualTo("UNUSED");
        verify(refundRequestMapper).insert(argThat((RefundRequest request) ->
                "ord-1".equals(request.getOrderId())
                        && "PENDING".equals(request.getStatus())
                        && "USER_REVIEW".equals(request.getSource())
        ));
        verify(seatStatusPublisher, never()).publishSeatStatus(any(), any(), any(), any());
        verify(dashboardRefreshPublisher).publish("ORDER_REFUND_REQUESTED", "ord-1");
    }

    @Test
    void refundAfterShowEndIsRejected() {
        OrderService service = createService();
        TicketOrder order = pendingOrder(LocalDateTime.now().plusMinutes(10));
        order.setStatus("PAID");
        TicketItem ticket = ticket();
        ticket.setStatus("UNUSED");
        ShowSchedule schedule = schedule(LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(1));

        when(ticketOrderMapper.selectById("ord-1")).thenReturn(order);
        when(showScheduleMapper.selectById("sch-1")).thenReturn(schedule);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-1");
            assertThatThrownBy(() -> service.refundOrder("ord-1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("演出已结束，无法申请退票");
        }

        verify(ticketItemMapper, never()).updateById(ticket);
        verify(refundRequestMapper, never()).insert(any(RefundRequest.class));
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
                userAccountMapper,
                refundRequestMapper,
                refundRequestTicketMapper
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

    private TicketOrder paidGroupOrder() {
        TicketOrder order = new TicketOrder();
        order.setId("ord-1");
        order.setUserId("u-101");
        order.setScheduleId("sch-1");
        order.setTotalAmount(BigDecimal.valueOf(250));
        order.setStatus("PAID");
        order.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        order.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        order.setPaidAt(LocalDateTime.now().minusMinutes(1));
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

    private TicketItem heldTicket(String id, String seatId, String holderUserId, String holderDisplayName) {
        TicketItem ticket = ticket();
        ticket.setId(id);
        ticket.setSeatId(seatId);
        ticket.setTicketCode(id.toUpperCase());
        ticket.setStatus("UNUSED");
        ticket.setHolderUserId(holderUserId);
        ticket.setHolderDisplayName(holderDisplayName);
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

    private ScheduleSeat seat(String seatId, int price) {
        ScheduleSeat seat = new ScheduleSeat();
        seat.setId("sch-1:" + seatId);
        seat.setScheduleId("sch-1");
        seat.setSeatCode(seatId);
        seat.setRowNo(seatId.endsWith("2") ? 1 : 1);
        seat.setColNo(seatId.endsWith("2") ? 2 : 1);
        seat.setStatus("AVAILABLE");
        seat.setPrice(BigDecimal.valueOf(price));
        return seat;
    }

    private ShowSchedule schedule(LocalDateTime startTime, LocalDateTime endTime) {
        ShowSchedule schedule = new ShowSchedule();
        schedule.setId("sch-1");
        schedule.setStartTime(startTime);
        schedule.setEndTime(endTime);
        schedule.setTheaterName("Main Hall");
        return schedule;
    }

    private com.encore.entity.UserAccount user(String id, String displayName, String role) {
        com.encore.entity.UserAccount user = new com.encore.entity.UserAccount();
        user.setId(id);
        user.setUsername(id);
        user.setDisplayName(displayName);
        user.setRole(role);
        user.setStatus("ACTIVE");
        return user;
    }
}
