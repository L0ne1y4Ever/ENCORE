package com.encore.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateScheduleStatusRequest(
        @NotBlank String status
) {
}
