package com.encore.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateStaffUserRequest(
        @NotBlank String displayName,
        @NotBlank String role,
        @NotBlank String status
) {
}
