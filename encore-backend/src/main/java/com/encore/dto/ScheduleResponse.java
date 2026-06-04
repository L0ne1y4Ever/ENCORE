package com.encore.dto;

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
        String ticketMode,
        String category
) {
}
