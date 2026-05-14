package com.encore.dto;

import java.time.OffsetDateTime;

public record HealthResponse(String status, String service, OffsetDateTime time) {
}
