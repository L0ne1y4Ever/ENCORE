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
        String priceRange,
        SchedulePricingRequest pricing,
        String ticketMode
) {
    public UpdateScheduleRequest(
            String showId,
            String hallId,
            String layoutId,
            String theaterName,
            LocalDateTime startTime,
            LocalDateTime endTime,
            LocalDateTime saleStartTime,
            LocalDateTime saleEndTime,
            String status,
            String publishStatus,
            String priceRange,
            String ticketMode
    ) {
        this(showId, hallId, layoutId, theaterName, startTime, endTime, saleStartTime, saleEndTime,
                status, publishStatus, priceRange, null, ticketMode);
    }
}
