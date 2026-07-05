package com.pararepilot.ui.controller;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.pararepilot.model.ConfidenceLevel;
import com.pararepilot.model.UserSettings;
import com.pararepilot.model.Worksheet;
import com.pararepilot.model.WorksheetAttempt;
import com.pararepilot.service.GamificationResult;
import com.pararepilot.service.ReflectionService;
import com.pararepilot.service.UserSettingsService;
import com.pararepilot.ui.AppIcon;
import com.pararepilot.ui.LevelUi;
import com.pararepilot.ui.OverlayService;
import com.pararepilot.ui.UiAnimations;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class ReflectionController {

    @FXML private Label summaryLabel;
    @FXML private ComboBox<ConfidenceLevel> confidenceCombo;
    @FXML private TextArea mainWeaknessArea;
    @FXML private TextArea nextActionArea;
    @FXML private TextArea reflectionNotesArea;
    @FXML private Label statusLabel;

    private final ReflectionService reflectionService = new ReflectionService();
    private final UserSettingsService userSettingsService = new UserSettingsService();

    private Worksheet worksheet;
    private WorksheetAttempt attempt;
    private Runnable onReflectionSaved;

    @FXML
    private void initialize() {
        confidenceCombo.getItems().setAll(ConfidenceLevel.values());
        confidenceCombo.setValue(ConfidenceLevel.MEDIUM);
        LevelUi.applyLevelBarStyling(confidenceCombo);
    }

    public void setContext(
            Worksheet worksheet,
            WorksheetAttempt attempt,
            Runnable onReflectionSaved
    ) {
        this.worksheet = worksheet;
        this.attempt = attempt;
        this.onReflectionSaved = onReflectionSaved;

        summaryLabel.setText(
                worksheet.title()
                        + " - Score: "
                        + attempt.score()
                        + "/"
                        + attempt.maxScore()
                        + " ("
                        + String.format("%.0f%%", attempt.scorePercent())
                        + ")"
        );
    }

    @FXML
    private void handleSaveReflection() {
        try {
            GamificationResult result = reflectionService.completeReflection(
                    worksheet,
                    attempt,
                    confidenceCombo.getValue(),
                    mainWeaknessArea.getText(),
                    nextActionArea.getText(),
                    reflectionNotesArea.getText()
            );

            finishAfterCompletion(result);

        } catch (IllegalArgumentException e) {
            UiAnimations.validationError(mainWeaknessArea, nextActionArea, reflectionNotesArea);
            setStatus(e.getMessage());
        } catch (SQLException e) {
            showError("Failed to save reflection", e.getMessage());
        }
    }

    @FXML
    private void handleSkipReflection() {
        try {
            GamificationResult result = reflectionService.completeReflection(
                    worksheet,
                    attempt,
                    ConfidenceLevel.MEDIUM,
                    null,
                    null,
                    "Reflection skipped."
            );

            finishAfterCompletion(result);

        } catch (SQLException e) {
            showError("Failed to skip reflection", e.getMessage());
        }
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void closeWindow() {
        OverlayService.closeFrom(summaryLabel);
    }

    private void finishAfterCompletion(GamificationResult result) throws SQLException {
        UserSettings settings = userSettingsService.load();

        if (!settings.completionCelebrationsEnabled()) {
            finishReflection();
            return;
        }

        showCompletionCelebration(result, this::finishReflection);
    }

    private void finishReflection() {
        if (onReflectionSaved != null) {
            onReflectionSaved.run();
        }

        closeWindow();
    }

    private void showCompletionCelebration(GamificationResult result, Runnable onDismiss) {
        var scene = summaryLabel.getScene();

        if (scene == null || !(scene.getRoot() instanceof StackPane rootStack)) {
            onDismiss.run();
            return;
        }

        VBox content = new VBox();
        content.setAlignment(Pos.CENTER_LEFT);
        content.setMaxSize(420, Region.USE_PREF_SIZE);
        content.getStyleClass().add("xp-reward-content");

        Label eyebrow = new Label("Worksheet complete");
        eyebrow.getStyleClass().add("xp-reward-eyebrow");

        Label title = new Label("Reflection saved");
        title.getStyleClass().add("xp-reward-title");

        Label xpAwarded = new Label("+" + result.xpAwarded() + " XP");
        xpAwarded.getStyleClass().add("xp-reward-amount");

        Label rank = new Label("Rank: " + result.rank());
        rank.getStyleClass().add("xp-reward-rank");

        Button okButton = new Button("OK");
        okButton.setDefaultButton(true);
        okButton.setCancelButton(true);
        okButton.getStyleClass().addAll("primary-button", "xp-reward-ok-button");

        HBox actions = new HBox(okButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("xp-reward-actions");

        content.getChildren().setAll(eyebrow, title, xpAwarded, rank, actions);
        content.setOnMouseClicked(event -> event.consume());

        StackPane scrim = new StackPane(content);
        scrim.getStyleClass().add("xp-reward-scrim");
        scrim.setFocusTraversable(true);
        scrim.setOnMouseClicked(event -> event.consume());

        AtomicBoolean dismissed = new AtomicBoolean(false);

        Runnable closeReward = () -> {
            if (!dismissed.compareAndSet(false, true)) {
                return;
            }

            UiAnimations.animateOverlayClose(scrim, () -> {
                rootStack.getChildren().remove(scrim);
                onDismiss.run();
            });
        };

        okButton.setOnAction(event -> closeReward.run());
        scrim.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                closeReward.run();
                event.consume();
            }
        });

        rootStack.getChildren().add(scrim);
        UiAnimations.animateOverlayOpen(scrim, content);
        Platform.runLater(okButton::requestFocus);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        AppIcon.applyTo(alert);
        alert.showAndWait();
    }
}
