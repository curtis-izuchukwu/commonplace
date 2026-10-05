package com.commonplace.ui.controller;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import com.commonplace.model.DifficultyLevel;
import com.commonplace.model.ImportanceLevel;
import com.commonplace.model.StudyModule;
import com.commonplace.model.Topic;
import com.commonplace.model.Worksheet;
import com.commonplace.service.WorksheetCreationService;
import com.commonplace.ui.LevelUi;
import com.commonplace.ui.OverlayService;
import com.commonplace.ui.UiAnimations;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

public class PdfWorksheetCreateController {

    @FXML private VBox pdfWorksheetRoot;
    @FXML private Label parentTopicLabel;
    @FXML private TextField worksheetPdfField;
    @FXML private TextField markSchemePdfField;
    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private ComboBox<DifficultyLevel> difficultyCombo;
    @FXML private ComboBox<ImportanceLevel> importanceCombo;
    @FXML private Button saveButton;
    @FXML private Label statusLabel;

    private final WorksheetCreationService service = new WorksheetCreationService();

    private Topic topic;
    private File worksheetPdf;
    private File markSchemePdf;
    private Runnable onWorksheetSaved;

    @FXML
    private void initialize() {
        difficultyCombo.getItems().setAll(DifficultyLevel.values());
        difficultyCombo.setValue(DifficultyLevel.MEDIUM);
        LevelUi.applyLevelBarStyling(difficultyCombo);

        importanceCombo.getItems().setAll(ImportanceLevel.values());
        importanceCombo.setValue(ImportanceLevel.MEDIUM);
        LevelUi.applyLevelBarStyling(importanceCombo);
    }

    public void setTopic(Topic topic, StudyModule module, Runnable onWorksheetSaved) {
        this.topic = topic;
        this.onWorksheetSaved = onWorksheetSaved;

        String moduleName = module == null ? "Unknown module" : module.name();
        parentTopicLabel.setText(
                "Module  " + moduleName + "  •  Topic  " + topic.name());
    }

    @FXML
    private void handleChooseWorksheetPdf() {
        File selected = choosePdf("Choose Worksheet PDF");
        if (selected == null) {
            return;
        }

        worksheetPdf = selected;
        worksheetPdfField.setText(selected.getAbsolutePath());
        if (titleField.getText() == null || titleField.getText().isBlank()) {
            titleField.setText(withoutPdfExtension(selected.getName()));
        }
        setStatus("");
    }

    @FXML
    private void handleChooseMarkSchemePdf() {
        File selected = choosePdf("Choose Mark Scheme PDF");
        if (selected == null) {
            return;
        }

        markSchemePdf = selected;
        markSchemePdfField.setText(selected.getAbsolutePath());
        setStatus("");
    }

    @FXML
    private void handleClearMarkScheme() {
        markSchemePdf = null;
        markSchemePdfField.clear();
        setStatus("Mark scheme removed.");
    }

    @FXML
    private void handleSave() {
        if (topic == null) {
            setStatus("No parent topic selected.");
            UiAnimations.validationError(parentTopicLabel);
            return;
        }
        if (worksheetPdf == null) {
            setStatus("Choose the worksheet PDF first.");
            UiAnimations.validationError(worksheetPdfField);
            return;
        }
        if (titleField.getText() == null || titleField.getText().isBlank()) {
            setStatus("Worksheet title cannot be empty.");
            UiAnimations.validationError(titleField);
            return;
        }

        saveButton.setDisable(true);
        setStatus("Saving PDF worksheet locally...");

        String title = titleField.getText();
        String description = descriptionArea.getText();
        DifficultyLevel difficulty = difficultyCombo.getValue();
        ImportanceLevel importance = importanceCombo.getValue();
        File selectedWorksheet = worksheetPdf;
        File selectedMarkScheme = markSchemePdf;

        Task<Worksheet> task = new Task<>() {
            @Override
            protected Worksheet call() throws SQLException, IOException {
                return service.createPdfWorksheet(
                        topic.id(),
                        title,
                        description,
                        difficulty,
                        importance,
                        selectedWorksheet.toPath(),
                        selectedMarkScheme == null ? null : selectedMarkScheme.toPath());
            }
        };

        task.setOnSucceeded(event -> {
            UiAnimations.validationSuccess(titleField, worksheetPdfField);
            if (onWorksheetSaved != null) {
                onWorksheetSaved.run();
            }
            OverlayService.closeFrom(pdfWorksheetRoot);
        });
        task.setOnFailed(event -> {
            saveButton.setDisable(false);
            Throwable error = task.getException();
            setStatus(
                    "Could not save PDF worksheet: "
                            + (error == null ? "Unknown error" : error.getMessage()));
            UiAnimations.validationError(titleField, worksheetPdfField);
        });

        Thread worker = new Thread(task, "pdf-worksheet-save-worker");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void handleCancel() {
        OverlayService.closeFrom(pdfWorksheetRoot);
    }

    private File choosePdf(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        return chooser.showOpenDialog(pdfWorksheetRoot.getScene().getWindow());
    }

    private String withoutPdfExtension(String fileName) {
        return fileName.toLowerCase().endsWith(".pdf")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
    }

    private void setStatus(String message) {
        statusLabel.setText(message == null ? "" : message);
    }
}
