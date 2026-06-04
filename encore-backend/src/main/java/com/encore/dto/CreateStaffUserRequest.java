package com.encore.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateStaffUserRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String displayName,
        @NotBlank String role,
        String status
) {
}
