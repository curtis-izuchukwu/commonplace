package com.pararepilot.repository;

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
import com.pararepilot.service.AttemptService;

class MistakeRepositoryTest {

    @Test
    void createsMistakeRecordsFromMarkedAnswers() throws Exception {
        ModuleRepository moduleRepository = new ModuleRepository();
        TopicRepository topicRepository = new TopicRepository();
        WorksheetRepository worksheetRepository = new WorksheetRepository();
        QuestionRepository questionRepository = new QuestionRepository();
        MistakeRepository mistakeRepository = new MistakeRepository();
        AttemptService attemptService = new AttemptService();

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
                "Binary Search Trees",
                "Traversal, insertion, deletion",
                ImportanceLevel.HIGH,
                ConfidenceLevel.LOW
        );

        Worksheet worksheet = worksheetRepository.create(
                topic.id(),
                "BST Deletion Practice",
                "Practise deletion cases.",
                DifficultyLevel.HARD,
                ImportanceLevel.HIGH
        );

        questionRepository.createMany(
                worksheet.id(),
                List.of(
                        new QuestionRepository.QuestionDraft(
                                "Why is deleting a node with two children tricky?",
                                "Use inorder successor or predecessor, then repair links.",
                                4,
                                "trees,deletion"
                        ),
                        new QuestionRepository.QuestionDraft(
                                "Explain inorder traversal.",
                                "Left subtree, root, right subtree.",
                                3,
                                "trees,traversal"
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
                                "You need to reconnect the children.",
                                2,
                                4,
                                true,
                                "Forgot successor/predecessor."
                        ),
                        new AnswerRepository.AnswerDraft(
                                questions.get(1).id(),
                                "Left, root, right.",
                                3,
                                3,
                                false,
                                null
                        )
                )
        );

        List<MistakeRepository.MistakeDisplayItem> mistakes =
                mistakeRepository.findDisplayItemsByTopicId(topic.id());

        assertEquals(1, mistakes.size());
        assertEquals(attempt.id(), mistakes.get(0).attemptId());
        assertEquals(worksheet.id(), mistakes.get(0).worksheetId());
        assertEquals("Forgot successor/predecessor.", mistakes.get(0).mistakeNote());
        assertTrue(mistakeRepository.countUnresolvedByWorksheetId(worksheet.id()) >= 1);

        mistakeRepository.markResolved(mistakes.get(0).id(), true);

        assertEquals(0, mistakeRepository.countUnresolvedByWorksheetId(worksheet.id()));

        } finally {
            if (module != null) {
                moduleRepository.deleteById(module.id());
            }
        }
    }
}
