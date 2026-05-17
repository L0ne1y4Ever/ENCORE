package com.encore.dto;

import java.math.BigDecimal;
import java.util.List;

public record ShowRecommendationResponse(
        String id,
        String title,
        String subtitle,
        String coverUrl,
        String description,
        Integer duration,
        String category,
        List<String> tags,
        Integer rank,
        Long ticketsSold,
        Long availableScheduleCount,
        BigDecimal hotScore
) {
}
