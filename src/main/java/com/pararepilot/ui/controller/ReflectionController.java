package com.pararepilot.ui.controller;

import java.sql.SQLException;

import com.pararepilot.model.ConfidenceLevel;
import com.pararepilot.model.Worksheet;
import com.pararepilot.model.WorksheetAttempt;
import com.pararepilot.service.GamificationResult;
import com.pararepilot.service.ReflectionService;
import com.pararepilot.service.UserSettingsService;
import com.pararepilot.ui.LevelUi;
import com.pararepilot.ui.OverlayService;
import com.pararepilot.ui.UiAnimations;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

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

            showCompletionCelebration(result);

            if (onReflectionSaved != null) {
                onReflectionSaved.run();
            }

            closeWindow();

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

            showCompletionCelebration(result);

            if (onReflectionSaved != null) {
                onReflectionSaved.run();
            }

            closeWindow();

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

    private void showCompletionCelebration(GamificationResult result) throws SQLException {
        if (!userSettingsService.load().completionCelebrationsEnabled()) {
            return;
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Worksheet Complete");
        alert.setHeaderText("Worksheet complete");
        alert.setContentText("XP awarded: " + result.xpAwarded()
                + "\nRank: " + result.rank());
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
