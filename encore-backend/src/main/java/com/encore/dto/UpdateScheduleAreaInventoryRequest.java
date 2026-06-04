package com.encore.dto;

import jakarta.validation.constraints.Min;

public record UpdateScheduleAreaInventoryRequest(
        @Min(0) Integer totalCount,
        @Min(0) Integer availableCount,
        String status
) {
}
