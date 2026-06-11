package com.encore.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateScheduleRequest(
        @NotBlank String showId,
        String hallId,
        String layoutId,
        String theaterName,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime,
        LocalDateTime saleStartTime,
        LocalDateTime saleEndTime,
        String status,
        String publishStatus,
        String priceRange,
        SchedulePricingRequest pricing,
        String ticketMode,
        @Min(1) @Max(30) Integer seatRows,
        @Min(1) @Max(40) Integer seatCols,
        @DecimalMin("0.01") BigDecimal vipPrice,
        @DecimalMin("0.01") BigDecimal standardPrice,
        @DecimalMin("0.01") BigDecimal economyPrice
) {
}
