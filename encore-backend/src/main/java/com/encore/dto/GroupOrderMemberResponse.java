package com.encore.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record GroupOrderMemberResponse(
        String userId,
        String displayName,
        List<String> seatIds,
        BigDecimal amount,
        LocalDateTime joinedAt
) {
}
