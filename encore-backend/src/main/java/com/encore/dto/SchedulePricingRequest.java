package com.encore.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record SchedulePricingRequest(
        @DecimalMin("0.01") BigDecimal basePrice,
        @DecimalMin("0.01") BigDecimal vipPrice,
        @DecimalMin("0.01") BigDecimal standardPrice,
        @DecimalMin("0.01") BigDecimal economyPrice
) {
}
