package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.encore.dto.AdminBoxOfficeResponse;
import com.encore.dto.AdminDashboardResponse;
import com.encore.dto.AdminOfflineSaleRequest;
import com.encore.dto.AdminOfflineSaleResponse;
import com.encore.dto.AdminOrderResponse;
import com.encore.dto.AdminScheduleResponse;
import com.encore.dto.ReviewRefundRequest;
import com.encore.dto.SchedulePricingRequest;
import com.encore.dto.UpdateScheduleRequest;
import com.encore.exception.BusinessException;
import com.encore.entity.RefundRequest;
import com.encore.entity.ScheduleAreaInventory;
import com.encore.entity.ScheduleSeat;
import com.encore.entity.ShowEntity;
import com.encore.entity.ShowSchedule;
import com.encore.entity.TicketItem;
import com.encore.entity.TicketOrder;
import com.encore.entity.UserAccount;
import com.encore.entity.VenueArea;
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
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {
    @Mock
    private ShowScheduleMapper showScheduleMapper;
    @Mock
    private ShowMapper showMapper;
    @Mock
    private ScheduleSeatMapper scheduleSeatMapper;
    @Mock
    private TicketOrderMapper ticketOrderMapper;
    @Mock
    private TicketItemMapper ticketItemMapper;
    @Mock
    private UserAccountMapper userAccountMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private SeatStatusPublisher seatStatusPublisher;
    @Mock
    private DashboardRefreshPublisher dashboardRefreshPublisher;
    @Mock
    private VenueAreaMapper venueAreaMapper;
    @Mock
    private ScheduleAreaInventoryMapper scheduleAreaInventoryMapper;
    @Mock
    private VenueManagementService venueManagementService;
    @Mock
    private SeatService seatService;
    @Mock
    private OrderService orderService;
    @Mock
    private RefundRequestMapper refundRequestMapper;
    @Mock
    private RefundRequestTicketMapper refundRequestTicketMapper;

    @Test
    void createOfflineSeatedSaleCreatesPaidOrderAndSellsSeat() {
        AdminService service = createService();
        ShowSchedule schedule = onSaleSchedule("SEATED");
        UserAccount buyer = user("u-buyer", "user");
        buyer.setUsername("alice");
        buyer.setDisplayName("Alice");
        ScheduleSeat seat = pricedSeat("seat-1-1", "VIP", 150);
        TicketOrder order = paidOfflineOrder("ord-offline-1", "u-buyer");
        TicketItem ticket = issuedTicket(order.getId(), "seat-1-1", null, "u-buyer", "Alice");

        when(userAccountMapper.selectById(any())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            if ("u-admin".equals(id)) return user("u-admin", "admin");
            if ("u-buyer".equals(id)) return buyer;
            return null;
        });
        when(userAccountMapper.selectOne(any())).thenReturn(buyer);
        when(showScheduleMapper.selectById("sch-1")).thenReturn(schedule);
        when(showMapper.selectById("show-1")).thenReturn(show());
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(ticket));
        when(orderService.createPaidCounterSale(
                eq(schedule),
                eq("SEATED"),
                eq("u-buyer"),
                eq("Alice"),
                eq("u-admin"),
                eq(List.of("seat-1-1")),
                eq(null),
                eq(null)
        )).thenReturn(new OrderService.CounterSaleResult(order, List.of(ticket), List.of(seat), null, null));

        AdminOfflineSaleResponse response;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");

            response = service.createOfflineSale(new AdminOfflineSaleRequest(
                    "sch-1",
                    "alice",
                    null,
                    List.of("seat-1-1"),
                    null,
                    null
            ));
        }

        assertThat(response.order().status()).isEqualTo("PAID");
        assertThat(response.totalAmount()).isEqualByComparingTo("150");
        assertThat(response.tickets()).hasSize(1);
        assertThat(response.tickets().get(0).holderUserId()).isEqualTo("u-buyer");
        assertThat(response.tickets().get(0).holderDisplayName()).isEqualTo("Alice");
        verify(orderService).createPaidCounterSale(
                schedule,
                "SEATED",
                "u-buyer",
                "Alice",
                "u-admin",
                List.of("seat-1-1"),
                null,
                null
        );
    }

    @Test
    void createOfflineSeatedSaleRejectsWhenAtomicSeatSaleFails() {
        AdminService service = createService();
        ShowSchedule schedule = onSaleSchedule("SEATED");
        UserAccount buyer = user("u-buyer", "user");
        buyer.setUsername("alice");
        buyer.setDisplayName("Alice");

        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(userAccountMapper.selectOne(any())).thenReturn(buyer);
        when(showScheduleMapper.selectById("sch-1")).thenReturn(schedule);
        when(orderService.createPaidCounterSale(
                eq(schedule),
                eq("SEATED"),
                eq("u-buyer"),
                eq("Alice"),
                eq("u-admin"),
                eq(List.of("seat-1-1")),
                eq(null),
                eq(null)
        )).thenThrow(new BusinessException(com.encore.common.ErrorCode.CONFLICT, "座位状态已变化，无法线下出票"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");

            assertThatThrownBy(() -> service.createOfflineSale(new AdminOfflineSaleRequest(
                    "sch-1",
                    "alice",
                    null,
                    List.of("seat-1-1"),
                    null,
                    null
            )))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("座位状态已变化，无法线下出票");
        }

        verify(ticketItemMapper, never()).insert(any(TicketItem.class));
        verify(orderService).createPaidCounterSale(
                schedule,
                "SEATED",
                "u-buyer",
                "Alice",
                "u-admin",
                List.of("seat-1-1"),
                null,
                null
        );
    }

    @Test
    void createOfflineAreaSaleCreatesGuestOrderAndSellsAvailableInventory() {
        AdminService service = createService();
        ShowSchedule schedule = onSaleSchedule("ZONED");
        ScheduleAreaInventory inventory = areaInventory();
        VenueArea area = area();
        AtomicReference<UserAccount> guest = new AtomicReference<>();
        TicketOrder order = paidOfflineOrder("ord-offline-1", "u-offline-guest");
        order.setTotalAmount(BigDecimal.valueOf(160));
        List<TicketItem> issuedTickets = List.of(
                issuedTicket(order.getId(), null, "inv-1", "u-offline-guest", "张三"),
                issuedTicket(order.getId(), null, "inv-1", "u-offline-guest", "张三")
        );

        when(userAccountMapper.selectById(any())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            if ("u-admin".equals(id)) return user("u-admin", "admin");
            if ("u-offline-guest".equals(id)) return guest.get();
            return null;
        });
        when(userAccountMapper.selectOne(any())).thenReturn(null);
        when(userAccountMapper.insert(any(UserAccount.class))).thenAnswer(invocation -> {
            guest.set(invocation.getArgument(0));
            return 1;
        });
        when(showScheduleMapper.selectById("sch-1")).thenReturn(schedule);
        when(showMapper.selectById("show-1")).thenReturn(show());
        when(ticketItemMapper.selectList(any())).thenReturn(issuedTickets);
        when(orderService.createPaidCounterSale(
                eq(schedule),
                eq("ZONED"),
                eq("u-offline-guest"),
                eq("张三"),
                eq("u-admin"),
                eq(null),
                eq("inv-1"),
                eq(2)
        )).thenReturn(new OrderService.CounterSaleResult(order, issuedTickets, List.of(), inventory, area));

        AdminOfflineSaleResponse response;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");

            response = service.createOfflineSale(new AdminOfflineSaleRequest(
                    "sch-1",
                    null,
                    "张三",
                    null,
                    "inv-1",
                    2
            ));
        }

        assertThat(response.order().status()).isEqualTo("PAID");
        assertThat(response.totalAmount()).isEqualByComparingTo("160");
        assertThat(response.tickets()).hasSize(2);
        assertThat(response.tickets()).allMatch(ticket -> "张三".equals(ticket.holderDisplayName()));
        assertThat(guest.get().getUsername()).isEqualTo("__offline_guest__");
        verify(orderService).createPaidCounterSale(
                schedule,
                "ZONED",
                "u-offline-guest",
                "张三",
                "u-admin",
                null,
                "inv-1",
                2
        );
    }

    @Test
    void createOfflineSaleRejectsEndedSchedule() {
        AdminService service = createService();
        ShowSchedule schedule = onSaleSchedule("SEATED");
        schedule.setEndTime(LocalDateTime.now().minusMinutes(1));

        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(showScheduleMapper.selectById("sch-1")).thenReturn(schedule);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");

            assertThatThrownBy(() -> service.createOfflineSale(new AdminOfflineSaleRequest(
                    "sch-1",
                    "alice",
                    null,
                    List.of("seat-1-1"),
                    null,
                    null
            )))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("该场次已结束，无法线下售票");
        }

        verify(ticketOrderMapper, never()).insert(any(TicketOrder.class));
    }

    @Test
    void createOfflineSaleRejectsConcertSchedule() {
        AdminService service = createService();
        ShowSchedule schedule = onSaleSchedule("SEATED");
        ShowEntity concert = show();
        concert.setCategory("Concert");

        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(showScheduleMapper.selectById("sch-1")).thenReturn(schedule);
        when(showMapper.selectById("show-1")).thenReturn(concert);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");

            assertThatThrownBy(() -> service.createOfflineSale(new AdminOfflineSaleRequest(
                    "sch-1",
                    "alice",
                    null,
                    List.of("seat-1-1"),
                    null,
                    null
            )))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("演唱会不支持线下售票");
        }

        verify(ticketOrderMapper, never()).insert(any(TicketOrder.class));
        verify(seatService, never()).lockSeatsForOwner(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void refundOrderPublishesAvailableEvent() {
        AdminService service = createService();
        TicketOrder order = paidOrder();
        TicketItem ticket = unusedTicket();
        ScheduleSeat seat = soldSeat();

        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(userAccountMapper.selectById("u-1")).thenReturn(user("u-1", "user"));
        when(ticketOrderMapper.selectById("ord-1")).thenReturn(order);
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(ticket));
        when(scheduleSeatMapper.selectOne(any())).thenReturn(seat);
        when(showScheduleMapper.selectById("sch-1")).thenReturn(schedule());
        when(showMapper.selectById("show-1")).thenReturn(show());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");

            service.refundOrder("ord-1");
        }

        verify(seatStatusPublisher).publishSeatStatus(
                "sch-1",
                "REFUNDED",
                "AVAILABLE",
                List.of("seat-1-1")
        );
        verify(dashboardRefreshPublisher).publish("ORDER_REFUNDED", "ord-1");
    }

    @Test
    void adminDirectRefundRejectsOrderWithPendingTicketRefund() {
        AdminService service = createService();
        TicketOrder order = paidOrder();
        TicketItem ticket = unusedTicket();
        ticket.setStatus("PENDING_REFUND");

        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(ticketOrderMapper.selectById("ord-1")).thenReturn(order);
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(ticket));
        when(refundRequestMapper.selectList(any())).thenReturn(List.of());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");

            assertThatThrownBy(() -> service.refundOrder("ord-1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("该订单已有待审核退票申请，请先完成审核");
        }

        verify(refundRequestMapper, never()).insert(any(RefundRequest.class));
        verify(ticketItemMapper, never()).updateById(ticket);
        verify(dashboardRefreshPublisher, never()).publish("ORDER_REFUNDED", "ord-1");
    }

    @Test
    void approveRefundRequestReleasesInventoryAndVoidsTickets() {
        AdminService service = createService();
        TicketOrder order = paidOrder();
        order.setStatus("PENDING_REFUND");
        TicketItem ticket = unusedTicket();
        ScheduleSeat seat = soldSeat();
        RefundRequest refundRequest = refundRequest("PENDING");

        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(userAccountMapper.selectById("u-1")).thenReturn(user("u-1", "user"));
        when(ticketOrderMapper.selectById("ord-1")).thenReturn(order);
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(ticket));
        when(refundRequestMapper.selectList(any())).thenReturn(List.of(refundRequest));
        when(scheduleSeatMapper.selectOne(any())).thenReturn(seat);
        when(showScheduleMapper.selectById("sch-1")).thenReturn(schedule());
        when(showMapper.selectById("show-1")).thenReturn(show());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");

            service.approveRefund("ord-1", new ReviewRefundRequest("符合规则"));
        }

        assertThat(order.getStatus()).isEqualTo("REFUNDED");
        assertThat(ticket.getStatus()).isEqualTo("VOID");
        assertThat(seat.getStatus()).isEqualTo("AVAILABLE");
        assertThat(refundRequest.getStatus()).isEqualTo("APPROVED");
        verify(refundRequestMapper).updateById(argThat((RefundRequest request) ->
                "APPROVED".equals(request.getStatus())
                        && "符合规则".equals(request.getReviewNote())
                        && "u-admin".equals(request.getReviewerId())
        ));
        verify(seatStatusPublisher).publishSeatStatus("sch-1", "REFUND_APPROVED", "AVAILABLE", List.of("seat-1-1"));
        verify(dashboardRefreshPublisher).publish("ORDER_REFUNDED", "ord-1");
    }

    @Test
    void approveTicketRefundRejectsMissingTicketScopeInsteadOfRefundingWholeOrder() {
        AdminService service = createService();
        TicketOrder order = paidOrder();
        RefundRequest refundRequest = refundRequest("PENDING");
        refundRequest.setScope("TICKET");

        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(ticketOrderMapper.selectById("ord-1")).thenReturn(order);
        when(refundRequestMapper.selectList(any())).thenReturn(List.of(refundRequest));
        when(refundRequestTicketMapper.selectList(any())).thenReturn(List.of());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");

            assertThatThrownBy(() -> service.approveRefund("ord-1", new ReviewRefundRequest("同意")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("票级退票申请缺少票据范围，无法审核");
        }

        verify(refundRequestMapper, never()).updateById(refundRequest);
        verify(ticketItemMapper, never()).updateById(any(TicketItem.class));
        verify(dashboardRefreshPublisher, never()).publish("ORDER_REFUNDED", "ord-1");
    }

    @Test
    void rejectRefundRequestRestoresPaidWithoutTouchingTickets() {
        AdminService service = createService();
        TicketOrder order = paidOrder();
        order.setStatus("PENDING_REFUND");
        TicketItem ticket = unusedTicket();
        RefundRequest refundRequest = refundRequest("PENDING");

        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(userAccountMapper.selectById("u-1")).thenReturn(user("u-1", "user"));
        when(ticketOrderMapper.selectById("ord-1")).thenReturn(order);
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(ticket));
        when(refundRequestMapper.selectList(any())).thenReturn(List.of(refundRequest));
        when(showScheduleMapper.selectById("sch-1")).thenReturn(schedule());
        when(showMapper.selectById("show-1")).thenReturn(show());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");

            service.rejectRefund("ord-1", new ReviewRefundRequest("已临近开演"));
        }

        assertThat(order.getStatus()).isEqualTo("PAID");
        assertThat(ticket.getStatus()).isEqualTo("UNUSED");
        assertThat(refundRequest.getStatus()).isEqualTo("REJECTED");
        verify(refundRequestMapper).updateById(argThat((RefundRequest request) ->
                "REJECTED".equals(request.getStatus())
                        && "已临近开演".equals(request.getReviewNote())
                        && "u-admin".equals(request.getReviewerId())
        ));
        verify(seatStatusPublisher, never()).publishSeatStatus(any(), any(), any(), any());
        verify(dashboardRefreshPublisher).publish("ORDER_REFUND_REJECTED", "ord-1");
    }

    @Test
    void updateScheduleRejectsLayoutChangeAfterCreation() {
        AdminService service = createService();
        ShowSchedule schedule = schedule();
        schedule.setLayoutId("lay-1");
        schedule.setHallId("hall-1");
        schedule.setTicketMode("SEATED");
        UpdateScheduleRequest request = new UpdateScheduleRequest(
                "show-1",
                "hall-1",
                "lay-2",
                "Main Hall",
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(2).plusHours(2),
                null,
                null,
                "ON_SALE",
                "PUBLISHED",
                "$50 - $150",
                "SEATED"
        );

        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(showScheduleMapper.selectById("sch-1")).thenReturn(schedule);
        when(showMapper.selectById("show-1")).thenReturn(show());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");

            assertThatThrownBy(() -> service.updateSchedule("sch-1", request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("排期创建后不可切换座位布局，请新建排期");
        }

        verify(showScheduleMapper, never()).updateById(any(ShowSchedule.class));
    }

    @Test
    void updateScheduleAppliesRealPricingAndAutoPriceRangeBeforeSales() {
        AdminService service = createService();
        ShowSchedule schedule = schedule();
        schedule.setLayoutId("lay-1");
        schedule.setHallId("hall-1");
        schedule.setTicketMode("SEATED");
        ScheduleSeat vip = pricedSeat("seat-1-1", "VIP", 150);
        ScheduleSeat standard = pricedSeat("seat-1-2", "A", 100);
        ScheduleSeat economy = pricedSeat("seat-1-3", "B", 50);
        UpdateScheduleRequest request = new UpdateScheduleRequest(
                "show-1",
                "hall-1",
                "lay-1",
                "Main Hall",
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(2).plusHours(2),
                null,
                null,
                "ON_SALE",
                "PUBLISHED",
                null,
                new SchedulePricingRequest(BigDecimal.valueOf(90), BigDecimal.valueOf(220), BigDecimal.valueOf(140), BigDecimal.valueOf(80)),
                "SEATED"
        );

        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(showScheduleMapper.selectById("sch-1")).thenReturn(schedule);
        when(showMapper.selectById("show-1")).thenReturn(show());
        when(scheduleSeatMapper.selectList(any())).thenReturn(List.of(vip, standard, economy));
        when(scheduleAreaInventoryMapper.selectList(any())).thenReturn(List.of());
        when(ticketItemMapper.selectList(any())).thenReturn(List.of());
        when(redisTemplate.keys(any())).thenReturn(Set.of());

        AdminScheduleResponse response;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");
            response = service.updateSchedule("sch-1", request);
        }

        assertThat(vip.getPrice()).isEqualByComparingTo("220");
        assertThat(standard.getPrice()).isEqualByComparingTo("140");
        assertThat(economy.getPrice()).isEqualByComparingTo("80");
        assertThat(response.priceRange()).isEqualTo("80 - 220");
        assertThat(response.vipPrice()).isEqualByComparingTo("220");
        assertThat(response.standardPrice()).isEqualByComparingTo("140");
        assertThat(response.economyPrice()).isEqualByComparingTo("80");
    }

    @Test
    void updateScheduleRejectsPricingChangeAfterTicketsExist() {
        AdminService service = createService();
        ShowSchedule schedule = schedule();
        schedule.setLayoutId("lay-1");
        schedule.setHallId("hall-1");
        schedule.setTicketMode("SEATED");
        ScheduleSeat vip = pricedSeat("seat-1-1", "VIP", 150);
        TicketItem ticket = unusedTicket();
        UpdateScheduleRequest request = new UpdateScheduleRequest(
                "show-1",
                "hall-1",
                "lay-1",
                "Main Hall",
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(2).plusHours(2),
                null,
                null,
                "ON_SALE",
                "PUBLISHED",
                null,
                new SchedulePricingRequest(BigDecimal.valueOf(90), BigDecimal.valueOf(220), BigDecimal.valueOf(140), BigDecimal.valueOf(80)),
                "SEATED"
        );

        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(showScheduleMapper.selectById("sch-1")).thenReturn(schedule);
        when(showMapper.selectById("show-1")).thenReturn(show());
        when(scheduleSeatMapper.selectList(any())).thenReturn(List.of(vip));
        when(scheduleAreaInventoryMapper.selectList(any())).thenReturn(List.of());
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(ticket));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");
            assertThatThrownBy(() -> service.updateSchedule("sch-1", request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("已有订单或票据的场次不可修改票价");
        }

        assertThat(vip.getPrice()).isEqualByComparingTo("150");
        verify(scheduleSeatMapper, never()).updateById(vip);
    }

    @Test
    void boxOfficeUsesDualRevenuePolicyAndCurrentPendingOrders() {
        AdminService service = createService();
        LocalDateTime now = LocalDateTime.now();
        TicketOrder paid = order("ord-paid", "PAID", BigDecimal.valueOf(180), now.minusDays(1), now.plusMinutes(10));
        TicketOrder pendingRefund = order("ord-pending-refund", "PENDING_REFUND", BigDecimal.valueOf(120), now.minusDays(1), now.plusMinutes(10));
        TicketOrder refunded = order("ord-refunded", "REFUNDED", BigDecimal.valueOf(80), now.minusDays(1), now.plusMinutes(10));
        TicketOrder pending = order("ord-pending", "PENDING_PAYMENT", BigDecimal.valueOf(50), null, now.plusMinutes(20));
        TicketOrder expiredPending = order("ord-expired", "PENDING_PAYMENT", BigDecimal.valueOf(90), null, now.minusMinutes(1));
        ShowSchedule schedule = schedule();
        ShowEntity show = show();

        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(ticketOrderMapper.selectList(any())).thenReturn(List.of(paid, pendingRefund, refunded, pending, expiredPending));
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(
                ticket("tk-paid-1", "ord-paid", "UNUSED"),
                ticket("tk-paid-2", "ord-paid", "CHECKED_IN"),
                ticket("tk-pending-refund", "ord-pending-refund", "UNUSED"),
                ticket("tk-refunded", "ord-refunded", "VOID"),
                ticket("tk-pending", "ord-pending", "PENDING")
        ));
        when(showScheduleMapper.selectList(any())).thenReturn(List.of(schedule));
        when(showMapper.selectList(any())).thenReturn(List.of(show));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");

            AdminBoxOfficeResponse response = service.boxOffice("LAST_30_DAYS", null, null, null, null);

            assertThat(response.globalSummary().salesRevenue()).isEqualByComparingTo("380");
            assertThat(response.globalSummary().netRevenue()).isEqualByComparingTo("300");
            assertThat(response.summary().salesRevenue()).isEqualByComparingTo("380");
            assertThat(response.summary().refundAmount()).isEqualByComparingTo("80");
            assertThat(response.summary().netRevenue()).isEqualByComparingTo("300");
            assertThat(response.summary().pendingAmount()).isEqualByComparingTo("50");
            assertThat(response.summary().paidTickets()).isEqualTo(4);
            assertThat(response.summary().refundedTickets()).isEqualTo(1);
            assertThat(response.summary().validTickets()).isEqualTo(3);
            assertThat(response.summary().checkedInTickets()).isEqualTo(1);
            assertThat(response.summary().attendanceRate()).isEqualByComparingTo("33.3");
            assertThat(response.categories()).hasSize(1);
            assertThat(response.categories().get(0).category()).isEqualTo("Drama");
            assertThat(response.categories().get(0).showCount()).isEqualTo(1);
            assertThat(response.shows()).hasSize(1);
            assertThat(response.shows().get(0).scheduleCount()).isEqualTo(1);
            assertThat(response.schedules()).hasSize(1);
            assertThat(response.schedules().get(0).netRevenue()).isEqualByComparingTo("300");
        }
    }

    @Test
    void listOrdersReportsActiveTicketCountExcludingVoidedTickets() {
        AdminService service = createService();
        TicketOrder order = paidOrder();
        TicketItem unused = ticket("tk-unused", "ord-1", "UNUSED");
        TicketItem checkedIn = ticket("tk-checked", "ord-1", "CHECKED_IN");
        TicketItem voided = ticket("tk-void", "ord-1", "VOID");

        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(userAccountMapper.selectById("u-1")).thenReturn(user("u-1", "user"));
        when(ticketOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(unused, checkedIn, voided));
        when(showScheduleMapper.selectById("sch-1")).thenReturn(schedule());
        when(showMapper.selectById("show-1")).thenReturn(show());
        when(refundRequestMapper.selectList(any())).thenReturn(List.of());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");

            List<AdminOrderResponse> responses = service.listOrders();

            assertThat(responses).singleElement().satisfies(response -> {
                assertThat(response.ticketCount()).isEqualTo(2);
                assertThat(response.checkedInCount()).isEqualTo(1);
            });
        }
    }

    @Test
    void dashboardUsesBoxOfficePolicyForRevenueTicketsAndTrends() {
        AdminService service = createService();
        LocalDateTime now = LocalDateTime.now();
        TicketOrder paid = order("ord-paid", "PAID", BigDecimal.valueOf(180), now.minusDays(1), now.plusMinutes(10));
        TicketOrder pendingRefund = order("ord-pending-refund", "PENDING_REFUND", BigDecimal.valueOf(120), now.minusDays(1), now.plusMinutes(10));
        TicketOrder refunded = order("ord-refunded", "REFUNDED", BigDecimal.valueOf(80), now.minusDays(1), now.plusMinutes(10));
        TicketOrder pending = order("ord-pending", "PENDING_PAYMENT", BigDecimal.valueOf(50), null, now.plusMinutes(20));
        ShowSchedule schedule = schedule();
        ShowEntity show = show();

        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(ticketOrderMapper.selectList(any())).thenReturn(List.of(paid, pendingRefund, refunded, pending));
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(
                ticket("tk-paid-1", "ord-paid", "UNUSED"),
                ticket("tk-paid-2", "ord-paid", "CHECKED_IN"),
                ticket("tk-pending-refund", "ord-pending-refund", "PENDING_REFUND"),
                ticket("tk-refunded", "ord-refunded", "VOID"),
                ticket("tk-pending", "ord-pending", "PENDING")
        ));
        when(showScheduleMapper.selectList(any())).thenReturn(List.of(schedule));
        when(showMapper.selectList(any())).thenReturn(List.of(show));
        when(showMapper.selectCount(any())).thenReturn(1L);
        when(refundRequestMapper.selectList(any())).thenReturn(List.of());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");

            AdminDashboardResponse response = service.dashboard();

            assertThat(response.totalRevenue()).isEqualByComparingTo("300");
            assertThat(response.ticketsSold()).isEqualTo(3);
            assertThat(response.activeShows()).isEqualTo(1);
            assertThat(response.avgAttendance()).isEqualByComparingTo("33.3");
            assertThat(response.checkInSummary().checkedIn()).isEqualTo(1);
            assertThat(response.checkInSummary().unused()).isEqualTo(2);
            assertThat(response.checkInSummary().voided()).isEqualTo(1);
            assertThat(response.financeSummary().salesRevenue()).isEqualByComparingTo("380");
            assertThat(response.financeSummary().netRevenue()).isEqualByComparingTo("300");
            assertThat(response.financeSummary().validTickets()).isEqualTo(3);

            AdminDashboardResponse.SalesTrendItem activeTrend = response.salesTrend().stream()
                    .filter(item -> item.date().equals(now.minusDays(1).toLocalDate()))
                    .findFirst()
                    .orElseThrow();
            assertThat(activeTrend.revenue()).isEqualByComparingTo("300");
            assertThat(activeTrend.ticketCount()).isEqualTo(3);
            assertThat(response.topShows()).hasSize(1);
            assertThat(response.topShows().get(0).revenue()).isEqualByComparingTo("300");
            assertThat(response.topShows().get(0).ticketCount()).isEqualTo(3);
        }
    }

    @Test
    void boxOfficeCanFilterByCategoryBeforeShow() {
        AdminService service = createService();
        LocalDateTime now = LocalDateTime.now();
        TicketOrder dramaOrder = order("ord-drama", "PAID", BigDecimal.valueOf(180), now.minusDays(1), now.plusMinutes(10));
        dramaOrder.setScheduleId("sch-drama");
        TicketOrder movieOrder = order("ord-movie", "PAID", BigDecimal.valueOf(90), now.minusDays(1), now.plusMinutes(10));
        movieOrder.setScheduleId("sch-movie");
        ShowSchedule dramaSchedule = schedule();
        dramaSchedule.setId("sch-drama");
        dramaSchedule.setShowId("show-drama");
        ShowSchedule movieSchedule = schedule();
        movieSchedule.setId("sch-movie");
        movieSchedule.setShowId("show-movie");
        ShowEntity drama = show();
        drama.setId("show-drama");
        drama.setTitle("茶馆");
        drama.setCategory("Drama");
        ShowEntity movie = show();
        movie.setId("show-movie");
        movie.setTitle("Dune");
        movie.setCategory("Movie");

        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(ticketOrderMapper.selectList(any())).thenReturn(List.of(dramaOrder, movieOrder));
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(
                ticket("tk-drama", "ord-drama", "UNUSED"),
                ticket("tk-movie", "ord-movie", "UNUSED")
        ));
        when(showScheduleMapper.selectList(any())).thenReturn(List.of(dramaSchedule, movieSchedule));
        when(showMapper.selectList(any())).thenReturn(List.of(drama, movie));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");

            AdminBoxOfficeResponse response = service.boxOffice("LAST_30_DAYS", null, null, null, "Movie");

            assertThat(response.globalSummary().salesRevenue()).isEqualByComparingTo("270");
            assertThat(response.summary().salesRevenue()).isEqualByComparingTo("90");
            assertThat(response.categories()).hasSize(1);
            assertThat(response.categories().get(0).category()).isEqualTo("Movie");
            assertThat(response.shows()).extracting(AdminBoxOfficeResponse.ShowRow::showTitle).containsExactly("Dune");
            assertThat(response.schedules()).extracting(AdminBoxOfficeResponse.ScheduleRow::scheduleId).containsExactly("sch-movie");
        }
    }

    @Test
    void listShowCategoriesGroupsExistingShows() {
        AdminService service = createService();
        ShowEntity drama = show();
        drama.setId("show-drama");
        drama.setCategory("Drama");
        ShowEntity movie = show();
        movie.setId("show-movie");
        movie.setCategory("Movie");

        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(showMapper.selectList(any())).thenReturn(List.of(drama, movie));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");

            assertThat(service.listShowCategories())
                    .extracting(option -> option.category() + ":" + option.showCount())
                    .containsExactly("Drama:1", "Movie:1");
        }
    }

    @Test
    void listShowOptionsSearchesWithinCategoryWithLimit() {
        AdminService service = createService();
        ShowEntity drama = show();
        drama.setId("show-drama");
        drama.setTitle("茶馆");
        drama.setCategory("Drama");
        ShowEntity movie = show();
        movie.setId("show-movie");
        movie.setTitle("Dune");
        movie.setCategory("Movie");
        ShowSchedule movieSchedule = schedule();
        movieSchedule.setShowId("show-movie");

        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(showScheduleMapper.selectList(any())).thenReturn(List.of(movieSchedule));
        when(showMapper.selectList(any())).thenReturn(List.of(drama, movie));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");

            assertThat(service.listShowOptions("Movie", "du", 30))
                    .extracting(option -> option.id() + ":" + option.category() + ":" + option.scheduleCount())
                    .containsExactly("show-movie:Movie:1");
        }
    }

    private AdminService createService() {
        return new AdminService(
                showScheduleMapper,
                showMapper,
                scheduleSeatMapper,
                ticketOrderMapper,
                ticketItemMapper,
                userAccountMapper,
                redisTemplate,
                seatStatusPublisher,
                dashboardRefreshPublisher,
                venueAreaMapper,
                scheduleAreaInventoryMapper,
                venueManagementService,
                seatService,
                orderService,
                refundRequestMapper,
                refundRequestTicketMapper
        );
    }

    private TicketOrder paidOrder() {
        TicketOrder order = new TicketOrder();
        order.setId("ord-1");
        order.setUserId("u-1");
        order.setScheduleId("sch-1");
        order.setTotalAmount(BigDecimal.valueOf(150));
        order.setStatus("PAID");
        order.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        order.setPaidAt(LocalDateTime.now().minusMinutes(5));
        return order;
    }

    private TicketOrder paidOfflineOrder(String orderId, String userId) {
        TicketOrder order = new TicketOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setScheduleId("sch-1");
        order.setTotalAmount(BigDecimal.valueOf(150));
        order.setStatus("PAID");
        order.setOrderChannel("OFFLINE");
        order.setPaymentMethod("COUNTER");
        order.setCashierUserId("u-admin");
        order.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        order.setExpiresAt(LocalDateTime.now().minusMinutes(2));
        order.setPaidAt(LocalDateTime.now().minusMinutes(1));
        return order;
    }

    private TicketItem issuedTicket(String orderId, String seatId, String areaInventoryId, String holderUserId, String holderDisplayName) {
        TicketItem ticket = new TicketItem();
        ticket.setId("tk-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        ticket.setOrderId(orderId);
        ticket.setScheduleId("sch-1");
        ticket.setSeatId(seatId);
        ticket.setAreaInventoryId(areaInventoryId);
        ticket.setTicketCode("T" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        ticket.setStatus("UNUSED");
        ticket.setHolderUserId(holderUserId);
        ticket.setHolderDisplayName(holderDisplayName);
        return ticket;
    }

    private RefundRequest refundRequest(String status) {
        RefundRequest request = new RefundRequest();
        request.setId("rr-1");
        request.setOrderId("ord-1");
        request.setUserId("u-1");
        request.setStatus(status);
        request.setSource("USER_REVIEW");
        request.setReason("临近开演");
        request.setRequestedAt(LocalDateTime.now().minusMinutes(5));
        request.setUpdatedAt(LocalDateTime.now().minusMinutes(5));
        return request;
    }

    private TicketItem unusedTicket() {
        TicketItem ticket = new TicketItem();
        ticket.setId("tk-1");
        ticket.setOrderId("ord-1");
        ticket.setScheduleId("sch-1");
        ticket.setSeatId("seat-1-1");
        ticket.setStatus("UNUSED");
        return ticket;
    }

    private TicketItem ticket(String id, String orderId, String status) {
        TicketItem ticket = new TicketItem();
        ticket.setId(id);
        ticket.setOrderId(orderId);
        ticket.setScheduleId("sch-1");
        ticket.setSeatId(id);
        ticket.setStatus(status);
        return ticket;
    }

    private ScheduleSeat soldSeat() {
        ScheduleSeat seat = new ScheduleSeat();
        seat.setId("sch-1:seat-1-1");
        seat.setScheduleId("sch-1");
        seat.setSeatCode("seat-1-1");
        seat.setStatus("SOLD");
        return seat;
    }

    private ScheduleSeat pricedSeat(String seatCode, String section, int price) {
        ScheduleSeat seat = new ScheduleSeat();
        seat.setId("sch-1:" + seatCode);
        seat.setScheduleId("sch-1");
        seat.setSeatCode(seatCode);
        seat.setRowNo(1);
        seat.setColNo(1);
        seat.setSection(section);
        seat.setStatus("AVAILABLE");
        seat.setPrice(BigDecimal.valueOf(price));
        return seat;
    }

    private ShowSchedule schedule() {
        ShowSchedule schedule = new ShowSchedule();
        schedule.setId("sch-1");
        schedule.setShowId("show-1");
        schedule.setTheaterName("Main Hall");
        schedule.setStartTime(LocalDateTime.now().plusDays(1));
        return schedule;
    }

    private ShowSchedule onSaleSchedule(String ticketMode) {
        ShowSchedule schedule = schedule();
        schedule.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        schedule.setSaleStartTime(LocalDateTime.now().minusDays(1));
        schedule.setSaleEndTime(LocalDateTime.now().plusHours(1));
        schedule.setStatus("ON_SALE");
        schedule.setPublishStatus("PUBLISHED");
        schedule.setTicketMode(ticketMode);
        return schedule;
    }

    private ScheduleAreaInventory areaInventory() {
        ScheduleAreaInventory inventory = new ScheduleAreaInventory();
        inventory.setId("inv-1");
        inventory.setScheduleId("sch-1");
        inventory.setAreaId("area-1");
        inventory.setPrice(BigDecimal.valueOf(80));
        inventory.setTotalCount(20);
        inventory.setAvailableCount(10);
        inventory.setLockedCount(0);
        inventory.setSoldCount(0);
        inventory.setStatus("AVAILABLE");
        return inventory;
    }

    private VenueArea area() {
        VenueArea area = new VenueArea();
        area.setId("area-1");
        area.setName("站席区");
        area.setAreaType("STANDING");
        area.setIsSeated(false);
        return area;
    }

    private ShowEntity show() {
        ShowEntity show = new ShowEntity();
        show.setId("show-1");
        show.setTitle("Test Show");
        show.setCategory("Drama");
        return show;
    }

    private UserAccount user(String id, String role) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setUsername(id);
        user.setRole(role);
        user.setStatus("ACTIVE");
        return user;
    }

    private TicketOrder order(String id, String status, BigDecimal amount, LocalDateTime paidAt, LocalDateTime expiresAt) {
        TicketOrder order = new TicketOrder();
        order.setId(id);
        order.setUserId("u-1");
        order.setScheduleId("sch-1");
        order.setTotalAmount(amount);
        order.setStatus(status);
        order.setCreatedAt(LocalDateTime.now().minusMinutes(20));
        order.setPaidAt(paidAt);
        order.setExpiresAt(expiresAt);
        return order;
    }
}
