package com.commonplace.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import com.commonplace.model.*;
import com.commonplace.press.PressGenerateResponse;
import com.commonplace.repository.*;
import org.junit.jupiter.api.Test;

class PressWorksheetIntegrationTest {
    @Test
    void pressQuestionsCanBeSavedReloadedAndAttemptedOffline() throws Exception {
        var modules = new ModuleRepository();
        StudyModule module = modules.create("Press integration " + UUID.randomUUID(), "Test fixture", null,
                ImportanceLevel.MEDIUM);
        try {
            var topic = new TopicRepository().create(module.id(), "Recursion", "", ImportanceLevel.MEDIUM,
                    ConfidenceLevel.LOW);
            var response = PressGenerateResponse.fromJson("""
                    {"questions":[{"type":"short-answer","question":"Define recursion.",
                     "answer":"A function calls itself.","markScheme":["Self-call","Base case"],"marks":2},
                     {"type":"long-answer","question":"Explain a base case.","answer":"It terminates recursion.",
                      "markScheme":["Termination","Example"],"marks":8}]}
                    """);
            var drafts = response.questions().stream().map(question -> question.toQuestionDraft()).toList();
            var creation = new WorksheetCreationService();
            var worksheet = creation.createWorksheetWithQuestions(topic.id(), "Recursion practice",
                    "Generated with Press", DifficultyLevel.HARD, ImportanceLevel.HIGH, drafts);
            assertTrue(creation.getWorksheetsForTopic(topic.id()).stream().anyMatch(item -> item.id() == worksheet.id()));
            var saved = creation.getQuestionsForWorksheet(worksheet.id());
            assertEquals(2, saved.size());
            for (int i = 0; i < saved.size(); i++) {
                assertEquals(drafts.get(i).prompt(), saved.get(i).prompt());
                assertEquals(drafts.get(i).markScheme(), saved.get(i).markScheme());
                assertEquals(drafts.get(i).maxMarks(), saved.get(i).maxMarks());
                assertEquals(drafts.get(i).tags(), saved.get(i).tags());
                assertEquals(i + 1, saved.get(i).questionOrder());
            }
            var answers = saved.stream().map(question -> new AnswerRepository.AnswerDraft(
                    question.id(), "My reviewed answer", question.maxMarks(), question.maxMarks(), false, null)).toList();
            var attempt = new AttemptService().submitAttempt(worksheet.id(), null, answers);
            assertEquals(10, attempt.maxScore());
            assertEquals(100.0, attempt.scorePercent());
            assertEquals(1, new AttemptRepository().findByWorksheetId(worksheet.id()).size());
        } finally {
            modules.deleteById(module.id());
        }
    }
}
