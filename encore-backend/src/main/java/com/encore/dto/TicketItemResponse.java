package com.encore.dto;

public record TicketItemResponse(
        String id,
        String orderId,
        String scheduleId,
        String seatId,
        String ticketCode,
        String status
) {
}
