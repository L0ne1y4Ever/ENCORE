package com.encore.dto;

import java.math.BigDecimal;
import java.util.List;

public record ShowRecommendationResponse(
        String id,
        String title,
        String subtitle,
        String coverUrl,
        String description,
        String intro,
        String castMembers,
        String creativeTeam,
        String fullSynopsis,
        Integer duration,
        String category,
        List<String> tags,
        String priceRange,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer rank,
        Long ticketsSold,
        Long availableScheduleCount,
        Long availableTicketCount,
        BigDecimal hotScore
) {
}
