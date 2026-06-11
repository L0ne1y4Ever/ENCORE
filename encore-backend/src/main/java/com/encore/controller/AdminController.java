package com.encore.controller;

import com.encore.common.ApiResponse;
import com.encore.dto.AdminBoxOfficeResponse;
import com.encore.dto.AdminDashboardResponse;
import com.encore.dto.AdminHallResponse;
import com.encore.dto.AdminLayoutAreaResponse;
import com.encore.dto.AdminLayoutResponse;
import com.encore.dto.AdminLayoutSeatResponse;
import com.encore.dto.AdminOfflineSaleRequest;
import com.encore.dto.AdminOfflineSaleResponse;
import com.encore.dto.AdminOperationLogResponse;
import com.encore.dto.AdminOrderResponse;
import com.encore.dto.AdminScheduleResponse;
import com.encore.dto.AdminScheduleInventoryResponse;
import com.encore.dto.AdminShowCategoryOption;
import com.encore.dto.AdminShowFilterOption;
import com.encore.dto.AdminShowResponse;
import com.encore.dto.AdminStaffUserResponse;
import com.encore.dto.AdminVenueResponse;
import com.encore.dto.CreateHallRequest;
import com.encore.dto.CreateLayoutRequest;
import com.encore.dto.CreateScheduleRequest;
import com.encore.dto.CreateShowRequest;
import com.encore.dto.CreateStaffUserRequest;
import com.encore.dto.CreateVenueRequest;
import com.encore.dto.LayoutSeatStatusSyncResponse;
import com.encore.dto.ResetStaffPasswordRequest;
import com.encore.dto.ReviewRefundRequest;
import com.encore.dto.SyncLayoutSeatStatusRequest;
import com.encore.dto.UpdateHallRequest;
import com.encore.dto.UpdateLayoutRequest;
import com.encore.dto.UpdateLayoutStatusRequest;
import com.encore.dto.UpdateScheduleAreaInventoryRequest;
import com.encore.dto.UpdateScheduleRequest;
import com.encore.dto.UpdateScheduleSeatStatusRequest;
import com.encore.dto.UpdateScheduleStatusRequest;
import com.encore.dto.UpdateShowRequest;
import com.encore.dto.UpdateShowStatusRequest;
import com.encore.dto.UpdateStaffUserRequest;
import com.encore.dto.UpdateVenueRequest;
import com.encore.service.AdminCsvExportService;
import com.encore.service.AdminService;
import com.encore.service.AuditLogService;
import com.encore.service.StaffAccountService;
import com.encore.service.VenueManagementService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;
    private final AdminCsvExportService adminCsvExportService;
    private final VenueManagementService venueManagementService;
    private final StaffAccountService staffAccountService;
    private final AuditLogService auditLogService;

    public AdminController(
            AdminService adminService,
            AdminCsvExportService adminCsvExportService,
            VenueManagementService venueManagementService,
            StaffAccountService staffAccountService,
            AuditLogService auditLogService
    ) {
        this.adminService = adminService;
        this.adminCsvExportService = adminCsvExportService;
        this.venueManagementService = venueManagementService;
        this.staffAccountService = staffAccountService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> dashboard() {
        return ApiResponse.ok(adminService.dashboard());
    }

    @GetMapping("/dashboard/export")
    public ResponseEntity<byte[]> exportDashboard() {
        return csvAttachment(
                "encore-dashboard-%s.csv".formatted(LocalDate.now()),
                adminCsvExportService.exportDashboardCsv()
        );
    }

    @GetMapping("/box-office")
    public ApiResponse<AdminBoxOfficeResponse> boxOffice(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String showId,
            @RequestParam(required = false) String category
    ) {
        return ApiResponse.ok(adminService.boxOffice(range, startDate, endDate, showId, category));
    }

    @GetMapping("/box-office/export")
    public ResponseEntity<byte[]> exportBoxOffice(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String showId,
            @RequestParam(required = false) String category
    ) {
        return csvAttachment(
                "encore-box-office-%s.csv".formatted(LocalDate.now()),
                adminCsvExportService.exportBoxOfficeCsv(range, startDate, endDate, showId, category)
        );
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<AdminOperationLogResponse>> listAuditLogs(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.ok(auditLogService.listLogs(module, result, keyword, limit));
    }

    @GetMapping("/venues")
    public ApiResponse<List<AdminVenueResponse>> listVenues() {
        return ApiResponse.ok(venueManagementService.listVenues());
    }

    @PostMapping("/venues")
    public ApiResponse<AdminVenueResponse> createVenue(@Valid @RequestBody CreateVenueRequest request) {
        return ApiResponse.ok(venueManagementService.createVenue(request));
    }

    @PutMapping("/venues/{id}")
    public ApiResponse<AdminVenueResponse> updateVenue(
            @PathVariable String id,
            @Valid @RequestBody UpdateVenueRequest request
    ) {
        return ApiResponse.ok(venueManagementService.updateVenue(id, request));
    }

    @DeleteMapping("/venues/{id}")
    public ApiResponse<Void> deleteVenue(@PathVariable String id) {
        venueManagementService.deleteVenue(id);
        return ApiResponse.ok();
    }

    @GetMapping("/halls")
    public ApiResponse<List<AdminHallResponse>> listHalls(@RequestParam(required = false) String venueId) {
        return ApiResponse.ok(venueManagementService.listHalls(venueId));
    }

    @PostMapping("/halls")
    public ApiResponse<AdminHallResponse> createHall(@Valid @RequestBody CreateHallRequest request) {
        return ApiResponse.ok(venueManagementService.createHall(request));
    }

    @PutMapping("/halls/{id}")
    public ApiResponse<AdminHallResponse> updateHall(
            @PathVariable String id,
            @Valid @RequestBody UpdateHallRequest request
    ) {
        return ApiResponse.ok(venueManagementService.updateHall(id, request));
    }

    @DeleteMapping("/halls/{id}")
    public ApiResponse<Void> deleteHall(@PathVariable String id) {
        venueManagementService.deleteHall(id);
        return ApiResponse.ok();
    }

    @GetMapping("/layouts")
    public ApiResponse<List<AdminLayoutResponse>> listLayouts(@RequestParam(required = false) String hallId) {
        return ApiResponse.ok(venueManagementService.listLayouts(hallId));
    }

    @PostMapping("/layouts")
    public ApiResponse<AdminLayoutResponse> createLayout(@Valid @RequestBody CreateLayoutRequest request) {
        return ApiResponse.ok(venueManagementService.createLayout(request));
    }

    @PutMapping("/layouts/{id}")
    public ApiResponse<AdminLayoutResponse> updateLayout(
            @PathVariable String id,
            @Valid @RequestBody UpdateLayoutRequest request
    ) {
        return ApiResponse.ok(venueManagementService.updateLayout(id, request));
    }

    @PatchMapping("/layouts/{id}/status")
    public ApiResponse<AdminLayoutResponse> updateLayoutStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateLayoutStatusRequest request
    ) {
        return ApiResponse.ok(venueManagementService.updateLayoutStatus(id, request.status()));
    }

    @GetMapping("/layouts/{id}/areas")
    public ApiResponse<List<AdminLayoutAreaResponse>> listLayoutAreas(@PathVariable String id) {
        return ApiResponse.ok(venueManagementService.listLayoutAreas(id));
    }

    @GetMapping("/layouts/{id}/seats")
    public ApiResponse<List<AdminLayoutSeatResponse>> listLayoutSeats(@PathVariable String id) {
        return ApiResponse.ok(venueManagementService.listLayoutSeats(id));
    }

    @PatchMapping("/layouts/{id}/seats/{seatCode}/status")
    public ApiResponse<AdminLayoutSeatResponse> updateLayoutSeatStatus(
            @PathVariable String id,
            @PathVariable String seatCode,
            @Valid @RequestBody UpdateScheduleSeatStatusRequest request
    ) {
        return ApiResponse.ok(venueManagementService.updateLayoutSeatStatus(id, seatCode, request.status()));
    }

    @PostMapping("/layouts/{id}/sync-seat-status")
    public ApiResponse<LayoutSeatStatusSyncResponse> syncLayoutSeatStatus(
            @PathVariable String id,
            @Valid @RequestBody SyncLayoutSeatStatusRequest request
    ) {
        return ApiResponse.ok(venueManagementService.syncLayoutSeatStatus(id, request));
    }

    @GetMapping("/users/staff")
    public ApiResponse<List<AdminStaffUserResponse>> listStaffUsers() {
        return ApiResponse.ok(staffAccountService.listStaffUsers());
    }

    @PostMapping("/users/staff")
    public ApiResponse<AdminStaffUserResponse> createStaffUser(@Valid @RequestBody CreateStaffUserRequest request) {
        return ApiResponse.ok(staffAccountService.createStaffUser(request));
    }

    @PatchMapping("/users/staff/{id}")
    public ApiResponse<AdminStaffUserResponse> updateStaffUser(
            @PathVariable String id,
            @Valid @RequestBody UpdateStaffUserRequest request
    ) {
        return ApiResponse.ok(staffAccountService.updateStaffUser(id, request));
    }

    @PostMapping("/users/staff/{id}/reset-password")
    public ApiResponse<AdminStaffUserResponse> resetStaffPassword(
            @PathVariable String id,
            @Valid @RequestBody ResetStaffPasswordRequest request
    ) {
        return ApiResponse.ok(staffAccountService.resetPassword(id, request));
    }

    @GetMapping("/shows")
    public ApiResponse<List<AdminShowResponse>> listShows() {
        return ApiResponse.ok(adminService.listShows());
    }

    @GetMapping("/show-categories")
    public ApiResponse<List<AdminShowCategoryOption>> listShowCategories() {
        return ApiResponse.ok(adminService.listShowCategories());
    }

    @GetMapping("/show-options")
    public ApiResponse<List<AdminShowFilterOption>> listShowOptions(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.ok(adminService.listShowOptions(category, keyword, limit));
    }

    @PostMapping("/shows")
    public ApiResponse<AdminShowResponse> createShow(@Valid @RequestBody CreateShowRequest request) {
        return ApiResponse.ok(adminService.createShow(request));
    }

    @PutMapping("/shows/{id}")
    public ApiResponse<AdminShowResponse> updateShow(
            @PathVariable String id,
            @Valid @RequestBody UpdateShowRequest request
    ) {
        return ApiResponse.ok(adminService.updateShow(id, request));
    }

    @PatchMapping("/shows/{id}/status")
    public ApiResponse<AdminShowResponse> updateShowStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateShowStatusRequest request
    ) {
        return ApiResponse.ok(adminService.updateShowStatus(id, request.status()));
    }

    @DeleteMapping("/shows/{id}")
    public ApiResponse<AdminShowResponse> deleteShow(@PathVariable String id) {
        return ApiResponse.ok(adminService.archiveShow(id));
    }

    @GetMapping("/schedules")
    public ApiResponse<List<AdminScheduleResponse>> listSchedules() {
        return ApiResponse.ok(adminService.listSchedules());
    }

    @PostMapping("/schedules")
    public ApiResponse<AdminScheduleResponse> createSchedule(@Valid @RequestBody CreateScheduleRequest request) {
        return ApiResponse.ok(adminService.createSchedule(request));
    }

    @PutMapping("/schedules/{id}")
    public ApiResponse<AdminScheduleResponse> updateSchedule(
            @PathVariable String id,
            @Valid @RequestBody UpdateScheduleRequest request
    ) {
        return ApiResponse.ok(adminService.updateSchedule(id, request));
    }

    @PatchMapping("/schedules/{id}/status")
    public ApiResponse<AdminScheduleResponse> updateScheduleStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateScheduleStatusRequest request
    ) {
        return ApiResponse.ok(adminService.updateScheduleStatus(id, request.status()));
    }

    @DeleteMapping("/schedules/{id}")
    public ApiResponse<AdminScheduleResponse> deleteSchedule(@PathVariable String id) {
        return ApiResponse.ok(adminService.cancelSchedule(id));
    }

    @GetMapping("/schedules/{id}/inventory")
    public ApiResponse<AdminScheduleInventoryResponse> getScheduleInventory(@PathVariable String id) {
        return ApiResponse.ok(venueManagementService.getScheduleInventory(id));
    }

    @PatchMapping("/schedules/{id}/inventory/seats/{seatCode}")
    public ApiResponse<AdminScheduleInventoryResponse> updateScheduleSeatStatus(
            @PathVariable String id,
            @PathVariable String seatCode,
            @Valid @RequestBody UpdateScheduleSeatStatusRequest request
    ) {
        return ApiResponse.ok(venueManagementService.updateScheduleSeatStatus(id, seatCode, request.status()));
    }

    @PatchMapping("/schedules/{id}/inventory/areas/{inventoryId}")
    public ApiResponse<AdminScheduleInventoryResponse> updateAreaInventory(
            @PathVariable String id,
            @PathVariable String inventoryId,
            @Valid @RequestBody UpdateScheduleAreaInventoryRequest request
    ) {
        return ApiResponse.ok(venueManagementService.updateAreaInventory(id, inventoryId, request));
    }

    @GetMapping("/orders")
    public ApiResponse<List<AdminOrderResponse>> listOrders() {
        return ApiResponse.ok(adminService.listOrders());
    }

    @PostMapping("/offline-sales")
    public ApiResponse<AdminOfflineSaleResponse> createOfflineSale(
            @Valid @RequestBody AdminOfflineSaleRequest request
    ) {
        return ApiResponse.ok(adminService.createOfflineSale(request));
    }

    @GetMapping("/orders/export")
    public ResponseEntity<byte[]> exportOrders() {
        return csvAttachment(
                "encore-orders-%s.csv".formatted(LocalDate.now()),
                adminCsvExportService.exportOrdersCsv()
        );
    }

    @PostMapping("/orders/{id}/refund")
    public ApiResponse<AdminOrderResponse> refundOrder(@PathVariable String id) {
        return ApiResponse.ok(adminService.refundOrder(id));
    }

    @PostMapping("/orders/{id}/refund/approve")
    public ApiResponse<AdminOrderResponse> approveRefund(
            @PathVariable String id,
            @RequestBody(required = false) ReviewRefundRequest request
    ) {
        return ApiResponse.ok(adminService.approveRefund(id, request));
    }

    @PostMapping("/orders/{id}/refund/reject")
    public ApiResponse<AdminOrderResponse> rejectRefund(
            @PathVariable String id,
            @RequestBody(required = false) ReviewRefundRequest request
    ) {
        return ApiResponse.ok(adminService.rejectRefund(id, request));
    }

    @PostMapping("/orders/{id}/force-checkin")
    public ApiResponse<AdminOrderResponse> forceCheckInOrder(@PathVariable String id) {
        return ApiResponse.ok(adminService.forceCheckInOrder(id));
    }

    private ResponseEntity<byte[]> csvAttachment(String filename, String csv) {
        byte[] bytes = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(bytes);
    }
}
