package com.encore.dto;

public record AdminVenueResponse(
        String id,
        String name,
        String city,
        String address,
        String status,
        long hallCount
) {
}
