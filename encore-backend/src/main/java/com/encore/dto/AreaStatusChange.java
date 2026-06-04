package com.encore.dto;

public record AreaStatusChange(
        String areaId,
        String code,
        Integer availableCount,
        Integer lockedCount,
        Integer soldCount,
        String status
) {
}
