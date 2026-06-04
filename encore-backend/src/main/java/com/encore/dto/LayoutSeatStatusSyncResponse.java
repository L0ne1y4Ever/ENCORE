package com.encore.dto;

import java.util.List;

public record LayoutSeatStatusSyncResponse(
        String layoutId,
        int scheduleCount,
        int updatedSeatCount,
        List<String> scheduleIds
) {
}
