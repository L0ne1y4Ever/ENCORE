package com.encore.dto;

public record LoginResponse(
        String tokenName,
        String tokenValue,
        UserProfileResponse user
) {
}
