package com.encore.dto;

public record RegisterRequest(
        String username,
        String password,
        String displayName
) {
}
