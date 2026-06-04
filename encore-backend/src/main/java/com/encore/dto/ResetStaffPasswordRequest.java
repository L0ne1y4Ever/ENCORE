package com.encore.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetStaffPasswordRequest(
        @NotBlank String password
) {
}
