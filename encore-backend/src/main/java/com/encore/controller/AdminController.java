package com.encore.controller;

import com.encore.common.ApiResponse;
import com.encore.dto.AdminOrderResponse;
import com.encore.dto.AdminScheduleResponse;
import com.encore.dto.UpdateScheduleStatusRequest;
import com.encore.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @GetMapping("/schedules")
    public ApiResponse<List<AdminScheduleResponse>> listSchedules() {
        return ApiResponse.ok(adminService.listSchedules());
    }

    @PatchMapping("/schedules/{id}/status")
    public ApiResponse<AdminScheduleResponse> updateScheduleStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateScheduleStatusRequest request
    ) {
        return ApiResponse.ok(adminService.updateScheduleStatus(id, request.status()));
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
