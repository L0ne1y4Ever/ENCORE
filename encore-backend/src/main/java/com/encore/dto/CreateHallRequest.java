package com.encore.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateHallRequest(
        @NotBlank String venueId,
        @NotBlank String name,
        String hallType,
        @Min(0) Integer capacity,
        @Min(0) Integer clearanceMinutes,
        String status
) {
}
