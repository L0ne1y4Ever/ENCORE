package com.encore.dto;

import java.time.LocalDateTime;

public record CheckInScheduleResponse(
        String id,
        String showTitle,
        String theaterName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        boolean checkInOpen
) {
}
