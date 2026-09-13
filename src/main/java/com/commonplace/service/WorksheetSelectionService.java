package com.commonplace.service;

import com.commonplace.model.DifficultyLevel;
import com.commonplace.model.Topic;
import com.commonplace.model.Worksheet;
import com.commonplace.repository.AttemptRepository;
import com.commonplace.repository.DailyRecommendationRepository;
import com.commonplace.repository.DatabaseManager;
import com.commonplace.repository.LearningRepository;
import com.commonplace.repository.MistakeRepository;
import com.commonplace.repository.ModuleRepository;
import com.commonplace.repository.QuestionRepository;
import com.commonplace.repository.TopicRepository;
import com.commonplace.repository.WorksheetRepository;
import com.commonplace.util.WeightedRandomPicker;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Deterministic recommendations derived from automatic question and worksheet evidence. */
public class WorksheetSelectionService {

    private final WorksheetRepository worksheets;
    private final TopicRepository topics;
    private final UserSettingsService settings;
    private final AttemptRepository attempts;
    private final DailyRecommendationRepository daily;
    private final PriorityScoreService priority;
    private final LearningRepository learning = new LearningRepository();
    private final LearningService learningService = new LearningService();

    public WorksheetSelectionService() {
        this(
                new WorksheetRepository(),
                new TopicRepository(),
                new MistakeRepository(),
                new ModuleRepository(),
                new UserSettingsService(),
                new AttemptRepository(),
                new DailyRecommendationRepository(),
                new PriorityScoreService(),
                new WeightedRandomPicker<>());
    }

    public WorksheetSelectionService(
            WorksheetRepository worksheetRepository,
            TopicRepository topicRepository,
            MistakeRepository ignoredMistakes,
            ModuleRepository ignoredModules,
            UserSettingsService userSettings,
            PriorityScoreService priorityScore,
            WeightedRandomPicker<WorksheetRecommendation> ignoredPicker) {
        this(
                worksheetRepository,
                topicRepository,
                ignoredMistakes,
                ignoredModules,
                userSettings,
                new AttemptRepository(),
                new DailyRecommendationRepository(),
                priorityScore,
                ignoredPicker);
    }

    public WorksheetSelectionService(
            WorksheetRepository worksheetRepository,
            TopicRepository topicRepository,
            MistakeRepository ignoredMistakes,
            ModuleRepository ignoredModules,
            UserSettingsService userSettings,
            DailyRecommendationRepository dailyRecommendationRepository,
            PriorityScoreService priorityScore,
            WeightedRandomPicker<WorksheetRecommendation> ignoredPicker) {
        this(
                worksheetRepository,
                topicRepository,
                ignoredMistakes,
                ignoredModules,
                userSettings,
                new AttemptRepository(),
                dailyRecommendationRepository,
                priorityScore,
                ignoredPicker);
    }

    public WorksheetSelectionService(
            WorksheetRepository worksheetRepository,
            TopicRepository topicRepository,
            MistakeRepository ignoredMistakes,
            ModuleRepository ignoredModules,
            UserSettingsService userSettings,
            AttemptRepository attemptRepository,
            DailyRecommendationRepository dailyRecommendationRepository,
            PriorityScoreService priorityScore,
            WeightedRandomPicker<WorksheetRecommendation> ignoredPicker) {
        worksheets = worksheetRepository;
        topics = topicRepository;
        settings = userSettings;
        attempts = attemptRepository;
        daily = dailyRecommendationRepository;
        priority = priorityScore;
    }

    public Optional<WorksheetRecommendation> recommendWorksheet() throws SQLException {
        return recommend(false);
    }

    public Optional<WorksheetRecommendation> pickAnotherRecommendation() throws SQLException {
        return recommend(true);
    }

    private Optional<WorksheetRecommendation> recommend(boolean another) throws SQLException {
        var completedToday = attempts.findWorksheetIdsCompletedOn(LocalDate.now());
        var choices =
                previewPriorities().stream()
                        .filter(item -> !completedToday.contains(item.worksheet().id()))
                        .toList();
        if (choices.isEmpty()) {
            daily.clear();
            return Optional.empty();
        }

        List<String> seen = history();
        String currentKey = seen.isEmpty() ? "" : seen.getFirst();
        if (!another) {
            var current =
                    choices.stream().filter(item -> item.key().equals(currentKey)).findFirst();
            if (current.isPresent()
                    && current.get().priorityScore() >= choices.getFirst().priorityScore() - 5) {
                return current;
            }
        }

        var selected =
                choices.stream()
                        .filter(item -> !seen.contains(item.key()))
                        .findFirst()
                        .orElseGet(
                                () ->
                                        choices.stream()
                                                .filter(item -> !item.key().equals(currentKey))
                                                .findFirst()
                                                .orElse(choices.getFirst()));
        saveHistory(selected.key());
        daily.saveTodayWorksheetId(selected.worksheet().id());
        return Optional.of(selected);
    }

    public List<WorksheetRecommendation> previewPriorities() throws SQLException {
        var config = settings.load();
        var allWorksheets = worksheets.findAll();
        List<WorksheetRecommendation> result = new ArrayList<>();

        for (long topicId : learning.topicIds()) {
            LearningRepository.TopicInfo topicInfo = learning.topic(topicId);
            if (config.archiveCompletedModules()
                    && topicInfo.examDate() != null
                    && topicInfo.examDate().isBefore(LocalDate.now())) {
                continue;
            }

            Topic topic = topics.findById(topicId).orElseThrow();
            LearningService.TopicProgress topicProgress = learningService.topic(topicId);
            List<Worksheet> relevant =
                    allWorksheets.stream()
                            .filter(worksheet -> worksheet.topicId() == topicId)
                            .toList();

            for (Worksheet worksheet : relevant) {
                if (!withinDifficultyPreference(worksheet, config)
                        || new QuestionRepository().findByWorksheetId(worksheet.id()).isEmpty()) {
                    continue;
                }

                LearningModel.Estimate estimate = learningService.worksheet(worksheet.id());
                var score =
                        priority.evaluate(
                                worksheet,
                                topic,
                                signals(
                                        estimate,
                                        worksheet,
                                        topicInfo,
                                        topicProgress.mastery(),
                                        relevant.size(),
                                        config.includeResolvedMistakesInRecommendations()),
                                config.recommendationFocus());
                result.add(
                        new WorksheetRecommendation(
                                worksheet, topic, score.score(), score.explanation()));
            }
        }

        return result.stream()
                .sorted(
                        Comparator.comparingInt(WorksheetRecommendation::priorityScore)
                                .reversed()
                                .thenComparing(WorksheetRecommendation::key))
                .toList();
    }

    private boolean withinDifficultyPreference(
            Worksheet worksheet, com.commonplace.model.UserSettings config) {
        int difficulty = worksheet.difficulty().ordinal();
        int minimum = DifficultyLevel.valueOf(config.preferredMinDifficulty()).ordinal();
        int maximum = DifficultyLevel.valueOf(config.preferredMaxDifficulty()).ordinal();
        return difficulty >= minimum && difficulty <= maximum;
    }

    private PriorityScoreService.Signals signals(
            LearningModel.Estimate estimate,
            Worksheet worksheet,
            LearningRepository.TopicInfo topicInfo,
            double topicMastery,
            int worksheetCount,
            boolean includeResolvedMistakes)
            throws SQLException {
        double difficultyFit =
                switch (worksheet.difficulty()) {
                    case EASY -> estimate.performance() < 70 || estimate.strength() < .3 ? 1 : .55;
                    case MEDIUM ->
                            estimate.performance() >= 50 || estimate.strength() < .15 ? .85 : .55;
                    case HARD ->
                            estimate.performance() >= 75 && estimate.strength() >= .35 ? 1 : .2;
                };

        long daysSince =
                estimate.lastAt() == null
                        ? 999
                        : ChronoUnit.DAYS.between(estimate.lastAt().toLocalDate(), LocalDate.now());
        double diversity = daysSince <= 0 ? .1 : daysSince < 3 ? .5 : 1;
        double examUrgency = 0;
        if (topicInfo.examDate() != null) {
            long daysUntilExam = ChronoUnit.DAYS.between(LocalDate.now(), topicInfo.examDate());
            if (daysUntilExam >= 0) {
                examUrgency =
                        Math.exp(-daysUntilExam / 21.0)
                                * (1 - topicMastery / 100)
                                / Math.max(1, worksheetCount);
            }
        }

        double mistakeRisk =
                learning.mistakeRiskForWorksheet(worksheet.id(), includeResolvedMistakes);
        double weakness = Math.max(estimate.weakness(), mistakeRisk);
        return new PriorityScoreService.Signals(
                estimate.mastery(),
                estimate.performance(),
                estimate.strength(),
                estimate.retention(),
                weakness,
                estimate.trend(),
                estimate.consistency(),
                estimate.dueAt(),
                difficultyFit,
                diversity,
                examUrgency);
    }

    private List<String> history() throws SQLException {
        List<String> keys = new ArrayList<>();
        String sql =
                """
                SELECT action_key
                FROM recommendation_actions
                WHERE user_id = ? AND study_date = ?
                ORDER BY selected_at DESC, id DESC;
                """;
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, AccountSession.currentUserId());
            stmt.setString(2, LocalDate.now().toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    keys.add(rs.getString(1));
                }
            }
        }
        return keys;
    }

    private void saveHistory(String key) throws SQLException {
        String sql =
                """
                INSERT INTO recommendation_actions(user_id, action_key, study_date, selected_at)
                VALUES(?, ?, ?, ?);
                """;
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, AccountSession.currentUserId());
            stmt.setString(2, key);
            stmt.setString(3, LocalDate.now().toString());
            stmt.setString(4, LocalDateTime.now().toString());
            stmt.executeUpdate();
        }
    }
}
