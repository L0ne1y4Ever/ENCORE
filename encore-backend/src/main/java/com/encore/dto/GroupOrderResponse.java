package com.encore.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record GroupOrderResponse(
        String inviteCode,
        String scheduleId,
        String hostUserId,
        String hostDisplayName,
        String status,
        LocalDateTime expiresAt,
        Integer maxSeats,
        BigDecimal totalAmount,
        String orderId,
        String orderStatus,
        List<GroupOrderMemberResponse> members
) {
}
