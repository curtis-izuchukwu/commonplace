package com.pararepilot.ui.controller;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.pararepilot.model.StudyModule;
import com.pararepilot.model.Topic;
import com.pararepilot.model.UserStats;
import com.pararepilot.model.Worksheet;
import com.pararepilot.repository.AttemptRepository;
import com.pararepilot.service.AccountSession;
import com.pararepilot.service.AccountService;
import com.pararepilot.service.DashboardReminder;
import com.pararepilot.service.DashboardService;
import com.pararepilot.service.DashboardSummary;
import com.pararepilot.service.UserSettingsService;
import com.pararepilot.service.WorksheetRecommendation;
import com.pararepilot.ui.AppChrome;
import com.pararepilot.ui.AppIcon;
import com.pararepilot.ui.AppPreferences;
import com.pararepilot.ui.LevelUi;
import com.pararepilot.ui.OverlayService;
import com.pararepilot.ui.UiAnimations;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.input.KeyCode;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class DashboardController {

    @FXML private Button dashboardButton;
    @FXML private Button manageModulesButton;
    @FXML private MenuButton accountMenuButton;
    @FXML private MenuItem accountNameItem;
    @FXML private Label welcomeLabel;
    @FXML private StackPane contentHost;
    @FXML private ScrollPane dashboardPane;

    @FXML private Label recommendationTitleLabel;
    @FXML private Label recommendationMetaLabel;
    @FXML private HBox recommendationVisualMeta;
    @FXML private Label recommendationReasonLabel;
    @FXML private Button openRecommendationButton;
    @FXML private Button pickAnotherButton;

    @FXML private Label rankLabel;
    @FXML private Label xpLabel;
    @FXML private ProgressBar rankProgressBar;
    @FXML private Label streakLabel;
    @FXML private Label mistakeCountLabel;
    @FXML private HBox rankRecordRow;
    @FXML private VBox xpRecordGroup;
    @FXML private HBox streakRecordRow;
    @FXML private Label examCalendarMonthLabel;
    @FXML private GridPane examCalendarGrid;
    @FXML private VBox examCalendarEvents;

    @FXML private VBox notificationList;
    @FXML private VBox weakTopicsList;
    @FXML private VBox recentAttemptsList;
    @FXML private Label statusLabel;

    private final DashboardService dashboardService = new DashboardService();
    private final UserSettingsService userSettingsService = new UserSettingsService();
    private final AccountService accountService = new AccountService();

    private WorksheetRecommendation currentRecommendation;
    private Timeline dashboardRefreshTimer;
    private boolean modulesPageActive;
    private Integer lastDisplayedXp;
    private String lastDisplayedRank;
    private YearMonth visibleExamMonth = YearMonth.now();
    private LocalDate selectedExamDate;
    private List<StudyModule> latestModules = List.of();

    @FXML
    private void initialize() {
        updateAccountMenu();
        updateWelcomeMessage();
        Platform.runLater(this::applySavedPreferences);
        Platform.runLater(this::registerChromeCommands);
        showDashboardPage();
        loadDashboard();
        startDashboardRefreshTimer();
    }

    @FXML
    private void handleRefresh() {
        UiAnimations.animateRecommendationRefresh(recommendationPanel(), this::refreshRecommendation);
    }

    @FXML
    private void handleOpenRecommendation() {
        if (currentRecommendation == null) {
            setStatus("No recommendation available yet.");
            return;
        }

        openWorksheetDetail(
                currentRecommendation.worksheet(),
                currentRecommendation.topic()
        );
    }

    @FXML
    private void handleOpenDashboard() {
        showDashboardPage();
        loadDashboard();
    }

    @FXML
    private void handleOpenModules() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/pararepilot/fxml/ModulesView.fxml")
            );

            Parent root = loader.load();
            UiAnimations.transitionContent(contentHost, root, UiAnimations.SlideDirection.FROM_RIGHT);
            setActivePage(manageModulesButton);
            modulesPageActive = true;
            AppChrome.setBreadcrumb(manageModulesButton.getScene(), "Manage Modules");
            setStatus("Manage modules");

        } catch (IOException e) {
            showError("Failed to open modules", e.getMessage());
        }
    }

    @FXML
    private void handleOpenMistakeBank() {
        try {
            var handle = OverlayService.<MistakeBankController>open(
                    accountMenuButton,
                    "/com/pararepilot/fxml/MistakeBankView.fxml",
                    920,
                    760
            );
            handle.controller().setOnMistakesChanged(this::loadDashboard);
            handle.controller().setOnClosed(() -> AppChrome.setBreadcrumb(
                    accountMenuButton.getScene(),
                    modulesPageActive ? "Manage Modules" : "Dashboard"
            ));
            AppChrome.setBreadcrumb(accountMenuButton.getScene(), "Mistake Bank");

        } catch (IOException e) {
            showError("Failed to open mistake bank", e.getMessage());
        }
    }

    @FXML
    private void handleSwitchAccount() {
        switchToLogin();
    }

    @FXML
    private void handleOpenSettings() {
        try {
            var handle = OverlayService.<SettingsController>open(
                    accountMenuButton,
                    "/com/pararepilot/fxml/SettingsView.fxml",
                    880,
                    720
            );

            handle.controller().setOnSettingsSaved(() -> {
                applySavedPreferences();
                refreshActivePage();
            });
            handle.controller().setOnSwitchAccount(this::switchToLogin);

        } catch (IOException e) {
            showError("Failed to open settings", e.getMessage());
        }
    }

    @FXML
    private void handleChangePassword() {
        try {
            OverlayService.open(accountMenuButton, "/com/pararepilot/fxml/ChangePasswordView.fxml", 520, 430);

        } catch (IOException e) {
            showError("Failed to open password settings", e.getMessage());
        }
    }

    @FXML
    private void handlePreviousExamMonth() {
        visibleExamMonth = visibleExamMonth.minusMonths(1);
        selectedExamDate = null;
        renderExamCalendar();
    }

    @FXML
    private void handleCurrentExamMonth() {
        selectedExamDate = LocalDate.now();
        visibleExamMonth = YearMonth.from(selectedExamDate);
        renderExamCalendar();
    }

    @FXML
    private void handleNextExamMonth() {
        visibleExamMonth = visibleExamMonth.plusMonths(1);
        selectedExamDate = null;
        renderExamCalendar();
    }

    private void loadDashboard() {
        try {
            DashboardSummary summary = dashboardService.loadDashboard();
            updateDashboard(summary);

        } catch (SQLException e) {
            e.printStackTrace();
            showError("Failed to load dashboard", e.getMessage());
        }
    }

    private void refreshRecommendation() {
        try {
            DashboardSummary summary = dashboardService.refreshRecommendation();
            updateDashboard(summary);

        } catch (SQLException e) {
            showError("Failed to refresh recommendation", e.getMessage());
        }
    }

    private void updateDashboard(DashboardSummary summary) {
        updateChromeDailyChip(summary);
        updateRecommendation(summary);
        updateStats(summary);
        updateExamCalendar(summary.modules());
        updateReminders(summary);
        updateWeakTopics(summary);
        updateRecentAttempts(summary);

        setStatus("");
    }

    private void updateChromeDailyChip(DashboardSummary summary) {
        if (accountMenuButton.getScene() == null) {
            Platform.runLater(() -> {
                if (accountMenuButton.getScene() != null) {
                    updateChromeDailyChip(summary);
                }
            });
            return;
        }

        if (summary.worksheetWindowLocked()) {
            AppChrome.setDailyChip(
                    accountMenuButton.getScene(),
                    "Next worksheet in " + countdownUntilNextWorksheet(summary.userStats())
            );
            return;
        }

        AppChrome.setDailyChip(
                accountMenuButton.getScene(),
                "Daily " + dailyProgressText(summary)
        );
    }

    private void updateRecommendation(DashboardSummary summary) {
        if (summary.worksheetWindowLocked()) {
            currentRecommendation = null;
            recommendationVisualMeta.getChildren().clear();
            Label completionBadge = new Label("Completed");
            completionBadge.getStyleClass().add("completion-badge");
            recommendationVisualMeta.getChildren().add(completionBadge);
            UiAnimations.popIn(completionBadge);

            UiAnimations.fadeTextChange(recommendationTitleLabel, "Today's worksheet is complete");
            UiAnimations.fadeTextChange(
                    recommendationMetaLabel,
                    "Nice work. Your next recommendation unlocks in "
                            + countdownUntilNextWorksheet(summary.userStats()) + "."
            );
            setRecommendationReason(
                    "Daily progress: " + dailyProgressText(summary)
                            + " - Streak: " + summary.userStats().streakCount()
                            + " day" + (summary.userStats().streakCount() == 1 ? "" : "s")
            );
            openRecommendationButton.setDisable(true);
            pickAnotherButton.setDisable(true);

            return;
        }

        pickAnotherButton.setDisable(false);

        if (summary.recommendation().isEmpty()) {
            currentRecommendation = null;
            recommendationVisualMeta.getChildren().clear();

            UiAnimations.fadeTextChange(recommendationTitleLabel, "No recommendation yet");
            UiAnimations.fadeTextChange(
                    recommendationMetaLabel,
                    "Add a worksheet in Manage Modules to choose your next study session."
            );
            setRecommendationReason("");
            openRecommendationButton.setDisable(true);
            pickAnotherButton.setDisable(true);

            return;
        }

        currentRecommendation = summary.recommendation().get();

        Worksheet worksheet = currentRecommendation.worksheet();

        UiAnimations.fadeTextChange(recommendationTitleLabel, worksheet.title());

        UiAnimations.fadeTextChange(
                recommendationMetaLabel,
                "Topic: " + currentRecommendation.topic().name()
        );

        recommendationVisualMeta.getChildren().setAll(
                LevelUi.createDifficultyIndicator(worksheet.difficulty()),
                LevelUi.createPriorityChip(worksheet.importance())
        );

        setRecommendationReason("Daily progress: " + dailyProgressText(summary));
        openRecommendationButton.setDisable(false);
    }

    private String countdownUntilNextWorksheet(UserStats stats) {
        LocalDate nextWorksheetDate = stats.lastCompletionDate()
                .plusDays(stats.worksheetIntervalDays());

        LocalDateTime unlockTime = nextWorksheetDate.atStartOfDay();
        Duration duration = Duration.between(LocalDateTime.now(), unlockTime);

        if (duration.isNegative() || duration.isZero()) {
            return "a moment";
        }

        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();

        if (hours <= 0) {
            return minutes + " minute" + (minutes == 1 ? "" : "s");
        }

        return hours + " hour" + (hours == 1 ? "" : "s")
                + " " + minutes + " minute" + (minutes == 1 ? "" : "s");
    }

    private String dailyProgressText(DashboardSummary summary) {
        int dailyGoal = summary.userSettings().dailyWorksheetGoal();
        int completed = Math.min(summary.completedWorksheetsToday(), dailyGoal);

        return completed + "/" + dailyGoal
                + " worksheet" + (dailyGoal == 1 ? "" : "s");
    }

    private void updateStats(DashboardSummary summary) {
        UserStats stats = summary.userStats();

        String rank = summary.rank();
        rankLabel.setText(rank);
        xpLabel.setText(stats.xp() + " XP");

        int nextRankXp = summary.nextRankXp();

        double targetProgress = nextRankXp <= stats.xp()
                ? 1.0
                : (double) stats.xp() / nextRankXp;
        UiAnimations.animateProgress(rankProgressBar, targetProgress);
        rankProgressBar.setAccessibleText(stats.xp() + " XP; next rank at " + nextRankXp + " XP");

        if (lastDisplayedXp != null && stats.xp() > lastDisplayedXp) {
            UiAnimations.showFloatingXp(rankProgressBar, stats.xp() - lastDisplayedXp);
        }

        if (lastDisplayedRank != null && !lastDisplayedRank.equals(rank)) {
            UiAnimations.flashGlow(rankProgressBar.getParent(), "rank-glow");
            UiAnimations.softPulse(rankLabel);
        }

        lastDisplayedXp = stats.xp();
        lastDisplayedRank = rank;

        streakLabel.setText(stats.streakCount() + " day" + (stats.streakCount() == 1 ? "" : "s"));

        int unresolvedMistakes = summary.unresolvedMistakeCount();
        mistakeCountLabel.setText(unresolvedMistakes + " unresolved");
        mistakeCountLabel.getStyleClass().removeAll("study-record-warning", "study-record-success");
        mistakeCountLabel.getStyleClass().add(
                unresolvedMistakes > 0 ? "study-record-warning" : "study-record-success"
        );

        boolean showXpAndRank = summary.userSettings().showXpAndRank();
        rankRecordRow.setVisible(showXpAndRank);
        rankRecordRow.setManaged(showXpAndRank);
        xpRecordGroup.setVisible(showXpAndRank);
        xpRecordGroup.setManaged(showXpAndRank);

        boolean showStreak = summary.userSettings().streakTrackingEnabled();
        streakRecordRow.setVisible(showStreak);
        streakRecordRow.setManaged(showStreak);
    }

    private void updateExamCalendar(List<StudyModule> modules) {
        latestModules = modules == null ? List.of() : modules;
        renderExamCalendar();
    }

    private void renderExamCalendar() {
        if (examCalendarGrid == null || examCalendarEvents == null || examCalendarMonthLabel == null) {
            return;
        }

        examCalendarGrid.getChildren().clear();
        examCalendarGrid.getColumnConstraints().clear();

        for (int i = 0; i < 7; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / 7.0);
            column.setHgrow(Priority.ALWAYS);
            column.setFillWidth(true);
            examCalendarGrid.getColumnConstraints().add(column);
        }

        examCalendarMonthLabel.setText(
                visibleExamMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK))
        );

        LocalDate firstOfMonth = visibleExamMonth.atDay(1);
        LocalDate firstOfNextMonth = visibleExamMonth.plusMonths(1).atDay(1);
        LocalDate gridStart = firstOfMonth.minusDays(firstOfMonth.getDayOfWeek().getValue() - 1L);
        int daySlots = (int) ChronoUnit.DAYS.between(gridStart, firstOfNextMonth);
        int visibleCells = (int) Math.ceil(daySlots / 7.0) * 7;
        Map<LocalDate, List<StudyModule>> examsByDate = latestModules.stream()
                .filter(module -> module.examDate() != null)
                .collect(Collectors.groupingBy(StudyModule::examDate));

        for (int column = 0; column < 7; column++) {
            LocalDate day = gridStart.plusDays(column);
            Label label = new Label(day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.UK));
            label.getStyleClass().add("exam-calendar-weekday");
            label.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(label, Priority.ALWAYS);
            examCalendarGrid.add(label, column, 0);
        }

        LocalDate today = LocalDate.now();

        for (int index = 0; index < visibleCells; index++) {
            LocalDate date = gridStart.plusDays(index);
            List<StudyModule> exams = examsByDate.getOrDefault(date, List.of());
            VBox cell = createExamCalendarCell(date, exams, today);

            int column = index % 7;
            int row = (index / 7) + 1;
            examCalendarGrid.add(cell, column, row);
        }

        renderExamEventList();
    }

    private VBox createExamCalendarCell(LocalDate date, List<StudyModule> exams, LocalDate today) {
        VBox cell = new VBox(3);
        cell.getStyleClass().add("exam-calendar-day");
        cell.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(cell, Priority.ALWAYS);

        if (!YearMonth.from(date).equals(visibleExamMonth)) {
            cell.getStyleClass().add("outside-month");
        }

        if (date.isEqual(today)) {
            cell.getStyleClass().add("today");
        }

        if (date.equals(selectedExamDate)) {
            cell.getStyleClass().add("selected");
        }

        if (!exams.isEmpty()) {
            cell.getStyleClass().add("has-exam");
        }

        Label dayNumber = new Label(String.valueOf(date.getDayOfMonth()));
        dayNumber.getStyleClass().add("exam-calendar-date-number");
        HBox dateHeading = new HBox(4, dayNumber);
        dateHeading.setAlignment(Pos.CENTER_LEFT);
        if (date.isEqual(today)) {
            Label todayLabel = new Label("Today");
            todayLabel.getStyleClass().add("exam-calendar-today-label");
            dateHeading.getChildren().add(todayLabel);
        }
        cell.getChildren().add(dateHeading);
        String examDescription = exams.stream().map(StudyModule::name).collect(Collectors.joining(", "));
        String accessibleDate = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK))
                + (date.isEqual(today) ? ", today" : "")
                + (date.equals(selectedExamDate) ? ", selected" : "")
                + (exams.isEmpty() ? ", no exams" : ", exams: " + examDescription);
        cell.setAccessibleText(accessibleDate);
        cell.setAccessibleRole(AccessibleRole.BUTTON);
        cell.setFocusTraversable(true);
        cell.setOnMouseClicked(event -> selectExamDate(date));
        cell.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                selectExamDate(date);
                event.consume();
            }
        });
        Tooltip.install(cell, new Tooltip(accessibleDate));

        exams.stream()
                .limit(1)
                .forEach(module -> {
                    Label examLabel = new Label(module.name());
                    examLabel.getStyleClass().add("exam-calendar-event-pill");
                    examLabel.setMaxWidth(Double.MAX_VALUE);
                    examLabel.setWrapText(false);
                    cell.getChildren().add(examLabel);
                });

        if (exams.size() > 1) {
            Label moreLabel = new Label("+" + (exams.size() - 1) + " more");
            moreLabel.getStyleClass().add("exam-calendar-more");
            cell.getChildren().add(moreLabel);
        }

        return cell;
    }

    private void selectExamDate(LocalDate date) {
        selectedExamDate = date;
        visibleExamMonth = YearMonth.from(date);
        renderExamCalendar();
    }

    private void renderExamEventList() {
        examCalendarEvents.getChildren().clear();

        List<StudyModule> monthExams = latestModules.stream()
                .filter(module -> module.examDate() != null)
                .filter(module -> YearMonth.from(module.examDate()).equals(visibleExamMonth))
                .sorted((first, second) -> first.examDate().compareTo(second.examDate()))
                .toList();

        if (monthExams.isEmpty()) {
            Label emptyLabel = new Label("No exams scheduled this month.");
            emptyLabel.getStyleClass().add("muted-text");
            emptyLabel.setWrapText(true);
            Label helpLabel = new Label("Add exam dates in Manage Modules when your assessments are confirmed.");
            helpLabel.getStyleClass().add("muted-text");
            helpLabel.setWrapText(true);
            VBox emptyState = new VBox(8, emptyLabel, helpLabel);
            emptyState.getStyleClass().add("exam-empty-state");
            examCalendarEvents.getChildren().add(emptyState);
            return;
        }

        for (StudyModule module : monthExams) {
            examCalendarEvents.getChildren().add(createExamEventRow(module));
        }
    }

    private HBox createExamEventRow(StudyModule module) {
        HBox row = new HBox(10);
        row.getStyleClass().add("exam-event-row");

        Label dateLabel = new Label(module.examDate().format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)));
        dateLabel.getStyleClass().add("exam-event-date");

        VBox details = new VBox(2);
        details.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(details, Priority.ALWAYS);

        Label title = new Label(module.name());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        Label countdown = new Label(formatExamCountdown(module.examDate()));
        countdown.getStyleClass().add("muted-text");

        details.getChildren().addAll(title, countdown);
        row.getChildren().addAll(dateLabel, details);
        return row;
    }

    private String formatExamCountdown(LocalDate examDate) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), examDate);

        if (days == 0) {
            return "Exam is today";
        }

        if (days == 1) {
            return "Exam is tomorrow";
        }

        if (days > 1) {
            return "Exam in " + days + " days";
        }

        long daysAgo = Math.abs(days);
        return "Exam was " + daysAgo + " day" + (daysAgo == 1 ? "" : "s") + " ago";
    }

    private void updateReminders(DashboardSummary summary) {
        notificationList.getChildren().clear();
        boolean hasReminders = !summary.reminders().isEmpty();

        notificationList.setVisible(hasReminders);
        notificationList.setManaged(hasReminders);

        if (!hasReminders) {
            return;
        }

        for (DashboardReminder reminder : summary.reminders()) {
            notificationList.getChildren().add(createReminderCard(reminder));
        }
    }

    private VBox createReminderCard(DashboardReminder reminder) {
        VBox card = new VBox(3);
        card.getStyleClass().addAll("notification-card", reminder.styleClass());

        Label title = new Label(reminder.title());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        Label message = new Label(reminder.message());
        message.getStyleClass().add("muted-text");
        message.setWrapText(true);

        card.getChildren().addAll(title, message);
        UiAnimations.animateCardEntry(card);
        return card;
    }

    private void updateWeakTopics(DashboardSummary summary) {
        weakTopicsList.getChildren().clear();

        if (summary.weakestTopics().isEmpty()) {
            Label emptyLabel = new Label("No topics yet.");
            emptyLabel.getStyleClass().add("muted-text");
            weakTopicsList.getChildren().add(emptyLabel);
            return;
        }

        for (Topic topic : summary.weakestTopics()) {
            weakTopicsList.getChildren().add(createWeakTopicCard(topic));
        }
    }

    private VBox createWeakTopicCard(Topic topic) {
        VBox card = new VBox(6);
        card.getStyleClass().add("dashboard-mini-card");

        Label title = new Label(topic.name());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        Label meta = new Label(
                "Mastery: " + String.format("%.0f%%", topic.masteryScore())
                        + " - Confidence: " + topic.confidence()
                        + " - Importance: " + topic.importance()
        );
        meta.getStyleClass().add("muted-text");
        meta.setWrapText(true);

        card.getChildren().addAll(title, meta);
        UiAnimations.animateCardEntry(card);
        return card;
    }

    private void updateRecentAttempts(DashboardSummary summary) {
        recentAttemptsList.getChildren().clear();

        if (summary.recentAttempts().isEmpty()) {
            Label emptyLabel = new Label("No attempts yet.");
            emptyLabel.getStyleClass().add("muted-text");
            recentAttemptsList.getChildren().add(emptyLabel);
            return;
        }

        for (AttemptRepository.RecentAttemptDisplayItem attempt : summary.recentAttempts()) {
            recentAttemptsList.getChildren().add(createRecentAttemptCard(attempt));
        }
    }

    private VBox createRecentAttemptCard(AttemptRepository.RecentAttemptDisplayItem attempt) {
        VBox card = new VBox(6);
        card.getStyleClass().add("dashboard-mini-card");

        Label title = new Label(attempt.worksheetTitle());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        Label meta = new Label(
                "Topic: " + attempt.topicName()
                        + " - Score: " + attempt.score()
                        + "/" + attempt.maxScore()
                        + " (" + String.format("%.0f%%", attempt.scorePercent()) + ")"
        );
        meta.getStyleClass().add("muted-text");
        meta.setWrapText(true);

        Label date = new Label(
                "Completed: " + attempt.completedAt().format(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                )
        );
        date.getStyleClass().add("muted-text");

        card.getChildren().addAll(title, meta, date);
        UiAnimations.animateCardEntry(card);
        return card;
    }

    private void openWorksheetDetail(Worksheet worksheet, Topic topic) {
        try {
            var handle = OverlayService.<WorksheetDetailController>open(
                    openRecommendationButton,
                    "/com/pararepilot/fxml/WorksheetDetailView.fxml",
                    840,
                    720
            );

            WorksheetDetailController controller = handle.controller();
            controller.setWorksheet(worksheet, topic);
            controller.setRecommendationDetails(currentRecommendation);
            controller.setOnWorksheetUpdated(this::loadDashboard);

        } catch (IOException e) {
            showError("Failed to open worksheet detail", e.getMessage());
        }
    }

    private String formatScore(Double score) {
        if (score == null) {
            return "Not attempted";
        }

        return String.format("%.0f%%", score);
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void setRecommendationReason(String message) {
        boolean hasMessage = message != null && !message.isBlank();
        recommendationReasonLabel.setText(hasMessage ? message : "");
        recommendationReasonLabel.setVisible(hasMessage);
        recommendationReasonLabel.setManaged(hasMessage);
    }

    private void showDashboardPage() {
        UiAnimations.transitionContent(contentHost, dashboardPane, UiAnimations.SlideDirection.FROM_LEFT);
        setActivePage(dashboardButton);
        modulesPageActive = false;
        AppChrome.setBreadcrumb(dashboardButton.getScene(), "Dashboard");
    }

    private void setActivePage(Button activeButton) {
        dashboardButton.getStyleClass().remove("primary-button");
        manageModulesButton.getStyleClass().remove("primary-button");

        if (!activeButton.getStyleClass().contains("primary-button")) {
            activeButton.getStyleClass().add("primary-button");
        }
    }

    private void updateAccountMenu() {
        String accountText = AccountSession.currentUser()
                .map(user -> user.username())
                .orElse("Account");

        accountMenuButton.setText(null);
        accountMenuButton.setGraphic(createAccountMenuIcon());
        accountMenuButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        accountMenuButton.setAccessibleText("Account menu");
        accountNameItem.setText("Signed in as " + accountText);
        accountNameItem.setDisable(true);
    }

    private void updateWelcomeMessage() {
        String username = AccountSession.currentUser()
                .map(user -> user.username())
                .orElse("");

        welcomeLabel.setText(username.isBlank() ? "Welcome Back" : "Welcome Back, " + username);
    }

    private VBox createAccountMenuIcon() {
        VBox icon = new VBox(4);
        icon.getStyleClass().add("account-menu-icon");
        icon.setMouseTransparent(true);

        for (int i = 0; i < 3; i++) {
            Region line = new Region();
            line.getStyleClass().add("account-menu-line");
            icon.getChildren().add(line);
        }

        return icon;
    }

    private void applySavedPreferences() {
        try {
            AppPreferences.apply(accountMenuButton.getScene(), userSettingsService.load());
        } catch (SQLException e) {
            setStatus("Settings failed to load: " + e.getMessage());
        }
    }

    private void registerChromeCommands() {
        if (accountMenuButton.getScene() == null) {
            return;
        }

        AppChrome.setCommands(
                accountMenuButton.getScene(),
                List.of(
                        new AppChrome.Command(
                                "Dashboard",
                                "Show recommendations, progress, reminders, weak topics, and recent attempts.",
                                "home today progress overview",
                                this::handleOpenDashboard
                        ),
                        new AppChrome.Command(
                                "Manage Modules",
                                "Create modules and topics, then manage worksheet generation.",
                                "modules topics worksheets create edit",
                                this::handleOpenModules
                        ),
                        new AppChrome.Command(
                                "Mistake Bank",
                                "Review unresolved and resolved mistakes.",
                                "mistakes review errors corrections",
                                this::handleOpenMistakeBank
                        ),
                        new AppChrome.Command(
                                "Open Recommended Worksheet",
                                "Open the current daily worksheet recommendation.",
                                "recommendation daily worksheet attempt",
                                this::handleOpenRecommendation
                        ),
                        new AppChrome.Command(
                                "Pick Another Worksheet",
                                "Refresh the recommendation for today.",
                                "refresh recommendation another",
                                this::handleRefresh
                        ),
                        new AppChrome.Command(
                                "Settings",
                                "Open account, appearance, study, notification, and accessibility settings.",
                                "preferences theme accent daily notifications accessibility",
                                this::handleOpenSettings
                        ),
                        new AppChrome.Command(
                                "Change Password",
                                "Update the current account password.",
                                "account security password",
                                this::handleChangePassword
                        ),
                        new AppChrome.Command(
                                "Switch Account",
                                "Return to the sign-in screen.",
                                "log out logout sign in account",
                                this::handleSwitchAccount
                        )
                )
        );
    }

    private Node recommendationPanel() {
        return recommendationTitleLabel.getParent();
    }

    private void switchToLogin() {
        try {
            stopDashboardRefreshTimer();
            accountService.signOut();

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/pararepilot/fxml/LoginView.fxml")
            );

            Parent root = loader.load();
            UiAnimations.installGlobalAnimations(root);
            AppChrome.setContent(accountMenuButton.getScene(), root);
            AppChrome.setBreadcrumb(accountMenuButton.getScene(), "Sign In");
            AppChrome.setDailyChip(accountMenuButton.getScene(), "");

        } catch (IOException e) {
            showError("Failed to switch account", e.getMessage());
        } catch (SQLException e) {
            showError("Failed to sign out", e.getMessage());
        }
    }

    private void refreshActivePage() {
        if (modulesPageActive) {
            handleOpenModules();
            return;
        }

        loadDashboard();
    }

    private void startDashboardRefreshTimer() {
        dashboardRefreshTimer = new Timeline(
                new KeyFrame(javafx.util.Duration.minutes(1), event -> loadDashboard())
        );
        dashboardRefreshTimer.setCycleCount(Timeline.INDEFINITE);
        dashboardRefreshTimer.play();
    }

    private void stopDashboardRefreshTimer() {
        if (dashboardRefreshTimer != null) {
            dashboardRefreshTimer.stop();
        }
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.setContentText(message == null || message.isBlank()
                    ? "No additional details were provided."
                    : message);
            AppIcon.applyTo(alert);
            alert.show();
        });
    }
}
