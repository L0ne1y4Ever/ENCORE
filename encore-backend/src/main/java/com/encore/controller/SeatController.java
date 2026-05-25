package com.encore.controller;

import com.encore.common.ApiResponse;
import com.encore.dto.LockSeatsRequest;
import com.encore.dto.SeatResponse;
import com.encore.dto.ScheduleAreaResponse;
import com.encore.service.SeatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/schedules/{scheduleId}")
public class SeatController {
    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    @GetMapping("/seats")
    public ApiResponse<List<SeatResponse>> listSeats(
            @PathVariable String scheduleId,
            @RequestParam(required = false) String areaId
    ) {
        return ApiResponse.ok(seatService.listSeats(scheduleId, areaId));
    }

    @GetMapping("/areas")
    public ApiResponse<List<ScheduleAreaResponse>> listScheduleAreas(@PathVariable String scheduleId) {
        return ApiResponse.ok(seatService.listScheduleAreas(scheduleId));
    }

    @PostMapping("/seats/lock")
    public ApiResponse<Boolean> lockSeats(
            @PathVariable String scheduleId,
            @Valid @RequestBody LockSeatsRequest request
    ) {
        return ApiResponse.ok(seatService.lockSeats(scheduleId, request.seatIds()));
    }
}
