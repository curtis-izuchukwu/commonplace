package com.pararepilot.model;

import java.time.LocalDateTime;

public record WorksheetAttempt(
        long id,
        long worksheetId,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        int score,
        int maxScore,
        double scorePercent,
        ConfidenceLevel confidenceAfter,
        String mainWeakness,
        String nextAction,
        String reflectionNotes
) {
}