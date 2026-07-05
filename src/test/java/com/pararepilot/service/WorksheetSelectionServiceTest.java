package com.pararepilot.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.pararepilot.model.ConfidenceLevel;
import com.pararepilot.model.DifficultyLevel;
import com.pararepilot.model.ImportanceLevel;
import com.pararepilot.model.StudyModule;
import com.pararepilot.model.Topic;
import com.pararepilot.model.User;
import com.pararepilot.model.Worksheet;
import com.pararepilot.repository.AttemptRepository;
import com.pararepilot.repository.DailyRecommendationRepository;
import com.pararepilot.repository.DatabaseManager;
import com.pararepilot.repository.MistakeRepository;
import com.pararepilot.repository.ModuleRepository;
import com.pararepilot.repository.TopicRepository;
import com.pararepilot.repository.UserRepository;
import com.pararepilot.repository.WorksheetRepository;
import com.pararepilot.util.DateUtils;
import com.pararepilot.util.WeightedRandomPicker;

class WorksheetSelectionServiceTest {

    @Test
    void refreshDoesNotRepeatAWorksheetAlreadyRecommendedToday() throws Exception {
        User user = null;

        try {
            user = new UserRepository().create(
                    "recommendation-test-" + UUID.randomUUID(),
                    "hash",
                    "salt"
            );
            AccountSession.signIn(user);

            ModuleRepository moduleRepository = new ModuleRepository();
            TopicRepository topicRepository = new TopicRepository();
            WorksheetRepository worksheetRepository = new WorksheetRepository();
            AttemptRepository attemptRepository = new AttemptRepository();
            DailyRecommendationRepository dailyRecommendationRepository =
                    new DailyRecommendationRepository();

            StudyModule module = moduleRepository.create(
                    "Recommendation Module",
                    "Temporary recommendation test module",
                    null,
                    ImportanceLevel.HIGH
            );

            Topic topic = topicRepository.create(
                    module.id(),
                    "Recommendation Topic",
                    "Temporary recommendation test topic",
                    ImportanceLevel.HIGH,
                    ConfidenceLevel.LOW
            );

            Worksheet firstWorksheet = worksheetRepository.create(
                    topic.id(),
                    "Recommendation A",
                    "",
                    DifficultyLevel.MEDIUM,
                    ImportanceLevel.HIGH
            );

            Worksheet secondWorksheet = worksheetRepository.create(
                    topic.id(),
                    "Recommendation B",
                    "",
                    DifficultyLevel.MEDIUM,
                    ImportanceLevel.HIGH
            );

            Worksheet thirdWorksheet = worksheetRepository.create(
                    topic.id(),
                    "Recommendation C",
                    "",
                    DifficultyLevel.MEDIUM,
                    ImportanceLevel.HIGH
            );

            WorksheetSelectionService service = new WorksheetSelectionService(
                    worksheetRepository,
                    topicRepository,
                    new MistakeRepository(),
                    moduleRepository,
                    new UserSettingsService(),
                    attemptRepository,
                    dailyRecommendationRepository,
                    new PriorityScoreService(),
                    new FirstItemPicker()
            );

            long firstRecommendationId = service.recommendWorksheet()
                    .orElseThrow()
                    .worksheet()
                    .id();

            long secondRecommendationId = service.pickAnotherRecommendation()
                    .orElseThrow()
                    .worksheet()
                    .id();

            attemptRepository.create(
                    firstRecommendationId,
                    DateUtils.now().minusMinutes(15),
                    DateUtils.now(),
                    1,
                    1,
                    100.0,
                    ConfidenceLevel.HIGH,
                    null,
                    null,
                    null
            );

            long thirdRecommendationId = service.pickAnotherRecommendation()
                    .orElseThrow()
                    .worksheet()
                    .id();

            assertTrue(
                    List.of(firstWorksheet.id(), secondWorksheet.id(), thirdWorksheet.id())
                            .contains(firstRecommendationId)
            );
            assertNotEquals(firstRecommendationId, secondRecommendationId);
            assertNotEquals(firstRecommendationId, thirdRecommendationId);
            assertNotEquals(secondRecommendationId, thirdRecommendationId);

        } finally {
            if (user != null) {
                deleteUserData(user.id());
            }

            AccountSession.signOut();
        }
    }

    private void deleteUserData(long userId) throws Exception {
        try (Connection conn = DatabaseManager.connect()) {
            deleteByUserId(conn, "daily_recommendation_history", userId);
            deleteByUserId(conn, "daily_recommendations", userId);
            deleteByUserId(conn, "user_settings", userId);
            deleteByUserId(conn, "user_stats", userId);
            deleteByUserId(conn, "modules", userId);

            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE id = ?;")) {
                stmt.setLong(1, userId);
                stmt.executeUpdate();
            }
        }
    }

    private void deleteByUserId(Connection conn, String tableName, long userId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM " + tableName + " WHERE user_id = ?;")) {

            stmt.setLong(1, userId);
            stmt.executeUpdate();
        }
    }

    private static class FirstItemPicker extends WeightedRandomPicker<WorksheetRecommendation> {

        @Override
        public WorksheetRecommendation pick(
                List<WorksheetRecommendation> items,
                ToIntFunction<WorksheetRecommendation> weightFunction
        ) {
            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("Cannot pick from an empty list.");
            }

            return items.get(0);
        }
    }
}
