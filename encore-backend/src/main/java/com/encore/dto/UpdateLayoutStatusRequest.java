package com.encore.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateLayoutStatusRequest(
        @NotBlank String status
) {
}
