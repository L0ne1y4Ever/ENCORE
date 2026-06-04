package com.encore.dto;

public record AdminHallResponse(
        String id,
        String venueId,
        String venueName,
        String name,
        String hallType,
        Integer capacity,
        String defaultLayoutId,
        Integer clearanceMinutes,
        String status,
        long layoutCount
) {
}
