package com.encore.dto;

import java.math.BigDecimal;

public record AdminLayoutSeatResponse(
        String id,
        String layoutId,
        String areaId,
        String seatCode,
        Integer rowNo,
        Integer colNo,
        String section,
        String status,
        BigDecimal price
) {
}
