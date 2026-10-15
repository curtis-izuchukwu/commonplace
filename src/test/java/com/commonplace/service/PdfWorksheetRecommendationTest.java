package com.commonplace.service;

import static org.junit.jupiter.api.Assertions.*;

import com.commonplace.model.*;
import com.commonplace.repository.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

class PdfWorksheetRecommendationTest {
    private User user;
    private StudyModule module;
    private Topic topic;
    private final WorksheetRepository worksheets = new WorksheetRepository();
    private final WorksheetSelectionService selection = new WorksheetSelectionService();
    private final LearningRepository learning = new LearningRepository();

    @BeforeEach
    void setUp() throws Exception {
        user = new UserRepository().create("pdf-recommendation-" + UUID.randomUUID(), "hash", "salt");
        AccountSession.signIn(user);
        module =
                new ModuleRepository()
                        .create("Practice", "", LocalDate.now().plusDays(7), ImportanceLevel.HIGH);
        topic =
                new TopicRepository()
                        .create(
                                module.id(), "Revision", "", ImportanceLevel.HIGH,
                                ConfidenceLevel.LOW);
    }

    @AfterEach
    void cleanUp() throws Exception {
        if (user != null) {
            try (Connection conn = DatabaseManager.connect();
                    PreparedStatement stmt =
                            conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
                stmt.setLong(1, user.id());
                stmt.executeUpdate();
            }
        }
        AccountSession.signOut();
    }

    @Test
    void freshPdfSharesRankingAndRefreshHistoryWithQuestionWorksheets() throws Exception {
        Worksheet pdf = pdf("Paper", DifficultyLevel.MEDIUM);
        Worksheet normal = normal("Questions", DifficultyLevel.MEDIUM);
        worksheets.create(
                topic.id(), "Empty draft", "", DifficultyLevel.MEDIUM, ImportanceLevel.HIGH);

        var priorities = selection.previewPriorities();
        assertEquals(Set.of(pdf.id(), normal.id()), worksheetIds(priorities));
        assertEquals(priorities.getFirst().priorityScore(), priorities.getLast().priorityScore());
        assertEquals(priorities.getFirst().explanation(), priorities.getLast().explanation());

        var first = selection.recommendWorksheet().orElseThrow();
        assertEquals(first.key(), selection.recommendWorksheet().orElseThrow().key());
        var second = selection.pickAnotherRecommendation().orElseThrow();
        assertNotEquals(first.key(), second.key());
        assertEquals(Set.of(pdf.id(), normal.id()), worksheetIds(List.of(first, second)));
        assertEquals(
                second.worksheet().id(),
                new DailyRecommendationRepository().findTodayWorksheetId().orElseThrow());
        assertEquals(first.key(), selection.pickAnotherRecommendation().orElseThrow().key());
    }

    @Test
    void overallMarksUseTheSameLearningModelWithoutDoubleCountingQuestionAnswers() throws Exception {
        Worksheet pdf = pdf("Paper", DifficultyLevel.MEDIUM);
        Worksheet normal = normal("Questions", DifficultyLevel.MEDIUM);
        LocalDateTime completedAt = LocalDate.now().minusDays(1).atTime(12, 0);
        score(pdf, completedAt, 4, 5);
        WorksheetAttempt attempt = score(normal, completedAt, 4, 5);
        long questionId = new QuestionRepository().findByWorksheetId(normal.id()).getFirst().id();
        new AnswerRepository()
                .createMany(
                        attempt.id(),
                        List.of(
                                new AnswerRepository.AnswerDraft(
                                        questionId, "Answer", 4, 5, false, null)));

        assertEquals(1, learning.worksheetEvidence(pdf.id()).size());
        assertEquals(1, learning.worksheetEvidence(normal.id()).size());
        assertEquals(2, learning.topicEvidence(topic.id()).size());
        assertEquals(
                new LearningService().worksheet(normal.id()),
                new LearningService().worksheet(pdf.id()));
        var priorities = selection.previewPriorities();
        assertEquals(priorities.getFirst().priorityScore(), priorities.getLast().priorityScore());
    }

    @Test
    void pdfPerformanceAndReviewDatesAffectTheSharedRanking() throws Exception {
        Worksheet strong = pdf("Strong paper", DifficultyLevel.MEDIUM);
        Worksheet weak = pdf("Weak paper", DifficultyLevel.MEDIUM);
        LocalDateTime completedAt = LocalDate.now().minusDays(1).atTime(12, 0);
        score(strong, completedAt, 9, 10);
        score(weak, completedAt, 2, 10);

        var strongEstimate = new LearningService().worksheet(strong.id());
        var weakEstimate = new LearningService().worksheet(weak.id());
        assertEquals(90, strongEstimate.performance(), .001);
        assertEquals(20, weakEstimate.performance(), .001);
        assertEquals(completedAt, weakEstimate.lastAt());
        assertTrue(strongEstimate.dueAt().isAfter(weakEstimate.dueAt()));
        assertTrue(weakEstimate.weakness() > strongEstimate.weakness());
        var priorities = selection.previewPriorities();
        assertEquals(weak.id(), priorities.getFirst().worksheet().id());
        assertTrue(priorities.getFirst().priorityScore() > priorities.getLast().priorityScore());
        assertTrue(priorities.getFirst().explanation().contains("Review is due"));
    }

    @Test
    void pdfCompletionUsesTheSameDailyExclusionAndGoalWindow() throws Exception {
        Worksheet pdf = pdf("Paper", DifficultyLevel.MEDIUM);
        Worksheet normal = normal("Questions", DifficultyLevel.MEDIUM);
        new AttemptService().submitPdfScore(pdf.id(), null, 7, 10);

        assertEquals(normal.id(), selection.recommendWorksheet().orElseThrow().worksheet().id());
        assertEquals(
                normal.id(), selection.pickAnotherRecommendation().orElseThrow().worksheet().id());
        var dashboard = new DashboardService().loadDashboard();
        assertEquals(1, dashboard.completedWorksheetsToday());
        assertTrue(dashboard.worksheetWindowLocked());
        assertTrue(dashboard.recommendation().isEmpty());
    }

    @Test
    void difficultyAndArchivedModuleFiltersApplyToBothFormats() throws Exception {
        pdf("Easy paper", DifficultyLevel.EASY);
        normal("Easy questions", DifficultyLevel.EASY);
        Worksheet mediumPdf = pdf("Medium paper", DifficultyLevel.MEDIUM);
        Worksheet mediumNormal = normal("Medium questions", DifficultyLevel.MEDIUM);
        pdf("Hard paper", DifficultyLevel.HARD);
        normal("Hard questions", DifficultyLevel.HARD);
        new UserSettingsService().load();
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "UPDATE user_settings SET preferred_min_difficulty = 'MEDIUM',"
                                        + " preferred_max_difficulty = 'MEDIUM',"
                                        + " archive_completed_modules = 1 WHERE user_id = ?")) {
            stmt.setLong(1, user.id());
            stmt.executeUpdate();
        }
        assertEquals(
                Set.of(mediumPdf.id(), mediumNormal.id()),
                worksheetIds(selection.previewPriorities()));

        try (Connection conn = DatabaseManager.connect();
                PreparedStatement stmt =
                        conn.prepareStatement("UPDATE modules SET exam_date = ? WHERE id = ?")) {
            stmt.setString(1, LocalDate.now().minusDays(1).toString());
            stmt.setLong(2, module.id());
            stmt.executeUpdate();
        }
        assertTrue(selection.previewPriorities().isEmpty());
    }

    @Test
    void pdfEvidenceAndRecommendationsStayWithinTheSignedInAccount() throws Exception {
        Worksheet pdf = pdf("Private paper", DifficultyLevel.MEDIUM);
        score(pdf, LocalDate.now().minusDays(1).atTime(12, 0), 7, 10);
        User other = new UserRepository().create("other-pdf-" + UUID.randomUUID(), "hash", "salt");
        AccountSession.signIn(other);
        try {
            assertTrue(selection.previewPriorities().isEmpty());
            assertThrows(IllegalArgumentException.class, () -> learning.worksheetEvidence(pdf.id()));
            assertThrows(IllegalArgumentException.class, () -> learning.topicEvidence(topic.id()));
        } finally {
            AccountSession.signIn(user);
            try (Connection conn = DatabaseManager.connect();
                    PreparedStatement stmt =
                            conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
                stmt.setLong(1, other.id());
                stmt.executeUpdate();
            }
        }
    }

    private Worksheet pdf(String title, DifficultyLevel difficulty) throws Exception {
        return worksheets.createPdf(
                topic.id(), title, "", difficulty, ImportanceLevel.HIGH, "pdfs/paper.pdf", null);
    }

    private Worksheet normal(String title, DifficultyLevel difficulty) throws Exception {
        Worksheet worksheet =
                worksheets.create(topic.id(), title, "", difficulty, ImportanceLevel.HIGH);
        new QuestionRepository()
                .createMany(
                        worksheet.id(),
                        List.of(
                                new QuestionRepository.QuestionDraft(
                                        title + " question", "Answer", 5, null)));
        return worksheet;
    }

    private WorksheetAttempt score(
            Worksheet worksheet, LocalDateTime completedAt, int score, int maxScore)
            throws Exception {
        return new AttemptRepository()
                .create(
                        worksheet.id(),
                        completedAt.minusMinutes(20),
                        completedAt,
                        score,
                        maxScore,
                        100.0 * score / maxScore,
                        ConfidenceLevel.MEDIUM,
                        null,
                        null,
                        null);
    }

    private Set<Long> worksheetIds(List<WorksheetRecommendation> recommendations) {
        return recommendations.stream().map(item -> item.worksheet().id()).collect(Collectors.toSet());
    }
}
