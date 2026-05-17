package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.encore.common.ErrorCode;
import com.encore.dto.SeatResponse;
import com.encore.entity.ScheduleSeat;
import com.encore.entity.ShowSchedule;
import com.encore.exception.BusinessException;
import com.encore.mapper.ScheduleSeatMapper;
import com.encore.mapper.ShowScheduleMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class SeatService {
    public static final Duration SEAT_LOCK_TTL = Duration.ofMinutes(15);

    private final ScheduleSeatMapper scheduleSeatMapper;
    private final ShowScheduleMapper showScheduleMapper;
    private final StringRedisTemplate redisTemplate;
    private final SeatStatusPublisher seatStatusPublisher;

    public SeatService(
            ScheduleSeatMapper scheduleSeatMapper,
            ShowScheduleMapper showScheduleMapper,
            StringRedisTemplate redisTemplate,
            SeatStatusPublisher seatStatusPublisher
    ) {
        this.scheduleSeatMapper = scheduleSeatMapper;
        this.showScheduleMapper = showScheduleMapper;
        this.redisTemplate = redisTemplate;
        this.seatStatusPublisher = seatStatusPublisher;
    }

    public List<SeatResponse> listSeats(String scheduleId) {
        ensureScheduleExists(scheduleId);
        return scheduleSeatMapper.selectList(new LambdaQueryWrapper<ScheduleSeat>()
                        .eq(ScheduleSeat::getScheduleId, scheduleId)
                        .orderByAsc(ScheduleSeat::getRowNo)
                        .orderByAsc(ScheduleSeat::getColNo))
                .stream()
                .map(this::toSeatResponse)
                .toList();
    }

    public boolean lockSeats(String scheduleId, List<String> seatIds) {
        String userId = StpUtil.getLoginIdAsString();
        return lockSeatsForOwner(scheduleId, seatIds, userId, SEAT_LOCK_TTL, true);
    }

    public boolean lockSeatsForOwner(String scheduleId, List<String> seatIds, String owner, Duration ttl, boolean publishLocked) {
        ensureOnSaleSchedule(scheduleId);
        List<String> normalizedSeatIds = normalizeSeatIds(seatIds);
        List<ScheduleSeat> seats = findSeats(scheduleId, normalizedSeatIds);
        List<String> acquiredKeys = new ArrayList<>();

        try {
            for (ScheduleSeat seat : seats) {
                ensureSeatAvailableForLock(scheduleId, seat, owner);
                String key = lockKey(scheduleId, seat.getSeatCode());
                String currentOwner = redisTemplate.opsForValue().get(key);
                if (currentOwner == null) {
                    Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, owner, ttl);
                    if (!Boolean.TRUE.equals(acquired)) {
                        throw new BusinessException(ErrorCode.CONFLICT, "座位已被锁定，请重新选择");
                    }
                    acquiredKeys.add(key);
                } else if (owner.equals(currentOwner)) {
                    redisTemplate.expire(key, ttl);
                } else {
                    throw new BusinessException(ErrorCode.CONFLICT, "座位已被锁定，请重新选择");
                }
            }
            if (publishLocked) {
                seatStatusPublisher.publishSeatStatus(scheduleId, "LOCKED", "LOCKED", normalizedSeatIds);
            }
            return true;
        } catch (BusinessException exception) {
            acquiredKeys.forEach(redisTemplate::delete);
            throw exception;
        }
    }

    public List<ScheduleSeat> findSeats(String scheduleId, List<String> seatIds) {
        List<String> normalizedSeatIds = normalizeSeatIds(seatIds);
        List<ScheduleSeat> seats = scheduleSeatMapper.selectList(new LambdaQueryWrapper<ScheduleSeat>()
                .eq(ScheduleSeat::getScheduleId, scheduleId)
                .in(ScheduleSeat::getSeatCode, normalizedSeatIds));
        if (seats.size() != normalizedSeatIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "座位不存在");
        }
        return seats;
    }

    public void ensureOnSaleSchedule(String scheduleId) {
        ShowSchedule schedule = ensureScheduleExists(scheduleId);
        if (!"ON_SALE".equals(schedule.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该场次暂不可售");
        }
    }

    public void ensureSeatAvailableForOrder(String scheduleId, ScheduleSeat seat) {
        if (!"AVAILABLE".equals(seat.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "座位不可售，请重新选择");
        }
    }

    public boolean isLockedByCurrentUser(String scheduleId, String seatId) {
        String owner = redisTemplate.opsForValue().get(lockKey(scheduleId, seatId));
        return StpUtil.getLoginIdAsString().equals(owner);
    }

    public void attachOrderToLocks(String scheduleId, List<String> seatIds, String orderId, Duration ttl) {
        for (String seatId : normalizeSeatIds(seatIds)) {
            redisTemplate.opsForValue().set(lockKey(scheduleId, seatId), orderId, ttl);
        }
    }

    public boolean isLockedByOrder(String scheduleId, String seatId, String orderId) {
        return orderId.equals(redisTemplate.opsForValue().get(lockKey(scheduleId, seatId)));
    }

    public void releaseLocks(String scheduleId, List<String> seatIds) {
        normalizeSeatIds(seatIds).forEach(seatId -> redisTemplate.delete(lockKey(scheduleId, seatId)));
    }

    public void releaseLocksOwnedBy(String scheduleId, List<String> seatIds, String owner, boolean publishAvailable) {
        List<String> releasedSeatIds = new ArrayList<>();
        for (String seatId : normalizeSeatIds(seatIds)) {
            String key = lockKey(scheduleId, seatId);
            if (owner.equals(redisTemplate.opsForValue().get(key))) {
                redisTemplate.delete(key);
                releasedSeatIds.add(seatId);
            }
        }
        if (publishAvailable && !releasedSeatIds.isEmpty()) {
            seatStatusPublisher.publishSeatStatus(scheduleId, "GROUP_RELEASED", "AVAILABLE", releasedSeatIds);
        }
    }

    public void ensureLocksOwnedBy(String scheduleId, List<String> seatIds, String owner) {
        for (String seatId : normalizeSeatIds(seatIds)) {
            if (!owner.equals(redisTemplate.opsForValue().get(lockKey(scheduleId, seatId)))) {
                throw new BusinessException(ErrorCode.CONFLICT, "拼座锁已失效，请重新发起拼座");
            }
        }
    }

    public void transferLocksOwner(String scheduleId, List<String> seatIds, String expectedOwner, String nextOwner, Duration ttl) {
        List<String> normalizedSeatIds = normalizeSeatIds(seatIds);
        ensureLocksOwnedBy(scheduleId, normalizedSeatIds, expectedOwner);
        for (String seatId : normalizedSeatIds) {
            redisTemplate.opsForValue().set(lockKey(scheduleId, seatId), nextOwner, ttl);
        }
    }

    public String lockKey(String scheduleId, String seatId) {
        return "encore:seat-lock:%s:%s".formatted(scheduleId, seatId);
    }

    private ShowSchedule ensureScheduleExists(String scheduleId) {
        ShowSchedule schedule = showScheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "场次不存在");
        }
        return schedule;
    }

    private void ensureSeatAvailableForLock(String scheduleId, ScheduleSeat seat, String owner) {
        if (!"AVAILABLE".equals(seat.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "座位不可售，请重新选择");
        }
        String currentOwner = redisTemplate.opsForValue().get(lockKey(scheduleId, seat.getSeatCode()));
        if (currentOwner != null && !owner.equals(currentOwner)) {
            throw new BusinessException(ErrorCode.CONFLICT, "座位已被锁定，请重新选择");
        }
    }

    private SeatResponse toSeatResponse(ScheduleSeat seat) {
        String runtimeStatus = seat.getStatus();
        if ("AVAILABLE".equals(runtimeStatus) && Boolean.TRUE.equals(redisTemplate.hasKey(lockKey(seat.getScheduleId(), seat.getSeatCode())))) {
            runtimeStatus = "LOCKED";
        }
        return new SeatResponse(
                seat.getSeatCode(),
                seat.getRowNo(),
                seat.getColNo(),
                seat.getSection(),
                runtimeStatus,
                seat.getPrice()
        );
    }

    private List<String> normalizeSeatIds(List<String> seatIds) {
        List<String> normalized = seatIds == null ? List.of() : seatIds.stream()
                .filter(seatId -> seatId != null && !seatId.isBlank())
                .distinct()
                .toList();
        if (normalized.isEmpty() || normalized.size() > 6) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择 1 到 6 个座位");
        }
        return normalized;
    }
}
