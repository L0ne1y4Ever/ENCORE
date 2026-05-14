package com.encore.dto;

import java.math.BigDecimal;

public record SeatResponse(
        String id,
        Integer row,
        Integer col,
        String section,
        String status,
        BigDecimal price
) {
}
