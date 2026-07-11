package com.pararepilot.service;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.pararepilot.model.ConfidenceLevel;
import com.pararepilot.model.DifficultyLevel;
import com.pararepilot.model.ImportanceLevel;
import com.pararepilot.model.Question;
import com.pararepilot.model.StudyModule;
import com.pararepilot.model.Topic;
import com.pararepilot.model.Worksheet;
import com.pararepilot.repository.AnswerRepository;
import com.pararepilot.repository.ModuleRepository;
import com.pararepilot.repository.QuestionRepository;
import com.pararepilot.repository.TopicRepository;
import com.pararepilot.repository.WorksheetRepository;

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
