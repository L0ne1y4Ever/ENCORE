package com.encore.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateLayoutRequest(
        @NotBlank String name,
        String status
) {
}
