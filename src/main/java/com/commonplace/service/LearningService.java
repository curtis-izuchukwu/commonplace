package com.commonplace.service;

import com.commonplace.repository.DatabaseManager;
import com.commonplace.repository.LearningRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;

/** Calculates automatic mastery estimates without requiring syllabus setup or question mapping. */
public class LearningService {

    public record TopicProgress(double mastery, LearningModel.Estimate estimate) {}

    private final LearningRepository repository = new LearningRepository();

    public TopicProgress topic(long topicId) throws SQLException {
        return topic(topicId, LocalDate.now());
    }

    public TopicProgress topic(long topicId, LocalDate today) throws SQLException {
        LearningModel.Estimate estimate =
                LearningModel.estimate(repository.topicEvidence(topicId), today);
        return new TopicProgress(estimate.mastery(), estimate);
    }

    public LearningModel.Estimate worksheet(long worksheetId) throws SQLException {
        return worksheet(worksheetId, LocalDate.now());
    }

    public LearningModel.Estimate worksheet(long worksheetId, LocalDate today) throws SQLException {
        return LearningModel.estimate(repository.worksheetEvidence(worksheetId), today);
    }

    public void refreshAll() throws SQLException {
        for (long topicId : repository.topicIds()) {
            refresh(topicId);
        }
    }

    public void refresh(long topicId) throws SQLException {
        double mastery = topic(topicId).mastery();
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement stmt =
                        conn.prepareStatement("UPDATE topics SET mastery_score = ? WHERE id = ?")) {
            stmt.setDouble(1, mastery);
            stmt.setLong(2, topicId);
            stmt.executeUpdate();
        }
    }

    public double moduleMastery(long moduleId) throws SQLException {
        double total = 0;
        double totalWeight = 0;

        for (long topicId : repository.topicIds()) {
            LearningRepository.TopicInfo info = repository.topic(topicId);
            if (info.moduleId() != moduleId) {
                continue;
            }

            TopicProgress progress = topic(topicId);
            double importance =
                    switch (info.importance()) {
                        case "HIGH" -> 1.5;
                        case "LOW" -> .75;
                        default -> 1;
                    };
            // Available question material provides a bounded size proxy without rewarding attempts.
            double breadth = 1 + Math.min(2, repository.topicQuestionCount(topicId) / 12.0);
            double weight = importance * breadth;
            total += progress.mastery() * weight;
            totalWeight += weight;
        }

        return totalWeight == 0 ? 0 : total / totalWeight;
    }
}
