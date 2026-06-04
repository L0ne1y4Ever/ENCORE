package com.encore.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateScheduleSeatStatusRequest(
        @NotBlank String status
) {
}
