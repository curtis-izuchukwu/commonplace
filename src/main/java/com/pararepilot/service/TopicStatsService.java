package com.pararepilot.service;

import java.sql.SQLException;
import java.util.List;

import com.pararepilot.model.ConfidenceLevel;
import com.pararepilot.model.Worksheet;
import com.pararepilot.model.WorksheetAttempt;
import com.pararepilot.repository.AttemptRepository;
import com.pararepilot.repository.TopicRepository;
import com.pararepilot.repository.WorksheetRepository;

public class TopicStatsService {

    private static final int RECENT_ATTEMPT_LIMIT = 5;

    private final WorksheetRepository worksheetRepository;
    private final AttemptRepository attemptRepository;
    private final TopicRepository topicRepository;

    public TopicStatsService() {
        this(new WorksheetRepository(), new AttemptRepository(), new TopicRepository());
    }

    public TopicStatsService(
            WorksheetRepository worksheetRepository,
            AttemptRepository attemptRepository,
            TopicRepository topicRepository
    ) {
        this.worksheetRepository = worksheetRepository;
        this.attemptRepository = attemptRepository;
        this.topicRepository = topicRepository;
    }

    public double calculateMasteryScore(long topicId, ConfidenceLevel confidence)
            throws SQLException {

        List<Worksheet> worksheets = worksheetRepository.findByTopicId(topicId);

        if (worksheets.isEmpty()) {
            return clamp(confidenceScore(confidence));
        }

        List<Long> worksheetIds = worksheets.stream()
                .map(Worksheet::id)
                .toList();

        List<WorksheetAttempt> recentAttempts = attemptRepository.findRecentByWorksheetIds(
                worksheetIds,
                RECENT_ATTEMPT_LIMIT
        );

        if (recentAttempts.isEmpty()) {
            return clamp(confidenceScore(confidence));
        }

        double recentAverageScore = recentAttempts.stream()
                .mapToDouble(WorksheetAttempt::scorePercent)
                .average()
                .orElse(0);

        double mastery = (recentAverageScore * 0.7)
                + (confidenceScore(confidence) * 0.3);

        return clamp(mastery);
    }

    public void updateTopicStats(long topicId, ConfidenceLevel confidence)
            throws SQLException {

        double masteryScore = calculateMasteryScore(topicId, confidence);
        topicRepository.updateStats(topicId, confidence, masteryScore);
    }

    private double confidenceScore(ConfidenceLevel confidence) {
        if (confidence == null) {
            return 65;
        }

        return switch (confidence) {
            case LOW -> 30;
            case MEDIUM -> 65;
            case HIGH -> 90;
        };
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }
}