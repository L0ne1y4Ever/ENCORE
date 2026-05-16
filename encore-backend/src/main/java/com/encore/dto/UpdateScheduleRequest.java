package com.encore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record UpdateScheduleRequest(
        @NotBlank String showId,
        @NotBlank String theaterName,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime,
        @NotBlank String status,
        @NotBlank String priceRange
) {
}
