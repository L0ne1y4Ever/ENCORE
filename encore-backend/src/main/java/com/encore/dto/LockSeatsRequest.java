package com.encore.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record LockSeatsRequest(
        @NotEmpty List<String> seatIds
) {
}
