package com.encore.dto;

import java.time.LocalDateTime;

public record CheckInResponse(
        String ticketId,
        String ticketCode,
        String orderId,
        String scheduleId,
        String showTitle,
        String theaterName,
        LocalDateTime startTime,
        String seatId,
        String status,
        LocalDateTime checkedInAt
) {
}
