package com.pararepilot.model;

import java.time.LocalDateTime;

public record Worksheet(
        long id,
        long topicId,
        String title,
        String description,
        DifficultyLevel difficulty,
        ImportanceLevel importance,
        String source,
        LocalDateTime createdAt,
        LocalDateTime lastAttemptedAt,
        int timesAttempted,
        Double latestScorePercent,
        Double averageScorePercent,
        int failureStreak
) {
}