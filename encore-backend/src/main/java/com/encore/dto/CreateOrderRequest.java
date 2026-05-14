package com.encore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank String scheduleId,
        @NotEmpty List<String> seatIds,
        BigDecimal totalAmount
) {
}
