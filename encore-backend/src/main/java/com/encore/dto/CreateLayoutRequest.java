package com.encore.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CreateLayoutRequest(
        @NotBlank String hallId,
        @NotBlank String name,
        @NotBlank String ticketMode,
        String status,
        @Min(1) @Max(40) Integer seatRows,
        @Min(1) @Max(60) Integer seatCols,
        @DecimalMin("0.01") BigDecimal vipPrice,
        @DecimalMin("0.01") BigDecimal standardPrice,
        @DecimalMin("0.01") BigDecimal economyPrice
) {
}
