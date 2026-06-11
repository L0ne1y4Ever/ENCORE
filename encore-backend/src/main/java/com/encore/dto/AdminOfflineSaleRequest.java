package com.encore.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AdminOfflineSaleRequest(
        @NotBlank String scheduleId,
        String buyerUsername,
        String buyerDisplayName,
        List<String> seatIds,
        String areaInventoryId,
        Integer quantity
) {
}
