package com.encore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record UpdateScheduleRequest(
        @NotBlank String showId,
        String hallId,
        String layoutId,
        @NotBlank String theaterName,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime,
        LocalDateTime saleStartTime,
        LocalDateTime saleEndTime,
        @NotBlank String status,
        String publishStatus,
        @NotBlank String priceRange,
        String ticketMode
) {
}
