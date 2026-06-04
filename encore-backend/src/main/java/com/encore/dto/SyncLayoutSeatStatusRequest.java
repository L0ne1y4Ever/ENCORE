package com.encore.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SyncLayoutSeatStatusRequest(
        @NotEmpty List<String> scheduleIds
) {
}
