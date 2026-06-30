package com.pararepilot.service;

import java.sql.SQLException;
import java.util.List;

import com.pararepilot.model.ConfidenceLevel;
import com.pararepilot.model.Worksheet;
import com.pararepilot.model.WorksheetAttempt;
import com.pararepilot.repository.AttemptRepository;
import com.pararepilot.repository.WorksheetRepository;

public class ReflectionService {

    private static final double FAILURE_THRESHOLD_PERCENT = 50.0;

    private final AttemptRepository attemptRepository;
    private final WorksheetRepository worksheetRepository;
    private final TopicStatsService topicStatsService;

    public ReflectionService() {
        this(new AttemptRepository(), new WorksheetRepository(), new TopicStatsService());
    }

    public ReflectionService(
            AttemptRepository attemptRepository,
            WorksheetRepository worksheetRepository,
            TopicStatsService topicStatsService
    ) {
        this.attemptRepository = attemptRepository;
        this.worksheetRepository = worksheetRepository;
        this.topicStatsService = topicStatsService;
    }

    public void completeReflection(
            Worksheet worksheet,
            WorksheetAttempt attempt,
            ConfidenceLevel confidenceAfter,
            String mainWeakness,
            String nextAction,
            String reflectionNotes
    ) throws SQLException {

        if (worksheet == null) {
            throw new IllegalArgumentException("Worksheet cannot be null.");
        }

        if (attempt == null) {
            throw new IllegalArgumentException("Attempt cannot be null.");
        }

        ConfidenceLevel resolvedConfidence = confidenceAfter == null
                ? ConfidenceLevel.MEDIUM
                : confidenceAfter;

        attemptRepository.updateReflection(
                attempt.id(),
                resolvedConfidence,
                mainWeakness,
                nextAction,
                reflectionNotes
        );

        updateWorksheetStats(worksheet, attempt);
        topicStatsService.updateTopicStats(worksheet.topicId(), resolvedConfidence);
    }

    private void updateWorksheetStats(Worksheet worksheet, WorksheetAttempt latestAttempt)
            throws SQLException {

        List<WorksheetAttempt> attempts = attemptRepository.findByWorksheetId(worksheet.id());

        int timesAttempted = attempts.size();

        double averageScore = attempts.stream()
                .mapToDouble(WorksheetAttempt::scorePercent)
                .average()
                .orElse(latestAttempt.scorePercent());

        int previousFailureStreak = worksheet.failureStreak();

        int failureStreak = latestAttempt.scorePercent() < FAILURE_THRESHOLD_PERCENT
                ? previousFailureStreak + 1
                : 0;

        worksheetRepository.updateStats(
                worksheet.id(),
                latestAttempt.completedAt(),
                timesAttempted,
                latestAttempt.scorePercent(),
                averageScore,
                failureStreak
        );
    }
}