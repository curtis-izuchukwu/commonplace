package com.pararepilot.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.pararepilot.model.DifficultyLevel;
import com.pararepilot.model.StudyModule;
import com.pararepilot.model.Topic;
import com.pararepilot.model.Worksheet;
import com.pararepilot.repository.DailyRecommendationRepository;
import com.pararepilot.repository.MistakeRepository;
import com.pararepilot.repository.ModuleRepository;
import com.pararepilot.repository.TopicRepository;
import com.pararepilot.repository.WorksheetRepository;
import com.pararepilot.util.WeightedRandomPicker;

public class WorksheetSelectionService {

    private final WorksheetRepository worksheetRepository;
    private final TopicRepository topicRepository;
    private final PriorityScoreService priorityScoreService;
    private final WeightedRandomPicker<WorksheetRecommendation> picker;
    private final MistakeRepository mistakeRepository;
    private final ModuleRepository moduleRepository;
    private final UserSettingsService userSettingsService;
    private final DailyRecommendationRepository dailyRecommendationRepository;

    public WorksheetSelectionService() {
        this(
                new WorksheetRepository(),
                new TopicRepository(),
                new MistakeRepository(),
                new ModuleRepository(),
                new UserSettingsService(),
                new DailyRecommendationRepository(),
                new PriorityScoreService(),
                new WeightedRandomPicker<>()
        );
    }

    public WorksheetSelectionService(
            WorksheetRepository worksheetRepository,
            TopicRepository topicRepository,
            MistakeRepository mistakeRepository,
            ModuleRepository moduleRepository,
            UserSettingsService userSettingsService,
            PriorityScoreService priorityScoreService,
            WeightedRandomPicker<WorksheetRecommendation> picker
    ) {
        this(
                worksheetRepository,
                topicRepository,
                mistakeRepository,
                moduleRepository,
                userSettingsService,
                new DailyRecommendationRepository(),
                priorityScoreService,
                picker
        );
    }

    public WorksheetSelectionService(
            WorksheetRepository worksheetRepository,
            TopicRepository topicRepository,
            MistakeRepository mistakeRepository,
            ModuleRepository moduleRepository,
            UserSettingsService userSettingsService,
            DailyRecommendationRepository dailyRecommendationRepository,
            PriorityScoreService priorityScoreService,
            WeightedRandomPicker<WorksheetRecommendation> picker
    ) {
        this.worksheetRepository = worksheetRepository;
        this.topicRepository = topicRepository;
        this.mistakeRepository = mistakeRepository;
        this.moduleRepository = moduleRepository;
        this.userSettingsService = userSettingsService;
        this.dailyRecommendationRepository = dailyRecommendationRepository;
        this.priorityScoreService = priorityScoreService;
        this.picker = picker;
    }

    public Optional<WorksheetRecommendation> recommendWorksheet() throws SQLException {
        return recommendWorksheet(false);
    }

    public Optional<WorksheetRecommendation> pickAnotherRecommendation() throws SQLException {
        return recommendWorksheet(true);
    }

    private Optional<WorksheetRecommendation> recommendWorksheet(boolean forceNew) throws SQLException {
        List<WorksheetRecommendation> recommendations = buildRecommendations();

        if (recommendations.isEmpty()) {
            dailyRecommendationRepository.clear();
            return Optional.empty();
        }

        Optional<Long> cachedWorksheetId = dailyRecommendationRepository.findTodayWorksheetId();

        if (!forceNew && cachedWorksheetId.isPresent()) {
            Optional<WorksheetRecommendation> cachedRecommendation = recommendations.stream()
                    .filter(recommendation -> recommendation.worksheet().id() == cachedWorksheetId.get())
                    .findFirst();

            if (cachedRecommendation.isPresent()) {
                return cachedRecommendation;
            }

            dailyRecommendationRepository.clear();
        }

        List<WorksheetRecommendation> pickableRecommendations = recommendations;

        if (forceNew && cachedWorksheetId.isPresent() && recommendations.size() > 1) {
            pickableRecommendations = recommendations.stream()
                    .filter(recommendation -> recommendation.worksheet().id() != cachedWorksheetId.get())
                    .toList();
        }

        WorksheetRecommendation recommendation = picker.pick(
                pickableRecommendations,
                WorksheetRecommendation::priorityScore
        );
        dailyRecommendationRepository.saveTodayWorksheetId(recommendation.worksheet().id());

        return Optional.of(recommendation);
    }

    public List<WorksheetRecommendation> previewPriorities() throws SQLException {
        return buildRecommendations().stream()
                .sorted((first, second) -> Integer.compare(
                        second.priorityScore(),
                        first.priorityScore()
                ))
                .toList();
    }

    private List<WorksheetRecommendation> buildRecommendations() throws SQLException {
        List<Worksheet> worksheets = worksheetRepository.findAll();
        List<WorksheetRecommendation> recommendations = new ArrayList<>();
        var settings = userSettingsService.load();
        DifficultyLevel minDifficulty = DifficultyLevel.valueOf(settings.preferredMinDifficulty());
        DifficultyLevel maxDifficulty = DifficultyLevel.valueOf(settings.preferredMaxDifficulty());

        for (Worksheet worksheet : worksheets) {
            if (!difficultyAllowed(worksheet.difficulty(), minDifficulty, maxDifficulty)) {
                continue;
            }

            Optional<Topic> topic = topicRepository.findById(worksheet.topicId());

            if (topic.isEmpty()) {
                continue;
            }

            int unresolvedMistakeCount = settings.includeResolvedMistakesInRecommendations()
                    ? mistakeRepository.countByWorksheetId(worksheet.id())
                    : mistakeRepository.countUnresolvedByWorksheetId(worksheet.id());

            int priorityScore = priorityScoreService.calculatePriority(
                    worksheet,
                    topic.get(),
                    unresolvedMistakeCount
            );

            priorityScore += preferenceBonus(settings.recommendationFocus(), topic.get());

            String explanation = priorityScoreService.explainPriority(
                    worksheet,
                    topic.get(),
                    unresolvedMistakeCount
            );

            recommendations.add(new WorksheetRecommendation(
                    worksheet,
                    topic.get(),
                    priorityScore,
                    explanation
            ));
        }

        return recommendations;
    }

    private boolean difficultyAllowed(
            DifficultyLevel worksheetDifficulty,
            DifficultyLevel minDifficulty,
            DifficultyLevel maxDifficulty
    ) {
        return worksheetDifficulty.ordinal() >= minDifficulty.ordinal()
                && worksheetDifficulty.ordinal() <= maxDifficulty.ordinal();
    }

    private int preferenceBonus(String recommendationFocus, Topic topic) throws SQLException {
        return switch (recommendationFocus) {
            case "WEAK_TOPICS" -> (int) Math.round((100.0 - topic.masteryScore()) / 3.0);
            case "UPCOMING_EXAMS" -> upcomingExamBonus(topic);
            default -> 0;
        };
    }

    private int upcomingExamBonus(Topic topic) throws SQLException {
        Optional<StudyModule> module = moduleRepository.findById(topic.moduleId());

        if (module.isEmpty() || module.get().examDate() == null) {
            return 0;
        }

        long daysUntilExam = ChronoUnit.DAYS.between(LocalDate.now(), module.get().examDate());

        if (daysUntilExam < 0 || daysUntilExam > 30) {
            return 0;
        }

        return (int) Math.max(0, 40 - daysUntilExam);
    }
}
