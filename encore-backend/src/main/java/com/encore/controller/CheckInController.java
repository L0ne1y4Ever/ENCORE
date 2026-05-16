package com.encore.controller;

import com.encore.common.ApiResponse;
import com.encore.dto.CheckInRequest;
import com.encore.dto.CheckInResponse;
import com.encore.dto.CheckInScheduleResponse;
import com.encore.service.CheckInService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/checkin")
public class CheckInController {
    private final CheckInService checkInService;

    public CheckInController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }

    @GetMapping("/schedules")
    public ApiResponse<List<CheckInScheduleResponse>> listSchedules() {
        return ApiResponse.ok(checkInService.listCheckInSchedules());
    }

    @PostMapping("/verify")
    public ApiResponse<CheckInResponse> verify(@Valid @RequestBody CheckInRequest request) {
        return ApiResponse.ok(checkInService.verify(request.ticketCode(), request.scheduleId()));
    }
}
