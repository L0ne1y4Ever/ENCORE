package com.encore.dto;

import java.time.LocalDateTime;

public record AdminScheduleResponse(
        String id,
        String showId,
        String showTitle,
        String theaterName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        String priceRange,
        String ticketMode,
        long totalSeats,
        long availableSeats,
        long lockedSeats,
        long soldSeats,
        long disabledSeats,
        long paidTickets,
        long checkedInTickets
) {
}
