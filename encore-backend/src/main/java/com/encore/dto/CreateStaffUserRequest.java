package com.encore.dto;

public record CreateStaffUserRequest(
        String username,
        String password,
        String displayName,
        String role,
        String status
) {
}
