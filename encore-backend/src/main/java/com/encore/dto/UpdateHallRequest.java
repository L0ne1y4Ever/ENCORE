package com.encore.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UpdateHallRequest(
        @NotBlank String venueId,
        @NotBlank String name,
        String hallType,
        String defaultLayoutId,
        @Min(0) Integer capacity,
        @Min(0) Integer clearanceMinutes,
        String status
) {
}
