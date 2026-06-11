package com.encore.dto;

import java.time.LocalDateTime;

public record SeatLockResponse(
        String seatId,
        LocalDateTime expiresAt
) {
}
