package com.encore.dto;

public record UserProfileResponse(
        String id,
        String username,
        String role,
        String displayName
) {
}
