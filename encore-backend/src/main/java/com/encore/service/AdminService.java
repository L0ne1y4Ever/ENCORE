package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.encore.common.ErrorCode;
import com.encore.dto.AdminBoxOfficeResponse;
import com.encore.dto.AdminDashboardResponse;
import com.encore.dto.AdminOrderResponse;
import com.encore.dto.AdminScheduleResponse;
import com.encore.dto.AdminShowCategoryOption;
import com.encore.dto.AdminShowFilterOption;
import com.encore.dto.AdminShowResponse;
import com.encore.dto.CreateScheduleRequest;
import com.encore.dto.CreateShowRequest;
import com.encore.dto.RefundRequestSummary;
import com.encore.dto.ReviewRefundRequest;
import com.encore.dto.UpdateScheduleRequest;
import com.encore.dto.UpdateShowRequest;
import com.encore.entity.RefundRequest;
import com.encore.entity.ScheduleSeat;
import com.encore.entity.SeatLayout;
import com.encore.entity.ShowEntity;
import com.encore.entity.ShowSchedule;
import com.encore.entity.TicketItem;
import com.encore.entity.TicketOrder;
import com.encore.entity.UserAccount;
import com.encore.entity.VenueArea;
import com.encore.entity.ScheduleAreaInventory;
import com.encore.exception.BusinessException;
import com.encore.mapper.RefundRequestMapper;
import com.encore.mapper.ScheduleSeatMapper;
import com.encore.mapper.ShowMapper;
import com.encore.mapper.ShowScheduleMapper;
import com.encore.mapper.TicketItemMapper;
import com.encore.mapper.TicketOrderMapper;
import com.encore.mapper.UserAccountMapper;
import com.encore.mapper.VenueAreaMapper;
import com.encore.mapper.ScheduleAreaInventoryMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminService {
    private static final int DEFAULT_SEAT_ROWS = 10;
    private static final int DEFAULT_SEAT_COLS = 15;
    private static final BigDecimal DEFAULT_VIP_PRICE = BigDecimal.valueOf(150);
    private static final BigDecimal DEFAULT_STANDARD_PRICE = BigDecimal.valueOf(100);
    private static final BigDecimal DEFAULT_ECONOMY_PRICE = BigDecimal.valueOf(50);
    private static final Set<String> ADMIN_ROLES = Set.of("admin", "sysadmin");
    private static final Set<String> SCHEDULE_STATUSES = Set.of(
            "DRAFT", "PUBLISHED", "COMING_SOON", "PREPARING", "ON_SALE", "SOLD_OUT", "CANCELLED", "ENDED"
            );
    private static final Set<String> SHOW_STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
    private static final Set<String> TICKET_MODES = Set.of("SEATED", "ZONED", "MIXED");
    private static final Set<String> BOX_OFFICE_RANGES = Set.of("LAST_7_DAYS", "LAST_30_DAYS", "ALL", "CUSTOM");

    private final ShowScheduleMapper showScheduleMapper;
    private final ShowMapper showMapper;
    private final ScheduleSeatMapper scheduleSeatMapper;
    private final TicketOrderMapper ticketOrderMapper;
    private final TicketItemMapper ticketItemMapper;
    private final UserAccountMapper userAccountMapper;
    private final StringRedisTemplate redisTemplate;
    private final SeatStatusPublisher seatStatusPublisher;
    private final DashboardRefreshPublisher dashboardRefreshPublisher;
    private final VenueAreaMapper venueAreaMapper;
    private final ScheduleAreaInventoryMapper scheduleAreaInventoryMapper;
    private final VenueManagementService venueManagementService;
    private final SeatService seatService;
    private final RefundRequestMapper refundRequestMapper;

    public AdminService(
            ShowScheduleMapper showScheduleMapper,
            ShowMapper showMapper,
            ScheduleSeatMapper scheduleSeatMapper,
            TicketOrderMapper ticketOrderMapper,
            TicketItemMapper ticketItemMapper,
            UserAccountMapper userAccountMapper,
            StringRedisTemplate redisTemplate,
            SeatStatusPublisher seatStatusPublisher,
            DashboardRefreshPublisher dashboardRefreshPublisher,
            VenueAreaMapper venueAreaMapper,
            ScheduleAreaInventoryMapper scheduleAreaInventoryMapper,
            VenueManagementService venueManagementService,
            SeatService seatService,
            RefundRequestMapper refundRequestMapper
    ) {
        this.showScheduleMapper = showScheduleMapper;
        this.showMapper = showMapper;
        this.scheduleSeatMapper = scheduleSeatMapper;
        this.ticketOrderMapper = ticketOrderMapper;
        this.ticketItemMapper = ticketItemMapper;
        this.userAccountMapper = userAccountMapper;
        this.redisTemplate = redisTemplate;
        this.seatStatusPublisher = seatStatusPublisher;
        this.dashboardRefreshPublisher = dashboardRefreshPublisher;
        this.venueAreaMapper = venueAreaMapper;
        this.scheduleAreaInventoryMapper = scheduleAreaInventoryMapper;
        this.venueManagementService = venueManagementService;
        this.seatService = seatService;
        this.refundRequestMapper = refundRequestMapper;
    }

    public AdminDashboardResponse dashboard() {
        ensureAdminRole();
        List<TicketOrder> paidOrders = ticketOrderMapper.selectList(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getStatus, "PAID"));
        Set<String> paidOrderIds = paidOrders.stream()
                .map(TicketOrder::getId)
                .collect(Collectors.toSet());
        List<TicketItem> tickets = ticketItemMapper.selectList(new LambdaQueryWrapper<>());
        List<TicketItem> validTickets = tickets.stream()
                .filter(ticket -> paidOrderIds.contains(ticket.getOrderId()))
                .filter(ticket -> "UNUSED".equals(ticket.getStatus()) || "CHECKED_IN".equals(ticket.getStatus()))
                .toList();

        BigDecimal totalRevenue = paidOrders.stream()
                .map(TicketOrder::getTotalAmount)
                .map(this::moneyOrZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long checkedIn = validTickets.stream()
                .filter(ticket -> "CHECKED_IN".equals(ticket.getStatus()))
                .count();
        long unused = validTickets.stream()
                .filter(ticket -> "UNUSED".equals(ticket.getStatus()))
                .count();
        long voided = tickets.stream()
                .filter(ticket -> "VOID".equals(ticket.getStatus()))
                .count();
        BigDecimal avgAttendance = validTickets.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(checkedIn)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(validTickets.size()), 1, RoundingMode.HALF_UP);
        long activeShows = showMapper.selectCount(new LambdaQueryWrapper<ShowEntity>()
                .eq(ShowEntity::getStatus, "PUBLISHED"));

        Map<String, TicketOrder> paidOrderById = paidOrders.stream()
                .collect(Collectors.toMap(TicketOrder::getId, Function.identity()));
        return new AdminDashboardResponse(
                totalRevenue,
                validTickets.size(),
                activeShows,
                avgAttendance,
                buildSalesTrend(paidOrders, validTickets, paidOrderById),
                buildTopShows(paidOrders, validTickets),
                new AdminDashboardResponse.CheckInSummary(checkedIn, unused, voided),
                boxOffice("LAST_7_DAYS", null, null, null, null).summary()
        );
    }

    public AdminBoxOfficeResponse boxOffice(String range, LocalDate startDate, LocalDate endDate, String showId, String category) {
        ensureAdminRole();
        BoxOfficeWindow window = resolveBoxOfficeWindow(range, startDate, endDate);
        String selectedShowId = cleanOptional(showId);
        String selectedCategory = cleanOptional(category);
        List<TicketOrder> orders = ticketOrderMapper.selectList(new LambdaQueryWrapper<TicketOrder>());
        List<TicketOrder> scopedOrders = orders.stream()
                .filter(order -> orderInBoxOfficeScope(order, window))
                .toList();
        List<TicketItem> tickets = ticketItemMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, List<TicketItem>> ticketsByOrderId = tickets.stream()
                .collect(Collectors.groupingBy(TicketItem::getOrderId));
        Map<String, ShowSchedule> scheduleById = showScheduleMapper.selectList(new LambdaQueryWrapper<ShowSchedule>())
                .stream()
                .collect(Collectors.toMap(ShowSchedule::getId, Function.identity(), (left, right) -> left));
        Map<String, ShowEntity> showById = showMapper.selectList(new LambdaQueryWrapper<ShowEntity>())
                .stream()
                .collect(Collectors.toMap(ShowEntity::getId, Function.identity(), (left, right) -> left));
        BoxOfficeAccumulator globalSummary = new BoxOfficeAccumulator();
        orders.stream()
                .filter(order -> orderInBoxOfficeScope(order, new BoxOfficeWindow("ALL", null, null)))
                .forEach(order -> globalSummary.add(metricsForOrder(
                        order,
                        ticketsByOrderId.getOrDefault(order.getId(), List.of())
                )));

        List<TicketOrder> filteredOrders = scopedOrders.stream()
                .filter(order -> {
                    ShowSchedule schedule = scheduleById.get(order.getScheduleId());
                    ShowEntity show = schedule == null ? null : showById.get(schedule.getShowId());
                    if (StringUtils.hasText(selectedCategory)
                            && (show == null || !selectedCategory.equalsIgnoreCase(categoryName(show)))) {
                        return false;
                    }
                    return !StringUtils.hasText(selectedShowId)
                            || (schedule != null && selectedShowId.equals(schedule.getShowId()));
                })
                .toList();

        BoxOfficeAccumulator summary = new BoxOfficeAccumulator();
        Map<LocalDate, BoxOfficeAccumulator> trendMap = buildTrendMap(window, filteredOrders);
        Map<String, BoxOfficeAccumulator> categoryMap = new LinkedHashMap<>();
        Map<String, BoxOfficeAccumulator> showMap = new LinkedHashMap<>();
        Map<String, BoxOfficeAccumulator> scheduleMap = new LinkedHashMap<>();

        for (TicketOrder order : filteredOrders) {
            ShowSchedule schedule = scheduleById.get(order.getScheduleId());
            ShowEntity show = schedule == null ? null : showById.get(schedule.getShowId());
            List<TicketItem> orderTickets = ticketsByOrderId.getOrDefault(order.getId(), List.of());
            BoxOfficeOrderMetrics metrics = metricsForOrder(order, orderTickets);

            summary.add(metrics);
            LocalDate date = dateForBoxOffice(order);
            if (date != null && trendMap.containsKey(date)) {
                trendMap.get(date).add(metrics);
            }
            if (schedule != null) {
                String categoryName = categoryName(show);
                BoxOfficeAccumulator categoryAccumulator = categoryMap.computeIfAbsent(categoryName, name -> {
                    BoxOfficeAccumulator accumulator = new BoxOfficeAccumulator();
                    accumulator.category = name;
                    return accumulator;
                });
                if (StringUtils.hasText(schedule.getShowId())) {
                    categoryAccumulator.addShow(schedule.getShowId());
                }
                categoryAccumulator.addSchedule(schedule.getId());
                categoryAccumulator.add(metrics);

                BoxOfficeAccumulator showAccumulator = showMap.computeIfAbsent(schedule.getShowId(), id -> {
                    BoxOfficeAccumulator accumulator = new BoxOfficeAccumulator();
                    accumulator.showId = id;
                    accumulator.showTitle = show == null ? "Unknown Show" : show.getTitle();
                    accumulator.category = categoryName(show);
                    return accumulator;
                });
                showAccumulator.addSchedule(schedule.getId());
                showAccumulator.add(metrics);
                scheduleMap.computeIfAbsent(schedule.getId(), id -> {
                    BoxOfficeAccumulator accumulator = new BoxOfficeAccumulator();
                    accumulator.scheduleId = id;
                    accumulator.showId = schedule.getShowId();
                    accumulator.showTitle = show == null ? "Unknown Show" : show.getTitle();
                    accumulator.theaterName = schedule.getTheaterName();
                    accumulator.startTime = schedule.getStartTime();
                    return accumulator;
                }).add(metrics);
            }
        }

        List<AdminBoxOfficeResponse.CategoryRow> categoryRows = categoryMap.values().stream()
                .sorted(Comparator.comparing(BoxOfficeAccumulator::netRevenue).reversed()
                        .thenComparing(accumulator -> accumulator.category == null ? "" : accumulator.category))
                .map(BoxOfficeAccumulator::toCategoryRow)
                .toList();
        List<AdminBoxOfficeResponse.ShowRow> showRows = showMap.values().stream()
                .sorted(Comparator.comparing(BoxOfficeAccumulator::netRevenue).reversed()
                        .thenComparing(accumulator -> accumulator.showTitle == null ? "" : accumulator.showTitle))
                .map(BoxOfficeAccumulator::toShowRow)
                .toList();
        List<AdminBoxOfficeResponse.ScheduleRow> scheduleRows = scheduleMap.values().stream()
                .sorted(Comparator.comparing((BoxOfficeAccumulator accumulator) -> accumulator.startTime,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(accumulator -> accumulator.scheduleId == null ? "" : accumulator.scheduleId))
                .map(BoxOfficeAccumulator::toScheduleRow)
                .toList();
        return new AdminBoxOfficeResponse(
                globalSummary.toSummary(),
                summary.toSummary(),
                trendMap.entrySet().stream()
                        .map(entry -> entry.getValue().toTrendItem(entry.getKey()))
                        .toList(),
                categoryRows,
                showRows,
                scheduleRows
        );
    }

    public List<AdminShowResponse> listShows() {
        ensureAdminRole();
        return showMapper.selectList(new LambdaQueryWrapper<ShowEntity>()
                        .orderByAsc(ShowEntity::getSortOrder)
                        .orderByDesc(ShowEntity::getCreatedAt))
                .stream()
                .map(this::toShowResponse)
                .toList();
    }

    public List<AdminShowCategoryOption> listShowCategories() {
        ensureAdminRole();
        Map<String, Long> counts = showMapper.selectList(new LambdaQueryWrapper<ShowEntity>())
                .stream()
                .collect(Collectors.groupingBy(
                        this::categoryName,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new AdminShowCategoryOption(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<AdminShowFilterOption> listShowOptions(String category, String keyword, Integer limit) {
        ensureAdminRole();
        String selectedCategory = cleanOptional(category);
        String normalizedKeyword = cleanOptional(keyword);
        if (normalizedKeyword != null) {
            normalizedKeyword = normalizedKeyword.toLowerCase(Locale.ROOT);
        }
        int max = limit == null ? 30 : Math.max(1, Math.min(limit, 100));
        Map<String, Long> scheduleCountByShowId = showScheduleMapper.selectList(new LambdaQueryWrapper<ShowSchedule>())
                .stream()
                .filter(schedule -> StringUtils.hasText(schedule.getShowId()))
                .collect(Collectors.groupingBy(ShowSchedule::getShowId, Collectors.counting()));

        String finalNormalizedKeyword = normalizedKeyword;
        return showMapper.selectList(new LambdaQueryWrapper<ShowEntity>()
                        .orderByAsc(ShowEntity::getSortOrder)
                        .orderByDesc(ShowEntity::getCreatedAt))
                .stream()
                .filter(show -> !StringUtils.hasText(selectedCategory)
                        || selectedCategory.equalsIgnoreCase(categoryName(show)))
                .filter(show -> !StringUtils.hasText(finalNormalizedKeyword)
                        || showMatchesKeyword(show, finalNormalizedKeyword))
                .limit(max)
                .map(show -> new AdminShowFilterOption(
                        show.getId(),
                        show.getTitle(),
                        show.getSubtitle(),
                        categoryName(show),
                        show.getStatus(),
                        scheduleCountByShowId.getOrDefault(show.getId(), 0L)
                ))
                .toList();
    }

    @Transactional
    public AdminShowResponse createShow(CreateShowRequest request) {
        ensureAdminRole();
        ShowEntity show = new ShowEntity();
        show.setId(generateShowId());
        show.setTitle(clean(request.title()));
        show.setSubtitle(clean(request.subtitle()));
        show.setCoverUrl(clean(request.coverUrl()));
        show.setDescription(clean(request.description()));
        show.setIntro(cleanOptional(request.intro()));
        show.setCastMembers(cleanOptional(request.castMembers()));
        show.setCreativeTeam(cleanOptional(request.creativeTeam()));
        show.setFullSynopsis(cleanOptional(request.fullSynopsis()));
        show.setDuration(request.duration());
        show.setCategory(clean(request.category()));
        show.setTags(normalizeTags(request.tags()));
        show.setStatus(StringUtils.hasText(request.status()) ? normalizeShowStatus(request.status()) : "DRAFT");
        show.setSortOrder(request.sortOrder() == null ? nextShowSortOrder() : request.sortOrder());
        showMapper.insert(show);
        dashboardRefreshPublisher.publish("SHOW_CHANGED", show.getId());
        return toShowResponse(showMapper.selectById(show.getId()));
    }

    @Transactional
    public AdminShowResponse updateShow(String showId, UpdateShowRequest request) {
        ensureAdminRole();
        ShowEntity show = getShow(showId);
        show.setTitle(clean(request.title()));
        show.setSubtitle(clean(request.subtitle()));
        show.setCoverUrl(clean(request.coverUrl()));
        show.setDescription(clean(request.description()));
        show.setIntro(cleanOptional(request.intro()));
        show.setCastMembers(cleanOptional(request.castMembers()));
        show.setCreativeTeam(cleanOptional(request.creativeTeam()));
        show.setFullSynopsis(cleanOptional(request.fullSynopsis()));
        show.setDuration(request.duration());
        show.setCategory(clean(request.category()));
        show.setTags(normalizeTags(request.tags()));
        if (StringUtils.hasText(request.status())) {
            show.setStatus(normalizeShowStatus(request.status()));
        }
        if (request.sortOrder() != null) {
            show.setSortOrder(request.sortOrder());
        }
        showMapper.updateById(show);
        dashboardRefreshPublisher.publish("SHOW_CHANGED", showId);
        return toShowResponse(showMapper.selectById(showId));
    }

    @Transactional
    public AdminShowResponse updateShowStatus(String showId, String status) {
        ensureAdminRole();
        ShowEntity show = getShow(showId);
        show.setStatus(normalizeShowStatus(status));
        showMapper.updateById(show);
        dashboardRefreshPublisher.publish("SHOW_CHANGED", showId);
        return toShowResponse(showMapper.selectById(showId));
    }

    @Transactional
    public AdminShowResponse archiveShow(String showId) {
        ensureAdminRole();
        ShowEntity show = getShow(showId);
        show.setStatus("ARCHIVED");
        showMapper.updateById(show);
        dashboardRefreshPublisher.publish("SHOW_CHANGED", showId);
        return toShowResponse(showMapper.selectById(showId));
    }

    public List<AdminScheduleResponse> listSchedules() {
        ensureAdminRole();
        return showScheduleMapper.selectList(new LambdaQueryWrapper<ShowSchedule>()
                        .orderByAsc(ShowSchedule::getStartTime))
                .stream()
                .map(this::toScheduleResponse)
                .toList();
    }

    @Transactional
    public AdminScheduleResponse createSchedule(CreateScheduleRequest request) {
        ensureAdminRole();
        ensureSchedulableShow(request.showId());
        validateScheduleTime(request.startTime(), request.endTime());

        ShowSchedule schedule = new ShowSchedule();
        schedule.setId(generateScheduleId());
        schedule.setShowId(clean(request.showId()));
        schedule.setStartTime(request.startTime());
        schedule.setEndTime(request.endTime());
        schedule.setBusinessDate(request.startTime().toLocalDate());
        schedule.setSaleStartTime(request.saleStartTime());
        schedule.setSaleEndTime(request.saleEndTime());
        schedule.setStatus(StringUtils.hasText(request.status()) ? normalizeScheduleStatus(request.status()) : "PREPARING");
        schedule.setPublishStatus(StringUtils.hasText(request.publishStatus()) ? request.publishStatus().trim().toUpperCase() : "DRAFT");
        schedule.setPriceRange(clean(request.priceRange()));

        String mode = request.ticketMode();
        if (mode == null || mode.isBlank()) {
            mode = "SEATED";
        }
        schedule.setTicketMode(mode);

        SeatLayout layout = null;
        if (StringUtils.hasText(request.layoutId())) {
            layout = venueManagementService.getPublishedLayout(request.layoutId());
            schedule.setLayoutId(layout.getId());
            schedule.setHallId(layout.getHallId());
            schedule.setTicketMode(layout.getTicketMode());
        } else if (StringUtils.hasText(request.hallId())) {
            schedule.setHallId(clean(request.hallId()));
        }
        if (StringUtils.hasText(schedule.getHallId())) {
            var hall = venueManagementService.getHall(schedule.getHallId());
            if (layout == null && StringUtils.hasText(hall.getDefaultLayoutId())) {
                layout = venueManagementService.getPublishedLayout(hall.getDefaultLayoutId());
                schedule.setLayoutId(layout.getId());
                schedule.setTicketMode(layout.getTicketMode());
            }
            schedule.setTheaterName(hall.getName());
            venueManagementService.ensureScheduleConflictFree(null, schedule.getHallId(), schedule.getStartTime(), schedule.getEndTime());
        } else {
            if (!StringUtils.hasText(request.theaterName())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择场馆厅或填写剧场名称");
            }
            schedule.setTheaterName(clean(request.theaterName()));
        }
        showScheduleMapper.insert(schedule);

        if (layout != null) {
            venueManagementService.snapshotLayoutToSchedule(layout, schedule);
        } else if ("SEATED".equals(mode)) {
            generateSeatPool(
                    schedule.getId(),
                    request.seatRows() == null ? DEFAULT_SEAT_ROWS : request.seatRows(),
                    request.seatCols() == null ? DEFAULT_SEAT_COLS : request.seatCols(),
                    request.vipPrice() == null ? DEFAULT_VIP_PRICE : request.vipPrice(),
                    request.standardPrice() == null ? DEFAULT_STANDARD_PRICE : request.standardPrice(),
                    request.economyPrice() == null ? DEFAULT_ECONOMY_PRICE : request.economyPrice()
            );
        } else {
            // ZONED or MIXED
            List<VenueArea> templateAreas = venueAreaMapper.selectList(new LambdaQueryWrapper<VenueArea>()
                    .eq(VenueArea::getHallId, request.theaterName()));
            for (VenueArea area : templateAreas) {
                ScheduleAreaInventory inventory = new ScheduleAreaInventory();
                inventory.setId("inv-" + schedule.getId().replace("sch-", "") + "-" + area.getCode().toLowerCase());
                inventory.setScheduleId(schedule.getId());
                inventory.setAreaId(area.getId());
                inventory.setPrice(area.getBasePrice());
                // 座位区(看台)的库存真相源是 schedule_seat，本行仅作区域注册/价格/枚举；
                // 下方为座位区按 capacity 逐座位生成，故 total/available 初值与座位数一致。
                inventory.setTotalCount(area.getCapacity());
                inventory.setAvailableCount(area.getCapacity());
                inventory.setLockedCount(0);
                inventory.setSoldCount(0);
                inventory.setStatus("AVAILABLE");
                inventory.setCreatedAt(LocalDateTime.now());
                inventory.setUpdatedAt(LocalDateTime.now());
                scheduleAreaInventoryMapper.insert(inventory);

                if (area.getIsSeated() != null && area.getIsSeated()) {
                    int cols = 20;
                    int rows = (int) Math.ceil((double) area.getCapacity() / cols);
                    int count = 0;
                    for (int row = 1; row <= rows; row++) {
                        for (int col = 1; col <= cols; col++) {
                            if (count >= area.getCapacity()) {
                                break;
                            }
                            ScheduleSeat seat = new ScheduleSeat();
                            seat.setId("%s:seat-%s-%d-%d".formatted(schedule.getId(), area.getCode().toLowerCase(), row, col));
                            seat.setScheduleId(schedule.getId());
                            seat.setSeatCode("seat-%s-%d-%d".formatted(area.getCode().toLowerCase(), row, col));
                            seat.setRowNo(row);
                            seat.setColNo(col);
                            seat.setStatus("AVAILABLE");
                            seat.setSection(area.getCode());
                            seat.setPrice(area.getBasePrice());
                            seat.setAreaId(area.getId());
                            scheduleSeatMapper.insert(seat);
                            count++;
                        }
                    }
                }
            }
        }
        return toScheduleResponse(showScheduleMapper.selectById(schedule.getId()));
    }

    @Transactional
    public AdminScheduleResponse updateSchedule(String scheduleId, UpdateScheduleRequest request) {
        ensureAdminRole();
        ShowSchedule schedule = getSchedule(scheduleId);
        ensureSchedulableShow(request.showId());
        validateScheduleTime(request.startTime(), request.endTime());
        ensureScheduleStructureStable(schedule, request);

        schedule.setShowId(clean(request.showId()));
        schedule.setHallId(cleanOptional(request.hallId()));
        schedule.setLayoutId(cleanOptional(request.layoutId()));
        schedule.setTheaterName(clean(request.theaterName()));
        schedule.setStartTime(request.startTime());
        schedule.setEndTime(request.endTime());
        schedule.setBusinessDate(request.startTime().toLocalDate());
        schedule.setSaleStartTime(request.saleStartTime());
        schedule.setSaleEndTime(request.saleEndTime());
        schedule.setStatus(normalizeScheduleStatus(request.status()));
        schedule.setPublishStatus(StringUtils.hasText(request.publishStatus()) ? request.publishStatus().trim().toUpperCase() : schedule.getPublishStatus());
        schedule.setPriceRange(clean(request.priceRange()));
        if (request.ticketMode() != null) {
            schedule.setTicketMode(normalizeTicketMode(request.ticketMode()));
        }
        if (StringUtils.hasText(schedule.getHallId())) {
            venueManagementService.ensureScheduleConflictFree(scheduleId, schedule.getHallId(), schedule.getStartTime(), schedule.getEndTime());
        }
        showScheduleMapper.updateById(schedule);
        if ("CANCELLED".equals(schedule.getStatus())) {
            releaseScheduleLocks(scheduleId);
            seatStatusPublisher.publishScheduleCancelled(scheduleId);
        }
        return toScheduleResponse(showScheduleMapper.selectById(scheduleId));
    }

    @Transactional
    public AdminScheduleResponse updateScheduleStatus(String scheduleId, String status) {
        ensureAdminRole();
        String normalizedStatus = normalizeScheduleStatus(status);
        ShowSchedule schedule = getSchedule(scheduleId);
        schedule.setStatus(normalizedStatus);
        showScheduleMapper.updateById(schedule);
        if ("CANCELLED".equals(normalizedStatus)) {
            releaseScheduleLocks(scheduleId);
            seatStatusPublisher.publishScheduleCancelled(scheduleId);
        }
        return toScheduleResponse(showScheduleMapper.selectById(scheduleId));
    }

    @Transactional
    public AdminScheduleResponse cancelSchedule(String scheduleId) {
        ensureAdminRole();
        ShowSchedule schedule = getSchedule(scheduleId);
        schedule.setStatus("CANCELLED");
        showScheduleMapper.updateById(schedule);
        releaseScheduleLocks(scheduleId);
        seatStatusPublisher.publishScheduleCancelled(scheduleId);
        return toScheduleResponse(showScheduleMapper.selectById(scheduleId));
    }

    public List<AdminOrderResponse> listOrders() {
        ensureAdminRole();
        return ticketOrderMapper.selectList(new LambdaQueryWrapper<TicketOrder>()
                        .orderByDesc(TicketOrder::getCreatedAt))
                .stream()
                .map(this::toOrderResponse)
                .toList();
    }

    @Transactional
    public AdminOrderResponse refundOrder(String orderId) {
        UserAccount reviewer = currentAdminUser();
        TicketOrder order = getOrder(orderId);
        if ("REFUNDED".equals(order.getStatus())) {
            return toOrderResponse(order);
        }
        if (!"PAID".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅已支付订单可退款");
        }

        List<TicketItem> tickets = listTickets(orderId);
        boolean hasCheckedInTicket = tickets.stream().anyMatch(ticket -> "CHECKED_IN".equals(ticket.getStatus()));
        if (hasCheckedInTicket) {
            throw new BusinessException(ErrorCode.CONFLICT, "已核销订单不可退款");
        }

        createRefundRequest(order, "APPROVED", "ADMIN_DIRECT", null, null, reviewer);
        finalizeRefund(order, tickets, "REFUNDED");
        return toOrderResponse(getOrder(orderId));
    }

    @Transactional
    public AdminOrderResponse approveRefund(String orderId, ReviewRefundRequest request) {
        UserAccount reviewer = currentAdminUser();
        TicketOrder order = getOrder(orderId);
        if ("REFUNDED".equals(order.getStatus())) {
            return toOrderResponse(order);
        }
        if (!"PENDING_REFUND".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅待审核退票订单可通过审核");
        }
        RefundRequest refundRequest = findPendingRefundRequest(orderId);
        if (refundRequest == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "未找到待审核退票申请");
        }
        List<TicketItem> tickets = listTickets(orderId);
        if (tickets.stream().anyMatch(ticket -> "CHECKED_IN".equals(ticket.getStatus()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "已核销订单不可退款");
        }
        reviewRefundRequest(refundRequest, "APPROVED", request, reviewer);
        finalizeRefund(order, tickets, "REFUND_APPROVED");
        return toOrderResponse(getOrder(orderId));
    }

    @Transactional
    public AdminOrderResponse rejectRefund(String orderId, ReviewRefundRequest request) {
        UserAccount reviewer = currentAdminUser();
        TicketOrder order = getOrder(orderId);
        if (!"PENDING_REFUND".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅待审核退票订单可拒绝审核");
        }
        RefundRequest refundRequest = findPendingRefundRequest(orderId);
        if (refundRequest == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "未找到待审核退票申请");
        }
        reviewRefundRequest(refundRequest, "REJECTED", request, reviewer);
        order.setStatus("PAID");
        order.setUpdatedAt(LocalDateTime.now());
        ticketOrderMapper.updateById(order);
        dashboardRefreshPublisher.publish("ORDER_REFUND_REJECTED", orderId);
        return toOrderResponse(getOrder(orderId));
    }

    @Transactional
    public AdminOrderResponse forceCheckInOrder(String orderId) {
        ensureAdminRole();
        TicketOrder order = getOrder(orderId);
        if (!"PAID".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅已支付订单可核销");
        }

        LocalDateTime now = LocalDateTime.now();
        List<TicketItem> tickets = listTickets(orderId);
        for (TicketItem ticket : tickets) {
            if ("UNUSED".equals(ticket.getStatus())) {
                ticket.setStatus("CHECKED_IN");
                ticket.setUpdatedAt(now);
                ticketItemMapper.updateById(ticket);
            }
        }
        dashboardRefreshPublisher.publish("ORDER_FORCE_CHECKED_IN", orderId);
        return toOrderResponse(getOrder(orderId));
    }

    private void ensureAdminRole() {
        currentAdminUser();
    }

    private UserAccount currentAdminUser() {
        String userId = StpUtil.getLoginIdAsString();
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus()) || !ADMIN_ROLES.contains(user.getRole())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "当前账号无后台管理权限");
        }
        return user;
    }

    private RefundRequest findPendingRefundRequest(String orderId) {
        List<RefundRequest> rows = refundRequestMapper.selectList(new LambdaQueryWrapper<RefundRequest>()
                .eq(RefundRequest::getOrderId, orderId)
                .eq(RefundRequest::getStatus, "PENDING")
                .orderByDesc(RefundRequest::getRequestedAt));
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    private RefundRequest latestRefundRequest(String orderId) {
        List<RefundRequest> rows = refundRequestMapper.selectList(new LambdaQueryWrapper<RefundRequest>()
                .eq(RefundRequest::getOrderId, orderId)
                .orderByDesc(RefundRequest::getRequestedAt));
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    private RefundRequest createRefundRequest(
            TicketOrder order,
            String status,
            String source,
            String reason,
            String reviewNote,
            UserAccount reviewer
    ) {
        LocalDateTime now = LocalDateTime.now();
        RefundRequest request = new RefundRequest();
        request.setId("rr-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18));
        request.setOrderId(order.getId());
        request.setUserId(order.getUserId());
        request.setStatus(status);
        request.setSource(source);
        request.setReason(cleanRefundText(reason));
        request.setReviewNote(cleanRefundText(reviewNote));
        if (reviewer != null) {
            request.setReviewerId(reviewer.getId());
            request.setReviewerUsername(reviewer.getUsername());
        }
        request.setRequestedAt(now);
        request.setReviewedAt("PENDING".equals(status) ? null : now);
        request.setUpdatedAt(now);
        refundRequestMapper.insert(request);
        return request;
    }

    private void reviewRefundRequest(
            RefundRequest refundRequest,
            String status,
            ReviewRefundRequest request,
            UserAccount reviewer
    ) {
        LocalDateTime now = LocalDateTime.now();
        refundRequest.setStatus(status);
        refundRequest.setReviewNote(cleanRefundText(request == null ? null : request.note()));
        refundRequest.setReviewerId(reviewer.getId());
        refundRequest.setReviewerUsername(reviewer.getUsername());
        refundRequest.setReviewedAt(now);
        refundRequest.setUpdatedAt(now);
        refundRequestMapper.updateById(refundRequest);
    }

    private void finalizeRefund(TicketOrder order, List<TicketItem> tickets, String seatReason) {
        LocalDateTime now = LocalDateTime.now();
        order.setStatus("REFUNDED");
        order.setUpdatedAt(now);
        ticketOrderMapper.updateById(order);

        boolean isZoned = !tickets.isEmpty() && tickets.get(0).getAreaInventoryId() != null;
        if (isZoned) {
            String areaInventoryId = tickets.get(0).getAreaInventoryId();
            for (TicketItem ticket : tickets) {
                ticket.setStatus("VOID");
                ticket.setUpdatedAt(now);
                ticketItemMapper.updateById(ticket);
            }
            scheduleAreaInventoryMapper.refundInventory(areaInventoryId, tickets.size());
            seatService.publishAreaInventory(order.getScheduleId(), "AREA_REFUNDED", areaInventoryId);
        } else {
            for (TicketItem ticket : tickets) {
                ticket.setStatus("VOID");
                ticket.setUpdatedAt(now);
                ticketItemMapper.updateById(ticket);
                markSeatAvailable(ticket.getScheduleId(), ticket.getSeatId());
            }
            seatStatusPublisher.publishSeatStatus(
                    order.getScheduleId(),
                    seatReason,
                    "AVAILABLE",
                    tickets.stream().map(TicketItem::getSeatId).toList()
            );
        }
        dashboardRefreshPublisher.publish("ORDER_REFUNDED", order.getId());
    }

    private String cleanRefundText(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > 500 ? cleaned.substring(0, 500) : cleaned;
    }

    private RefundRequestSummary toRefundSummary(RefundRequest request) {
        if (request == null) {
            return null;
        }
        return new RefundRequestSummary(
                request.getId(),
                request.getStatus(),
                request.getSource(),
                request.getReason(),
                request.getReviewNote(),
                request.getReviewerUsername(),
                request.getRequestedAt(),
                request.getReviewedAt()
        );
    }

    private void ensureScheduleStructureStable(ShowSchedule schedule, UpdateScheduleRequest request) {
        String currentLayoutId = cleanOptional(schedule.getLayoutId());
        String nextLayoutId = cleanOptional(request.layoutId());
        if (!Objects.equals(currentLayoutId, nextLayoutId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "排期创建后不可切换座位布局，请新建排期");
        }

        String currentHallId = cleanOptional(schedule.getHallId());
        String nextHallId = cleanOptional(request.hallId());
        if (!Objects.equals(currentHallId, nextHallId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "排期创建后不可切换场馆厅，请新建排期");
        }

        if (StringUtils.hasText(request.ticketMode())) {
            String currentMode = StringUtils.hasText(schedule.getTicketMode())
                    ? schedule.getTicketMode().trim().toUpperCase()
                    : "SEATED";
            String nextMode = normalizeTicketMode(request.ticketMode());
            if (!Objects.equals(currentMode, nextMode)) {
                throw new BusinessException(ErrorCode.CONFLICT, "排期创建后不可切换票制，请新建排期");
            }
        }
    }

    private String normalizeScheduleStatus(String status) {
        if (!StringUtils.hasText(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "场次状态不能为空");
        }
        String normalized = status.trim().toUpperCase();
        if (!SCHEDULE_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的场次状态");
        }
        return normalized;
    }

    private String normalizeTicketMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "售票模式不能为空");
        }
        String normalized = mode.trim().toUpperCase();
        if (!TICKET_MODES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的售票模式");
        }
        return normalized;
    }

    private void validateScheduleTime(LocalDateTime startTime, LocalDateTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "场次结束时间必须晚于开始时间");
        }
    }

    private String normalizeShowStatus(String status) {
        if (!StringUtils.hasText(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "演出状态不能为空");
        }
        String normalized = status.trim().toUpperCase();
        if (!SHOW_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的演出状态");
        }
        return normalized;
    }

    private void ensureSchedulableShow(String showId) {
        ShowEntity show = getShow(showId);
        if ("ARCHIVED".equals(show.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已归档演出不可新增或绑定场次");
        }
    }

    private ShowSchedule getSchedule(String scheduleId) {
        ShowSchedule schedule = showScheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "场次不存在");
        }
        return schedule;
    }

    private ShowEntity getShow(String showId) {
        ShowEntity show = showMapper.selectById(showId);
        if (show == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "演出不存在");
        }
        return show;
    }

    private TicketOrder getOrder(String orderId) {
        TicketOrder order = ticketOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private String generateShowId() {
        String id;
        do {
            id = "s-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        } while (showMapper.selectById(id) != null);
        return id;
    }

    private String generateScheduleId() {
        String id;
        do {
            id = "sch-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        } while (showScheduleMapper.selectById(id) != null);
        return id;
    }

    private int nextShowSortOrder() {
        List<ShowEntity> shows = showMapper.selectList(new LambdaQueryWrapper<ShowEntity>()
                .orderByDesc(ShowEntity::getSortOrder)
                .last("limit 1"));
        if (shows.isEmpty() || shows.get(0).getSortOrder() == null) {
            return 10;
        }
        return shows.get(0).getSortOrder() + 10;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String cleanOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String categoryName(ShowEntity show) {
        if (show == null || !StringUtils.hasText(show.getCategory())) {
            return "Unknown";
        }
        return show.getCategory().trim();
    }

    private boolean showMatchesKeyword(ShowEntity show, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String haystack = String.join(" ",
                clean(show.getTitle()),
                clean(show.getSubtitle()),
                categoryName(show),
                clean(show.getStatus())
        ).toLowerCase(Locale.ROOT);
        return haystack.contains(keyword);
    }

    private List<TicketItem> listTickets(String orderId) {
        return ticketItemMapper.selectList(new LambdaQueryWrapper<TicketItem>()
                .eq(TicketItem::getOrderId, orderId)
                .orderByAsc(TicketItem::getSeatId));
    }

    private List<TicketItem> listScheduleTickets(String scheduleId) {
        return ticketItemMapper.selectList(new LambdaQueryWrapper<TicketItem>()
                .eq(TicketItem::getScheduleId, scheduleId));
    }

    private void markSeatAvailable(String scheduleId, String seatId) {
        ScheduleSeat seat = scheduleSeatMapper.selectOne(new LambdaQueryWrapper<ScheduleSeat>()
                .eq(ScheduleSeat::getScheduleId, scheduleId)
                .eq(ScheduleSeat::getSeatCode, seatId)
                .last("limit 1"));
        if (seat != null && "SOLD".equals(seat.getStatus())) {
            seat.setStatus("AVAILABLE");
            scheduleSeatMapper.updateById(seat);
        }
    }

    private void generateSeatPool(
            String scheduleId,
            int rows,
            int cols,
            BigDecimal vipPrice,
            BigDecimal standardPrice,
            BigDecimal economyPrice
    ) {
        int vipRows = Math.max(1, (int) Math.ceil(rows * 0.3));
        int standardRows = Math.max(vipRows, (int) Math.ceil(rows * 0.7));
        int centerCol = (cols + 1) / 2;

        for (int row = 1; row <= rows; row++) {
            for (int col = 1; col <= cols; col++) {
                ScheduleSeat seat = new ScheduleSeat();
                seat.setId("%s:seat-%d-%d".formatted(scheduleId, row, col));
                seat.setScheduleId(scheduleId);
                seat.setSeatCode("seat-%d-%d".formatted(row, col));
                seat.setRowNo(row);
                seat.setColNo(col);
                boolean disabled = cols >= 6 && row % 4 == 1 && col == centerCol;
                seat.setStatus(disabled ? "DISABLED" : "AVAILABLE");
                if (row <= vipRows) {
                    seat.setSection("VIP");
                    seat.setPrice(vipPrice);
                } else if (row <= standardRows) {
                    seat.setSection("A");
                    seat.setPrice(standardPrice);
                } else {
                    seat.setSection("B");
                    seat.setPrice(economyPrice);
                }
                scheduleSeatMapper.insert(seat);
            }
        }
    }

    private void releaseScheduleLocks(String scheduleId) {
        Set<String> lockKeys = redisTemplate.keys("encore:seat-lock:%s:*".formatted(scheduleId));
        if (lockKeys != null && !lockKeys.isEmpty()) {
            redisTemplate.delete(lockKeys);
        }
        redisTemplate.delete("encore:seat-lock-index:%s".formatted(scheduleId));
    }

    private List<AdminDashboardResponse.SalesTrendItem> buildSalesTrend(
            List<TicketOrder> paidOrders,
            List<TicketItem> validTickets,
            Map<String, TicketOrder> paidOrderById
    ) {
        LocalDate startDate = LocalDate.now().minusDays(6);
        Map<LocalDate, BigDecimal> revenueByDate = new LinkedHashMap<>();
        Map<LocalDate, Long> ticketsByDate = new LinkedHashMap<>();
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = startDate.plusDays(offset);
            revenueByDate.put(date, BigDecimal.ZERO);
            ticketsByDate.put(date, 0L);
        }

        for (TicketOrder order : paidOrders) {
            if (order.getPaidAt() == null) {
                continue;
            }
            LocalDate paidDate = order.getPaidAt().toLocalDate();
            if (revenueByDate.containsKey(paidDate)) {
                revenueByDate.compute(paidDate, (date, revenue) -> revenue.add(moneyOrZero(order.getTotalAmount())));
            }
        }

        for (TicketItem ticket : validTickets) {
            TicketOrder order = paidOrderById.get(ticket.getOrderId());
            if (order == null || order.getPaidAt() == null) {
                continue;
            }
            LocalDate paidDate = order.getPaidAt().toLocalDate();
            if (ticketsByDate.containsKey(paidDate)) {
                ticketsByDate.compute(paidDate, (date, count) -> count + 1);
            }
        }

        return revenueByDate.keySet().stream()
                .map(date -> new AdminDashboardResponse.SalesTrendItem(
                        date,
                        revenueByDate.get(date),
                        ticketsByDate.get(date)
                ))
                .toList();
    }

    private List<AdminDashboardResponse.TopShowItem> buildTopShows(
            List<TicketOrder> paidOrders,
            List<TicketItem> validTickets
    ) {
        Map<String, ShowSchedule> scheduleById = showScheduleMapper.selectList(new LambdaQueryWrapper<ShowSchedule>())
                .stream()
                .collect(Collectors.toMap(ShowSchedule::getId, Function.identity()));
        Map<String, ShowEntity> showById = showMapper.selectList(new LambdaQueryWrapper<ShowEntity>())
                .stream()
                .collect(Collectors.toMap(ShowEntity::getId, Function.identity()));
        Map<String, Long> ticketsByShow = new HashMap<>();
        Map<String, BigDecimal> revenueByShow = new HashMap<>();

        for (TicketItem ticket : validTickets) {
            ShowSchedule schedule = scheduleById.get(ticket.getScheduleId());
            if (schedule != null) {
                ticketsByShow.merge(schedule.getShowId(), 1L, Long::sum);
            }
        }

        for (TicketOrder order : paidOrders) {
            ShowSchedule schedule = scheduleById.get(order.getScheduleId());
            if (schedule != null) {
                revenueByShow.merge(schedule.getShowId(), moneyOrZero(order.getTotalAmount()), BigDecimal::add);
            }
        }

        return ticketsByShow.entrySet().stream()
                .sorted((left, right) -> {
                    int ticketCompare = Long.compare(right.getValue(), left.getValue());
                    if (ticketCompare != 0) {
                        return ticketCompare;
                    }
                    return revenueByShow.getOrDefault(right.getKey(), BigDecimal.ZERO)
                            .compareTo(revenueByShow.getOrDefault(left.getKey(), BigDecimal.ZERO));
                })
                .limit(5)
                .map(entry -> {
                    ShowEntity show = showById.get(entry.getKey());
                    return new AdminDashboardResponse.TopShowItem(
                            entry.getKey(),
                            show == null ? "Unknown Show" : show.getTitle(),
                            entry.getValue(),
                            revenueByShow.getOrDefault(entry.getKey(), BigDecimal.ZERO)
                    );
                })
                .toList();
    }

    private BoxOfficeWindow resolveBoxOfficeWindow(String range, LocalDate startDate, LocalDate endDate) {
        String normalizedRange = StringUtils.hasText(range) ? range.trim().toUpperCase() : "LAST_30_DAYS";
        if (!BOX_OFFICE_RANGES.contains(normalizedRange)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的票房统计范围");
        }
        if ("ALL".equals(normalizedRange)) {
            return new BoxOfficeWindow(normalizedRange, null, null);
        }
        LocalDate today = LocalDate.now();
        if ("CUSTOM".equals(normalizedRange)) {
            if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择有效的票房统计日期范围");
            }
            return new BoxOfficeWindow(normalizedRange, startDate, endDate);
        }
        int days = "LAST_7_DAYS".equals(normalizedRange) ? 7 : 30;
        return new BoxOfficeWindow(normalizedRange, today.minusDays(days - 1L), today);
    }

    private boolean orderInBoxOfficeScope(TicketOrder order, BoxOfficeWindow window) {
        String status = order.getStatus();
        if (!"PAID".equals(status)
                && !"PENDING_REFUND".equals(status)
                && !"REFUNDED".equals(status)
                && !"PENDING_PAYMENT".equals(status)) {
            return false;
        }
        if ("PENDING_PAYMENT".equals(status) && (order.getExpiresAt() == null || !order.getExpiresAt().isAfter(LocalDateTime.now()))) {
            return false;
        }
        if (window.isAll()) {
            return true;
        }
        LocalDate date = dateForBoxOffice(order);
        return date != null && !date.isBefore(window.startDate()) && !date.isAfter(window.endDate());
    }

    private LocalDate dateForBoxOffice(TicketOrder order) {
        if ("PENDING_PAYMENT".equals(order.getStatus())) {
            return order.getCreatedAt() == null ? null : order.getCreatedAt().toLocalDate();
        }
        return order.getPaidAt() == null ? null : order.getPaidAt().toLocalDate();
    }

    private Map<LocalDate, BoxOfficeAccumulator> buildTrendMap(BoxOfficeWindow window, List<TicketOrder> orders) {
        Map<LocalDate, BoxOfficeAccumulator> trends = new LinkedHashMap<>();
        LocalDate start = window.startDate();
        LocalDate end = window.endDate();
        if (window.isAll()) {
            List<LocalDate> dates = orders.stream()
                    .map(this::dateForBoxOffice)
                    .filter(Objects::nonNull)
                    .sorted()
                    .toList();
            start = dates.isEmpty() ? LocalDate.now() : dates.get(0);
            end = dates.isEmpty() ? LocalDate.now() : dates.get(dates.size() - 1);
        }
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            trends.put(date, new BoxOfficeAccumulator());
        }
        return trends;
    }

    private BoxOfficeOrderMetrics metricsForOrder(TicketOrder order, List<TicketItem> tickets) {
        long ticketCount = tickets.size();
        long checkedInCount = tickets.stream()
                .filter(ticket -> "CHECKED_IN".equals(ticket.getStatus()))
                .count();
        BigDecimal amount = moneyOrZero(order.getTotalAmount());
        if ("PAID".equals(order.getStatus()) || "PENDING_REFUND".equals(order.getStatus())) {
            long validTickets = tickets.stream()
                    .filter(ticket -> "UNUSED".equals(ticket.getStatus()) || "CHECKED_IN".equals(ticket.getStatus()))
                    .count();
            return new BoxOfficeOrderMetrics(amount, BigDecimal.ZERO, BigDecimal.ZERO, validTickets, 0, validTickets, checkedInCount);
        }
        if ("REFUNDED".equals(order.getStatus())) {
            return new BoxOfficeOrderMetrics(amount, amount, BigDecimal.ZERO, ticketCount, ticketCount, 0, 0);
        }
        if ("PENDING_PAYMENT".equals(order.getStatus())) {
            return new BoxOfficeOrderMetrics(BigDecimal.ZERO, BigDecimal.ZERO, amount, 0, 0, 0, 0);
        }
        return new BoxOfficeOrderMetrics(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0, 0);
    }

    private BigDecimal attendanceRate(long checkedInTickets, long validTickets) {
        if (validTickets <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(checkedInTickets)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(validTickets), 1, RoundingMode.HALF_UP);
    }

    private record BoxOfficeWindow(String range, LocalDate startDate, LocalDate endDate) {
        boolean isAll() {
            return "ALL".equals(range);
        }
    }

    private record BoxOfficeOrderMetrics(
            BigDecimal salesRevenue,
            BigDecimal refundAmount,
            BigDecimal pendingAmount,
            long paidTickets,
            long refundedTickets,
            long validTickets,
            long checkedInTickets
    ) {
    }

    private class BoxOfficeAccumulator {
        private String showId;
        private String showTitle;
        private String category;
        private String scheduleId;
        private String theaterName;
        private LocalDateTime startTime;
        private BigDecimal salesRevenue = BigDecimal.ZERO;
        private BigDecimal refundAmount = BigDecimal.ZERO;
        private BigDecimal pendingAmount = BigDecimal.ZERO;
        private long paidTickets;
        private long refundedTickets;
        private long validTickets;
        private long checkedInTickets;
        private long scheduleCount;
        private long showCount;
        private Set<String> scheduleIds = new java.util.HashSet<>();
        private Set<String> showIds = new java.util.HashSet<>();

        private void add(BoxOfficeOrderMetrics metrics) {
            salesRevenue = salesRevenue.add(metrics.salesRevenue());
            refundAmount = refundAmount.add(metrics.refundAmount());
            pendingAmount = pendingAmount.add(metrics.pendingAmount());
            paidTickets += metrics.paidTickets();
            refundedTickets += metrics.refundedTickets();
            validTickets += metrics.validTickets();
            checkedInTickets += metrics.checkedInTickets();
            if (StringUtils.hasText(scheduleId)) {
                scheduleCount = 1;
            }
        }

        private void addSchedule(String scheduleId) {
            if (StringUtils.hasText(scheduleId)) {
                scheduleIds.add(scheduleId);
                scheduleCount = scheduleIds.size();
            }
        }

        private void addShow(String showId) {
            if (StringUtils.hasText(showId)) {
                showIds.add(showId);
                showCount = showIds.size();
            }
        }

        private BigDecimal netRevenue() {
            return salesRevenue.subtract(refundAmount);
        }

        private AdminBoxOfficeResponse.Summary toSummary() {
            return new AdminBoxOfficeResponse.Summary(
                    salesRevenue,
                    refundAmount,
                    netRevenue(),
                    pendingAmount,
                    paidTickets,
                    refundedTickets,
                    validTickets,
                    checkedInTickets,
                    attendanceRate(checkedInTickets, validTickets)
            );
        }

        private AdminBoxOfficeResponse.TrendItem toTrendItem(LocalDate date) {
            return new AdminBoxOfficeResponse.TrendItem(
                    date,
                    salesRevenue,
                    refundAmount,
                    netRevenue(),
                    pendingAmount,
                    paidTickets,
                    refundedTickets,
                    validTickets
            );
        }

        private AdminBoxOfficeResponse.CategoryRow toCategoryRow() {
            return new AdminBoxOfficeResponse.CategoryRow(
                    category,
                    salesRevenue,
                    refundAmount,
                    netRevenue(),
                    pendingAmount,
                    paidTickets,
                    refundedTickets,
                    validTickets,
                    checkedInTickets,
                    attendanceRate(checkedInTickets, validTickets),
                    showCount,
                    scheduleCount
            );
        }

        private AdminBoxOfficeResponse.ShowRow toShowRow() {
            return new AdminBoxOfficeResponse.ShowRow(
                    showId,
                    showTitle,
                    category,
                    salesRevenue,
                    refundAmount,
                    netRevenue(),
                    pendingAmount,
                    paidTickets,
                    refundedTickets,
                    validTickets,
                    checkedInTickets,
                    attendanceRate(checkedInTickets, validTickets),
                    scheduleCount
            );
        }

        private AdminBoxOfficeResponse.ScheduleRow toScheduleRow() {
            return new AdminBoxOfficeResponse.ScheduleRow(
                    scheduleId,
                    showId,
                    showTitle,
                    theaterName,
                    startTime,
                    salesRevenue,
                    refundAmount,
                    netRevenue(),
                    pendingAmount,
                    paidTickets,
                    refundedTickets,
                    validTickets,
                    checkedInTickets,
                    attendanceRate(checkedInTickets, validTickets)
            );
        }
    }

    private AdminShowResponse toShowResponse(ShowEntity show) {
        long scheduleCount = showScheduleMapper.selectCount(new LambdaQueryWrapper<ShowSchedule>()
                .eq(ShowSchedule::getShowId, show.getId()));
        return new AdminShowResponse(
                show.getId(),
                show.getTitle(),
                show.getSubtitle(),
                show.getCoverUrl(),
                show.getDescription(),
                show.getIntro(),
                show.getCastMembers(),
                show.getCreativeTeam(),
                show.getFullSynopsis(),
                show.getDuration(),
                show.getCategory(),
                show.getTags(),
                show.getStatus(),
                show.getSortOrder(),
                scheduleCount
        );
    }

    private AdminScheduleResponse toScheduleResponse(ShowSchedule schedule) {
        ShowEntity show = showMapper.selectById(schedule.getShowId());
        List<ScheduleSeat> seats = scheduleSeatMapper.selectList(new LambdaQueryWrapper<ScheduleSeat>()
                .eq(ScheduleSeat::getScheduleId, schedule.getId()));
        List<TicketItem> tickets = listScheduleTickets(schedule.getId());
        Set<String> lockKeys = redisTemplate.keys("encore:seat-lock:%s:*".formatted(schedule.getId()));
        long lockedSeats = lockKeys == null ? 0 : lockKeys.size();

        long totalSeats = seats.size();
        long availableSeats = countSeats(seats, "AVAILABLE");
        long lockedSeatsVal = lockedSeats;
        long soldSeats = countSeats(seats, "SOLD");
        long disabledSeats = countSeats(seats, "DISABLED");

        if ("ZONED".equals(schedule.getTicketMode()) || "MIXED".equals(schedule.getTicketMode())) {
            // Reuse the per-area counts (non-seated from inventory, seated derived from
            // schedule_seat) so MIXED seated areas are not double-counted as full capacity.
            var areaResponses = seatService.listScheduleAreas(schedule.getId());
            totalSeats = areaResponses.stream().mapToLong(a -> a.totalCount() == null ? 0 : a.totalCount()).sum();
            availableSeats = areaResponses.stream().mapToLong(a -> a.availableCount() == null ? 0 : a.availableCount()).sum();
            lockedSeatsVal = areaResponses.stream().mapToLong(a -> a.lockedCount() == null ? 0 : a.lockedCount()).sum();
            soldSeats = areaResponses.stream().mapToLong(a -> a.soldCount() == null ? 0 : a.soldCount()).sum();
        }

        return new AdminScheduleResponse(
                schedule.getId(),
                schedule.getShowId(),
                show == null ? "Unknown Show" : show.getTitle(),
                show == null ? "Unknown" : show.getCategory(),
                schedule.getHallId(),
                hallName(schedule.getHallId()),
                schedule.getLayoutId(),
                layoutName(schedule.getLayoutId()),
                schedule.getTheaterName(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getSaleStartTime(),
                schedule.getSaleEndTime(),
                schedule.getStatus(),
                schedule.getPublishStatus() == null ? "DRAFT" : schedule.getPublishStatus(),
                schedule.getPriceRange(),
                schedule.getTicketMode(),
                totalSeats,
                availableSeats,
                lockedSeatsVal,
                soldSeats,
                disabledSeats,
                tickets.stream().filter(ticket -> "UNUSED".equals(ticket.getStatus()) || "CHECKED_IN".equals(ticket.getStatus())).count(),
                tickets.stream().filter(ticket -> "CHECKED_IN".equals(ticket.getStatus())).count()
        );
    }

    private String hallName(String hallId) {
        if (!StringUtils.hasText(hallId)) {
            return null;
        }
        try {
            return venueManagementService.getHall(hallId).getName();
        } catch (BusinessException ignored) {
            return null;
        }
    }

    private String layoutName(String layoutId) {
        if (!StringUtils.hasText(layoutId)) {
            return null;
        }
        try {
            return venueManagementService.getLayout(layoutId).getName();
        } catch (BusinessException ignored) {
            return null;
        }
    }

    private long countSeats(List<ScheduleSeat> seats, String status) {
        return seats.stream().filter(seat -> status.equals(seat.getStatus())).count();
    }

    private BigDecimal moneyOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private AdminOrderResponse toOrderResponse(TicketOrder order) {
        UserAccount user = userAccountMapper.selectById(order.getUserId());
        ShowSchedule schedule = showScheduleMapper.selectById(order.getScheduleId());
        ShowEntity show = schedule == null ? null : showMapper.selectById(schedule.getShowId());
        List<TicketItem> tickets = listTickets(order.getId()).stream()
                .sorted(Comparator.comparing(t -> t.getSeatId() == null ? "" : t.getSeatId()))
                .toList();
        int checkedInCount = (int) tickets.stream()
                .filter(ticket -> "CHECKED_IN".equals(ticket.getStatus()))
                .count();
        return new AdminOrderResponse(
                order.getId(),
                order.getUserId(),
                user == null ? "Unknown User" : user.getUsername(),
                order.getScheduleId(),
                show == null ? "Unknown Show" : show.getTitle(),
                schedule == null ? "Unknown Theater" : schedule.getTheaterName(),
                schedule == null ? null : schedule.getStartTime(),
                order.getTotalAmount(),
                order.getStatus(),
                tickets.size(),
                checkedInCount,
                order.getCreatedAt(),
                order.getPaidAt(),
                toRefundSummary(latestRefundRequest(order.getId()))
        );
    }
}
