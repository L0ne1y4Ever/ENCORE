package com.encore.dto;

import java.time.LocalDateTime;

public record AdminOperationLogResponse(
        String id,
        String actorId,
        String actorUsername,
        String actorRole,
        String module,
        String action,
        String targetId,
        String targetLabel,
        String result,
        String detail,
        LocalDateTime createdAt
) {
}
