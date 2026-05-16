package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.encore.common.ErrorCode;
import com.encore.dto.CheckInResponse;
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

import java.time.LocalDateTime;

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
            assertThat(response.checkedInAt()).isNotNull();
            verify(ticketItemMapper).updateById(argThat((TicketItem updated) ->
                    "CHECKED_IN".equals(updated.getStatus()) && updated.getUpdatedAt() != null
            ));
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

    private CheckInService createService() {
        return new CheckInService(
                ticketItemMapper,
                ticketOrderMapper,
                showScheduleMapper,
                showMapper,
                userAccountMapper
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
        ShowSchedule schedule = new ShowSchedule();
        schedule.setId("sch-101");
        schedule.setShowId("s-001");
        schedule.setTheaterName("Main Hall");
        schedule.setStartTime(LocalDateTime.of(2026, 5, 24, 19, 30));
        return schedule;
    }

    private ShowEntity show() {
        ShowEntity show = new ShowEntity();
        show.setId("s-001");
        show.setTitle("THE PHANTOM OF THE OPERA");
        return show;
    }
}
