package com.encore.dto;

import java.math.BigDecimal;

public record AdminLayoutAreaResponse(
        String id,
        String layoutId,
        String name,
        String code,
        String areaType,
        Boolean isSeated,
        Integer capacity,
        BigDecimal basePrice,
        String color,
        String description,
        String positionData
) {
}
