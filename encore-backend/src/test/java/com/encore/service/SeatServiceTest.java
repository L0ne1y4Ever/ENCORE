package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.encore.entity.ScheduleSeat;
import com.encore.entity.ShowSchedule;
import com.encore.entity.TicketItem;
import com.encore.mapper.ScheduleSeatMapper;
import com.encore.mapper.ScheduleAreaInventoryMapper;
import com.encore.mapper.ShowScheduleMapper;
import com.encore.mapper.TicketItemMapper;
import com.encore.mapper.VenueAreaMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {
    @Mock
    private ScheduleSeatMapper scheduleSeatMapper;
    @Mock
    private ShowScheduleMapper showScheduleMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private SetOperations<String, String> setOperations;
    @Mock
    private SeatStatusPublisher seatStatusPublisher;
    @Mock
    private ScheduleAreaInventoryMapper scheduleAreaInventoryMapper;
    @Mock
    private VenueAreaMapper venueAreaMapper;
    @Mock
    private TicketItemMapper ticketItemMapper;

    @Test
    void lockSeatsPublishesLockedEvent() {
        SeatService service = new SeatService(
                scheduleSeatMapper,
                showScheduleMapper,
                redisTemplate,
                seatStatusPublisher,
                scheduleAreaInventoryMapper,
                venueAreaMapper,
                ticketItemMapper
        );

        when(showScheduleMapper.selectById("sch-1")).thenReturn(onSaleSchedule());
        when(scheduleSeatMapper.selectList(any())).thenReturn(List.of(seat("seat-1-1")));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.setIfAbsent(
                eq("encore:seat-lock:sch-1:seat-1-1"),
                eq("u-1"),
                eq(SeatService.SEAT_LOCK_TTL)
        )).thenReturn(true);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-1");

            service.lockSeats("sch-1", List.of("seat-1-1"));
        }

        verify(seatStatusPublisher).publishSeatStatus(
                "sch-1",
                "LOCKED",
                "LOCKED",
                List.of("seat-1-1")
        );
    }

    @Test
    void cleanupExpiredSeatLocksPublishesAvailableEvent() {
        SeatService service = new SeatService(
                scheduleSeatMapper,
                showScheduleMapper,
                redisTemplate,
                seatStatusPublisher,
                scheduleAreaInventoryMapper,
                venueAreaMapper,
                ticketItemMapper
        );

        when(redisTemplate.keys("encore:seat-lock-index:*"))
                .thenReturn(Set.of("encore:seat-lock-index:sch-1"));
        when(showScheduleMapper.selectById("sch-1")).thenReturn(onSaleSchedule());
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("encore:seat-lock-index:sch-1")).thenReturn(Set.of("seat-1-1"));
        when(redisTemplate.hasKey("encore:seat-lock:sch-1:seat-1-1")).thenReturn(false);
        when(scheduleSeatMapper.selectOne(any())).thenReturn(seat("seat-1-1"));
        when(setOperations.size("encore:seat-lock-index:sch-1")).thenReturn(0L);

        service.cleanupExpiredSeatLocks();

        verify(setOperations).remove("encore:seat-lock-index:sch-1", "seat-1-1");
        verify(redisTemplate).delete("encore:seat-lock-index:sch-1");
        verify(seatStatusPublisher).publishSeatStatus(
                "sch-1",
                "LOCK_EXPIRED",
                "AVAILABLE",
                List.of("seat-1-1")
        );
    }

    @Test
    void listSeatsMergesTicketAndRedisRuntimeStatus() {
        SeatService service = new SeatService(
                scheduleSeatMapper,
                showScheduleMapper,
                redisTemplate,
                seatStatusPublisher,
                scheduleAreaInventoryMapper,
                venueAreaMapper,
                ticketItemMapper
        );
        ScheduleSeat availableSeat = seat("seat-1-1");
        ScheduleSeat lockedSeat = seat("seat-1-2");
        ScheduleSeat soldSeat = seat("seat-1-3");
        TicketItem soldTicket = new TicketItem();
        soldTicket.setScheduleId("sch-1");
        soldTicket.setSeatId("seat-1-3");
        soldTicket.setStatus("UNUSED");

        when(showScheduleMapper.selectById("sch-1")).thenReturn(onSaleSchedule());
        when(scheduleSeatMapper.selectList(any())).thenReturn(List.of(availableSeat, lockedSeat, soldSeat));
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(soldTicket));
        when(redisTemplate.hasKey("encore:seat-lock:sch-1:seat-1-1")).thenReturn(false);
        when(redisTemplate.hasKey("encore:seat-lock:sch-1:seat-1-2")).thenReturn(true);

        var seats = service.listSeats("sch-1");

        assertEquals("AVAILABLE", seats.get(0).status());
        assertEquals("LOCKED", seats.get(1).status());
        assertEquals("SOLD", seats.get(2).status());
    }

    @Test
    void listMySeatLocksOnlyReturnsCurrentUserPlainLocks() {
        SeatService service = new SeatService(
                scheduleSeatMapper,
                showScheduleMapper,
                redisTemplate,
                seatStatusPublisher,
                scheduleAreaInventoryMapper,
                venueAreaMapper,
                ticketItemMapper
        );

        when(showScheduleMapper.selectById("sch-1")).thenReturn(onSaleSchedule());
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(setOperations.members("encore:seat-lock-index:sch-1"))
                .thenReturn(Set.of("seat-1-1", "seat-1-2", "seat-1-3"));
        when(valueOperations.get("encore:seat-lock:sch-1:seat-1-1")).thenReturn("u-1");
        when(valueOperations.get("encore:seat-lock:sch-1:seat-1-2")).thenReturn("u-2");
        when(valueOperations.get("encore:seat-lock:sch-1:seat-1-3")).thenReturn("u-1");
        when(scheduleSeatMapper.selectOne(any())).thenReturn(seat("seat-1-1"));
        when(redisTemplate.getExpire(eq("encore:seat-lock:sch-1:seat-1-1"), eq(java.util.concurrent.TimeUnit.SECONDS))).thenReturn(60L);
        when(redisTemplate.getExpire(eq("encore:seat-lock:sch-1:seat-1-3"), eq(java.util.concurrent.TimeUnit.SECONDS))).thenReturn(120L);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-1");

            var response = service.listMySeatLocks("sch-1");

            assertEquals(List.of("seat-1-1", "seat-1-3"), response.seats().stream().map(lock -> lock.seatId()).toList());
        }
    }

    @Test
    void unlockMySeatLocksOnlyReleasesCurrentUserLocks() {
        SeatService service = new SeatService(
                scheduleSeatMapper,
                showScheduleMapper,
                redisTemplate,
                seatStatusPublisher,
                scheduleAreaInventoryMapper,
                venueAreaMapper,
                ticketItemMapper
        );

        when(showScheduleMapper.selectById("sch-1")).thenReturn(onSaleSchedule());
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("encore:seat-lock:sch-1:seat-1-1")).thenReturn("u-1").thenReturn(null);
        when(valueOperations.get("encore:seat-lock:sch-1:seat-1-2")).thenReturn("u-2", "u-2");
        when(setOperations.members("encore:seat-lock-index:sch-1")).thenReturn(Set.of("seat-1-1", "seat-1-2"));
        when(scheduleSeatMapper.selectOne(any())).thenReturn(seat("seat-1-1"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-1");

            service.unlockMySeatLocks("sch-1", List.of("seat-1-1", "seat-1-2"));
        }

        verify(redisTemplate).delete("encore:seat-lock:sch-1:seat-1-1");
        verify(setOperations).remove("encore:seat-lock-index:sch-1", "seat-1-1");
        verify(redisTemplate, never()).delete("encore:seat-lock:sch-1:seat-1-2");
        verify(seatStatusPublisher).publishSeatStatus(
                "sch-1",
                "USER_RELEASED",
                "AVAILABLE",
                List.of("seat-1-1")
        );
    }

    private ShowSchedule onSaleSchedule() {
        ShowSchedule schedule = new ShowSchedule();
        schedule.setId("sch-1");
        schedule.setStatus("ON_SALE");
        return schedule;
    }

    private ScheduleSeat seat(String seatId) {
        ScheduleSeat seat = new ScheduleSeat();
        seat.setScheduleId("sch-1");
        seat.setSeatCode(seatId);
        seat.setStatus("AVAILABLE");
        return seat;
    }
}
