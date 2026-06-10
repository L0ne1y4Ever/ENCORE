package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.encore.common.ErrorCode;
import com.encore.dto.AdminHallResponse;
import com.encore.dto.AdminLayoutAreaResponse;
import com.encore.dto.AdminLayoutResponse;
import com.encore.dto.AdminLayoutSeatResponse;
import com.encore.dto.AdminScheduleInventoryResponse;
import com.encore.dto.AdminVenueResponse;
import com.encore.dto.CreateHallRequest;
import com.encore.dto.CreateLayoutRequest;
import com.encore.dto.CreateVenueRequest;
import com.encore.dto.LayoutSeatStatusSyncResponse;
import com.encore.dto.ScheduleAreaResponse;
import com.encore.dto.SeatResponse;
import com.encore.dto.SyncLayoutSeatStatusRequest;
import com.encore.dto.UpdateHallRequest;
import com.encore.dto.UpdateLayoutRequest;
import com.encore.dto.UpdateScheduleAreaInventoryRequest;
import com.encore.dto.UpdateVenueRequest;
import com.encore.entity.ScheduleAreaInventory;
import com.encore.entity.ScheduleSeat;
import com.encore.entity.SeatLayout;
import com.encore.entity.SeatLayoutArea;
import com.encore.entity.SeatLayoutSeat;
import com.encore.entity.ShowEntity;
import com.encore.entity.ShowSchedule;
import com.encore.entity.TicketItem;
import com.encore.entity.UserAccount;
import com.encore.entity.Venue;
import com.encore.entity.VenueArea;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VenueManagementService {
    private static final Set<String> ADMIN_ROLES = Set.of("admin", "sysadmin");
    private static final Set<String> ACTIVE_STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final Set<String> LAYOUT_STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
    private static final Set<String> TICKET_MODES = Set.of("SEATED", "ZONED", "MIXED");
    private static final int DEFAULT_CLEARANCE_MINUTES = 30;

    private final VenueMapper venueMapper;
    private final VenueHallMapper venueHallMapper;
    private final SeatLayoutMapper seatLayoutMapper;
    private final SeatLayoutAreaMapper seatLayoutAreaMapper;
    private final SeatLayoutSeatMapper seatLayoutSeatMapper;
    private final VenueAreaMapper venueAreaMapper;
    private final ScheduleSeatMapper scheduleSeatMapper;
    private final ScheduleAreaInventoryMapper scheduleAreaInventoryMapper;
    private final ShowScheduleMapper showScheduleMapper;
    private final ShowMapper showMapper;
    private final TicketItemMapper ticketItemMapper;
    private final UserAccountMapper userAccountMapper;
    private final StringRedisTemplate redisTemplate;
    private final SeatService seatService;
    private final SeatStatusPublisher seatStatusPublisher;

    public VenueManagementService(
            VenueMapper venueMapper,
            VenueHallMapper venueHallMapper,
            SeatLayoutMapper seatLayoutMapper,
            SeatLayoutAreaMapper seatLayoutAreaMapper,
            SeatLayoutSeatMapper seatLayoutSeatMapper,
            VenueAreaMapper venueAreaMapper,
            ScheduleSeatMapper scheduleSeatMapper,
            ScheduleAreaInventoryMapper scheduleAreaInventoryMapper,
            ShowScheduleMapper showScheduleMapper,
            ShowMapper showMapper,
            TicketItemMapper ticketItemMapper,
            UserAccountMapper userAccountMapper,
            StringRedisTemplate redisTemplate,
            SeatService seatService,
            SeatStatusPublisher seatStatusPublisher
    ) {
        this.venueMapper = venueMapper;
        this.venueHallMapper = venueHallMapper;
        this.seatLayoutMapper = seatLayoutMapper;
        this.seatLayoutAreaMapper = seatLayoutAreaMapper;
        this.seatLayoutSeatMapper = seatLayoutSeatMapper;
        this.venueAreaMapper = venueAreaMapper;
        this.scheduleSeatMapper = scheduleSeatMapper;
        this.scheduleAreaInventoryMapper = scheduleAreaInventoryMapper;
        this.showScheduleMapper = showScheduleMapper;
        this.showMapper = showMapper;
        this.ticketItemMapper = ticketItemMapper;
        this.userAccountMapper = userAccountMapper;
        this.redisTemplate = redisTemplate;
        this.seatService = seatService;
        this.seatStatusPublisher = seatStatusPublisher;
    }

    public List<AdminVenueResponse> listVenues() {
        ensureAdminRole();
        return venueMapper.selectList(new LambdaQueryWrapper<Venue>().orderByAsc(Venue::getName))
                .stream()
                .map(this::toVenueResponse)
                .toList();
    }

    @Transactional
    public AdminVenueResponse createVenue(CreateVenueRequest request) {
        ensureAdminRole();
        Venue venue = new Venue();
        venue.setId(generateId("ven"));
        venue.setName(clean(request.name()));
        venue.setCity(cleanOptional(request.city()));
        venue.setAddress(cleanOptional(request.address()));
        venue.setStatus(normalizeActiveStatus(request.status(), "ACTIVE"));
        venueMapper.insert(venue);
        return toVenueResponse(venueMapper.selectById(venue.getId()));
    }

    @Transactional
    public AdminVenueResponse updateVenue(String id, UpdateVenueRequest request) {
        ensureAdminRole();
        Venue venue = getVenue(id);
        venue.setName(clean(request.name()));
        venue.setCity(cleanOptional(request.city()));
        venue.setAddress(cleanOptional(request.address()));
        venue.setStatus(normalizeActiveStatus(request.status(), venue.getStatus()));
        venueMapper.updateById(venue);
        return toVenueResponse(venueMapper.selectById(id));
    }

    public List<AdminHallResponse> listHalls(String venueId) {
        ensureAdminRole();
        LambdaQueryWrapper<VenueHall> wrapper = new LambdaQueryWrapper<VenueHall>().orderByAsc(VenueHall::getName);
        if (StringUtils.hasText(venueId)) {
            wrapper.eq(VenueHall::getVenueId, venueId);
        }
        return venueHallMapper.selectList(wrapper).stream()
                .map(this::toHallResponse)
                .toList();
    }

    @Transactional
    public AdminHallResponse createHall(CreateHallRequest request) {
        ensureAdminRole();
        getVenue(request.venueId());
        VenueHall hall = new VenueHall();
        hall.setId(generateId("hall"));
        hall.setVenueId(clean(request.venueId()));
        hall.setName(clean(request.name()));
        hall.setHallType(StringUtils.hasText(request.hallType()) ? request.hallType().trim().toUpperCase() : "THEATER");
        hall.setCapacity(request.capacity() == null ? 0 : request.capacity());
        hall.setClearanceMinutes(request.clearanceMinutes() == null ? DEFAULT_CLEARANCE_MINUTES : request.clearanceMinutes());
        hall.setStatus(normalizeActiveStatus(request.status(), "ACTIVE"));
        venueHallMapper.insert(hall);
        return toHallResponse(venueHallMapper.selectById(hall.getId()));
    }

    @Transactional
    public AdminHallResponse updateHall(String id, UpdateHallRequest request) {
        ensureAdminRole();
        getVenue(request.venueId());
        VenueHall hall = getHall(id);
        hall.setVenueId(clean(request.venueId()));
        hall.setName(clean(request.name()));
        hall.setHallType(StringUtils.hasText(request.hallType()) ? request.hallType().trim().toUpperCase() : hall.getHallType());
        hall.setCapacity(request.capacity() == null ? hall.getCapacity() : request.capacity());
        hall.setDefaultLayoutId(cleanOptional(request.defaultLayoutId()));
        hall.setClearanceMinutes(request.clearanceMinutes() == null ? DEFAULT_CLEARANCE_MINUTES : request.clearanceMinutes());
        hall.setStatus(normalizeActiveStatus(request.status(), hall.getStatus()));
        venueHallMapper.updateById(hall);
        return toHallResponse(venueHallMapper.selectById(id));
    }

    public List<AdminLayoutResponse> listLayouts(String hallId) {
        ensureAdminRole();
        LambdaQueryWrapper<SeatLayout> wrapper = new LambdaQueryWrapper<SeatLayout>().orderByAsc(SeatLayout::getHallId).orderByDesc(SeatLayout::getVersion);
        if (StringUtils.hasText(hallId)) {
            wrapper.eq(SeatLayout::getHallId, hallId);
        }
        return seatLayoutMapper.selectList(wrapper).stream()
                .map(this::toLayoutResponse)
                .toList();
    }

    @Transactional
    public AdminLayoutResponse createLayout(CreateLayoutRequest request) {
        ensureAdminRole();
        VenueHall hall = getHall(request.hallId());
        String ticketMode = normalizeTicketMode(request.ticketMode());
        SeatLayout layout = new SeatLayout();
        layout.setId(generateId("lay"));
        layout.setHallId(hall.getId());
        layout.setName(clean(request.name()));
        layout.setTicketMode(ticketMode);
        layout.setVersion(nextLayoutVersion(hall.getId()));
        layout.setStatus(normalizeLayoutStatus(request.status(), "DRAFT"));
        seatLayoutMapper.insert(layout);

        if ("SEATED".equals(ticketMode)) {
            generateSeatedLayout(layout, request);
        } else if ("ZONED".equals(ticketMode)) {
            generateZonedLayout(layout);
        } else {
            generateMixedLayout(layout);
        }
        if ("PUBLISHED".equals(layout.getStatus())) {
            hall.setDefaultLayoutId(layout.getId());
            venueHallMapper.updateById(hall);
        }
        return toLayoutResponse(seatLayoutMapper.selectById(layout.getId()));
    }

    @Transactional
    public AdminLayoutResponse updateLayout(String id, UpdateLayoutRequest request) {
        ensureAdminRole();
        SeatLayout layout = getLayout(id);
        layout.setName(clean(request.name()));
        if (StringUtils.hasText(request.status())) {
            layout.setStatus(normalizeLayoutStatus(request.status(), layout.getStatus()));
        }
        seatLayoutMapper.updateById(layout);
        syncHallDefaultLayout(layout);
        return toLayoutResponse(seatLayoutMapper.selectById(id));
    }

    @Transactional
    public AdminLayoutResponse updateLayoutStatus(String id, String status) {
        ensureAdminRole();
        SeatLayout layout = getLayout(id);
        String normalized = normalizeLayoutStatus(status, layout.getStatus());
        layout.setStatus(normalized);
        seatLayoutMapper.updateById(layout);
        syncHallDefaultLayout(layout);
        return toLayoutResponse(seatLayoutMapper.selectById(id));
    }

    private void syncHallDefaultLayout(SeatLayout layout) {
        VenueHall hall = getHall(layout.getHallId());
        if ("PUBLISHED".equals(layout.getStatus())) {
            hall.setDefaultLayoutId(layout.getId());
            venueHallMapper.updateById(hall);
            return;
        }
        if (layout.getId().equals(hall.getDefaultLayoutId())) {
            hall.setDefaultLayoutId(null);
            venueHallMapper.updateById(hall);
        }
    }

    public List<AdminLayoutAreaResponse> listLayoutAreas(String layoutId) {
        ensureAdminRole();
        getLayout(layoutId);
        return seatLayoutAreaMapper.selectList(new LambdaQueryWrapper<SeatLayoutArea>()
                        .eq(SeatLayoutArea::getLayoutId, layoutId)
                        .orderByAsc(SeatLayoutArea::getCode))
                .stream()
                .map(this::toLayoutAreaResponse)
                .toList();
    }

    public List<AdminLayoutSeatResponse> listLayoutSeats(String layoutId) {
        ensureAdminRole();
        getLayout(layoutId);
        return seatLayoutSeatMapper.selectList(new LambdaQueryWrapper<SeatLayoutSeat>()
                        .eq(SeatLayoutSeat::getLayoutId, layoutId)
                        .orderByAsc(SeatLayoutSeat::getRowNo)
                        .orderByAsc(SeatLayoutSeat::getColNo))
                .stream()
                .map(this::toLayoutSeatResponse)
                .toList();
    }

    @Transactional
    public AdminLayoutSeatResponse updateLayoutSeatStatus(String layoutId, String seatCode, String status) {
        ensureAdminRole();
        SeatLayout layout = getLayout(layoutId);
        if ("ARCHIVED".equals(layout.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已归档布局不可修改座位");
        }
        String normalized = normalizeLayoutSeatStatus(status);
        SeatLayoutSeat seat = seatLayoutSeatMapper.selectOne(new LambdaQueryWrapper<SeatLayoutSeat>()
                .eq(SeatLayoutSeat::getLayoutId, layoutId)
                .eq(SeatLayoutSeat::getSeatCode, seatCode)
                .last("limit 1"));
        if (seat == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "布局座位不存在");
        }
        seat.setStatus(normalized);
        seatLayoutSeatMapper.updateById(seat);
        return toLayoutSeatResponse(seatLayoutSeatMapper.selectById(seat.getId()));
    }

    @Transactional
    public LayoutSeatStatusSyncResponse syncLayoutSeatStatus(String layoutId, SyncLayoutSeatStatusRequest request) {
        ensureAdminRole();
        SeatLayout layout = getLayout(layoutId);
        List<String> scheduleIds = request.scheduleIds() == null ? List.of() : request.scheduleIds().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (scheduleIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要同步的场次");
        }

        Map<String, String> layoutStatusBySeatCode = seatLayoutSeatMapper.selectList(new LambdaQueryWrapper<SeatLayoutSeat>()
                        .eq(SeatLayoutSeat::getLayoutId, layoutId))
                .stream()
                .filter(seat -> "AVAILABLE".equals(seat.getStatus()) || "DISABLED".equals(seat.getStatus()))
                .collect(Collectors.toMap(SeatLayoutSeat::getSeatCode, SeatLayoutSeat::getStatus));
        if (layoutStatusBySeatCode.isEmpty()) {
            return new LayoutSeatStatusSyncResponse(layoutId, 0, 0, List.of());
        }

        LocalDateTime now = LocalDateTime.now();
        List<SeatStatusChange> changes = new ArrayList<>();
        Map<String, ShowSchedule> schedulesById = new LinkedHashMap<>();
        for (String scheduleId : scheduleIds) {
            ShowSchedule schedule = getSchedule(scheduleId);
            validateLayoutSyncSchedule(layout, schedule, now);
            schedulesById.put(scheduleId, schedule);
            List<ScheduleSeat> scheduleSeats = scheduleSeatMapper.selectList(new LambdaQueryWrapper<ScheduleSeat>()
                    .eq(ScheduleSeat::getScheduleId, scheduleId));
            for (ScheduleSeat scheduleSeat : scheduleSeats) {
                String targetStatus = layoutStatusBySeatCode.get(scheduleSeat.getSeatCode());
                if (targetStatus == null || targetStatus.equals(scheduleSeat.getStatus())) {
                    continue;
                }
                ensureScheduleSeatEditable(scheduleId, scheduleSeat);
                changes.add(new SeatStatusChange(scheduleId, scheduleSeat, targetStatus));
            }
        }

        Map<String, Map<String, List<String>>> changedSeatCodesByScheduleAndStatus = new HashMap<>();
        for (SeatStatusChange change : changes) {
            change.seat().setStatus(change.targetStatus());
            scheduleSeatMapper.updateById(change.seat());
            changedSeatCodesByScheduleAndStatus
                    .computeIfAbsent(change.scheduleId(), ignored -> new HashMap<>())
                    .computeIfAbsent(change.targetStatus(), ignored -> new ArrayList<>())
                    .add(change.seat().getSeatCode());
        }

        changedSeatCodesByScheduleAndStatus.forEach((scheduleId, byStatus) ->
                byStatus.forEach((status, seatCodes) ->
                        seatStatusPublisher.publishSeatStatus(scheduleId, "LAYOUT_SYNC", status, seatCodes)));

        return new LayoutSeatStatusSyncResponse(layoutId, schedulesById.size(), changes.size(), List.copyOf(schedulesById.keySet()));
    }

    public AdminScheduleInventoryResponse getScheduleInventory(String scheduleId) {
        ensureAdminRole();
        ShowSchedule schedule = getSchedule(scheduleId);
        ShowEntity show = showMapper.selectById(schedule.getShowId());
        List<SeatResponse> seats = scheduleSeatMapper.selectCount(new LambdaQueryWrapper<ScheduleSeat>()
                .eq(ScheduleSeat::getScheduleId, scheduleId)) == 0
                ? List.of()
                : seatService.listSeats(scheduleId);
        List<ScheduleAreaResponse> areas = scheduleAreaInventoryMapper.selectCount(new LambdaQueryWrapper<ScheduleAreaInventory>()
                .eq(ScheduleAreaInventory::getScheduleId, scheduleId)) == 0
                ? List.of()
                : seatService.listScheduleAreas(scheduleId);
        return new AdminScheduleInventoryResponse(
                scheduleId,
                show == null ? "Unknown Show" : show.getTitle(),
                schedule.getTheaterName(),
                schedule.getTicketMode(),
                seats.size() + areas.stream().filter(area -> !Boolean.TRUE.equals(area.isSeated())).mapToLong(ScheduleAreaResponse::totalCount).sum(),
                seats.stream().filter(seat -> "AVAILABLE".equals(seat.status())).count() + areas.stream().filter(area -> !Boolean.TRUE.equals(area.isSeated())).mapToLong(ScheduleAreaResponse::availableCount).sum(),
                seats.stream().filter(seat -> "LOCKED".equals(seat.status())).count() + areas.stream().filter(area -> !Boolean.TRUE.equals(area.isSeated())).mapToLong(ScheduleAreaResponse::lockedCount).sum(),
                seats.stream().filter(seat -> "SOLD".equals(seat.status())).count() + areas.stream().filter(area -> !Boolean.TRUE.equals(area.isSeated())).mapToLong(ScheduleAreaResponse::soldCount).sum(),
                seats.stream().filter(seat -> "DISABLED".equals(seat.status())).count(),
                seats,
                areas
        );
    }

    @Transactional
    public AdminScheduleInventoryResponse updateScheduleSeatStatus(String scheduleId, String seatCode, String status) {
        ensureAdminRole();
        String normalized = status.trim().toUpperCase();
        if (!Set.of("AVAILABLE", "DISABLED").contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "座位只能调整为可售或禁用");
        }
        ScheduleSeat seat = scheduleSeatMapper.selectOne(new LambdaQueryWrapper<ScheduleSeat>()
                .eq(ScheduleSeat::getScheduleId, scheduleId)
                .eq(ScheduleSeat::getSeatCode, seatCode)
                .last("limit 1"));
        if (seat == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "座位不存在");
        }
        if ("SOLD".equals(seat.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已售座位不可直接修改");
        }
        ensureScheduleSeatEditable(scheduleId, seat);
        seat.setStatus(normalized);
        scheduleSeatMapper.updateById(seat);
        seatStatusPublisher.publishSeatStatus(scheduleId, "INVENTORY_ADJUSTED", normalized, List.of(seatCode));
        return getScheduleInventory(scheduleId);
    }

    @Transactional
    public AdminScheduleInventoryResponse updateAreaInventory(String scheduleId, String inventoryId, UpdateScheduleAreaInventoryRequest request) {
        ensureAdminRole();
        // 加行锁重读最新计数，避免与并发的下单/支付/退票原子更新发生丢失更新(lost update)。
        ScheduleAreaInventory inventory = scheduleAreaInventoryMapper.selectByIdForUpdate(inventoryId);
        if (inventory == null || !scheduleId.equals(inventory.getScheduleId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "区域库存不存在");
        }
        // 座位区库存真相源是 schedule_seat，整片调整会与按座位派生的读侧口径冲突，
        // 这类区域应在座位快照里逐座位禁用/恢复。
        VenueArea area = venueAreaMapper.selectById(inventory.getAreaId());
        if (area != null && Boolean.TRUE.equals(area.getIsSeated())) {
            throw new BusinessException(ErrorCode.CONFLICT, "座位区库存请在座位快照中按座位管理");
        }
        int sold = inventory.getSoldCount() == null ? 0 : inventory.getSoldCount();
        int locked = inventory.getLockedCount() == null ? 0 : inventory.getLockedCount();
        int minTotal = sold + locked;
        int total = request.totalCount() == null ? inventory.getTotalCount() : request.totalCount();
        if (total < minTotal) {
            throw new BusinessException(ErrorCode.CONFLICT, "总库存不能小于已售与锁定数量");
        }
        int available = request.availableCount() == null ? total - minTotal : request.availableCount();
        if (available < 0 || available + minTotal > total) {
            throw new BusinessException(ErrorCode.CONFLICT, "可售库存数量不合法");
        }
        // 仅更新管理员实际调整的列(total/available/status)，绝不回写 locked_count/sold_count，
        // 这两个计数由下单链路的原子 SQL 独占维护。
        String nextStatus = StringUtils.hasText(request.status())
                ? request.status().trim().toUpperCase()
                : inventory.getStatus();
        scheduleAreaInventoryMapper.adjustInventory(inventoryId, total, available, nextStatus);
        seatService.publishAreaInventory(scheduleId, "AREA_ADJUSTED", inventoryId);
        return getScheduleInventory(scheduleId);
    }

    public SeatLayout getPublishedLayout(String layoutId) {
        SeatLayout layout = getLayout(layoutId);
        if (!"PUBLISHED".equals(layout.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只能使用已发布布局创建场次");
        }
        return layout;
    }

    public VenueHall getHall(String hallId) {
        VenueHall hall = venueHallMapper.selectById(hallId);
        if (hall == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "场馆厅不存在");
        }
        return hall;
    }

    public void ensureScheduleConflictFree(String currentScheduleId, String hallId, LocalDateTime startTime, LocalDateTime endTime) {
        if (!StringUtils.hasText(hallId)) {
            return;
        }
        VenueHall hall = getHall(hallId);
        int clearance = hall.getClearanceMinutes() == null ? DEFAULT_CLEARANCE_MINUTES : hall.getClearanceMinutes();
        LocalDateTime guardedStart = startTime.minusMinutes(clearance);
        LocalDateTime guardedEnd = endTime.plusMinutes(clearance);
        List<ShowSchedule> schedules = showScheduleMapper.selectList(new LambdaQueryWrapper<ShowSchedule>()
                .eq(ShowSchedule::getHallId, hallId)
                .ne(ShowSchedule::getStatus, "CANCELLED"));
        for (ShowSchedule schedule : schedules) {
            if (currentScheduleId != null && currentScheduleId.equals(schedule.getId())) {
                continue;
            }
            boolean overlap = schedule.getStartTime().isBefore(guardedEnd)
                    && schedule.getEndTime().isAfter(guardedStart);
            if (overlap) {
                throw new BusinessException(ErrorCode.CONFLICT, "该厅在当前时间段已有排片");
            }
        }
    }

    public void snapshotLayoutToSchedule(SeatLayout layout, ShowSchedule schedule) {
        List<SeatLayoutArea> areas = seatLayoutAreaMapper.selectList(new LambdaQueryWrapper<SeatLayoutArea>()
                .eq(SeatLayoutArea::getLayoutId, layout.getId()));
        List<SeatLayoutSeat> seats = seatLayoutSeatMapper.selectList(new LambdaQueryWrapper<SeatLayoutSeat>()
                .eq(SeatLayoutSeat::getLayoutId, layout.getId()));
        for (SeatLayoutArea area : areas) {
            mirrorVenueArea(layout.getHallId(), area);
            // 座位区(看台)的库存真相源是 schedule_seat，本行仅作区域注册/价格/枚举与体育场图瓦片来源。
            // 计数初值与实际生成的座位数对齐，避免与按座位派生的读侧口径(见 SeatService.seatedAreaCounts)漂移。
            int areaTotal = Boolean.TRUE.equals(area.getIsSeated())
                    ? (int) seats.stream().filter(seat -> area.getId().equals(seat.getAreaId())).count()
                    : area.getCapacity();
            ScheduleAreaInventory inventory = new ScheduleAreaInventory();
            inventory.setId(generateInventoryId(schedule.getId(), area.getCode()));
            inventory.setScheduleId(schedule.getId());
            inventory.setAreaId(area.getId());
            inventory.setPrice(area.getBasePrice());
            inventory.setTotalCount(areaTotal);
            inventory.setAvailableCount(areaTotal);
            inventory.setLockedCount(0);
            inventory.setSoldCount(0);
            inventory.setStatus("AVAILABLE");
            scheduleAreaInventoryMapper.insert(inventory);
        }

        for (SeatLayoutSeat source : seats) {
            ScheduleSeat seat = new ScheduleSeat();
            seat.setId("%s:%s".formatted(schedule.getId(), source.getSeatCode()));
            seat.setScheduleId(schedule.getId());
            seat.setSeatCode(source.getSeatCode());
            seat.setRowNo(source.getRowNo());
            seat.setColNo(source.getColNo());
            seat.setSection(source.getSection());
            seat.setStatus(source.getStatus());
            seat.setPrice(source.getPrice());
            seat.setAreaId(source.getAreaId());
            scheduleSeatMapper.insert(seat);
        }
    }

    private void generateSeatedLayout(SeatLayout layout, CreateLayoutRequest request) {
        int rows = request.seatRows() == null ? 10 : request.seatRows();
        int cols = request.seatCols() == null ? 15 : request.seatCols();
        BigDecimal vipPrice = request.vipPrice() == null ? BigDecimal.valueOf(150) : request.vipPrice();
        BigDecimal standardPrice = request.standardPrice() == null ? BigDecimal.valueOf(100) : request.standardPrice();
        BigDecimal economyPrice = request.economyPrice() == null ? BigDecimal.valueOf(50) : request.economyPrice();
        // 贴近真实剧场定价：最前排紧贴舞台、仰视疲劳 → 最便宜(B)；
        // 中段视野最佳 → 最贵(VIP)；后段 → 中等(A)。少于 3 排时不设前排低价段。
        int frontRows = rows >= 3 ? Math.max(1, (int) Math.round(rows * 0.2)) : 0;
        int premiumEndRow = Math.max(frontRows + 1, (int) Math.ceil(rows * 0.65));
        int centerCol = (cols + 1) / 2;
        for (int row = 1; row <= rows; row++) {
            for (int col = 1; col <= cols; col++) {
                String section = row <= frontRows ? "B" : row <= premiumEndRow ? "VIP" : "A";
                BigDecimal price = row <= frontRows ? economyPrice : row <= premiumEndRow ? vipPrice : standardPrice;
                boolean disabled = cols >= 6 && row % 4 == 1 && col == centerCol;
                insertLayoutSeat(layout.getId(), null, "seat-%d-%d".formatted(row, col), row, col, section, disabled ? "DISABLED" : "AVAILABLE", price);
            }
        }
    }

    private void generateZonedLayout(SeatLayout layout) {
        insertLayoutArea(layout, "VIP A区", "VIP_A", "VIP", false, 80, BigDecimal.valueOf(1680), "#c8955a", "舞台正前站席");
        insertLayoutArea(layout, "内场A区", "INFIELD_A", "FIELD", false, 300, BigDecimal.valueOf(1280), "#4a90e2", "内场前区站席");
        insertLayoutArea(layout, "内场B区", "INFIELD_B", "FIELD", false, 500, BigDecimal.valueOf(980), "#50e3c2", "内场后区站席");
    }

    private void generateMixedLayout(SeatLayout layout) {
        generateZonedLayout(layout);
        SeatLayoutArea stand1 = insertLayoutArea(layout, "看台一区", "STAND_1", "BALCONY", true, 200, BigDecimal.valueOf(680), "#f5a623", "一侧固定座椅");
        SeatLayoutArea stand2 = insertLayoutArea(layout, "看台二区", "STAND_2", "BALCONY", true, 200, BigDecimal.valueOf(580), "#b8e986", "两侧固定座椅");
        SeatLayoutArea stand3 = insertLayoutArea(layout, "看台三区", "STAND_3", "BALCONY", true, 300, BigDecimal.valueOf(480), "#bd10e0", "后方固定座椅");
        generateAreaSeats(layout.getId(), stand1, 1, 10, 20);
        generateAreaSeats(layout.getId(), stand2, 11, 20, 20);
        generateAreaSeats(layout.getId(), stand3, 21, 30, 30);
    }

    private SeatLayoutArea insertLayoutArea(
            SeatLayout layout,
            String name,
            String code,
            String areaType,
            boolean seated,
            int capacity,
            BigDecimal price,
            String color,
            String description
    ) {
        SeatLayoutArea area = new SeatLayoutArea();
        area.setId("%s-%s".formatted(layout.getId(), code.toLowerCase().replace("_", "-")));
        area.setLayoutId(layout.getId());
        area.setName(name);
        area.setCode(code);
        area.setAreaType(areaType);
        area.setIsSeated(seated);
        area.setCapacity(capacity);
        area.setBasePrice(price);
        area.setColor(color);
        area.setDescription(description);
        seatLayoutAreaMapper.insert(area);
        mirrorVenueArea(layout.getHallId(), area);
        return area;
    }

    private void mirrorVenueArea(String hallId, SeatLayoutArea area) {
        VenueArea mirror = venueAreaMapper.selectById(area.getId());
        if (mirror == null) {
            mirror = new VenueArea();
            mirror.setId(area.getId());
        }
        mirror.setHallId(hallId);
        mirror.setName(area.getName());
        mirror.setCode(area.getCode());
        mirror.setAreaType(area.getAreaType());
        mirror.setIsSeated(area.getIsSeated());
        mirror.setCapacity(area.getCapacity());
        mirror.setBasePrice(area.getBasePrice());
        mirror.setAvailableCount(area.getCapacity());
        mirror.setLockedCount(0);
        mirror.setSoldCount(0);
        mirror.setColor(area.getColor());
        mirror.setDescription(area.getDescription());
        mirror.setPositionData(area.getPositionData());
        if (venueAreaMapper.selectById(area.getId()) == null) {
            venueAreaMapper.insert(mirror);
        } else {
            venueAreaMapper.updateById(mirror);
        }
    }

    private void generateAreaSeats(String layoutId, SeatLayoutArea area, int startRow, int endRow, int cols) {
        for (int row = startRow; row <= endRow; row++) {
            for (int col = 1; col <= cols; col++) {
                insertLayoutSeat(
                        layoutId,
                        area.getId(),
                        "seat-%s-%d-%d".formatted(area.getCode().toLowerCase().replace("_", "-"), row, col),
                        row,
                        col,
                        area.getCode(),
                        "AVAILABLE",
                        area.getBasePrice()
                );
            }
        }
    }

    private void insertLayoutSeat(String layoutId, String areaId, String seatCode, int row, int col, String section, String status, BigDecimal price) {
        SeatLayoutSeat seat = new SeatLayoutSeat();
        seat.setId("%s:%s".formatted(layoutId, seatCode));
        seat.setLayoutId(layoutId);
        seat.setAreaId(areaId);
        seat.setSeatCode(seatCode);
        seat.setRowNo(row);
        seat.setColNo(col);
        seat.setSection(section);
        seat.setStatus(status);
        seat.setPrice(price);
        seatLayoutSeatMapper.insert(seat);
    }

    private AdminVenueResponse toVenueResponse(Venue venue) {
        long hallCount = venueHallMapper.selectCount(new LambdaQueryWrapper<VenueHall>().eq(VenueHall::getVenueId, venue.getId()));
        return new AdminVenueResponse(venue.getId(), venue.getName(), venue.getCity(), venue.getAddress(), venue.getStatus(), hallCount);
    }

    private AdminHallResponse toHallResponse(VenueHall hall) {
        Venue venue = venueMapper.selectById(hall.getVenueId());
        long layoutCount = seatLayoutMapper.selectCount(new LambdaQueryWrapper<SeatLayout>().eq(SeatLayout::getHallId, hall.getId()));
        return new AdminHallResponse(
                hall.getId(),
                hall.getVenueId(),
                venue == null ? "Unknown Venue" : venue.getName(),
                hall.getName(),
                hall.getHallType(),
                hall.getCapacity(),
                hall.getDefaultLayoutId(),
                hall.getClearanceMinutes(),
                hall.getStatus(),
                layoutCount
        );
    }

    private AdminLayoutResponse toLayoutResponse(SeatLayout layout) {
        VenueHall hall = venueHallMapper.selectById(layout.getHallId());
        Venue venue = hall == null ? null : venueMapper.selectById(hall.getVenueId());
        long areaCount = seatLayoutAreaMapper.selectCount(new LambdaQueryWrapper<SeatLayoutArea>().eq(SeatLayoutArea::getLayoutId, layout.getId()));
        long seatCount = seatLayoutSeatMapper.selectCount(new LambdaQueryWrapper<SeatLayoutSeat>().eq(SeatLayoutSeat::getLayoutId, layout.getId()));
        return new AdminLayoutResponse(
                layout.getId(),
                layout.getHallId(),
                hall == null ? "Unknown Hall" : hall.getName(),
                venue == null ? "Unknown Venue" : venue.getName(),
                layout.getName(),
                layout.getTicketMode(),
                layout.getVersion(),
                layout.getStatus(),
                areaCount,
                seatCount
        );
    }

    private AdminLayoutAreaResponse toLayoutAreaResponse(SeatLayoutArea area) {
        return new AdminLayoutAreaResponse(
                area.getId(),
                area.getLayoutId(),
                area.getName(),
                area.getCode(),
                area.getAreaType(),
                area.getIsSeated(),
                area.getCapacity(),
                area.getBasePrice(),
                area.getColor(),
                area.getDescription(),
                area.getPositionData()
        );
    }

    private AdminLayoutSeatResponse toLayoutSeatResponse(SeatLayoutSeat seat) {
        return new AdminLayoutSeatResponse(
                seat.getId(),
                seat.getLayoutId(),
                seat.getAreaId(),
                seat.getSeatCode(),
                seat.getRowNo(),
                seat.getColNo(),
                seat.getSection(),
                seat.getStatus(),
                seat.getPrice()
        );
    }

    private Venue getVenue(String id) {
        Venue venue = venueMapper.selectById(id);
        if (venue == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "场馆不存在");
        }
        return venue;
    }

    public SeatLayout getLayout(String id) {
        SeatLayout layout = seatLayoutMapper.selectById(id);
        if (layout == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "座位布局不存在");
        }
        return layout;
    }

    private ShowSchedule getSchedule(String scheduleId) {
        ShowSchedule schedule = showScheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "场次不存在");
        }
        return schedule;
    }

    private void validateLayoutSyncSchedule(SeatLayout layout, ShowSchedule schedule, LocalDateTime now) {
        if (!layout.getId().equals(schedule.getLayoutId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "场次 %s 未使用当前布局，不能同步".formatted(schedule.getId()));
        }
        if ("CANCELLED".equals(schedule.getStatus()) || "ENDED".equals(schedule.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "场次 %s 已取消或已结束，不能同步".formatted(schedule.getId()));
        }
        if (schedule.getStartTime() == null || !schedule.getStartTime().isAfter(now)) {
            throw new BusinessException(ErrorCode.CONFLICT, "场次 %s 已开演或开始时间无效，不能同步".formatted(schedule.getId()));
        }
    }

    private void ensureScheduleSeatEditable(String scheduleId, ScheduleSeat seat) {
        if ("SOLD".equals(seat.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "场次 %s 座位 %s 已售，不能修改".formatted(scheduleId, seat.getSeatCode()));
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(seatLockKey(scheduleId, seat.getSeatCode())))) {
            throw new BusinessException(ErrorCode.CONFLICT, "场次 %s 座位 %s 已锁定，不能修改".formatted(scheduleId, seat.getSeatCode()));
        }
        if (hasReservedTicket(scheduleId, seat.getSeatCode())) {
            throw new BusinessException(ErrorCode.CONFLICT, "场次 %s 座位 %s 存在待支付订单，不能修改".formatted(scheduleId, seat.getSeatCode()));
        }
    }

    private boolean hasReservedTicket(String scheduleId, String seatCode) {
        Long count = ticketItemMapper.selectCount(new LambdaQueryWrapper<TicketItem>()
                .eq(TicketItem::getScheduleId, scheduleId)
                .eq(TicketItem::getSeatId, seatCode)
                .eq(TicketItem::getStatus, "RESERVED"));
        return count != null && count > 0;
    }

    private String seatLockKey(String scheduleId, String seatCode) {
        return "encore:seat-lock:%s:%s".formatted(scheduleId, seatCode);
    }

    private record SeatStatusChange(String scheduleId, ScheduleSeat seat, String targetStatus) {
    }

    private int nextLayoutVersion(String hallId) {
        List<SeatLayout> layouts = seatLayoutMapper.selectList(new LambdaQueryWrapper<SeatLayout>()
                .eq(SeatLayout::getHallId, hallId)
                .orderByDesc(SeatLayout::getVersion)
                .last("limit 1"));
        if (layouts.isEmpty() || layouts.get(0).getVersion() == null) {
            return 1;
        }
        return layouts.get(0).getVersion() + 1;
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

    private String normalizeLayoutStatus(String status, String fallback) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase() : fallback;
        if (!LAYOUT_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的布局状态");
        }
        return normalized;
    }

    private String normalizeLayoutSeatStatus(String status) {
        if (!StringUtils.hasText(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "座位状态不能为空");
        }
        String normalized = status.trim().toUpperCase();
        if (!Set.of("AVAILABLE", "DISABLED").contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "布局座位只能设置为可售或禁用");
        }
        return normalized;
    }

    private String normalizeActiveStatus(String status, String fallback) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase() : fallback;
        if (!ACTIVE_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的状态");
        }
        return normalized;
    }

    private String generateInventoryId(String scheduleId, String code) {
        String base = "inv-" + scheduleId.replace("sch-", "") + "-" + code.toLowerCase().replace("_", "-");
        return base.length() <= 32 ? base : base.substring(0, 32);
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String cleanOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void ensureAdminRole() {
        String userId = StpUtil.getLoginIdAsString();
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus()) || !ADMIN_ROLES.contains(user.getRole())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "当前账号无后台管理权限");
        }
    }
}
