package com.encore.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record JoinGroupOrderRequest(
        @NotEmpty List<String> seatIds
) {
}
