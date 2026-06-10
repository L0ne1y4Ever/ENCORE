package com.encore.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ScheduleResponse(
        String id,
        String showId,
        String theaterName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime saleStartTime,
        LocalDateTime saleEndTime,
        String status,
        String publishStatus,
        String priceRange,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String ticketMode,
        String category
) {
}
