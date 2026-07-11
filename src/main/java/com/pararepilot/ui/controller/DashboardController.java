package com.pararepilot.ui.controller;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
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
                    "Create modules, topics, and worksheets to unlock recommendations."
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
        UiAnimations.softPulse(openRecommendationButton);
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
        rankLabel.setText("Rank: " + rank);
        xpLabel.setText(stats.xp() + " XP");

        int nextRankXp = summary.nextRankXp();

        double targetProgress = nextRankXp <= stats.xp()
                ? 1.0
                : (double) stats.xp() / nextRankXp;
        UiAnimations.animateProgress(rankProgressBar, targetProgress);

        if (lastDisplayedXp != null && stats.xp() > lastDisplayedXp) {
            UiAnimations.showFloatingXp(rankProgressBar, stats.xp() - lastDisplayedXp);
        }

        if (lastDisplayedRank != null && !lastDisplayedRank.equals(rank)) {
            UiAnimations.flashGlow(rankProgressBar.getParent(), "rank-glow");
            UiAnimations.softPulse(rankLabel);
        }

        lastDisplayedXp = stats.xp();
        lastDisplayedRank = rank;

        streakLabel.setText(
                stats.streakCount()
                        + " day"
                        + (stats.streakCount() == 1 ? "" : "s")
                        + " streak"
        );

        mistakeCountLabel.setText(
                summary.unresolvedMistakeCount()
                        + " unresolved mistake"
                        + (summary.unresolvedMistakeCount() == 1 ? "" : "s")
        );

        boolean showXpAndRank = summary.userSettings().showXpAndRank();
        rankLabel.setVisible(showXpAndRank);
        rankLabel.setManaged(showXpAndRank);
        xpLabel.setVisible(showXpAndRank);
        xpLabel.setManaged(showXpAndRank);
        rankProgressBar.setVisible(showXpAndRank);
        rankProgressBar.setManaged(showXpAndRank);

        boolean showStreak = summary.userSettings().streakTrackingEnabled();
        streakLabel.setVisible(showStreak);
        streakLabel.setManaged(showStreak);
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
