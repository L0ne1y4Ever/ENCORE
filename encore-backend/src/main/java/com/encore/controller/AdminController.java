package com.encore.controller;

import com.encore.common.ApiResponse;
import com.encore.dto.AdminOrderResponse;
import com.encore.dto.AdminScheduleResponse;
import com.encore.dto.AdminShowResponse;
import com.encore.dto.CreateScheduleRequest;
import com.encore.dto.CreateShowRequest;
import com.encore.dto.UpdateScheduleRequest;
import com.encore.dto.UpdateScheduleStatusRequest;
import com.encore.dto.UpdateShowRequest;
import com.encore.dto.UpdateShowStatusRequest;
import com.encore.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/shows")
    public ApiResponse<List<AdminShowResponse>> listShows() {
        return ApiResponse.ok(adminService.listShows());
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

    @GetMapping("/orders")
    public ApiResponse<List<AdminOrderResponse>> listOrders() {
        return ApiResponse.ok(adminService.listOrders());
    }

    @PostMapping("/orders/{id}/refund")
    public ApiResponse<AdminOrderResponse> refundOrder(@PathVariable String id) {
        return ApiResponse.ok(adminService.refundOrder(id));
    }

    @PostMapping("/orders/{id}/force-checkin")
    public ApiResponse<AdminOrderResponse> forceCheckInOrder(@PathVariable String id) {
        return ApiResponse.ok(adminService.forceCheckInOrder(id));
    }
}
