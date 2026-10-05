package com.commonplace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.UUID;

import com.commonplace.model.ConfidenceLevel;
import com.commonplace.model.DifficultyLevel;
import com.commonplace.model.ImportanceLevel;
import com.commonplace.model.StudyModule;
import com.commonplace.model.Topic;
import com.commonplace.model.User;
import com.commonplace.model.Worksheet;
import com.commonplace.repository.AnswerRepository;
import com.commonplace.repository.DatabaseManager;
import com.commonplace.repository.ModuleRepository;
import com.commonplace.repository.TopicRepository;
import com.commonplace.repository.UserRepository;
import com.commonplace.repository.WorksheetRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PdfWorksheetAttemptTest {

    private User user;
    private StudyModule module;
    private Worksheet worksheet;

    @BeforeEach
    void setUp() throws Exception {
        UserRepository users = new UserRepository();
        user = users.create("pdf-attempt-" + UUID.randomUUID(), "hash", "salt");
        users.ensureStatsRow(user.id());
        AccountSession.signIn(user);

        module =
                new ModuleRepository()
                        .create("Paper practice", "", null, ImportanceLevel.MEDIUM);
        Topic topic =
                new TopicRepository()
                        .create(
                                module.id(),
                                "Past papers",
                                "",
                                ImportanceLevel.HIGH,
                                ConfidenceLevel.MEDIUM);
        worksheet =
                new WorksheetRepository()
                        .createPdf(
                                topic.id(),
                                "Paper 1",
                                "",
                                DifficultyLevel.MEDIUM,
                                ImportanceLevel.HIGH,
                                "pdfs/paper.pdf",
                                "pdfs/mark-scheme.pdf");
    }

    @AfterEach
    void cleanUp() throws Exception {
        if (module != null) {
            new ModuleRepository().deleteById(module.id());
        }
        if (user != null) {
            try (Connection connection = DatabaseManager.connect();
                    PreparedStatement statement =
                            connection.prepareStatement("DELETE FROM users WHERE id = ?")) {
                statement.setLong(1, user.id());
                statement.executeUpdate();
            }
        }
        AccountSession.signOut();
    }

    @Test
    void storedOverallMarkCreatesAttemptWithoutAnswersAndAwardsXp() throws Exception {
        AttemptService.SubmissionResult result =
                new AttemptService()
                        .submitPdfScore(worksheet.id(), LocalDateTime.now().minusMinutes(20), 42, 60);

        assertEquals(42, result.attempt().score());
        assertEquals(60, result.attempt().maxScore());
        assertEquals(70, result.attempt().scorePercent());
        assertTrue(result.practiceReward().xpAwarded() > 0);
        assertTrue(new AnswerRepository().findByAttemptId(result.attempt().id()).isEmpty());

        Worksheet reloaded = new WorksheetRepository().findById(worksheet.id()).orElseThrow();
        assertEquals(1, reloaded.timesAttempted());
        assertEquals(70, reloaded.latestScorePercent());
    }
}
