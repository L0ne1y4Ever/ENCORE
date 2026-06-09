package com.encore.dto;

import java.time.LocalDateTime;

public record RefundRequestSummary(
        String id,
        String status,
        String source,
        String reason,
        String reviewNote,
        String reviewerUsername,
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt
) {
}
