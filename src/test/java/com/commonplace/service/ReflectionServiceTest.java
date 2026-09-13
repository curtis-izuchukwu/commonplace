package com.commonplace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.commonplace.model.ConfidenceLevel;
import com.commonplace.model.DifficultyLevel;
import com.commonplace.model.ImportanceLevel;
import com.commonplace.model.Question;
import com.commonplace.model.StudyModule;
import com.commonplace.model.Topic;
import com.commonplace.model.Worksheet;
import com.commonplace.model.WorksheetAttempt;
import com.commonplace.repository.AnswerRepository;
import com.commonplace.repository.ModuleRepository;
import com.commonplace.repository.QuestionRepository;
import com.commonplace.repository.TopicRepository;
import com.commonplace.repository.WorksheetRepository;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class ReflectionServiceTest {

    @Test
    void reflectionUpdatesWorksheetStatsAndTopicMastery() throws Exception {
        ModuleRepository moduleRepository = new ModuleRepository();
        TopicRepository topicRepository = new TopicRepository();
        WorksheetRepository worksheetRepository = new WorksheetRepository();
        QuestionRepository questionRepository = new QuestionRepository();

        AttemptService attemptService = new AttemptService();
        ReflectionService reflectionService = new ReflectionService();

        StudyModule module = null;

        try {
            module =
                    moduleRepository.create(
                            "Module " + UUID.randomUUID(),
                            "Temporary test module",
                            null,
                            ImportanceLevel.HIGH);

            Topic topic =
                    topicRepository.create(
                            module.id(),
                            "Binary Search Trees",
                            "Traversal, insertion, deletion",
                            ImportanceLevel.HIGH,
                            ConfidenceLevel.LOW);

            Worksheet worksheet =
                    worksheetRepository.create(
                            topic.id(),
                            "BST Traversal Practice",
                            "Practise traversal.",
                            DifficultyLevel.MEDIUM,
                            ImportanceLevel.HIGH);

            questionRepository.createMany(
                    worksheet.id(),
                    List.of(
                            new QuestionRepository.QuestionDraft(
                                    "Explain inorder traversal.",
                                    "Left, root, right.",
                                    3,
                                    "trees")));

            List<Question> questions = questionRepository.findByWorksheetId(worksheet.id());

            WorksheetAttempt attempt =
                    attemptService.submitAttempt(
                            worksheet.id(),
                            null,
                            List.of(
                                    new AnswerRepository.AnswerDraft(
                                            questions.get(0).id(),
                                            "Left, root, right.",
                                            3,
                                            3,
                                            false,
                                            null)));

            reflectionService.completeReflection(
                    worksheet,
                    attempt,
                    ConfidenceLevel.HIGH,
                    "No major weakness.",
                    "Continue to deletion cases.",
                    "Good attempt.");

            Worksheet updatedWorksheet = worksheetRepository.findById(worksheet.id()).orElseThrow();
            Topic updatedTopic = topicRepository.findById(topic.id()).orElseThrow();

            assertEquals(1, updatedWorksheet.timesAttempted());
            assertEquals(100.0, updatedWorksheet.latestScorePercent(), 0.001);
            assertEquals(100.0, updatedWorksheet.averageScorePercent(), 0.001);
            assertEquals(0, updatedWorksheet.failureStreak());
            assertTrue(updatedWorksheet.lastAttemptedAt() != null);

            assertEquals(ConfidenceLevel.HIGH, updatedTopic.confidence());
            assertTrue(updatedTopic.masteryScore() > 0 && updatedTopic.masteryScore() < 10);

        } finally {
            if (module != null) {
                moduleRepository.deleteById(module.id());
            }
        }
    }
}
