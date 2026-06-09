package com.encore.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminOrderResponse(
        String id,
        String userId,
        String username,
        String scheduleId,
        String showName,
        String theaterName,
        LocalDateTime startTime,
        BigDecimal totalAmount,
        String status,
        int ticketCount,
        int checkedInCount,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        RefundRequestSummary refundRequest
) {
}
