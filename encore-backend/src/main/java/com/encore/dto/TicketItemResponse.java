package com.encore.dto;

import java.math.BigDecimal;

public record TicketItemResponse(
        String id,
        String orderId,
        String scheduleId,
        String seatId,
        String ticketCode,
        String status,
        String areaInventoryId,
        String areaName,
        String areaType,
        String seatLabel,
        Integer rowNo,
        Integer colNo,
        BigDecimal price,
        String holderUserId,
        String holderDisplayName
) {
}
