package com.pararepilot.repository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.pararepilot.model.Answer;
import com.pararepilot.model.ConfidenceLevel;
import com.pararepilot.model.DifficultyLevel;
import com.pararepilot.model.ImportanceLevel;
import com.pararepilot.model.Question;
import com.pararepilot.model.StudyModule;
import com.pararepilot.model.Topic;
import com.pararepilot.model.Worksheet;
import com.pararepilot.model.WorksheetAttempt;
import com.pararepilot.service.AttemptService;

class AttemptRepositoryTest {

    @Test
    void canSubmitAttemptAndSaveAnswers() throws Exception {
        ModuleRepository moduleRepository = new ModuleRepository();
        TopicRepository topicRepository = new TopicRepository();
        WorksheetRepository worksheetRepository = new WorksheetRepository();
        QuestionRepository questionRepository = new QuestionRepository();
        AnswerRepository answerRepository = new AnswerRepository();
        AttemptService attemptService = new AttemptService();

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
                "Practise inorder, preorder, and postorder traversal.",
                DifficultyLevel.MEDIUM,
                ImportanceLevel.HIGH
        );

        questionRepository.createMany(
                worksheet.id(),
                List.of(
                        new QuestionRepository.QuestionDraft(
                                "Explain inorder traversal.",
                                "Left subtree, root, right subtree.",
                                3,
                                "trees,traversal"
                        ),
                        new QuestionRepository.QuestionDraft(
                                "Why is deletion in a BST tricky?",
                                "The node may have zero, one, or two children.",
                                4,
                                "trees,deletion"
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
                        ),
                        new AnswerRepository.AnswerDraft(
                                questions.get(1).id(),
                                "You need to repair the tree links.",
                                2,
                                4,
                                true,
                                "Forgot inorder successor/predecessor."
                        )
                )
        );

        List<Answer> answers = answerRepository.findByAttemptId(attempt.id());

        assertTrue(attempt.id() > 0);
        assertEquals(5, attempt.score());
        assertEquals(7, attempt.maxScore());
        assertEquals((5.0 / 7.0) * 100.0, attempt.scorePercent(), 0.001);

        assertEquals(2, answers.size());
        assertEquals(3, answers.get(0).awardedMarks());
        assertEquals(2, answers.get(1).awardedMarks());
        assertTrue(answers.get(1).markedAsMistake());

        moduleRepository.deleteById(module.id());
    }
}