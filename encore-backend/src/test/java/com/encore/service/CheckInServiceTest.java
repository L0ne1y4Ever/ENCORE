package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.encore.common.ErrorCode;
import com.encore.dto.CheckInResponse;
import com.encore.dto.CheckInScheduleResponse;
import com.encore.entity.ShowEntity;
import com.encore.entity.ShowSchedule;
import com.encore.entity.TicketItem;
import com.encore.entity.TicketOrder;
import com.encore.entity.UserAccount;
import com.encore.exception.BusinessException;
import com.encore.mapper.ShowMapper;
import com.encore.mapper.ShowScheduleMapper;
import com.encore.mapper.TicketItemMapper;
import com.encore.mapper.TicketOrderMapper;
import com.encore.mapper.UserAccountMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckInServiceTest {
    private static final ZoneId TEST_ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate SHOW_DATE = LocalDate.of(2026, 5, 24);
    private static final LocalDateTime SHOW_START = LocalDateTime.of(SHOW_DATE, LocalTime.of(19, 30));
    private static final LocalDateTime SHOW_END = LocalDateTime.of(SHOW_DATE, LocalTime.of(22, 0));
    private static final LocalDateTime WINDOW_NOW = LocalDateTime.of(SHOW_DATE, LocalTime.of(18, 0));

    @Mock
    private TicketItemMapper ticketItemMapper;
    @Mock
    private TicketOrderMapper ticketOrderMapper;
    @Mock
    private ShowScheduleMapper showScheduleMapper;
    @Mock
    private ShowMapper showMapper;
    @Mock
    private UserAccountMapper userAccountMapper;
    @Mock
    private DashboardRefreshPublisher dashboardRefreshPublisher;

    @Test
    void verifyMarksUnusedPaidTicketAsCheckedIn() {
        CheckInService service = createService();
        TicketItem ticket = ticket("UNUSED");
        TicketOrder order = paidOrder();
        ShowSchedule schedule = schedule();
        ShowEntity show = show();

        when(userAccountMapper.selectById("u-801")).thenReturn(user("checker"));
        when(ticketItemMapper.selectOne(any())).thenReturn(ticket);
        when(ticketOrderMapper.selectById(ticket.getOrderId())).thenReturn(order);
        when(showScheduleMapper.selectById(ticket.getScheduleId())).thenReturn(schedule);
        when(showMapper.selectById(schedule.getShowId())).thenReturn(show);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-801");

            CheckInResponse response = service.verify(" TK-VALID ");

            assertThat(response.ticketCode()).isEqualTo("TK-VALID");
            assertThat(response.showTitle()).isEqualTo("THE PHANTOM OF THE OPERA");
            assertThat(response.status()).isEqualTo("CHECKED_IN");
            assertThat(response.checkedInAt()).isEqualTo(WINDOW_NOW);
            verify(ticketItemMapper).updateById(argThat((TicketItem updated) ->
                    "CHECKED_IN".equals(updated.getStatus()) && WINDOW_NOW.equals(updated.getUpdatedAt())
            ));
            verify(dashboardRefreshPublisher).publish("TICKET_CHECKED_IN", ticket.getId());
        }
    }

    @Test
    void verifyWithBoundScheduleMarksMatchingTicketAsCheckedIn() {
        CheckInService service = createService();
        TicketItem ticket = ticket("UNUSED");
        ShowSchedule schedule = schedule();

        when(userAccountMapper.selectById("u-801")).thenReturn(user("checker"));
        when(showScheduleMapper.selectById("sch-101")).thenReturn(schedule);
        when(ticketItemMapper.selectOne(any())).thenReturn(ticket);
        when(ticketOrderMapper.selectById(ticket.getOrderId())).thenReturn(paidOrder());
        when(showMapper.selectById(schedule.getShowId())).thenReturn(show());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-801");

            CheckInResponse response = service.verify("TK-VALID", "sch-101");

            assertThat(response.scheduleId()).isEqualTo("sch-101");
            assertThat(response.status()).isEqualTo("CHECKED_IN");
            verify(ticketItemMapper).updateById(argThat((TicketItem updated) ->
                    "CHECKED_IN".equals(updated.getStatus()) && WINDOW_NOW.equals(updated.getUpdatedAt())
            ));
            verify(dashboardRefreshPublisher).publish("TICKET_CHECKED_IN", ticket.getId());
        }
    }

    @Test
    void verifyRejectsTicketOutsideBoundSchedule() {
        CheckInService service = createService();
        TicketItem ticket = ticket("UNUSED");
        ticket.setScheduleId("sch-102");

        when(userAccountMapper.selectById("u-801")).thenReturn(user("checker"));
        when(showScheduleMapper.selectById("sch-101")).thenReturn(schedule());
        when(ticketItemMapper.selectOne(any())).thenReturn(ticket);
        when(ticketOrderMapper.selectById(ticket.getOrderId())).thenReturn(paidOrder());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-801");

            assertThatThrownBy(() -> service.verify("TK-VALID", "sch-101"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("票据不属于当前检票场次")
                    .extracting("code")
                    .isEqualTo(ErrorCode.CONFLICT);
            verify(ticketItemMapper, never()).updateById(any(TicketItem.class));
            verify(dashboardRefreshPublisher, never()).publish(any(), any());
        }
    }

    @Test
    void verifyRejectsMissingBoundScheduleBeforeTicketLookup() {
        CheckInService service = createService();

        when(userAccountMapper.selectById("u-801")).thenReturn(user("checker"));
        when(showScheduleMapper.selectById("missing")).thenReturn(null);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-801");

            assertThatThrownBy(() -> service.verify("TK-VALID", "missing"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("场次不存在，无法检票")
                    .extracting("code")
                    .isEqualTo(ErrorCode.CONFLICT);
            verify(ticketItemMapper, never()).selectOne(any());
            verify(ticketItemMapper, never()).updateById(any(TicketItem.class));
        }
    }

    @Test
    void verifyRejectsCancelledBoundScheduleBeforeTicketLookup() {
        CheckInService service = createService();
        ShowSchedule schedule = schedule();
        schedule.setStatus("CANCELLED");

        when(userAccountMapper.selectById("u-801")).thenReturn(user("checker"));
        when(showScheduleMapper.selectById("sch-101")).thenReturn(schedule);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-801");

            assertThatThrownBy(() -> service.verify("TK-VALID", "sch-101"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("场次已取消，无法检票")
                    .extracting("code")
                    .isEqualTo(ErrorCode.CONFLICT);
            verify(ticketItemMapper, never()).selectOne(any());
        }
    }

    @Test
    void verifyRejectsTooEarlyBoundScheduleBeforeTicketLookup() {
        CheckInService service = createService(SHOW_START.minusHours(2).minusSeconds(1));

        when(userAccountMapper.selectById("u-801")).thenReturn(user("checker"));
        when(showScheduleMapper.selectById("sch-101")).thenReturn(schedule());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-801");

            assertThatThrownBy(() -> service.verify("TK-VALID", "sch-101"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("未到检票时间，开演前 2 小时开放检票")
                    .extracting("code")
                    .isEqualTo(ErrorCode.CONFLICT);
            verify(ticketItemMapper, never()).selectOne(any());
        }
    }

    @Test
    void verifyRejectsEndedBoundScheduleBeforeTicketLookup() {
        CheckInService service = createService(SHOW_END.plusSeconds(1));

        when(userAccountMapper.selectById("u-801")).thenReturn(user("checker"));
        when(showScheduleMapper.selectById("sch-101")).thenReturn(schedule());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-801");

            assertThatThrownBy(() -> service.verify("TK-VALID", "sch-101"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("演出已结束，无法检票")
                    .extracting("code")
                    .isEqualTo(ErrorCode.CONFLICT);
            verify(ticketItemMapper, never()).selectOne(any());
        }
    }

    @Test
    void verifyAllowsCheckInAtOpeningBoundary() {
        CheckInService service = createService(SHOW_START.minusHours(2));
        TicketItem ticket = ticket("UNUSED");
        ShowSchedule schedule = schedule();

        when(userAccountMapper.selectById("u-801")).thenReturn(user("checker"));
        when(ticketItemMapper.selectOne(any())).thenReturn(ticket);
        when(ticketOrderMapper.selectById(ticket.getOrderId())).thenReturn(paidOrder());
        when(showScheduleMapper.selectById(ticket.getScheduleId())).thenReturn(schedule);
        when(showMapper.selectById(schedule.getShowId())).thenReturn(show());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-801");

            CheckInResponse response = service.verify("TK-VALID");

            assertThat(response.status()).isEqualTo("CHECKED_IN");
            assertThat(response.checkedInAt()).isEqualTo(SHOW_START.minusHours(2));
            verify(ticketItemMapper).updateById(any(TicketItem.class));
        }
    }

    @Test
    void verifyAllowsCheckInAtScheduleEndBoundary() {
        CheckInService service = createService(SHOW_END);
        TicketItem ticket = ticket("UNUSED");
        ShowSchedule schedule = schedule();

        when(userAccountMapper.selectById("u-801")).thenReturn(user("checker"));
        when(ticketItemMapper.selectOne(any())).thenReturn(ticket);
        when(ticketOrderMapper.selectById(ticket.getOrderId())).thenReturn(paidOrder());
        when(showScheduleMapper.selectById(ticket.getScheduleId())).thenReturn(schedule);
        when(showMapper.selectById(schedule.getShowId())).thenReturn(show());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-801");

            CheckInResponse response = service.verify("TK-VALID");

            assertThat(response.status()).isEqualTo("CHECKED_IN");
            assertThat(response.checkedInAt()).isEqualTo(SHOW_END);
            verify(ticketItemMapper).updateById(any(TicketItem.class));
        }
    }

    @Test
    void verifyRejectsTicketBeforeCheckInWindow() {
        CheckInService service = createService(SHOW_START.minusHours(2).minusSeconds(1));
        TicketItem ticket = ticket("UNUSED");

        when(userAccountMapper.selectById("u-801")).thenReturn(user("checker"));
        when(ticketItemMapper.selectOne(any())).thenReturn(ticket);
        when(ticketOrderMapper.selectById(ticket.getOrderId())).thenReturn(paidOrder());
        when(showScheduleMapper.selectById(ticket.getScheduleId())).thenReturn(schedule());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-801");

            assertThatThrownBy(() -> service.verify("TK-VALID"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("未到检票时间，开演前 2 小时开放检票")
                    .extracting("code")
                    .isEqualTo(ErrorCode.CONFLICT);
            verify(ticketItemMapper, never()).updateById(any(TicketItem.class));
        }
    }

    @Test
    void verifyRejectsTicketAfterScheduleEnd() {
        CheckInService service = createService(SHOW_END.plusSeconds(1));
        TicketItem ticket = ticket("UNUSED");

        when(userAccountMapper.selectById("u-801")).thenReturn(user("checker"));
        when(ticketItemMapper.selectOne(any())).thenReturn(ticket);
        when(ticketOrderMapper.selectById(ticket.getOrderId())).thenReturn(paidOrder());
        when(showScheduleMapper.selectById(ticket.getScheduleId())).thenReturn(schedule());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-801");

            assertThatThrownBy(() -> service.verify("TK-VALID"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("演出已结束，无法检票")
                    .extracting("code")
                    .isEqualTo(ErrorCode.CONFLICT);
            verify(ticketItemMapper, never()).updateById(any(TicketItem.class));
        }
    }

    @Test
    void verifyRejectsCancelledSchedule() {
        CheckInService service = createService();
        TicketItem ticket = ticket("UNUSED");
        ShowSchedule schedule = schedule();
        schedule.setStatus("CANCELLED");

        when(userAccountMapper.selectById("u-801")).thenReturn(user("checker"));
        when(ticketItemMapper.selectOne(any())).thenReturn(ticket);
        when(ticketOrderMapper.selectById(ticket.getOrderId())).thenReturn(paidOrder());
        when(showScheduleMapper.selectById(ticket.getScheduleId())).thenReturn(schedule);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-801");

            assertThatThrownBy(() -> service.verify("TK-VALID"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("场次已取消，无法检票")
                    .extracting("code")
                    .isEqualTo(ErrorCode.CONFLICT);
            verify(ticketItemMapper, never()).updateById(any(TicketItem.class));
        }
    }

    @Test
    void verifyRejectsMissingSchedule() {
        CheckInService service = createService();
        TicketItem ticket = ticket("UNUSED");

        when(userAccountMapper.selectById("u-801")).thenReturn(user("checker"));
        when(ticketItemMapper.selectOne(any())).thenReturn(ticket);
        when(ticketOrderMapper.selectById(ticket.getOrderId())).thenReturn(paidOrder());
        when(showScheduleMapper.selectById(ticket.getScheduleId())).thenReturn(null);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-801");

            assertThatThrownBy(() -> service.verify("TK-VALID"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("场次不存在，无法检票")
                    .extracting("code")
                    .isEqualTo(ErrorCode.CONFLICT);
            verify(ticketItemMapper, never()).updateById(any(TicketItem.class));
        }
    }

    @Test
    void verifyRejectsDuplicateCheckIn() {
        CheckInService service = createService();
        TicketItem ticket = ticket("CHECKED_IN");

        when(userAccountMapper.selectById("u-801")).thenReturn(user("checker"));
        when(ticketItemMapper.selectOne(any())).thenReturn(ticket);
        when(ticketOrderMapper.selectById(ticket.getOrderId())).thenReturn(paidOrder());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-801");

            assertThatThrownBy(() -> service.verify("TK-VALID"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.CONFLICT);
            verify(ticketItemMapper, never()).updateById(any(TicketItem.class));
        }
    }

    @Test
    void verifyRejectsUserWithoutCheckInRole() {
        CheckInService service = createService();
        when(userAccountMapper.selectById("u-101")).thenReturn(user("user"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-101");

            assertThatThrownBy(() -> service.verify("TK-VALID"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
            verify(ticketItemMapper, never()).selectOne(any());
        }
    }

    @Test
    void listCheckInSchedulesAllowsCheckerAndSortsOpenSchedulesFirst() {
        CheckInService service = createService();
        ShowSchedule futureSchedule = schedule(
                "sch-201",
                SHOW_START.plusDays(1),
                SHOW_END.plusDays(1),
                "ON_SALE"
        );

        when(userAccountMapper.selectById("u-801")).thenReturn(user("checker"));
        when(showScheduleMapper.selectList(any())).thenReturn(List.of(futureSchedule, schedule()));
        when(showMapper.selectById("s-001")).thenReturn(show());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-801");

            List<CheckInScheduleResponse> schedules = service.listCheckInSchedules();

            assertThat(schedules).hasSize(2);
            assertThat(schedules.get(0).id()).isEqualTo("sch-101");
            assertThat(schedules.get(0).checkInOpen()).isTrue();
            assertThat(schedules.get(1).id()).isEqualTo("sch-201");
            assertThat(schedules.get(1).checkInOpen()).isFalse();
        }
    }

    @Test
    void listCheckInSchedulesAllowsAdminAndSysadmin() {
        CheckInService service = createService();

        when(userAccountMapper.selectById("u-admin")).thenReturn(user("admin"));
        when(userAccountMapper.selectById("u-sys")).thenReturn(user("sysadmin"));
        when(showScheduleMapper.selectList(any())).thenReturn(List.of());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");
            assertThat(service.listCheckInSchedules()).isEmpty();

            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-sys");
            assertThat(service.listCheckInSchedules()).isEmpty();
        }
    }

    @Test
    void listCheckInSchedulesRejectsUserRole() {
        CheckInService service = createService();
        when(userAccountMapper.selectById("u-101")).thenReturn(user("user"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-101");

            assertThatThrownBy(service::listCheckInSchedules)
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
            verify(showScheduleMapper, never()).selectList(any());
        }
    }

    private CheckInService createService() {
        return createService(WINDOW_NOW);
    }

    private CheckInService createService(LocalDateTime now) {
        return new CheckInService(
                ticketItemMapper,
                ticketOrderMapper,
                showScheduleMapper,
                showMapper,
                userAccountMapper,
                Clock.fixed(now.atZone(TEST_ZONE).toInstant(), TEST_ZONE),
                dashboardRefreshPublisher
        );
    }

    private UserAccount user(String role) {
        UserAccount user = new UserAccount();
        user.setId("u-801");
        user.setRole(role);
        return user;
    }

    private TicketItem ticket(String status) {
        TicketItem ticket = new TicketItem();
        ticket.setId("tk-001");
        ticket.setOrderId("ord-001");
        ticket.setScheduleId("sch-101");
        ticket.setSeatId("seat-1-1");
        ticket.setTicketCode("TK-VALID");
        ticket.setStatus(status);
        return ticket;
    }

    private TicketOrder paidOrder() {
        TicketOrder order = new TicketOrder();
        order.setId("ord-001");
        order.setStatus("PAID");
        return order;
    }

    private ShowSchedule schedule() {
        return schedule("sch-101", SHOW_START, SHOW_END, "ON_SALE");
    }

    private ShowSchedule schedule(String id, LocalDateTime startTime, LocalDateTime endTime, String status) {
        ShowSchedule schedule = new ShowSchedule();
        schedule.setId(id);
        schedule.setShowId("s-001");
        schedule.setTheaterName("Main Hall");
        schedule.setStartTime(startTime);
        schedule.setEndTime(endTime);
        schedule.setStatus(status);
        return schedule;
    }

    private ShowEntity show() {
        ShowEntity show = new ShowEntity();
        show.setId("s-001");
        show.setTitle("THE PHANTOM OF THE OPERA");
        return show;
    }
}
