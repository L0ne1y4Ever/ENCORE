package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.encore.common.ErrorCode;
import com.encore.dto.SeatResponse;
import com.encore.dto.ScheduleAreaResponse;
import com.encore.entity.ScheduleSeat;
import com.encore.entity.ShowSchedule;
import com.encore.entity.ScheduleAreaInventory;
import com.encore.entity.VenueArea;
import com.encore.exception.BusinessException;
import com.encore.mapper.ScheduleSeatMapper;
import com.encore.mapper.ShowScheduleMapper;
import com.encore.mapper.ScheduleAreaInventoryMapper;
import com.encore.mapper.VenueAreaMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class SeatService {
    public static final Duration SEAT_LOCK_TTL = Duration.ofMinutes(15);
    private static final String SEAT_LOCK_PREFIX = "encore:seat-lock";
    private static final String SEAT_LOCK_INDEX_PREFIX = "encore:seat-lock-index";

    private final ScheduleSeatMapper scheduleSeatMapper;
    private final ShowScheduleMapper showScheduleMapper;
    private final StringRedisTemplate redisTemplate;
    private final SeatStatusPublisher seatStatusPublisher;
    private final ScheduleAreaInventoryMapper scheduleAreaInventoryMapper;
    private final VenueAreaMapper venueAreaMapper;

    public SeatService(
            ScheduleSeatMapper scheduleSeatMapper,
            ShowScheduleMapper showScheduleMapper,
            StringRedisTemplate redisTemplate,
            SeatStatusPublisher seatStatusPublisher,
            ScheduleAreaInventoryMapper scheduleAreaInventoryMapper,
            VenueAreaMapper venueAreaMapper
    ) {
        this.scheduleSeatMapper = scheduleSeatMapper;
        this.showScheduleMapper = showScheduleMapper;
        this.redisTemplate = redisTemplate;
        this.seatStatusPublisher = seatStatusPublisher;
        this.scheduleAreaInventoryMapper = scheduleAreaInventoryMapper;
        this.venueAreaMapper = venueAreaMapper;
    }

    public List<SeatResponse> listSeats(String scheduleId) {
        return listSeats(scheduleId, null);
    }

    public List<SeatResponse> listSeats(String scheduleId, String areaId) {
        ensureScheduleExists(scheduleId);
        LambdaQueryWrapper<ScheduleSeat> wrapper = new LambdaQueryWrapper<ScheduleSeat>()
                .eq(ScheduleSeat::getScheduleId, scheduleId);
        if (areaId != null && !areaId.isBlank()) {
            wrapper.eq(ScheduleSeat::getAreaId, areaId);
        }
        return scheduleSeatMapper.selectList(wrapper
                        .orderByAsc(ScheduleSeat::getRowNo)
                        .orderByAsc(ScheduleSeat::getColNo))
                .stream()
                .map(this::toSeatResponse)
                .toList();
    }

    public List<ScheduleAreaResponse> listScheduleAreas(String scheduleId) {
        ensureScheduleExists(scheduleId);
        List<ScheduleAreaInventory> inventories = scheduleAreaInventoryMapper.selectList(
                new LambdaQueryWrapper<ScheduleAreaInventory>()
                        .eq(ScheduleAreaInventory::getScheduleId, scheduleId)
        );
        return inventories.stream()
                .map(inv -> {
                    VenueArea area = venueAreaMapper.selectById(inv.getAreaId());
                    return new ScheduleAreaResponse(
                            inv.getId(),
                            inv.getAreaId(),
                            area == null ? "Unknown" : area.getName(),
                            area == null ? "UNKNOWN" : area.getCode(),
                            area == null ? "UNKNOWN" : area.getAreaType(),
                            area == null ? false : area.getIsSeated(),
                            inv.getPrice(),
                            inv.getTotalCount(),
                            inv.getAvailableCount(),
                            inv.getLockedCount(),
                            inv.getSoldCount(),
                            area == null ? "#ffffff" : area.getColor(),
                            area == null ? "" : area.getDescription(),
                            area == null ? "" : area.getPositionData()
                    );
                })
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
                    trackSeatLock(scheduleId, seat.getSeatCode(), ttl);
                } else if (owner.equals(currentOwner)) {
                    redisTemplate.expire(key, ttl);
                    trackSeatLock(scheduleId, seat.getSeatCode(), ttl);
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
            trackSeatLock(scheduleId, seatId, ttl);
        }
    }

    public boolean isLockedByOrder(String scheduleId, String seatId, String orderId) {
        return orderId.equals(redisTemplate.opsForValue().get(lockKey(scheduleId, seatId)));
    }

    public void releaseLocks(String scheduleId, List<String> seatIds) {
        normalizeSeatIds(seatIds).forEach(seatId -> {
            redisTemplate.delete(lockKey(scheduleId, seatId));
            untrackSeatLock(scheduleId, seatId);
        });
    }

    public void releaseLocksOwnedBy(String scheduleId, List<String> seatIds, String owner, boolean publishAvailable) {
        List<String> releasedSeatIds = new ArrayList<>();
        for (String seatId : normalizeSeatIds(seatIds)) {
            String key = lockKey(scheduleId, seatId);
            if (owner.equals(redisTemplate.opsForValue().get(key))) {
                redisTemplate.delete(key);
                untrackSeatLock(scheduleId, seatId);
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
            trackSeatLock(scheduleId, seatId, ttl);
        }
    }

    public String lockKey(String scheduleId, String seatId) {
        return "%s:%s:%s".formatted(SEAT_LOCK_PREFIX, scheduleId, seatId);
    }

    @Scheduled(fixedRate = 5000)
    public void cleanupExpiredSeatLocks() {
        Set<String> indexKeys = redisTemplate.keys("%s:*".formatted(SEAT_LOCK_INDEX_PREFIX));
        if (indexKeys == null || indexKeys.isEmpty()) {
            return;
        }
        for (String indexKey : indexKeys) {
            String scheduleId = indexKey.substring((SEAT_LOCK_INDEX_PREFIX + ":").length());
            ShowSchedule schedule = showScheduleMapper.selectById(scheduleId);
            if (schedule == null || !"ON_SALE".equals(schedule.getStatus())) {
                redisTemplate.delete(indexKey);
                continue;
            }
            Set<String> seatIds = redisTemplate.opsForSet().members(indexKey);
            if (seatIds == null || seatIds.isEmpty()) {
                redisTemplate.delete(indexKey);
                continue;
            }
            List<String> releasedSeatIds = new ArrayList<>();
            for (String seatId : seatIds) {
                if (!Boolean.TRUE.equals(redisTemplate.hasKey(lockKey(scheduleId, seatId)))) {
                    redisTemplate.opsForSet().remove(indexKey, seatId);
                    if (isSeatAvailable(scheduleId, seatId)) {
                        releasedSeatIds.add(seatId);
                    }
                }
            }
            if (!releasedSeatIds.isEmpty()) {
                seatStatusPublisher.publishSeatStatus(scheduleId, "LOCK_EXPIRED", "AVAILABLE", releasedSeatIds);
            }
            Long remaining = redisTemplate.opsForSet().size(indexKey);
            if (remaining == null || remaining == 0) {
                redisTemplate.delete(indexKey);
            }
        }
    }

    private ShowSchedule ensureScheduleExists(String scheduleId) {
        ShowSchedule schedule = showScheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "场次不存在");
        }
        return schedule;
    }

    private void trackSeatLock(String scheduleId, String seatId, Duration ttl) {
        String indexKey = seatLockIndexKey(scheduleId);
        redisTemplate.opsForSet().add(indexKey, seatId);
        redisTemplate.expire(indexKey, ttl.plus(Duration.ofHours(1)));
    }

    private void untrackSeatLock(String scheduleId, String seatId) {
        redisTemplate.opsForSet().remove(seatLockIndexKey(scheduleId), seatId);
    }

    private String seatLockIndexKey(String scheduleId) {
        return "%s:%s".formatted(SEAT_LOCK_INDEX_PREFIX, scheduleId);
    }

    private boolean isSeatAvailable(String scheduleId, String seatId) {
        ScheduleSeat seat = scheduleSeatMapper.selectOne(new LambdaQueryWrapper<ScheduleSeat>()
                .eq(ScheduleSeat::getScheduleId, scheduleId)
                .eq(ScheduleSeat::getSeatCode, seatId)
                .last("limit 1"));
        return seat != null && "AVAILABLE".equals(seat.getStatus());
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
