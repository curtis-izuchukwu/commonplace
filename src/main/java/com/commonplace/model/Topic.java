package com.commonplace.model;

import java.time.LocalDateTime;

public record Topic(
        long id,
        long moduleId,
        String name,
        String description,
        ImportanceLevel importance,
        ConfidenceLevel confidence,
        double masteryScore,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}