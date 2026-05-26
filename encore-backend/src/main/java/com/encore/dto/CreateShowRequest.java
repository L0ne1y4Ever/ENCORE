package com.encore.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateShowRequest(
        @NotBlank String title,
        @NotBlank String subtitle,
        @NotBlank String coverUrl,
        @NotBlank String description,
        String intro,
        String castMembers,
        String creativeTeam,
        String fullSynopsis,
        @NotNull @Min(1) Integer duration,
        @NotBlank String category,
        List<String> tags,
        String status,
        Integer sortOrder
) {
}
