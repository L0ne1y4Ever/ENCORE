package com.encore.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank String scheduleId,
        List<String> seatIds,
        String areaInventoryId,
        Integer quantity,
        BigDecimal totalAmount
) {
}
