package com.commonplace.service;

import static org.junit.jupiter.api.Assertions.*;

import com.commonplace.model.*;
import com.commonplace.repository.*;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.time.*;
import java.util.*;

class LearningFlowTest {
    User user;
    StudyModule module;
    Topic topic;
    final LearningRepository learning = new LearningRepository();

    @BeforeEach
    void setup() throws Exception {
        user = new UserRepository().create("learning-" + UUID.randomUUID(), "hash", "salt");
        AccountSession.signIn(user);
        module =
                new ModuleRepository()
                        .create(
                                "Algorithms",
                                "",
                                LocalDate.now().plusDays(7),
                                ImportanceLevel.HIGH);
        topic =
                new TopicRepository()
                        .create(module.id(), "BSTs", "", ImportanceLevel.HIGH, ConfidenceLevel.LOW);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (module != null) new ModuleRepository().deleteById(module.id());
        if (user != null)
            try (Connection c = DatabaseManager.connect()) {
                try (PreparedStatement s =
                        c.prepareStatement("DELETE FROM user_stats WHERE user_id=?")) {
                    s.setLong(1, user.id());
                    s.executeUpdate();
                }
                try (PreparedStatement s = c.prepareStatement("DELETE FROM users WHERE id=?")) {
                    s.setLong(1, user.id());
                    s.executeUpdate();
                }
            }
        AccountSession.signOut();
    }

    Worksheet worksheet(String title, String scope, int count, int marks) throws Exception {
        List<QuestionRepository.QuestionDraft> questions = new ArrayList<>();
        for (int i = 0; i < count; i++)
            questions.add(
                    new QuestionRepository.QuestionDraft(
                            title + " explain case " + i,
                            "Reference answer",
                            marks,
                            "press,short-answer",
                            null,
                            DifficultyLevel.MEDIUM));
        return new WorksheetCreationService()
                .createWorksheetWithQuestions(
                        topic.id(),
                        title,
                        "",
                        DifficultyLevel.MEDIUM,
                        ImportanceLevel.MEDIUM,
                        questions,
                        "Computer Science",
                        scope);
    }

    WorksheetAttempt submit(Worksheet w, int awarded, boolean assisted) throws Exception {
        return submitWithReward(w, awarded, assisted).attempt();
    }

    AttemptService.SubmissionResult submitWithReward(
            Worksheet w, int awarded, boolean assisted) throws Exception {
        var drafts =
                new QuestionRepository()
                        .findByWorksheetId(w.id()).stream()
                                .map(
                                        q ->
                                                new AnswerRepository.AnswerDraft(
                                                        q.id(),
                                                        "An original answer to the question",
                                                        Math.min(awarded, q.maxMarks()),
                                                        q.maxMarks(),
                                                        awarded < q.maxMarks(),
                                                        null,
                                                        assisted,
                                                        90,
                                                        true))
                                .toList();
        return new AttemptService()
                .submitAttemptWithReward(w.id(), LocalDateTime.now().minusMinutes(5), drafts);
    }

    int xp() throws Exception {
        return new UserStatsRepository().find().xp();
    }

    @Test
    void avlWorksheetDoesNotMasterWholeBstTopicAndKeepsSpecificScope() throws Exception {
        Worksheet w = worksheet("AVL rotations", "AVL rotations", 3, 3);
        submit(w, 3, false);
        var p = new LearningService().topic(topic.id());
        assertTrue(p.mastery() > 0 && p.mastery() < 20);
        assertEquals(3, p.estimate().uniqueQuestions());
        assertEquals(1, p.estimate().uniqueWorksheets());
        assertEquals(1, p.estimate().uniqueScopes());
        assertEquals(
                ConfidenceLevel.LOW,
                new TopicRepository().findById(topic.id()).orElseThrow().confidence());
        try (Connection c = DatabaseManager.connect();
                PreparedStatement s =
                        c.prepareStatement(
                                "SELECT study_scope,generation_subject FROM worksheets WHERE"
                                        + " id=?")) {
            s.setLong(1, w.id());
            try (ResultSet r = s.executeQuery()) {
                assertTrue(r.next());
                assertEquals("AVL rotations", r.getString(1));
                assertEquals("Computer Science", r.getString(2));
            }
        }
    }

    @Test
    void variedWorksheetsAndStudyScopesBuildMoreEvidenceWithoutManualSetup() throws Exception {
        var w = worksheet("Rotations", "Rotations", 6, 5);
        submit(w, 5, false);
        var first = new LearningService().topic(topic.id());
        submit(worksheet("Traversal", "Tree traversal", 6, 5), 5, false);
        var varied = new LearningService().topic(topic.id());
        assertTrue(varied.mastery() > first.mastery());
        assertEquals(2, varied.estimate().uniqueWorksheets());
        assertEquals(2, varied.estimate().uniqueScopes());
    }

    @Test
    void deletingPracticeImmediatelyRemovesItsMasteryEvidence() throws Exception {
        Worksheet worksheet = worksheet("Temporary practice", "AVL rotations", 4, 3);
        submit(worksheet, 3, false);
        assertTrue(new LearningService().topic(topic.id()).mastery() > 0);

        new WorksheetCreationService().deleteWorksheet(worksheet.id());

        assertEquals(0, new LearningService().topic(topic.id()).mastery(), .001);
        assertEquals(
                0, new TopicRepository().findById(topic.id()).orElseThrow().masteryScore(), .001);
    }

    @Test
    void completionUpdatesAllStatsWithoutReflectionAndRepeatedSubmissionDoesNotFarmXp()
            throws Exception {
        var w = worksheet("Rotation practice", "Rotations", 3, 3);
        var submission = submitWithReward(w, 3, false);
        var a = submission.attempt();
        int earned = xp();
        assertTrue(earned > 0 && earned < 100);
        assertEquals(earned, submission.practiceReward().xpAwarded());
        assertEquals(1, new WorksheetRepository().findById(w.id()).orElseThrow().timesAttempted());
        assertTrue(new TopicRepository().findById(topic.id()).orElseThrow().masteryScore() > 0);
        assertEquals(1, new UserStatsRepository().find().streakCount());
        assertEquals(0, new GamificationService().awardWorksheetCompletion(a).xpAwarded());
        assertEquals(earned, xp());
        submit(w, 3, false);
        assertEquals(earned, xp());
        assertEquals(1, new AttemptRepository().countCompletedOn(LocalDate.now()));
        var clone = worksheet("Rotation practice", "Rotations", 3, 3);
        submit(clone, 3, false);
        assertEquals(earned, xp());
    }

    @Test
    void emptyOrSkippedReflectionEarnsNothingAndMeaningfulReflectionPaysOnlyOnce()
            throws Exception {
        var w = worksheet("Review", "Rotations", 2, 3);
        var a = submit(w, 3, false);
        int before = xp();
        var service = new ReflectionService();
        service.completeReflection(w, a, ConfidenceLevel.HIGH, null, null, "Reflection skipped.");
        assertEquals(before, xp());
        service.completeReflection(
                w,
                a,
                ConfidenceLevel.HIGH,
                "I mixed up the two rotation directions.",
                "Practise left rotation diagrams tomorrow.",
                "");
        assertEquals(before + 5, xp());
        service.completeReflection(
                w,
                a,
                ConfidenceLevel.HIGH,
                "I mixed up the two rotation directions.",
                "Practise left rotation diagrams tomorrow.",
                "");
        assertEquals(before + 5, xp());
        assertEquals(1, new WorksheetRepository().findById(w.id()).orElseThrow().timesAttempted());
        assertEquals(
                ConfidenceLevel.HIGH,
                new TopicRepository().findById(topic.id()).orElseThrow().confidence());
    }

    @Test
    void xpScalesWithWorkAndAssistance() throws Exception {
        int start = xp();
        submit(worksheet("Short", "Rotations", 1, 1), 1, false);
        int small = xp() - start;
        start = xp();
        submit(worksheet("Long", "Rotations", 6, 5), 5, false);
        int large = xp() - start;
        assertTrue(large > small);
        start = xp();
        submit(worksheet("Assisted", "Rotations", 6, 5), 5, true);
        assertTrue(xp() - start < large);
    }

    @Test
    void rewardFailureRollsBackAttemptAnswersStatsAndRewardMarker() throws Exception {
        var w = worksheet("Atomic", "Rotations", 3, 3);
        xp();
        try (Connection c = DatabaseManager.connect();
                Statement s = c.createStatement()) {
            s.executeUpdate(
                    "CREATE TRIGGER fail_learning_xp BEFORE UPDATE OF xp ON user_stats WHEN"
                            + " NEW.user_id="
                            + user.id()
                            + " BEGIN SELECT RAISE(ABORT,'injected reward failure'); END");
        }
        try {
            assertThrows(SQLException.class, () -> submit(w, 3, false));
            assertEquals(0, new AttemptRepository().findByWorksheetId(w.id()).size());
            assertEquals(
                    0, new WorksheetRepository().findById(w.id()).orElseThrow().timesAttempted());
            assertEquals(0, xp());
        } finally {
            try (Connection c = DatabaseManager.connect();
                    Statement s = c.createStatement()) {
                s.executeUpdate("DROP TRIGGER fail_learning_xp");
            }
        }
        submit(w, 3, false);
        assertTrue(xp() > 0);
    }

    @Test
    void recommendationResetPreservesStreakAndCompletionDate() throws Exception {
        submit(worksheet("Reset", "Rotations", 2, 3), 3, false);
        var repo = new UserStatsRepository();
        var before = repo.find();
        new WorksheetSelectionService().recommendWorksheet();
        repo.resetTodaysRecommendationWindow();
        assertEquals(before, repo.find());
    }

    @Test
    void recommendationsNeverInventTargetedPracticeActions() throws Exception {
        var recommendations = new WorksheetSelectionService().previewPriorities();
        assertTrue(recommendations.isEmpty());

        submit(worksheet("AVL practice", "AVL rotations", 3, 3), 3, false);
        recommendations = new WorksheetSelectionService().previewPriorities();
        assertEquals(1, recommendations.size());
        assertEquals("AVL practice", recommendations.getFirst().worksheet().title());
        for (var r : recommendations) {
            assertTrue(r.priorityScore() >= 1 && r.priorityScore() <= 100);
            assertFalse(r.explanation().isBlank());
            assertFalse(r.explanation().contains("/100"));
        }
    }

    @Test
    void worksheetScopesStayAutomaticWithoutClutteringRecommendationCopy() throws Exception {
        var search = worksheet("Search foundations", "Search", 3, 3);
        var deletion = worksheet("Deletion cases", "Deletion", 3, 3);

        var recommendations = new WorksheetSelectionService().previewPriorities();
        assertEquals("Search", learning.worksheetScope(search.id()));
        assertEquals("Deletion", learning.worksheetScope(deletion.id()));
        assertTrue(recommendations.stream().anyMatch(r -> r.worksheet().id() == search.id()));
        assertTrue(recommendations.stream().anyMatch(r -> r.worksheet().id() == deletion.id()));
    }

    @Test
    void meetingDailyGoalShowsCompletionWindowUntilTheNextRefresh() throws Exception {
        var w = worksheet("Finished", "Rotations", 2, 3);
        worksheet("Next", "Traversal", 3, 3);
        submit(w, 3, false);
        var dashboard = new DashboardService().loadDashboard();
        assertTrue(dashboard.worksheetWindowLocked());
        assertTrue(dashboard.recommendation().isEmpty());
    }

    @Test
    void noEligibleWorksheetsKeepsTheTrueEmptyStateEvenAfterCompletion() throws Exception {
        var w = worksheet("Temporary", "Rotations", 2, 3);
        submit(w, 3, false);
        new WorksheetCreationService().deleteWorksheet(w.id());

        var dashboard = new DashboardService().loadDashboard();
        assertFalse(dashboard.worksheetWindowLocked());
        assertTrue(dashboard.recommendation().isEmpty());
    }

    @Test
    void mistakeRecallRequiresAnswerAndCannotBeFarmedByClicksOrRepeats() throws Exception {
        var w = worksheet("Mistake", "Rotations", 1, 3);
        submit(w, 0, false);
        var bank = new MistakeBankService();
        var m = bank.getMistakesForTopic(topic.id()).getFirst();
        int before = xp();
        bank.markRevisited(m.id());
        bank.markRevisited(m.id());
        assertEquals(before, xp());
        assertThrows(IllegalArgumentException.class, () -> bank.review(m.id(), "", true, false));
        bank.review(m.id(), "Recalled answer", true, false);
        assertEquals(before + 10, xp());
        bank.review(m.id(), "Recalled answer again", true, false);
        assertEquals(before + 10, xp());
        bank.review(m.id(), "Needed the hint", true, true);
        assertEquals(before + 10, xp());
        assertTrue(bank.getMistakesForTopic(topic.id()).getFirst().resolved());
    }

    @Test
    void anotherAccountCannotReadOrMutateLearningEvidence() throws Exception {
        var w = worksheet("Private", "Rotations", 1, 3);
        long questionId = new QuestionRepository().findByWorksheetId(w.id()).getFirst().id();
        User other = new UserRepository().create("other-" + UUID.randomUUID(), "hash", "salt");
        AccountSession.signIn(other);
        try {
            assertThrows(IllegalArgumentException.class, () -> learning.topic(topic.id()));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> learning.setQuestionDifficulty(questionId, DifficultyLevel.HARD));
            assertThrows(IllegalArgumentException.class, () -> submit(w, 3, false));
        } finally {
            AccountSession.signIn(user);
            try (Connection c = DatabaseManager.connect();
                    PreparedStatement s = c.prepareStatement("DELETE FROM users WHERE id=?")) {
                s.setLong(1, other.id());
                s.executeUpdate();
            }
        }
    }

    @Test
    void pointsAreCappedPerDay() throws Exception {
        for (int i = 0; i < 5; i++)
            submit(worksheet("Unique material " + i, "Rotations", 8, 5), 5, false);
        assertEquals(250, xp());
    }
}
