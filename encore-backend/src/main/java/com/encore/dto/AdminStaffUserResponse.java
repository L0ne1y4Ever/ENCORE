package com.encore.dto;

import java.time.LocalDateTime;

public record AdminStaffUserResponse(
        String id,
        String username,
        String role,
        String displayName,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean editable
) {
}
