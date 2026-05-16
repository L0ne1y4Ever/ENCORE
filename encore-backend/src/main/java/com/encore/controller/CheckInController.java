package com.encore.controller;

import com.encore.common.ApiResponse;
import com.encore.dto.CheckInRequest;
import com.encore.dto.CheckInResponse;
import com.encore.service.CheckInService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkin")
public class CheckInController {
    private final CheckInService checkInService;

    public CheckInController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }

    @PostMapping("/verify")
    public ApiResponse<CheckInResponse> verify(@Valid @RequestBody CheckInRequest request) {
        return ApiResponse.ok(checkInService.verify(request.ticketCode()));
    }
}
