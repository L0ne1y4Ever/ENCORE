package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.encore.common.ErrorCode;
import com.encore.dto.AdminDashboardResponse;
import com.encore.dto.AdminOrderResponse;
import com.encore.dto.AdminScheduleResponse;
import com.encore.dto.AdminShowResponse;
import com.encore.dto.CreateScheduleRequest;
import com.encore.dto.CreateShowRequest;
import com.encore.dto.UpdateScheduleRequest;
import com.encore.dto.UpdateShowRequest;
import com.encore.entity.ScheduleSeat;
import com.encore.entity.ShowEntity;
import com.encore.entity.ShowSchedule;
import com.encore.entity.TicketItem;
import com.encore.entity.TicketOrder;
import com.encore.entity.UserAccount;
import com.encore.entity.VenueArea;
import com.encore.entity.ScheduleAreaInventory;
import com.encore.exception.BusinessException;
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
import java.util.Map;
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
            "COMING_SOON", "PREPARING", "ON_SALE", "SOLD_OUT", "CANCELLED"
            );
    private static final Set<String> SHOW_STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");

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
            ScheduleAreaInventoryMapper scheduleAreaInventoryMapper
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
                new AdminDashboardResponse.CheckInSummary(checkedIn, unused, voided)
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
        schedule.setTheaterName(clean(request.theaterName()));
        schedule.setStartTime(request.startTime());
        schedule.setEndTime(request.endTime());
        schedule.setStatus(StringUtils.hasText(request.status()) ? normalizeScheduleStatus(request.status()) : "PREPARING");
        schedule.setPriceRange(clean(request.priceRange()));

        String mode = request.ticketMode();
        if (mode == null || mode.isBlank()) {
            mode = "SEATED";
        }
        schedule.setTicketMode(mode);
        showScheduleMapper.insert(schedule);

        if ("SEATED".equals(mode)) {
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

        schedule.setShowId(clean(request.showId()));
        schedule.setTheaterName(clean(request.theaterName()));
        schedule.setStartTime(request.startTime());
        schedule.setEndTime(request.endTime());
        schedule.setStatus(normalizeScheduleStatus(request.status()));
        schedule.setPriceRange(clean(request.priceRange()));
        if (request.ticketMode() != null) {
            schedule.setTicketMode(request.ticketMode());
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
        ensureAdminRole();
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

        order.setStatus("REFUNDED");
        ticketOrderMapper.updateById(order);

        boolean isZoned = !tickets.isEmpty() && tickets.get(0).getAreaInventoryId() != null;
        if (isZoned) {
            String areaInventoryId = tickets.get(0).getAreaInventoryId();
            int quantity = tickets.size();
            for (TicketItem ticket : tickets) {
                ticket.setStatus("VOID");
                ticketItemMapper.updateById(ticket);
            }
            scheduleAreaInventoryMapper.refundInventory(areaInventoryId, quantity);
        } else {
            for (TicketItem ticket : tickets) {
                ticket.setStatus("VOID");
                ticketItemMapper.updateById(ticket);
                markSeatAvailable(ticket.getScheduleId(), ticket.getSeatId());
            }
            seatStatusPublisher.publishSeatStatus(
                    order.getScheduleId(),
                    "REFUNDED",
                    "AVAILABLE",
                    tickets.stream().map(TicketItem::getSeatId).toList()
            );
        }
        dashboardRefreshPublisher.publish("ORDER_REFUNDED", orderId);
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
        String userId = StpUtil.getLoginIdAsString();
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null || !ADMIN_ROLES.contains(user.getRole())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "当前账号无后台管理权限");
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
        return value.trim();
    }

    private String cleanOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
                revenueByDate.compute(paidDate, (date, revenue) -> revenue.add(order.getTotalAmount()));
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
                revenueByShow.merge(schedule.getShowId(), order.getTotalAmount(), BigDecimal::add);
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
            List<ScheduleAreaInventory> inventories = scheduleAreaInventoryMapper.selectList(
                    new LambdaQueryWrapper<ScheduleAreaInventory>().eq(ScheduleAreaInventory::getScheduleId, schedule.getId())
            );
            long zonedTotal = inventories.stream().mapToLong(ScheduleAreaInventory::getTotalCount).sum();
            long zonedAvailable = inventories.stream().mapToLong(ScheduleAreaInventory::getAvailableCount).sum();
            long zonedLocked = inventories.stream().mapToLong(ScheduleAreaInventory::getLockedCount).sum();
            long zonedSold = inventories.stream().mapToLong(ScheduleAreaInventory::getSoldCount).sum();

            totalSeats = zonedTotal;
            availableSeats = zonedAvailable;
            lockedSeatsVal = zonedLocked;
            soldSeats = zonedSold;
        }

        return new AdminScheduleResponse(
                schedule.getId(),
                schedule.getShowId(),
                show == null ? "Unknown Show" : show.getTitle(),
                schedule.getTheaterName(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getStatus(),
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

    private long countSeats(List<ScheduleSeat> seats, String status) {
        return seats.stream().filter(seat -> status.equals(seat.getStatus())).count();
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
                order.getPaidAt()
        );
    }
}
