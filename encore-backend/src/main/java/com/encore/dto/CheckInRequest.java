package com.encore.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckInRequest(
        @NotBlank String ticketCode,
        String scheduleId
) {
}
