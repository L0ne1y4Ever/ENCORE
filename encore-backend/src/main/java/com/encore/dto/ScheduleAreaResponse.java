package com.encore.dto;

import java.math.BigDecimal;

public record ScheduleAreaResponse(
        String id,
        String areaId,
        String name,
        String code,
        String areaType,
        Boolean isSeated,
        BigDecimal price,
        Integer totalCount,
        Integer availableCount,
        Integer lockedCount,
        Integer soldCount,
        String color,
        String description,
        String positionData
) {}
