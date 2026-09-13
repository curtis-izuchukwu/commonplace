package com.commonplace.service;

import com.commonplace.model.ConfidenceLevel;
import com.commonplace.repository.*;

import java.sql.SQLException;

public class TopicStatsService {
    public TopicStatsService() {}

    public TopicStatsService(
            WorksheetRepository worksheets, AttemptRepository attempts, TopicRepository topics) {}

    public double calculateMasteryScore(long topicId, ConfidenceLevel confidence)
            throws SQLException {
        return new LearningService().topic(topicId).mastery();
    }

    public void updateTopicStats(long topicId, ConfidenceLevel confidence) throws SQLException {
        double mastery = new LearningService().topic(topicId).mastery();
        new TopicRepository()
                .updateStats(
                        topicId, confidence == null ? ConfidenceLevel.MEDIUM : confidence, mastery);
    }
}
