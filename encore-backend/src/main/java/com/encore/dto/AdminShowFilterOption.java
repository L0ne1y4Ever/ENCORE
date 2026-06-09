package com.encore.dto;

public record AdminShowFilterOption(
        String id,
        String title,
        String subtitle,
        String category,
        String status,
        long scheduleCount
) {
}
