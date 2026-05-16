package com.encore.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateShowStatusRequest(
        @NotBlank String status
) {
}
