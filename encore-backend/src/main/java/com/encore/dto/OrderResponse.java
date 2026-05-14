package com.encore.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        String id,
        String userId,
        String scheduleId,
        BigDecimal totalAmount,
        String status,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        List<TicketItemResponse> tickets
) {
}
