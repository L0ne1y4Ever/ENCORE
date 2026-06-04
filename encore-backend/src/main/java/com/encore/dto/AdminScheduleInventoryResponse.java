package com.encore.dto;

import java.util.List;

public record AdminScheduleInventoryResponse(
        String scheduleId,
        String showTitle,
        String theaterName,
        String ticketMode,
        long totalSeats,
        long availableSeats,
        long lockedSeats,
        long soldSeats,
        long disabledSeats,
        List<SeatResponse> seats,
        List<ScheduleAreaResponse> areas
) {
}
