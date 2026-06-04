package com.encore.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateVenueRequest(
        @NotBlank String name,
        String city,
        String address,
        String status
) {
}
