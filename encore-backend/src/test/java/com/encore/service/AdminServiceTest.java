package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.encore.dto.UpdateScheduleRequest;
import com.encore.exception.BusinessException;
import com.encore.entity.ScheduleSeat;
import com.encore.entity.ShowEntity;
import com.encore.entity.ShowSchedule;
import com.encore.entity.TicketItem;
import com.encore.entity.TicketOrder;
import com.encore.entity.UserAccount;
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
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
                seatService
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

    private TicketItem unusedTicket() {
        TicketItem ticket = new TicketItem();
        ticket.setId("tk-1");
        ticket.setOrderId("ord-1");
        ticket.setScheduleId("sch-1");
        ticket.setSeatId("seat-1-1");
        ticket.setStatus("UNUSED");
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

    private ShowSchedule schedule() {
        ShowSchedule schedule = new ShowSchedule();
        schedule.setId("sch-1");
        schedule.setShowId("show-1");
        schedule.setTheaterName("Main Hall");
        schedule.setStartTime(LocalDateTime.now().plusDays(1));
        return schedule;
    }

    private ShowEntity show() {
        ShowEntity show = new ShowEntity();
        show.setId("show-1");
        show.setTitle("Test Show");
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
}
