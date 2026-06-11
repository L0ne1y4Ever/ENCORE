package com.encore.dto;

import java.util.List;

public record SeatLocksResponse(
        List<SeatLockResponse> seats
) {
}
