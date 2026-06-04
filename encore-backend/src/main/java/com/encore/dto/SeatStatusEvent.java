package com.encore.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SeatStatusEvent(
        String scheduleId,
        String reason,
        LocalDateTime timestamp,
        List<SeatStatusChange> seats,
        List<AreaStatusChange> areas
) {
}
