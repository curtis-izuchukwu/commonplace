package com.pararepilot.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.pararepilot.model.StudyModule;
import com.pararepilot.model.Topic;
import com.pararepilot.model.UserStats;
import com.pararepilot.model.UserSettings;
import com.pararepilot.repository.AttemptRepository;
import com.pararepilot.repository.MistakeRepository;
import com.pararepilot.repository.ModuleRepository;
import com.pararepilot.repository.TopicRepository;

public class DashboardService {

    private static final int WEAKEST_TOPIC_LIMIT = 5;
    private static final int RECENT_ATTEMPT_LIMIT = 5;

    private final GamificationService gamificationService;
    private final WorksheetSelectionService worksheetSelectionService;
    private final TopicRepository topicRepository;
    private final AttemptRepository attemptRepository;
    private final MistakeRepository mistakeRepository;
    private final ModuleRepository moduleRepository;
    private final UserSettingsService userSettingsService;

    public DashboardService() {
        this(
                new GamificationService(),
                new WorksheetSelectionService(),
                new TopicRepository(),
                new AttemptRepository(),
                new MistakeRepository(),
                new ModuleRepository(),
                new UserSettingsService()
        );
    }

    public DashboardService(
            GamificationService gamificationService,
            WorksheetSelectionService worksheetSelectionService,
            TopicRepository topicRepository,
            AttemptRepository attemptRepository,
            MistakeRepository mistakeRepository,
            ModuleRepository moduleRepository,
            UserSettingsService userSettingsService
    ) {
        this.gamificationService = gamificationService;
        this.worksheetSelectionService = worksheetSelectionService;
        this.topicRepository = topicRepository;
        this.attemptRepository = attemptRepository;
        this.mistakeRepository = mistakeRepository;
        this.moduleRepository = moduleRepository;
        this.userSettingsService = userSettingsService;
    }

    public DashboardSummary loadDashboard() throws SQLException {
        UserStats userStats = gamificationService.getUserStats();
        UserSettings userSettings = userSettingsService.load();
        String rank = gamificationService.calculateRank(userStats.xp());
        int nextRankXp = gamificationService.xpForNextRank(userStats.xp());

        List<Topic> weakestTopics =
                topicRepository.findWeakestTopics(WEAKEST_TOPIC_LIMIT);

        List<AttemptRepository.RecentAttemptDisplayItem> recentAttempts =
                attemptRepository.findRecentDisplayItems(RECENT_ATTEMPT_LIMIT);

        List<StudyModule> modules = moduleRepository.findAll();

        int unresolvedMistakeCount =
                mistakeRepository.countUnresolved();

        int completedWorksheetsToday =
                attemptRepository.countCompletedOn(LocalDate.now());

        boolean worksheetWindowLocked = worksheetWindowIsLocked(
                userStats,
                userSettings,
                completedWorksheetsToday
        );

        Optional<WorksheetRecommendation> recommendation = worksheetWindowLocked
                ? Optional.empty()
                : worksheetSelectionService.recommendWorksheet();

        List<DashboardReminder> reminders = buildReminders(
                userStats,
                userSettings,
                completedWorksheetsToday,
                worksheetWindowLocked,
                unresolvedMistakeCount,
                modules
        );

        return new DashboardSummary(
                userStats,
                userSettings,
                rank,
                nextRankXp,
                recommendation,
                modules,
                weakestTopics,
                recentAttempts,
                unresolvedMistakeCount,
                completedWorksheetsToday,
                worksheetWindowLocked,
                reminders
        );
    }

    public DashboardSummary refreshRecommendation() throws SQLException {
        UserStats userStats = gamificationService.getUserStats();
        UserSettings userSettings = userSettingsService.load();
        int completedWorksheetsToday = attemptRepository.countCompletedOn(LocalDate.now());

        if (!worksheetWindowIsLocked(userStats, userSettings, completedWorksheetsToday)) {
            worksheetSelectionService.pickAnotherRecommendation();
        }

        return loadDashboard();
    }

    private List<DashboardReminder> buildReminders(
            UserStats stats,
            UserSettings settings,
            int completedWorksheetsToday,
            boolean worksheetWindowLocked,
            int unresolvedMistakeCount,
            List<StudyModule> modules
    ) {
        List<DashboardReminder> reminders = new ArrayList<>();
        int dailyGoal = settings.dailyWorksheetGoal();
        boolean dailyGoalMet = completedWorksheetsToday >= dailyGoal;

        if (quietHoursActive(settings)) {
            return reminders;
        }

        if (settings.dailyReminderEnabled()
                && !worksheetWindowLocked
                && !dailyGoalMet
                && !LocalTime.now().isBefore(LocalTime.parse(settings.dailyReminderTime()))) {
            reminders.add(new DashboardReminder(
                    "Daily study reminder",
                    "You have completed " + completedWorksheetsToday + "/"
                            + dailyGoal + " worksheets today.",
                    "notification-info"
            ));
        }

        if (settings.mistakeReminderEnabled() && unresolvedMistakeCount > 0) {
            reminders.add(new DashboardReminder(
                    "Mistake review",
                    unresolvedMistakeCount + " unresolved mistake"
                            + (unresolvedMistakeCount == 1 ? "" : "s")
                            + (unresolvedMistakeCount == 1 ? " is" : " are")
                            + " waiting in the mistake bank.",
                    "notification-review"
            ));
        }

        if (settings.streakReminderEnabled()
                && stats.streakCount() > 0
                && !worksheetWindowLocked
                && !dailyGoalMet
                && (completedWorksheetsToday > 0 || streakNeedsWorkToday(stats))) {
            reminders.add(new DashboardReminder(
                    "Streak reminder",
                    "Complete today's worksheet goal to keep your "
                            + stats.streakCount() + " day streak alive.",
                    "notification-success"
            ));
        }

        return reminders;
    }

    private boolean worksheetWindowIsLocked(
            UserStats stats,
            UserSettings settings,
            int completedWorksheetsToday
    ) {
        if (stats.lastCompletionDate() == null) {
            return false;
        }

        LocalDate today = LocalDate.now();

        if (stats.lastCompletionDate().isEqual(today)) {
            return completedWorksheetsToday >= settings.dailyWorksheetGoal();
        }

        LocalDate nextWorksheetDate = stats.lastCompletionDate()
                .plusDays(stats.worksheetIntervalDays());

        return today.isBefore(nextWorksheetDate);
    }

    private boolean streakNeedsWorkToday(UserStats stats) {
        if (stats.lastCompletionDate() == null) {
            return true;
        }

        LocalDate nextDueDate = stats.lastCompletionDate()
                .plusDays(stats.worksheetIntervalDays());

        return !LocalDate.now().isBefore(nextDueDate);
    }

    private boolean quietHoursActive(UserSettings settings) {
        if (!settings.quietHoursEnabled()) {
            return false;
        }

        LocalTime now = LocalTime.now();
        LocalTime start = LocalTime.parse(settings.quietHoursStart());
        LocalTime end = LocalTime.parse(settings.quietHoursEnd());

        if (start.equals(end)) {
            return true;
        }

        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }

        return !now.isBefore(start) || now.isBefore(end);
    }
}
