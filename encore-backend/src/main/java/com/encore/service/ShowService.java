package com.encore.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.encore.common.ErrorCode;
import com.encore.dto.ScheduleResponse;
import com.encore.dto.ShowRecommendationResponse;
import com.encore.dto.ShowResponse;
import com.encore.entity.ScheduleAreaInventory;
import com.encore.entity.ScheduleSeat;
import com.encore.entity.ShowEntity;
import com.encore.entity.ShowSchedule;
import com.encore.entity.TicketItem;
import com.encore.entity.TicketOrder;
import com.encore.entity.VenueArea;
import com.encore.exception.BusinessException;
import com.encore.mapper.ScheduleAreaInventoryMapper;
import com.encore.mapper.ScheduleSeatMapper;
import com.encore.mapper.ShowMapper;
import com.encore.mapper.ShowScheduleMapper;
import com.encore.mapper.TicketItemMapper;
import com.encore.mapper.TicketOrderMapper;
import com.encore.mapper.VenueAreaMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ShowService {
    private static final Pattern PRICE_NUMBER_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?");

    private final ShowMapper showMapper;
    private final ShowScheduleMapper showScheduleMapper;
    private final TicketOrderMapper ticketOrderMapper;
    private final TicketItemMapper ticketItemMapper;
    private final ScheduleSeatMapper scheduleSeatMapper;
    private final ScheduleAreaInventoryMapper scheduleAreaInventoryMapper;
    private final VenueAreaMapper venueAreaMapper;
    private final StringRedisTemplate redisTemplate;

    public ShowService(
            ShowMapper showMapper,
            ShowScheduleMapper showScheduleMapper,
            TicketOrderMapper ticketOrderMapper,
            TicketItemMapper ticketItemMapper,
            ScheduleSeatMapper scheduleSeatMapper,
            ScheduleAreaInventoryMapper scheduleAreaInventoryMapper,
            VenueAreaMapper venueAreaMapper,
            StringRedisTemplate redisTemplate
    ) {
        this.showMapper = showMapper;
        this.showScheduleMapper = showScheduleMapper;
        this.ticketOrderMapper = ticketOrderMapper;
        this.ticketItemMapper = ticketItemMapper;
        this.scheduleSeatMapper = scheduleSeatMapper;
        this.scheduleAreaInventoryMapper = scheduleAreaInventoryMapper;
        this.venueAreaMapper = venueAreaMapper;
        this.redisTemplate = redisTemplate;
    }

    public List<ShowResponse> listShows(String keyword, String category) {
        LambdaQueryWrapper<ShowEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query
                    .like(ShowEntity::getTitle, keyword)
                    .or()
                    .like(ShowEntity::getSubtitle, keyword)
                    .or()
                    .like(ShowEntity::getDescription, keyword)
                    .or()
                    .like(ShowEntity::getIntro, keyword)
                    .or()
                    .like(ShowEntity::getCastMembers, keyword)
                    .or()
                    .like(ShowEntity::getFullSynopsis, keyword));
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(ShowEntity::getCategory, category);
        }
        wrapper.eq(ShowEntity::getStatus, "PUBLISHED")
                .orderByAsc(ShowEntity::getSortOrder)
                .orderByDesc(ShowEntity::getCreatedAt);

        List<ShowEntity> shows = showMapper.selectList(wrapper);
        Map<String, PriceBounds> priceBoundsByShow = priceBoundsByShow(shows.stream()
                .map(ShowEntity::getId)
                .toList());
        return shows.stream()
                .map(show -> toShowResponse(show, priceBoundsByShow.get(show.getId())))
                .toList();
    }

    public List<ShowRecommendationResponse> listTopRecommendations() {
        List<ShowEntity> publishedShows = showMapper.selectList(new LambdaQueryWrapper<ShowEntity>()
                        .eq(ShowEntity::getStatus, "PUBLISHED"))
                .stream()
                .filter(show -> "PUBLISHED".equals(show.getStatus()))
                .toList();
        if (publishedShows.isEmpty()) {
            return List.of();
        }

        List<String> showIds = publishedShows.stream()
                .map(ShowEntity::getId)
                .toList();
        List<ShowSchedule> schedules = showScheduleMapper.selectList(new LambdaQueryWrapper<ShowSchedule>()
                .in(ShowSchedule::getShowId, showIds))
                .stream()
                .filter(this::isPublicSchedule)
                .toList();
        Map<String, ShowSchedule> scheduleById = schedules.stream()
                .collect(Collectors.toMap(ShowSchedule::getId, Function.identity(), (left, right) -> left));
        Map<String, Long> availableSchedulesByShow = schedules.stream()
                .filter(this::isPurchasableSchedule)
                .collect(Collectors.groupingBy(ShowSchedule::getShowId, Collectors.counting()));
        Map<String, Long> availableTicketsByShow = calculateAvailableTicketsByShow(schedules);
        Map<String, PriceBounds> priceBoundsByShow = collectPriceBoundsByShow(schedules);

        List<String> scheduleIds = schedules.stream()
                .map(ShowSchedule::getId)
                .toList();
        List<TicketOrder> paidOrders = scheduleIds.isEmpty()
                ? List.of()
                : ticketOrderMapper.selectList(new LambdaQueryWrapper<TicketOrder>()
                        .in(TicketOrder::getScheduleId, scheduleIds)
                        .eq(TicketOrder::getStatus, "PAID"))
                        .stream()
                        .filter(order -> "PAID".equals(order.getStatus()))
                        .toList();
        Map<String, TicketOrder> paidOrderById = paidOrders.stream()
                .collect(Collectors.toMap(TicketOrder::getId, Function.identity(), (left, right) -> left));

        Map<String, BigDecimal> revenueByShow = new HashMap<>();
        for (TicketOrder order : paidOrders) {
            ShowSchedule schedule = scheduleById.get(order.getScheduleId());
            if (schedule != null) {
                revenueByShow.merge(
                        schedule.getShowId(),
                        order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount(),
                        BigDecimal::add
                );
            }
        }

        List<String> paidOrderIds = paidOrders.stream()
                .map(TicketOrder::getId)
                .toList();
        List<TicketItem> validTickets = paidOrderIds.isEmpty()
                ? List.of()
                : ticketItemMapper.selectList(new LambdaQueryWrapper<TicketItem>()
                        .in(TicketItem::getOrderId, paidOrderIds)
                        .in(TicketItem::getStatus, List.of("UNUSED", "CHECKED_IN", "PENDING_REFUND")))
                        .stream()
                        .filter(ticket -> paidOrderById.containsKey(ticket.getOrderId()))
                        .filter(ticket -> "UNUSED".equals(ticket.getStatus())
                                || "CHECKED_IN".equals(ticket.getStatus())
                                || "PENDING_REFUND".equals(ticket.getStatus()))
                        .toList();

        Map<String, Long> ticketsByShow = new HashMap<>();
        for (TicketItem ticket : validTickets) {
            ShowSchedule schedule = scheduleById.get(ticket.getScheduleId());
            if (schedule != null) {
                ticketsByShow.merge(schedule.getShowId(), 1L, Long::sum);
            }
        }

        List<ShowEntity> rankedShows = publishedShows.stream()
                .sorted(recommendationComparator(availableSchedulesByShow, ticketsByShow, revenueByShow))
                .limit(8)
                .toList();

        return java.util.stream.IntStream.range(0, rankedShows.size())
                .mapToObj(index -> toRecommendationResponse(
                        rankedShows.get(index),
                        index + 1,
                        ticketsByShow.getOrDefault(rankedShows.get(index).getId(), 0L),
                        availableSchedulesByShow.getOrDefault(rankedShows.get(index).getId(), 0L),
                        availableTicketsByShow.getOrDefault(rankedShows.get(index).getId(), 0L),
                        revenueByShow.getOrDefault(rankedShows.get(index).getId(), BigDecimal.ZERO),
                        priceBoundsByShow.get(rankedShows.get(index).getId())
                ))
                .toList();
    }

    public ShowResponse getShowDetail(String id) {
        ShowEntity show = showMapper.selectById(id);
        if (show == null || !"PUBLISHED".equals(show.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "演出不存在或未发布");
        }
        return toShowResponse(show, priceBoundsByShow(List.of(id)).get(id));
    }

    public List<ScheduleResponse> listSchedules(String showId) {
        getShowDetail(showId);
        return showScheduleMapper.selectList(new LambdaQueryWrapper<ShowSchedule>()
                        .eq(ShowSchedule::getShowId, showId)
                        .orderByAsc(ShowSchedule::getStartTime))
                .stream()
                .filter(this::isPublicSchedule)
                .filter(this::isUserListableSchedule)
                .map(this::toScheduleResponse)
                .toList();
    }

    private ShowResponse toShowResponse(ShowEntity show, PriceBounds priceBounds) {
        return new ShowResponse(
                show.getId(),
                show.getTitle(),
                show.getSubtitle(),
                show.getCoverUrl(),
                show.getDescription(),
                fallback(show.getIntro(), show.getDescription()),
                fallback(show.getCastMembers(), "演职人员信息待补充"),
                fallback(show.getCreativeTeam(), "主创团队信息待补充"),
                fallback(show.getFullSynopsis(), show.getDescription()),
                show.getDuration(),
                show.getCategory(),
                show.getTags(),
                displayPriceRange(priceBounds),
                minPrice(priceBounds),
                maxPrice(priceBounds)
        );
    }

    private Comparator<ShowEntity> recommendationComparator(
            Map<String, Long> availableSchedulesByShow,
            Map<String, Long> ticketsByShow,
            Map<String, BigDecimal> revenueByShow
    ) {
        return (left, right) -> {
            int availabilityCompare = Boolean.compare(
                    availableSchedulesByShow.getOrDefault(right.getId(), 0L) > 0,
                    availableSchedulesByShow.getOrDefault(left.getId(), 0L) > 0
            );
            if (availabilityCompare != 0) {
                return availabilityCompare;
            }

            int ticketCompare = Long.compare(
                    ticketsByShow.getOrDefault(right.getId(), 0L),
                    ticketsByShow.getOrDefault(left.getId(), 0L)
            );
            if (ticketCompare != 0) {
                return ticketCompare;
            }

            int revenueCompare = revenueByShow.getOrDefault(right.getId(), BigDecimal.ZERO)
                    .compareTo(revenueByShow.getOrDefault(left.getId(), BigDecimal.ZERO));
            if (revenueCompare != 0) {
                return revenueCompare;
            }

            int sortCompare = Integer.compare(
                    left.getSortOrder() == null ? Integer.MAX_VALUE : left.getSortOrder(),
                    right.getSortOrder() == null ? Integer.MAX_VALUE : right.getSortOrder()
            );
            if (sortCompare != 0) {
                return sortCompare;
            }

            if (left.getCreatedAt() == null && right.getCreatedAt() == null) {
                return 0;
            }
            if (left.getCreatedAt() == null) {
                return 1;
            }
            if (right.getCreatedAt() == null) {
                return -1;
            }
            return right.getCreatedAt().compareTo(left.getCreatedAt());
        };
    }

    private ShowRecommendationResponse toRecommendationResponse(
            ShowEntity show,
            int rank,
            long ticketsSold,
            long availableScheduleCount,
            long availableTicketCount,
            BigDecimal revenue,
            PriceBounds priceBounds
    ) {
        BigDecimal hotScore = BigDecimal.valueOf(ticketsSold)
                .multiply(BigDecimal.valueOf(100))
                .add(BigDecimal.valueOf(availableScheduleCount).multiply(BigDecimal.TEN))
                .add(revenue == null ? BigDecimal.ZERO : revenue);
        return new ShowRecommendationResponse(
                show.getId(),
                show.getTitle(),
                show.getSubtitle(),
                show.getCoverUrl(),
                show.getDescription(),
                fallback(show.getIntro(), show.getDescription()),
                fallback(show.getCastMembers(), "演职人员信息待补充"),
                fallback(show.getCreativeTeam(), "主创团队信息待补充"),
                fallback(show.getFullSynopsis(), show.getDescription()),
                show.getDuration(),
                show.getCategory(),
                show.getTags(),
                displayPriceRange(priceBounds),
                minPrice(priceBounds),
                maxPrice(priceBounds),
                rank,
                ticketsSold,
                availableScheduleCount,
                availableTicketCount,
                hotScore
        );
    }

    private Map<String, Long> calculateAvailableTicketsByShow(List<ShowSchedule> schedules) {
        Map<String, Long> availableTicketsByShow = new HashMap<>();
        for (ShowSchedule schedule : schedules) {
            if (!isPurchasableSchedule(schedule)) {
                continue;
            }
            availableTicketsByShow.merge(schedule.getShowId(), countAvailableTickets(schedule), Long::sum);
        }
        return availableTicketsByShow;
    }

    private Map<String, PriceBounds> priceBoundsByShow(List<String> showIds) {
        if (showIds == null || showIds.isEmpty()) {
            return Map.of();
        }
        List<ShowSchedule> schedules = showScheduleMapper.selectList(new LambdaQueryWrapper<ShowSchedule>()
                        .in(ShowSchedule::getShowId, showIds))
                .stream()
                .filter(this::isPublicSchedule)
                .toList();
        return collectPriceBoundsByShow(schedules);
    }

    private Map<String, PriceBounds> collectPriceBoundsByShow(List<ShowSchedule> schedules) {
        Map<String, PriceBounds> boundsByShow = new HashMap<>();
        for (ShowSchedule schedule : schedules) {
            if (!hasDisplayablePrice(schedule)) {
                continue;
            }
            boundsByShow.merge(schedule.getShowId(), priceBoundsForSchedule(schedule), PriceBounds::merge);
        }
        return boundsByShow;
    }

    private boolean hasDisplayablePrice(ShowSchedule schedule) {
        return !"CANCELLED".equals(schedule.getStatus())
                && !"ENDED".equals(schedule.getStatus())
                && (schedule.getEndTime() == null || !LocalDateTime.now().isAfter(schedule.getEndTime()))
                && !isSaleEnded(schedule);
    }

    private PriceBounds priceBoundsForSchedule(ShowSchedule schedule) {
        PriceBounds bounds = new PriceBounds(null, null, schedule.getPriceRange());
        String mode = schedule.getTicketMode() == null ? "SEATED" : schedule.getTicketMode();
        if (!"ZONED".equals(mode)) {
            bounds = bounds.merge(seatPriceBounds(schedule.getId(), Set.of()));
        }
        if ("ZONED".equals(mode) || "MIXED".equals(mode)) {
            bounds = bounds.merge(inventoryPriceBounds(schedule.getId()));
        }
        if (bounds.hasPrice()) {
            return bounds;
        }
        return fallbackPriceBounds(schedule.getPriceRange());
    }

    private PriceBounds seatPriceBounds(String scheduleId, Set<String> areaIds) {
        LambdaQueryWrapper<ScheduleSeat> wrapper = new LambdaQueryWrapper<ScheduleSeat>()
                .eq(ScheduleSeat::getScheduleId, scheduleId)
                .ne(ScheduleSeat::getStatus, "DISABLED");
        if (areaIds != null && !areaIds.isEmpty()) {
            wrapper.in(ScheduleSeat::getAreaId, areaIds);
        }
        List<ScheduleSeat> seats = scheduleSeatMapper.selectList(wrapper);
        PriceBounds bounds = new PriceBounds(null, null, null);
        if (seats == null) {
            return bounds;
        }
        for (ScheduleSeat seat : seats) {
            if ("DISABLED".equals(seat.getStatus())) {
                continue;
            }
            bounds = bounds.include(seat.getPrice());
        }
        return bounds;
    }

    private PriceBounds inventoryPriceBounds(String scheduleId) {
        PriceBounds bounds = new PriceBounds(null, null, null);
        for (ScheduleAreaInventory inventory : listInventories(scheduleId)) {
            if (inventory.getTotalCount() != null && inventory.getTotalCount() <= 0) {
                continue;
            }
            if ("DISABLED".equals(inventory.getStatus()) || "CANCELLED".equals(inventory.getStatus())) {
                continue;
            }
            bounds = bounds.include(inventory.getPrice());
        }
        return bounds;
    }

    private PriceBounds fallbackPriceBounds(String priceRange) {
        PriceBounds bounds = new PriceBounds(null, null, priceRange);
        Matcher matcher = PRICE_NUMBER_PATTERN.matcher(priceRange == null ? "" : priceRange.replace(",", ""));
        while (matcher.find()) {
            bounds = bounds.include(new BigDecimal(matcher.group()));
        }
        return bounds;
    }

    private String displayPriceRange(PriceBounds bounds) {
        if (bounds == null) {
            return null;
        }
        if (!bounds.hasPrice()) {
            return bounds.fallbackRange();
        }
        if (bounds.max() == null || bounds.min().compareTo(bounds.max()) == 0) {
            return normalizePrice(bounds.min());
        }
        return "%s - %s".formatted(normalizePrice(bounds.min()), normalizePrice(bounds.max()));
    }

    private BigDecimal minPrice(PriceBounds bounds) {
        return bounds == null ? null : bounds.min();
    }

    private BigDecimal maxPrice(PriceBounds bounds) {
        return bounds == null ? null : bounds.max();
    }

    private String normalizePrice(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private long countAvailableTickets(ShowSchedule schedule) {
        String mode = schedule.getTicketMode() == null ? "SEATED" : schedule.getTicketMode();
        if ("ZONED".equals(mode)) {
            return listInventories(schedule.getId()).stream()
                    .mapToLong(inv -> inv.getAvailableCount() == null ? 0 : inv.getAvailableCount())
                    .sum();
        }
        if ("MIXED".equals(mode)) {
            List<ScheduleAreaInventory> inventories = listInventories(schedule.getId());
            Map<String, VenueArea> areaById = inventories.stream()
                    .map(inv -> venueAreaMapper.selectById(inv.getAreaId()))
                    .filter(area -> area != null)
                    .collect(Collectors.toMap(VenueArea::getId, Function.identity(), (left, right) -> left));

            long standingAvailable = inventories.stream()
                    .filter(inv -> {
                        VenueArea area = areaById.get(inv.getAreaId());
                        return area == null || !Boolean.TRUE.equals(area.getIsSeated());
                    })
                    .mapToLong(inv -> inv.getAvailableCount() == null ? 0 : inv.getAvailableCount())
                    .sum();
            Set<String> seatedAreaIds = areaById.values().stream()
                    .filter(area -> Boolean.TRUE.equals(area.getIsSeated()))
                    .map(VenueArea::getId)
                    .collect(Collectors.toSet());
            long seatedAvailable = seatedAreaIds.isEmpty() ? 0 : countAvailableSeats(schedule.getId(), seatedAreaIds);
            return standingAvailable + seatedAvailable;
        }
        return countAvailableSeats(schedule.getId(), Set.of());
    }

    private List<ScheduleAreaInventory> listInventories(String scheduleId) {
        List<ScheduleAreaInventory> inventories = scheduleAreaInventoryMapper.selectList(new LambdaQueryWrapper<ScheduleAreaInventory>()
                .eq(ScheduleAreaInventory::getScheduleId, scheduleId));
        return inventories == null ? List.of() : inventories;
    }

    private long countAvailableSeats(String scheduleId, Set<String> areaIds) {
        LambdaQueryWrapper<ScheduleSeat> wrapper = new LambdaQueryWrapper<ScheduleSeat>()
                .eq(ScheduleSeat::getScheduleId, scheduleId)
                .eq(ScheduleSeat::getStatus, "AVAILABLE");
        if (areaIds != null && !areaIds.isEmpty()) {
            wrapper.in(ScheduleSeat::getAreaId, areaIds);
        }
        List<ScheduleSeat> seats = scheduleSeatMapper.selectList(wrapper);
        if (seats == null || seats.isEmpty()) {
            return 0;
        }
        return seats.stream()
                .filter(seat -> !Boolean.TRUE.equals(redisTemplate.hasKey(SeatService.lockKey(scheduleId, seat.getSeatCode()))))
                .count();
    }

    private String fallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    public ScheduleResponse getScheduleDetail(String scheduleId) {
        ShowSchedule schedule = showScheduleMapper.selectById(scheduleId);
        if (schedule == null || !isPublicSchedule(schedule)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "场次不存在");
        }
        return toScheduleResponse(schedule);
    }

    private boolean isPublicSchedule(ShowSchedule schedule) {
        return schedule.getPublishStatus() == null || "PUBLISHED".equals(schedule.getPublishStatus());
    }

    private boolean isPurchasableSchedule(ShowSchedule schedule) {
        if (!isPublicSchedule(schedule) || !"ON_SALE".equals(schedule.getStatus())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (schedule.getSaleStartTime() != null && now.isBefore(schedule.getSaleStartTime())) {
            return false;
        }
        if (schedule.getEndTime() != null && now.isAfter(schedule.getEndTime())) {
            return false;
        }
        return !isSaleEnded(schedule);
    }

    private boolean isUserListableSchedule(ShowSchedule schedule) {
        if (!isPublicSchedule(schedule)) {
            return false;
        }
        if ("CANCELLED".equals(schedule.getStatus()) || "ENDED".equals(schedule.getStatus())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (schedule.getEndTime() != null && now.isAfter(schedule.getEndTime())) {
            return false;
        }
        return schedule.getSaleEndTime() == null || !now.isAfter(schedule.getSaleEndTime());
    }

    private boolean isSaleEnded(ShowSchedule schedule) {
        return schedule.getSaleEndTime() != null && LocalDateTime.now().isAfter(schedule.getSaleEndTime());
    }

    private String effectiveScheduleStatus(ShowSchedule schedule) {
        if (schedule.getEndTime() != null && LocalDateTime.now().isAfter(schedule.getEndTime())) {
            return "ENDED";
        }
        if (!"ON_SALE".equals(schedule.getStatus())) {
            return schedule.getStatus();
        }
        LocalDateTime now = LocalDateTime.now();
        if (schedule.getSaleStartTime() != null && now.isBefore(schedule.getSaleStartTime())) {
            return "COMING_SOON";
        }
        if (isSaleEnded(schedule)) {
            return "ENDED";
        }
        return schedule.getStatus();
    }

    private ScheduleResponse toScheduleResponse(ShowSchedule schedule) {
        ShowEntity show = showMapper.selectById(schedule.getShowId());
        String category = show != null ? show.getCategory() : null;
        PriceBounds priceBounds = priceBoundsForSchedule(schedule);
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getShowId(),
                schedule.getTheaterName(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getSaleStartTime(),
                schedule.getSaleEndTime(),
                effectiveScheduleStatus(schedule),
                schedule.getPublishStatus() == null ? "PUBLISHED" : schedule.getPublishStatus(),
                displayPriceRange(priceBounds),
                minPrice(priceBounds),
                maxPrice(priceBounds),
                schedule.getTicketMode(),
                category
        );
    }

    private record PriceBounds(BigDecimal min, BigDecimal max, String fallbackRange) {
        boolean hasPrice() {
            return min != null;
        }

        PriceBounds include(BigDecimal price) {
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                return this;
            }
            BigDecimal nextMin = min == null || price.compareTo(min) < 0 ? price : min;
            BigDecimal nextMax = max == null || price.compareTo(max) > 0 ? price : max;
            return new PriceBounds(nextMin, nextMax, fallbackRange);
        }

        PriceBounds merge(PriceBounds other) {
            if (other == null || !other.hasPrice()) {
                return this;
            }
            return include(other.min()).include(other.max());
        }
    }
}
