package com.encore.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record RefundRequestSummary(
        String id,
        String status,
        String source,
        String scope,
        String reason,
        String reviewNote,
        String reviewerUsername,
        BigDecimal refundAmount,
        Integer ticketCount,
        List<String> ticketIds,
        String requesterId,
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt
) {
}
