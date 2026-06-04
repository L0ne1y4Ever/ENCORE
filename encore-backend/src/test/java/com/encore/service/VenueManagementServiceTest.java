package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.encore.dto.CreateLayoutRequest;
import com.encore.dto.SyncLayoutSeatStatusRequest;
import com.encore.dto.UpdateLayoutRequest;
import com.encore.dto.UpdateScheduleAreaInventoryRequest;
import com.encore.entity.ScheduleAreaInventory;
import com.encore.entity.ScheduleSeat;
import com.encore.entity.SeatLayout;
import com.encore.entity.SeatLayoutArea;
import com.encore.entity.SeatLayoutSeat;
import com.encore.entity.ShowSchedule;
import com.encore.entity.UserAccount;
import com.encore.entity.Venue;
import com.encore.entity.VenueHall;
import com.encore.exception.BusinessException;
import com.encore.mapper.ScheduleAreaInventoryMapper;
import com.encore.mapper.ScheduleSeatMapper;
import com.encore.mapper.SeatLayoutAreaMapper;
import com.encore.mapper.SeatLayoutMapper;
import com.encore.mapper.SeatLayoutSeatMapper;
import com.encore.mapper.ShowMapper;
import com.encore.mapper.ShowScheduleMapper;
import com.encore.mapper.TicketItemMapper;
import com.encore.mapper.UserAccountMapper;
import com.encore.mapper.VenueAreaMapper;
import com.encore.mapper.VenueHallMapper;
import com.encore.mapper.VenueMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VenueManagementServiceTest {
    @Mock
    private VenueMapper venueMapper;
    @Mock
    private VenueHallMapper venueHallMapper;
    @Mock
    private SeatLayoutMapper seatLayoutMapper;
    @Mock
    private SeatLayoutAreaMapper seatLayoutAreaMapper;
    @Mock
    private SeatLayoutSeatMapper seatLayoutSeatMapper;
    @Mock
    private VenueAreaMapper venueAreaMapper;
    @Mock
    private ScheduleSeatMapper scheduleSeatMapper;
    @Mock
    private ScheduleAreaInventoryMapper scheduleAreaInventoryMapper;
    @Mock
    private ShowScheduleMapper showScheduleMapper;
    @Mock
    private ShowMapper showMapper;
    @Mock
    private TicketItemMapper ticketItemMapper;
    @Mock
    private UserAccountMapper userAccountMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private SeatService seatService;
    @Mock
    private SeatStatusPublisher seatStatusPublisher;

    @Test
    void listVenuesRejectsNonAdmin() {
        VenueManagementService service = createService();
        when(userAccountMapper.selectById("u-user")).thenReturn(user("u-user", "user"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-user");
            assertThrows(BusinessException.class, service::listVenues);
        }
    }

    @Test
    void updateScheduleSeatStatusRejectsSoldSeat() {
        VenueManagementService service = createService();
        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(scheduleSeatMapper.selectOne(any())).thenReturn(seat("seat-1-1", "SOLD"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");
            assertThrows(BusinessException.class,
                    () -> service.updateScheduleSeatStatus("sch-1", "seat-1-1", "DISABLED"));
        }

        verify(seatStatusPublisher, never()).publishSeatStatus(any(), any(), any(), any());
        verify(scheduleSeatMapper, never()).updateById(any(ScheduleSeat.class));
    }

    @Test
    void updateScheduleSeatStatusRejectsLockedSeat() {
        VenueManagementService service = createService();
        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(scheduleSeatMapper.selectOne(any())).thenReturn(seat("seat-1-1", "AVAILABLE"));
        when(redisTemplate.hasKey("encore:seat-lock:sch-1:seat-1-1")).thenReturn(true);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");
            assertThrows(BusinessException.class,
                    () -> service.updateScheduleSeatStatus("sch-1", "seat-1-1", "DISABLED"));
        }

        verify(seatStatusPublisher, never()).publishSeatStatus(any(), any(), any(), any());
        verify(scheduleSeatMapper, never()).updateById(any(ScheduleSeat.class));
    }

    @Test
    void updateScheduleSeatStatusRejectsReservedTicket() {
        VenueManagementService service = createService();
        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(scheduleSeatMapper.selectOne(any())).thenReturn(seat("seat-1-1", "AVAILABLE"));
        when(ticketItemMapper.selectCount(any())).thenReturn(1L);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");
            assertThrows(BusinessException.class,
                    () -> service.updateScheduleSeatStatus("sch-1", "seat-1-1", "DISABLED"));
        }

        verify(seatStatusPublisher, never()).publishSeatStatus(any(), any(), any(), any());
        verify(scheduleSeatMapper, never()).updateById(any(ScheduleSeat.class));
    }

    @Test
    void updateScheduleSeatStatusBroadcastsOnSuccess() {
        VenueManagementService service = createService();
        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(scheduleSeatMapper.selectOne(any())).thenReturn(seat("seat-1-1", "AVAILABLE"));
        when(scheduleSeatMapper.selectCount(any())).thenReturn(0L);
        when(scheduleAreaInventoryMapper.selectCount(any())).thenReturn(0L);
        when(showScheduleMapper.selectById("sch-1")).thenReturn(schedule());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");
            service.updateScheduleSeatStatus("sch-1", "seat-1-1", "DISABLED");
        }

        verify(seatStatusPublisher).publishSeatStatus("sch-1", "DISABLED", "DISABLED", List.of("seat-1-1"));
        verify(scheduleSeatMapper).updateById(any(ScheduleSeat.class));
    }

    @Test
    void syncLayoutSeatStatusUpdatesFutureScheduleAndBroadcasts() {
        VenueManagementService service = createService();
        SeatLayout layout = layout("SEATED");
        ShowSchedule schedule = schedule();
        schedule.setId("sch-1");
        schedule.setLayoutId("lay-1");
        schedule.setStatus("ON_SALE");
        schedule.setStartTime(LocalDateTime.now().plusDays(2));
        ScheduleSeat scheduleSeat = seat("seat-1-1", "AVAILABLE");

        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(seatLayoutMapper.selectById("lay-1")).thenReturn(layout);
        when(seatLayoutSeatMapper.selectList(any())).thenReturn(List.of(layoutSeat("seat-1-1", "DISABLED")));
        when(showScheduleMapper.selectById("sch-1")).thenReturn(schedule);
        when(scheduleSeatMapper.selectList(any())).thenReturn(List.of(scheduleSeat));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");
            var response = service.syncLayoutSeatStatus("lay-1", new SyncLayoutSeatStatusRequest(List.of("sch-1")));

            assertThat(response.scheduleCount()).isEqualTo(1);
            assertThat(response.updatedSeatCount()).isEqualTo(1);
        }

        assertThat(scheduleSeat.getStatus()).isEqualTo("DISABLED");
        verify(scheduleSeatMapper).updateById(scheduleSeat);
        verify(seatStatusPublisher).publishSeatStatus("sch-1", "LAYOUT_SYNC", "DISABLED", List.of("seat-1-1"));
    }

    @Test
    void syncLayoutSeatStatusRejectsLockedSeatWithoutPartialUpdate() {
        VenueManagementService service = createService();
        SeatLayout layout = layout("SEATED");
        ShowSchedule schedule = schedule();
        schedule.setId("sch-1");
        schedule.setLayoutId("lay-1");
        schedule.setStatus("ON_SALE");
        schedule.setStartTime(LocalDateTime.now().plusDays(2));

        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(seatLayoutMapper.selectById("lay-1")).thenReturn(layout);
        when(seatLayoutSeatMapper.selectList(any())).thenReturn(List.of(layoutSeat("seat-1-1", "DISABLED")));
        when(showScheduleMapper.selectById("sch-1")).thenReturn(schedule);
        when(scheduleSeatMapper.selectList(any())).thenReturn(List.of(seat("seat-1-1", "AVAILABLE")));
        when(redisTemplate.hasKey("encore:seat-lock:sch-1:seat-1-1")).thenReturn(true);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");
            assertThrows(BusinessException.class,
                    () -> service.syncLayoutSeatStatus("lay-1", new SyncLayoutSeatStatusRequest(List.of("sch-1"))));
        }

        verify(scheduleSeatMapper, never()).updateById(any(ScheduleSeat.class));
        verify(seatStatusPublisher, never()).publishSeatStatus(any(), any(), any(), any());
    }

    @Test
    void updateLayoutSeatStatusMarksBrokenSeatDisabled() {
        VenueManagementService service = createService();
        SeatLayout layout = layout("SEATED");
        SeatLayoutSeat seat = layoutSeat("seat-1-1", "AVAILABLE");
        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(seatLayoutMapper.selectById("lay-1")).thenReturn(layout);
        when(seatLayoutSeatMapper.selectOne(any())).thenReturn(seat);
        when(seatLayoutSeatMapper.selectById("lay-1:seat-1-1")).thenReturn(seat);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");
            var response = service.updateLayoutSeatStatus("lay-1", "seat-1-1", "DISABLED");
            assertThat(response.status()).isEqualTo("DISABLED");
        }

        assertThat(seat.getStatus()).isEqualTo("DISABLED");
        verify(seatLayoutSeatMapper).updateById(seat);
    }

    @Test
    void updateLayoutEditsNameAndPublishedDefaultLayout() {
        VenueManagementService service = createService();
        SeatLayout layout = layout("SEATED");
        VenueHall hall = hall(30);
        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(seatLayoutMapper.selectById("lay-1")).thenReturn(layout);
        when(venueHallMapper.selectById("hall-1")).thenReturn(hall);
        when(seatLayoutAreaMapper.selectCount(any())).thenReturn(0L);
        when(seatLayoutSeatMapper.selectCount(any())).thenReturn(0L);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");
            var response = service.updateLayout("lay-1", new UpdateLayoutRequest("Updated Layout", "PUBLISHED"));
            assertThat(response.name()).isEqualTo("Updated Layout");
            assertThat(response.status()).isEqualTo("PUBLISHED");
        }

        assertThat(layout.getName()).isEqualTo("Updated Layout");
        assertThat(hall.getDefaultLayoutId()).isEqualTo("lay-1");
        verify(seatLayoutMapper).updateById(layout);
        verify(venueHallMapper).updateById(hall);
    }

    @Test
    void updateAreaInventoryRejectsTotalBelowCommitted() {
        VenueManagementService service = createService();
        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(scheduleAreaInventoryMapper.selectById("inv-1")).thenReturn(inventory(100, 85, 10, 5));

        UpdateScheduleAreaInventoryRequest request = new UpdateScheduleAreaInventoryRequest(10, 10, "AVAILABLE");

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");
            assertThrows(BusinessException.class,
                    () -> service.updateAreaInventory("sch-1", "inv-1", request));
        }

        verify(seatService, never()).publishAreaInventory(any(), any(), any());
        verify(scheduleAreaInventoryMapper, never()).updateById(any(ScheduleAreaInventory.class));
    }

    @Test
    void updateAreaInventoryBroadcastsAdjustment() {
        VenueManagementService service = createService();
        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(scheduleAreaInventoryMapper.selectById("inv-1")).thenReturn(inventory(100, 100, 0, 0));
        when(scheduleSeatMapper.selectCount(any())).thenReturn(0L);
        when(scheduleAreaInventoryMapper.selectCount(any())).thenReturn(0L);
        when(showScheduleMapper.selectById("sch-1")).thenReturn(schedule());

        UpdateScheduleAreaInventoryRequest request = new UpdateScheduleAreaInventoryRequest(120, 120, "AVAILABLE");

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");
            service.updateAreaInventory("sch-1", "inv-1", request);
        }

        verify(scheduleAreaInventoryMapper).updateById(any(ScheduleAreaInventory.class));
        verify(seatService).publishAreaInventory("sch-1", "AREA_ADJUSTED", "inv-1");
    }

    @Test
    void ensureScheduleConflictFreeRejectsOverlap() {
        VenueManagementService service = createService();
        when(venueHallMapper.selectById("hall-1")).thenReturn(hall(30));
        ShowSchedule existing = scheduleWindow("sch-existing",
                LocalDateTime.of(2026, 6, 10, 20, 0),
                LocalDateTime.of(2026, 6, 10, 22, 0));
        when(showScheduleMapper.selectList(any())).thenReturn(List.of(existing));

        assertThrows(BusinessException.class, () -> service.ensureScheduleConflictFree(
                null,
                "hall-1",
                LocalDateTime.of(2026, 6, 10, 19, 0),
                LocalDateTime.of(2026, 6, 10, 21, 0)));
    }

    @Test
    void ensureScheduleConflictFreeAllowsNonOverlap() {
        VenueManagementService service = createService();
        when(venueHallMapper.selectById("hall-1")).thenReturn(hall(30));
        ShowSchedule existing = scheduleWindow("sch-existing",
                LocalDateTime.of(2026, 6, 11, 19, 0),
                LocalDateTime.of(2026, 6, 11, 21, 0));
        when(showScheduleMapper.selectList(any())).thenReturn(List.of(existing));

        assertDoesNotThrow(() -> service.ensureScheduleConflictFree(
                null,
                "hall-1",
                LocalDateTime.of(2026, 6, 10, 19, 0),
                LocalDateTime.of(2026, 6, 10, 21, 0)));
    }

    @Test
    void createZonedLayoutGeneratesAreasWithoutSeats() {
        VenueManagementService service = createService();
        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(venueHallMapper.selectById("hall-1")).thenReturn(hall(30));
        when(seatLayoutMapper.selectList(any())).thenReturn(List.of());
        when(seatLayoutMapper.selectById(anyString())).thenReturn(layout("ZONED"));
        when(seatLayoutAreaMapper.selectCount(any())).thenReturn(0L);
        when(seatLayoutSeatMapper.selectCount(any())).thenReturn(0L);

        CreateLayoutRequest request = new CreateLayoutRequest(
                "hall-1", "Zoned Layout", "ZONED", null, null, null, null, null, null);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");
            service.createLayout(request);
        }

        verify(seatLayoutAreaMapper, times(3)).insert(any(SeatLayoutArea.class));
        verify(seatLayoutSeatMapper, never()).insert(any(SeatLayoutSeat.class));
    }

    @Test
    void createMixedLayoutGeneratesAreasAndStandSeats() {
        VenueManagementService service = createService();
        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));
        when(venueHallMapper.selectById("hall-1")).thenReturn(hall(30));
        when(seatLayoutMapper.selectList(any())).thenReturn(List.of());
        when(seatLayoutMapper.selectById(anyString())).thenReturn(layout("MIXED"));
        when(seatLayoutAreaMapper.selectCount(any())).thenReturn(0L);
        when(seatLayoutSeatMapper.selectCount(any())).thenReturn(0L);

        CreateLayoutRequest request = new CreateLayoutRequest(
                "hall-1", "Mixed Layout", "MIXED", null, null, null, null, null, null);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");
            service.createLayout(request);
        }

        // 3 standing zones + 3 seated stands = 6 areas; stands generate 200 + 200 + 300 seats.
        verify(seatLayoutAreaMapper, times(6)).insert(any(SeatLayoutArea.class));
        verify(seatLayoutSeatMapper, times(700)).insert(any(SeatLayoutSeat.class));
    }

    private VenueManagementService createService() {
        return new VenueManagementService(
                venueMapper,
                venueHallMapper,
                seatLayoutMapper,
                seatLayoutAreaMapper,
                seatLayoutSeatMapper,
                venueAreaMapper,
                scheduleSeatMapper,
                scheduleAreaInventoryMapper,
                showScheduleMapper,
                showMapper,
                ticketItemMapper,
                userAccountMapper,
                redisTemplate,
                seatService,
                seatStatusPublisher
        );
    }

    private UserAccount user(String id, String role) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setUsername(id);
        user.setRole(role);
        return user;
    }

    private ScheduleSeat seat(String seatCode, String status) {
        ScheduleSeat seat = new ScheduleSeat();
        seat.setId("sch-1:" + seatCode);
        seat.setScheduleId("sch-1");
        seat.setSeatCode(seatCode);
        seat.setStatus(status);
        return seat;
    }

    private ScheduleAreaInventory inventory(int total, int available, int sold, int locked) {
        ScheduleAreaInventory inventory = new ScheduleAreaInventory();
        inventory.setId("inv-1");
        inventory.setScheduleId("sch-1");
        inventory.setAreaId("area-1");
        inventory.setPrice(BigDecimal.valueOf(100));
        inventory.setTotalCount(total);
        inventory.setAvailableCount(available);
        inventory.setSoldCount(sold);
        inventory.setLockedCount(locked);
        inventory.setStatus("AVAILABLE");
        return inventory;
    }

    private ShowSchedule schedule() {
        ShowSchedule schedule = new ShowSchedule();
        schedule.setId("sch-1");
        schedule.setShowId("show-1");
        schedule.setTheaterName("Main Hall");
        schedule.setTicketMode("SEATED");
        schedule.setStartTime(LocalDateTime.now().plusDays(1));
        return schedule;
    }

    private ShowSchedule scheduleWindow(String id, LocalDateTime start, LocalDateTime end) {
        ShowSchedule schedule = new ShowSchedule();
        schedule.setId(id);
        schedule.setHallId("hall-1");
        schedule.setStatus("ON_SALE");
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        return schedule;
    }

    private VenueHall hall(int clearanceMinutes) {
        VenueHall hall = new VenueHall();
        hall.setId("hall-1");
        hall.setVenueId("ven-1");
        hall.setName("Hall One");
        hall.setClearanceMinutes(clearanceMinutes);
        return hall;
    }

    private SeatLayout layout(String ticketMode) {
        SeatLayout layout = new SeatLayout();
        layout.setId("lay-1");
        layout.setHallId("hall-1");
        layout.setName("Layout");
        layout.setTicketMode(ticketMode);
        layout.setVersion(1);
        layout.setStatus("DRAFT");
        return layout;
    }

    private SeatLayoutSeat layoutSeat(String seatCode, String status) {
        SeatLayoutSeat seat = new SeatLayoutSeat();
        seat.setId("lay-1:" + seatCode);
        seat.setLayoutId("lay-1");
        seat.setSeatCode(seatCode);
        seat.setRowNo(1);
        seat.setColNo(1);
        seat.setSection("A");
        seat.setStatus(status);
        seat.setPrice(BigDecimal.valueOf(100));
        return seat;
    }
}
