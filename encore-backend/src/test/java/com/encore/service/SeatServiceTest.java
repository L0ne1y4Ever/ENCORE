package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.encore.entity.ScheduleSeat;
import com.encore.entity.ShowSchedule;
import com.encore.mapper.ScheduleSeatMapper;
import com.encore.mapper.ShowScheduleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
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
    private SeatStatusPublisher seatStatusPublisher;

    @Test
    void lockSeatsPublishesLockedEvent() {
        SeatService service = new SeatService(
                scheduleSeatMapper,
                showScheduleMapper,
                redisTemplate,
                seatStatusPublisher
        );

        when(showScheduleMapper.selectById("sch-1")).thenReturn(onSaleSchedule());
        when(scheduleSeatMapper.selectList(any())).thenReturn(List.of(seat("seat-1-1")));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
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
