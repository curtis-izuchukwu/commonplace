package com.commonplace.service;

import com.commonplace.model.ConfidenceLevel;
import com.commonplace.model.Worksheet;
import com.commonplace.model.WorksheetAttempt;
import com.commonplace.repository.AttemptRepository;
import com.commonplace.repository.WorksheetRepository;

import java.sql.SQLException;
import java.util.List;

public class ReflectionService {

    private static final double FAILURE_THRESHOLD_PERCENT = 50.0;

    private final AttemptRepository attemptRepository;
    private final WorksheetRepository worksheetRepository;
    private final TopicStatsService topicStatsService;
    private final GamificationService gamificationService;

    public ReflectionService() {
        this(
                new AttemptRepository(),
                new WorksheetRepository(),
                new TopicStatsService(),
                new GamificationService());
    }

    public ReflectionService(
            AttemptRepository attemptRepository,
            WorksheetRepository worksheetRepository,
            TopicStatsService topicStatsService,
            GamificationService gamificationService) {
        this.attemptRepository = attemptRepository;
        this.worksheetRepository = worksheetRepository;
        this.topicStatsService = topicStatsService;
        this.gamificationService = gamificationService;
    }

    public GamificationResult completeReflection(
            Worksheet worksheet,
            WorksheetAttempt attempt,
            ConfidenceLevel confidenceAfter,
            String mainWeakness,
            String nextAction,
            String reflectionNotes)
            throws SQLException {

        if (worksheet == null) {
            throw new IllegalArgumentException("Worksheet cannot be null.");
        }

        if (attempt == null) {
            throw new IllegalArgumentException("Attempt cannot be null.");
        }
        return com.commonplace.repository.DatabaseManager.transaction(
                () -> {
                    WorksheetAttempt saved =
                            attemptRepository
                                    .findById(attempt.id())
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "Attempt is not available in this"
                                                                    + " account."));
                    if (saved.worksheetId() != worksheet.id())
                        throw new IllegalArgumentException(
                                "Attempt belongs to a different worksheet.");

                    ConfidenceLevel resolvedConfidence =
                            confidenceAfter == null ? ConfidenceLevel.MEDIUM : confidenceAfter;

                    attemptRepository.updateReflection(
                            attempt.id(),
                            resolvedConfidence,
                            mainWeakness,
                            nextAction,
                            reflectionNotes);

                    updateWorksheetStats(worksheet, attempt);
                    topicStatsService.updateTopicStats(worksheet.topicId(), resolvedConfidence);
                    return gamificationService.awardReflection(
                            attempt.id(), mainWeakness, nextAction, reflectionNotes);
                });
    }

    public void updateWorksheetStats(Worksheet worksheet, WorksheetAttempt latestAttempt)
            throws SQLException {

        List<WorksheetAttempt> attempts = attemptRepository.findByWorksheetId(worksheet.id());

        int timesAttempted = attempts.size();

        double averageScore =
                attempts.stream()
                        .mapToDouble(WorksheetAttempt::scorePercent)
                        .average()
                        .orElse(latestAttempt.scorePercent());

        var ordered =
                attempts.stream()
                        .sorted(
                                java.util.Comparator.comparing(WorksheetAttempt::completedAt)
                                        .thenComparingLong(WorksheetAttempt::id)
                                        .reversed())
                        .toList();
        if (ordered.isEmpty()) return;
        WorksheetAttempt newest = ordered.getFirst();
        int failureStreak = 0;
        for (var item : ordered) {
            if (item.scorePercent() >= FAILURE_THRESHOLD_PERCENT) break;
            failureStreak++;
        }

        worksheetRepository.updateStats(
                worksheet.id(),
                newest.completedAt(),
                timesAttempted,
                newest.scorePercent(),
                averageScore,
                failureStreak);
    }
}
