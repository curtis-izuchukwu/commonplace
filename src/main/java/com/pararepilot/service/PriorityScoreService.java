package com.pararepilot.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.pararepilot.model.ConfidenceLevel;
import com.pararepilot.model.DifficultyLevel;
import com.pararepilot.model.ImportanceLevel;
import com.pararepilot.model.Topic;
import com.pararepilot.model.Worksheet;

public class PriorityScoreService {

    private static final int MIN_PRIORITY = 1;
    private static final int MAX_PRIORITY = 100;

    public int calculatePriority(Worksheet worksheet, Topic topic) {
        return calculatePriority(worksheet, topic, 0);
    }

    public int calculatePriority(
            Worksheet worksheet,
            Topic topic,
            int unresolvedMistakeCount
    ) {
        if (worksheet == null) {
            throw new IllegalArgumentException("Worksheet cannot be null.");
        }

        if (topic == null) {
            throw new IllegalArgumentException("Topic cannot be null.");
        }

        int priority =
                10
                        + ageScore(worksheet)
                        + lowScoreBoost(worksheet)
                        + confidenceBoost(topic.confidence())
                        + difficultyBoost(worksheet.difficulty())
                        + importanceBoost(worksheet.importance())
                        + importanceBoost(topic.importance())
                        + failureBoost(worksheet.failureStreak())
                        + mistakeBoost(unresolvedMistakeCount);

        return clamp(priority);
    }

    public String explainPriority(
            Worksheet worksheet,
            Topic topic,
            int unresolvedMistakeCount
    ) {
        int score = calculatePriority(worksheet, topic, unresolvedMistakeCount);

        return "Priority " + score + "/100"
                + " - " + ageExplanation(worksheet)
                + " - " + scoreExplanation(worksheet)
                + " - Topic confidence: " + topic.confidence()
                + " - Difficulty: " + worksheet.difficulty()
                + " - Worksheet importance: " + worksheet.importance()
                + " - Topic importance: " + topic.importance()
                + " - Failure streak: " + worksheet.failureStreak()
                + " - Unresolved mistakes: " + unresolvedMistakeCount;
    }

    private int ageScore(Worksheet worksheet) {
        if (worksheet.lastAttemptedAt() == null) {
            return 25;
        }

        long daysSinceLastAttempt = ChronoUnit.DAYS.between(
                worksheet.lastAttemptedAt().toLocalDate(),
                LocalDate.now()
        );

        return (int) Math.min(Math.max(daysSinceLastAttempt, 0) * 2, 30);
    }

    private int lowScoreBoost(Worksheet worksheet) {
        Double latestScore = worksheet.latestScorePercent();

        if (latestScore == null) {
            return 10;
        }

        if (latestScore < 50) {
            return 30;
        }

        if (latestScore < 70) {
            return 15;
        }

        return 0;
    }

    private int confidenceBoost(ConfidenceLevel confidence) {
        if (confidence == null) {
            return 15;
        }

        return switch (confidence) {
            case LOW -> 30;
            case MEDIUM -> 15;
            case HIGH -> 0;
        };
    }

    private int difficultyBoost(DifficultyLevel difficulty) {
        if (difficulty == null) {
            return 10;
        }

        return switch (difficulty) {
            case EASY -> 5;
            case MEDIUM -> 10;
            case HARD -> 20;
        };
    }

    private int importanceBoost(ImportanceLevel importance) {
        if (importance == null) {
            return 10;
        }

        return switch (importance) {
            case LOW -> 0;
            case MEDIUM -> 10;
            case HIGH -> 20;
        };
    }

    private int failureBoost(int failureStreak) {
        return Math.min(Math.max(failureStreak, 0) * 10, 30);
    }

    private int mistakeBoost(int unresolvedMistakeCount) {
        return Math.min(Math.max(unresolvedMistakeCount, 0) * 5, 25);
    }

    private int clamp(int value) {
        return Math.max(MIN_PRIORITY, Math.min(MAX_PRIORITY, value));
    }

    private String ageExplanation(Worksheet worksheet) {
        if (worksheet.lastAttemptedAt() == null) {
            return "Never attempted";
        }

        long days = ChronoUnit.DAYS.between(
                worksheet.lastAttemptedAt().toLocalDate(),
                LocalDate.now()
        );

        return Math.max(days, 0) + " days since last attempt";
    }

    private String scoreExplanation(Worksheet worksheet) {
        if (worksheet.latestScorePercent() == null) {
            return "No previous score";
        }

        return "Latest score: " + String.format("%.0f%%", worksheet.latestScorePercent());
    }
}
