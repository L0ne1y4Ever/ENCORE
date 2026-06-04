package com.encore.dto;

public record AdminLayoutResponse(
        String id,
        String hallId,
        String hallName,
        String venueName,
        String name,
        String ticketMode,
        Integer version,
        String status,
        long areaCount,
        long seatCount
) {
}
