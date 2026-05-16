package com.encore.dto;

import java.time.LocalDateTime;

public record DashboardRefreshEvent(
        String reason,
        String referenceId,
        LocalDateTime timestamp
) {
}
