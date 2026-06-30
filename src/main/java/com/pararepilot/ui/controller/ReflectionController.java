package com.pararepilot.ui.controller;

import java.sql.SQLException;

import com.pararepilot.model.ConfidenceLevel;
import com.pararepilot.model.Worksheet;
import com.pararepilot.model.WorksheetAttempt;
import com.pararepilot.service.ReflectionService;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

public class ReflectionController {

    @FXML private Label summaryLabel;
    @FXML private ComboBox<ConfidenceLevel> confidenceCombo;
    @FXML private TextArea mainWeaknessArea;
    @FXML private TextArea nextActionArea;
    @FXML private TextArea reflectionNotesArea;
    @FXML private Label statusLabel;

    private final ReflectionService reflectionService = new ReflectionService();

    private Worksheet worksheet;
    private WorksheetAttempt attempt;
    private Runnable onReflectionSaved;

    @FXML
    private void initialize() {
        confidenceCombo.getItems().setAll(ConfidenceLevel.values());
        confidenceCombo.setValue(ConfidenceLevel.MEDIUM);
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
                        + " • Score: "
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
            reflectionService.completeReflection(
                    worksheet,
                    attempt,
                    confidenceCombo.getValue(),
                    mainWeaknessArea.getText(),
                    nextActionArea.getText(),
                    reflectionNotesArea.getText()
            );

            if (onReflectionSaved != null) {
                onReflectionSaved.run();
            }

            closeWindow();

        } catch (IllegalArgumentException e) {
            setStatus(e.getMessage());
        } catch (SQLException e) {
            showError("Failed to save reflection", e.getMessage());
        }
    }

    @FXML
    private void handleSkipReflection() {
        try {
            reflectionService.completeReflection(
                    worksheet,
                    attempt,
                    ConfidenceLevel.MEDIUM,
                    null,
                    null,
                    "Reflection skipped."
            );

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
        Stage stage = (Stage) summaryLabel.getScene().getWindow();
        stage.close();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}