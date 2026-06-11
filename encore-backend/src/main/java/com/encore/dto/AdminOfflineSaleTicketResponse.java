package com.encore.dto;

import java.math.BigDecimal;

public record AdminOfflineSaleTicketResponse(
        String id,
        String ticketCode,
        String seatId,
        String areaInventoryId,
        String areaName,
        String areaType,
        String seatLabel,
        BigDecimal price,
        String status,
        String holderUserId,
        String holderDisplayName
) {
}
