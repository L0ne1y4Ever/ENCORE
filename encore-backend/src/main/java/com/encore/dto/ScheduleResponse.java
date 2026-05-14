package com.encore.dto;

import java.time.LocalDateTime;

public record ScheduleResponse(
        String id,
        String showId,
        String theaterName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        String priceRange
) {
}
