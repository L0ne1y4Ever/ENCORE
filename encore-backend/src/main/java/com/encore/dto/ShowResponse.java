package com.encore.dto;

import java.util.List;

public record ShowResponse(
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
        String priceRange
) {
}
