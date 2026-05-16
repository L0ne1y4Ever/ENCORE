package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class CheckInService {
    private static final Set<String> CHECKIN_ROLES = Set.of("checker", "admin", "sysadmin");
    private static final Duration CHECKIN_OPEN_BEFORE_START = Duration.ofHours(2);

    private final TicketItemMapper ticketItemMapper;
    private final TicketOrderMapper ticketOrderMapper;
    private final ShowScheduleMapper showScheduleMapper;
    private final ShowMapper showMapper;
    private final UserAccountMapper userAccountMapper;
    private final Clock clock;
    private final DashboardRefreshPublisher dashboardRefreshPublisher;

    public CheckInService(
            TicketItemMapper ticketItemMapper,
            TicketOrderMapper ticketOrderMapper,
            ShowScheduleMapper showScheduleMapper,
            ShowMapper showMapper,
            UserAccountMapper userAccountMapper,
            Clock clock,
            DashboardRefreshPublisher dashboardRefreshPublisher
    ) {
        this.ticketItemMapper = ticketItemMapper;
        this.ticketOrderMapper = ticketOrderMapper;
        this.showScheduleMapper = showScheduleMapper;
        this.showMapper = showMapper;
        this.userAccountMapper = userAccountMapper;
        this.clock = clock;
        this.dashboardRefreshPublisher = dashboardRefreshPublisher;
    }

    @Transactional
    public CheckInResponse verify(String ticketCode) {
        return verify(ticketCode, null);
    }

    @Transactional
    public CheckInResponse verify(String ticketCode, String currentScheduleId) {
        ensureCheckInRole();
        if (!StringUtils.hasText(ticketCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "票码不能为空");
        }

        ShowSchedule boundSchedule = null;
        if (StringUtils.hasText(currentScheduleId)) {
            boundSchedule = showScheduleMapper.selectById(currentScheduleId.trim());
            validateScheduleForCheckIn(boundSchedule);
        }

        TicketItem ticket = ticketItemMapper.selectOne(new LambdaQueryWrapper<TicketItem>()
                .eq(TicketItem::getTicketCode, ticketCode.trim())
                .last("limit 1"));
        if (ticket == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "票码不存在");
        }

        TicketOrder order = ticketOrderMapper.selectById(ticket.getOrderId());
        if (order == null || !"PAID".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "订单未支付或已失效");
        }
        if ("CHECKED_IN".equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "票据已核销，不能重复入场");
        }
        if (!"UNUSED".equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "票据状态不可核销");
        }

        if (boundSchedule != null && !boundSchedule.getId().equals(ticket.getScheduleId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "票据不属于当前检票场次");
        }

        ShowSchedule schedule = boundSchedule == null ? showScheduleMapper.selectById(ticket.getScheduleId()) : boundSchedule;
        if (boundSchedule == null) {
            validateScheduleForCheckIn(schedule);
        }

        LocalDateTime checkedInAt = LocalDateTime.now(clock);
        ticket.setStatus("CHECKED_IN");
        ticket.setUpdatedAt(checkedInAt);
        ticketItemMapper.updateById(ticket);
        dashboardRefreshPublisher.publish("TICKET_CHECKED_IN", ticket.getId());

        ShowEntity show = schedule == null ? null : showMapper.selectById(schedule.getShowId());
        return toResponse(ticket, schedule, show, checkedInAt);
    }

    public List<CheckInScheduleResponse> listCheckInSchedules() {
        ensureCheckInRole();
        return showScheduleMapper.selectList(new LambdaQueryWrapper<ShowSchedule>()
                        .ne(ShowSchedule::getStatus, "CANCELLED"))
                .stream()
                .sorted(Comparator
                        .comparing((ShowSchedule schedule) -> !isCheckInOpen(schedule))
                        .thenComparing(
                                ShowSchedule::getStartTime,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ))
                .map(this::toScheduleResponse)
                .toList();
    }

    private void validateScheduleForCheckIn(ShowSchedule schedule) {
        if (schedule == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "场次不存在，无法检票");
        }
        if ("CANCELLED".equals(schedule.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "场次已取消，无法检票");
        }
        if (schedule.getStartTime() == null || schedule.getEndTime() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "场次时间异常，无法检票");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime checkInStart = schedule.getStartTime().minus(CHECKIN_OPEN_BEFORE_START);
        if (now.isBefore(checkInStart)) {
            throw new BusinessException(ErrorCode.CONFLICT, "未到检票时间，开演前 2 小时开放检票");
        }
        if (now.isAfter(schedule.getEndTime())) {
            throw new BusinessException(ErrorCode.CONFLICT, "演出已结束，无法检票");
        }
    }

    private boolean isCheckInOpen(ShowSchedule schedule) {
        if (schedule == null
                || "CANCELLED".equals(schedule.getStatus())
                || schedule.getStartTime() == null
                || schedule.getEndTime() == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime checkInStart = schedule.getStartTime().minus(CHECKIN_OPEN_BEFORE_START);
        return !now.isBefore(checkInStart) && !now.isAfter(schedule.getEndTime());
    }

    private void ensureCheckInRole() {
        String userId = StpUtil.getLoginIdAsString();
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null || !CHECKIN_ROLES.contains(user.getRole())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "当前账号无检票权限");
        }
    }

    private CheckInScheduleResponse toScheduleResponse(ShowSchedule schedule) {
        ShowEntity show = showMapper.selectById(schedule.getShowId());
        return new CheckInScheduleResponse(
                schedule.getId(),
                show == null ? "Unknown Show" : show.getTitle(),
                schedule.getTheaterName(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getStatus(),
                isCheckInOpen(schedule)
        );
    }

    private CheckInResponse toResponse(
            TicketItem ticket,
            ShowSchedule schedule,
            ShowEntity show,
            LocalDateTime checkedInAt
    ) {
        return new CheckInResponse(
                ticket.getId(),
                ticket.getTicketCode(),
                ticket.getOrderId(),
                ticket.getScheduleId(),
                show == null ? "Unknown Show" : show.getTitle(),
                schedule == null ? "Unknown Theater" : schedule.getTheaterName(),
                schedule == null ? null : schedule.getStartTime(),
                ticket.getSeatId(),
                ticket.getStatus(),
                checkedInAt
        );
    }
}
