package com.encore.dto;

import java.util.List;

public record ShowResponse(
        String id,
        String title,
        String subtitle,
        String coverUrl,
        String description,
        Integer duration,
        String category,
        List<String> tags
) {
}
