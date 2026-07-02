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

class WorksheetRepositoryTest {

    @Test
    void canCreateWorksheetWithQuestions() throws Exception {
        ModuleRepository moduleRepository = new ModuleRepository();
        TopicRepository topicRepository = new TopicRepository();
        WorksheetRepository worksheetRepository = new WorksheetRepository();
        QuestionRepository questionRepository = new QuestionRepository();

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

        List<Worksheet> worksheets = worksheetRepository.findByTopicId(topic.id());
        List<Question> questions = questionRepository.findByWorksheetId(worksheet.id());

        assertEquals(1, worksheets.size());
        assertEquals("BST Traversal Practice", worksheets.get(0).title());

        assertEquals(2, questions.size());
        assertEquals(1, questions.get(0).questionOrder());
        assertEquals(2, questions.get(1).questionOrder());

        assertTrue(worksheetRepository.countByTopicId(topic.id()) >= 1);

        } finally {
            if (module != null) {
                moduleRepository.deleteById(module.id());
            }
        }
    }
}
