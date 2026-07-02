package com.pararepilot.ui.controller;

import java.io.IOException;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;

import com.pararepilot.model.Topic;
import com.pararepilot.model.UserStats;
import com.pararepilot.model.Worksheet;
import com.pararepilot.repository.AttemptRepository;
import com.pararepilot.service.DashboardService;
import com.pararepilot.service.DashboardSummary;
import com.pararepilot.service.WorksheetRecommendation;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class DashboardController {

    @FXML private Label recommendationTitleLabel;
    @FXML private Label recommendationMetaLabel;
    @FXML private Label recommendationReasonLabel;
    @FXML private Button openRecommendationButton;

    @FXML private Label rankLabel;
    @FXML private Label xpLabel;
    @FXML private ProgressBar rankProgressBar;
    @FXML private Label streakLabel;
    @FXML private Label mistakeCountLabel;

    @FXML private VBox weakTopicsList;
    @FXML private VBox recentAttemptsList;
    @FXML private Label statusLabel;

    private final DashboardService dashboardService = new DashboardService();

    private WorksheetRecommendation currentRecommendation;

    @FXML
    private void initialize() {
        loadDashboard();
    }

    @FXML
    private void handleRefresh() {
        loadDashboard();
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
    private void handleOpenModules() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/pararepilot/fxml/ModulesView.fxml")
            );

            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Manage Modules");
            stage.initModality(Modality.APPLICATION_MODAL);

            Scene scene = new Scene(root, 1100, 720);
            scene.getStylesheets().add(
                    getClass().getResource("/com/pararepilot/css/app.css").toExternalForm()
            );

            stage.setScene(scene);
            stage.showAndWait();

            loadDashboard();

        } catch (IOException e) {
            showError("Failed to open modules", e.getMessage());
        }
    }

    @FXML
    private void handleOpenMistakeBank() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/pararepilot/fxml/MistakeBankView.fxml")
            );

            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Mistake Bank");
            stage.initModality(Modality.APPLICATION_MODAL);

            Scene scene = new Scene(root, 860, 740);
            scene.getStylesheets().add(
                    getClass().getResource("/com/pararepilot/css/app.css").toExternalForm()
            );

            stage.setScene(scene);
            stage.showAndWait();

            loadDashboard();

        } catch (IOException e) {
            showError("Failed to open mistake bank", e.getMessage());
        }
    }

    private void loadDashboard() {
        try {
            DashboardSummary summary = dashboardService.loadDashboard();

            updateRecommendation(summary);
            updateStats(summary);
            updateWeakTopics(summary);
            updateRecentAttempts(summary);

            setStatus("");

        } catch (SQLException e) {
            showError("Failed to load dashboard", e.getMessage());
        }
    }

    private void updateRecommendation(DashboardSummary summary) {
        if (summary.recommendation().isEmpty()) {
            currentRecommendation = null;

            recommendationTitleLabel.setText("No recommendation yet");
            recommendationMetaLabel.setText("Create modules, topics, and worksheets to unlock recommendations.");
            recommendationReasonLabel.setText("");
            openRecommendationButton.setDisable(true);

            return;
        }

        currentRecommendation = summary.recommendation().get();

        Worksheet worksheet = currentRecommendation.worksheet();

        recommendationTitleLabel.setText(worksheet.title());

        recommendationMetaLabel.setText(
                "Topic: " + currentRecommendation.topic().name()
                        + " • Difficulty: " + worksheet.difficulty()
                        + " • Importance: " + worksheet.importance()
                        + " • Latest score: " + formatScore(worksheet.latestScorePercent())
        );

        recommendationReasonLabel.setText(currentRecommendation.explanation());
        openRecommendationButton.setDisable(false);
    }

    private void updateStats(DashboardSummary summary) {
        UserStats stats = summary.userStats();

        rankLabel.setText("Rank: " + summary.rank());
        xpLabel.setText(stats.xp() + " XP");

        int nextRankXp = summary.nextRankXp();

        if (nextRankXp <= stats.xp()) {
            rankProgressBar.setProgress(1.0);
        } else {
            rankProgressBar.setProgress((double) stats.xp() / nextRankXp);
        }

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
                        + " • Confidence: " + topic.confidence()
                        + " • Importance: " + topic.importance()
        );
        meta.getStyleClass().add("muted-text");
        meta.setWrapText(true);

        card.getChildren().addAll(title, meta);
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
                        + " • Score: " + attempt.score()
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
        return card;
    }

    private void openWorksheetDetail(Worksheet worksheet, Topic topic) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/pararepilot/fxml/WorksheetDetailView.fxml")
            );

            Parent root = loader.load();

            WorksheetDetailController controller = loader.getController();
            controller.setWorksheet(worksheet, topic);

            Stage stage = new Stage();
            stage.setTitle("Worksheet Details - " + worksheet.title());
            stage.initModality(Modality.APPLICATION_MODAL);

            Scene scene = new Scene(root, 760, 680);
            scene.getStylesheets().add(
                    getClass().getResource("/com/pararepilot/css/app.css").toExternalForm()
            );

            stage.setScene(scene);
            stage.showAndWait();

            loadDashboard();

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

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}