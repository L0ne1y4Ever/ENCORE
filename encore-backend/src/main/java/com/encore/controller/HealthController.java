package com.encore.controller;

import com.encore.common.ApiResponse;
import com.encore.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.ok(new HealthResponse("UP", "encore-backend", OffsetDateTime.now()));
    }
}
