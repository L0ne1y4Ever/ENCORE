package com.encore.dto;

import java.time.LocalDateTime;

public record AdminScheduleResponse(
        String id,
        String showId,
        String showTitle,
        String category,
        String hallId,
        String hallName,
        String layoutId,
        String layoutName,
        String theaterName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime saleStartTime,
        LocalDateTime saleEndTime,
        String status,
        String publishStatus,
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
