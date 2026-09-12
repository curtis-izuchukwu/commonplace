package com.commonplace.service;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.commonplace.model.ConfidenceLevel;
import com.commonplace.model.DifficultyLevel;
import com.commonplace.model.ImportanceLevel;
import com.commonplace.model.Question;
import com.commonplace.model.StudyModule;
import com.commonplace.model.Topic;
import com.commonplace.model.Worksheet;
import com.commonplace.repository.AnswerRepository;
import com.commonplace.repository.ModuleRepository;
import com.commonplace.repository.QuestionRepository;
import com.commonplace.repository.TopicRepository;
import com.commonplace.repository.WorksheetRepository;

class TopicStatsServiceTest {

    @Test
    void masteryUsesRecentScoreAndConfidence() throws Exception {
        ModuleRepository moduleRepository = new ModuleRepository();
        TopicRepository topicRepository = new TopicRepository();
        WorksheetRepository worksheetRepository = new WorksheetRepository();
        QuestionRepository questionRepository = new QuestionRepository();

        AttemptService attemptService = new AttemptService();
        TopicStatsService topicStatsService = new TopicStatsService();

        StudyModule module = null;

        try {
            module = moduleRepository.create(
                    "Module " + UUID.randomUUID(),
                    "Temporary test module",
                    null,
                    ImportanceLevel.HIGH
            );

        Topic topic = topicRepository.create(
                module.id(),
                "Dynamic Programming",
                "Memoisation and tabulation.",
                ImportanceLevel.HIGH,
                ConfidenceLevel.MEDIUM
        );

        Worksheet worksheet = worksheetRepository.create(
                topic.id(),
                "DP Basics",
                "Practice recurrence explanations.",
                DifficultyLevel.MEDIUM,
                ImportanceLevel.HIGH
        );

        questionRepository.createMany(
                worksheet.id(),
                List.of(
                        new QuestionRepository.QuestionDraft(
                                "What is memoisation?",
                                "Caching recursive results.",
                                10,
                                "dp"
                        )
                )
        );

        Question question = questionRepository.findByWorksheetId(worksheet.id()).get(0);

        attemptService.submitAttempt(
                worksheet.id(),
                null,
                List.of(
                        new AnswerRepository.AnswerDraft(
                                question.id(),
                                "Caching results.",
                                8,
                                10,
                                false,
                                null
                        )
                )
        );

        double mastery = topicStatsService.calculateMasteryScore(
                topic.id(),
                ConfidenceLevel.MEDIUM
        );

        // 80 * 0.7 + 65 * 0.3 = 75.5
        assertEquals(75.5, mastery, 0.001);

        } finally {
            if (module != null) {
                moduleRepository.deleteById(module.id());
            }
        }
    }
}
