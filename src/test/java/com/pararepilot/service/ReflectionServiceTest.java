package com.pararepilot.service;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.pararepilot.model.ConfidenceLevel;
import com.pararepilot.model.DifficultyLevel;
import com.pararepilot.model.ImportanceLevel;
import com.pararepilot.model.Question;
import com.pararepilot.model.StudyModule;
import com.pararepilot.model.Topic;
import com.pararepilot.model.Worksheet;
import com.pararepilot.model.WorksheetAttempt;
import com.pararepilot.repository.AnswerRepository;
import com.pararepilot.repository.ModuleRepository;
import com.pararepilot.repository.QuestionRepository;
import com.pararepilot.repository.TopicRepository;
import com.pararepilot.repository.WorksheetRepository;

class ReflectionServiceTest {

    @Test
    void reflectionUpdatesWorksheetStatsAndTopicMastery() throws Exception {
        ModuleRepository moduleRepository = new ModuleRepository();
        TopicRepository topicRepository = new TopicRepository();
        WorksheetRepository worksheetRepository = new WorksheetRepository();
        QuestionRepository questionRepository = new QuestionRepository();

        AttemptService attemptService = new AttemptService();
        ReflectionService reflectionService = new ReflectionService();

        StudyModule module = moduleRepository.create(
                "Module " + UUID.randomUUID(),
                "Temporary test module",
                null,
                ImportanceLevel.HIGH
        );

        Topic topic = topicRepository.create(
                module.id(),
                "Binary Search Trees",
                "Traversal, insertion, deletion",
                ImportanceLevel.HIGH,
                ConfidenceLevel.LOW
        );

        Worksheet worksheet = worksheetRepository.create(
                topic.id(),
                "BST Traversal Practice",
                "Practise traversal.",
                DifficultyLevel.MEDIUM,
                ImportanceLevel.HIGH
        );

        questionRepository.createMany(
                worksheet.id(),
                List.of(
                        new QuestionRepository.QuestionDraft(
                                "Explain inorder traversal.",
                                "Left, root, right.",
                                3,
                                "trees"
                        )
                )
        );

        List<Question> questions = questionRepository.findByWorksheetId(worksheet.id());

        WorksheetAttempt attempt = attemptService.submitAttempt(
                worksheet.id(),
                null,
                List.of(
                        new AnswerRepository.AnswerDraft(
                                questions.get(0).id(),
                                "Left, root, right.",
                                3,
                                3,
                                false,
                                null
                        )
                )
        );

        reflectionService.completeReflection(
                worksheet,
                attempt,
                ConfidenceLevel.HIGH,
                "No major weakness.",
                "Continue to deletion cases.",
                "Good attempt."
        );

        Worksheet updatedWorksheet = worksheetRepository.findById(worksheet.id()).orElseThrow();
        Topic updatedTopic = topicRepository.findById(topic.id()).orElseThrow();

        assertEquals(1, updatedWorksheet.timesAttempted());
        assertEquals(100.0, updatedWorksheet.latestScorePercent(), 0.001);
        assertEquals(100.0, updatedWorksheet.averageScorePercent(), 0.001);
        assertEquals(0, updatedWorksheet.failureStreak());
        assertTrue(updatedWorksheet.lastAttemptedAt() != null);

        assertEquals(ConfidenceLevel.HIGH, updatedTopic.confidence());
        assertEquals(97.0, updatedTopic.masteryScore(), 0.001);

        moduleRepository.deleteById(module.id());
    }
}