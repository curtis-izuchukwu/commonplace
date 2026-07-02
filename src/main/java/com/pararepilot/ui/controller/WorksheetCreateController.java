package com.pararepilot.ui.controller;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.pararepilot.model.DifficultyLevel;
import com.pararepilot.model.ImportanceLevel;
import com.pararepilot.model.StudyModule;
import com.pararepilot.model.Topic;
import com.pararepilot.repository.QuestionRepository;
import com.pararepilot.service.WorksheetCreationService;
import com.pararepilot.ui.LevelUi;
import com.pararepilot.ui.OverlayService;
import com.pararepilot.ui.UiAnimations;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class WorksheetCreateController {

    @FXML private Label parentTopicLabel;
    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private ComboBox<DifficultyLevel> difficultyCombo;
    @FXML private ComboBox<ImportanceLevel> importanceCombo;
    @FXML private VBox questionsContainer;
    @FXML private Label statusLabel;

    private final WorksheetCreationService service = new WorksheetCreationService();

    private Topic topic;
    private StudyModule module;
    private Runnable onWorksheetSaved;

    @FXML
    private void initialize() {
        difficultyCombo.getItems().setAll(DifficultyLevel.values());
        difficultyCombo.setValue(DifficultyLevel.MEDIUM);
        LevelUi.applyLevelBarStyling(difficultyCombo);

        importanceCombo.getItems().setAll(ImportanceLevel.values());
        importanceCombo.setValue(ImportanceLevel.MEDIUM);
        LevelUi.applyLevelBarStyling(importanceCombo);

        addQuestionRow();
    }

    public void setTopic(Topic topic, StudyModule module, Runnable onWorksheetSaved) {
        this.topic = topic;
        this.module = module;
        this.onWorksheetSaved = onWorksheetSaved;

        String moduleText = module == null ? "Unknown module" : module.name();
        parentTopicLabel.setText("Module: " + moduleText + " - Topic: " + topic.name());
    }

    @FXML
    private void handleAddQuestion() {
        addQuestionRow();
    }

    @FXML
    private void handleSaveWorksheet() {
        if (topic == null) {
            setStatus("No parent topic selected.");
            return;
        }

        try {
            List<QuestionRepository.QuestionDraft> drafts = collectQuestionDrafts();

            service.createWorksheetWithQuestions(
                    topic.id(),
                    titleField.getText(),
                    descriptionArea.getText(),
                    difficultyCombo.getValue(),
                    importanceCombo.getValue(),
                    drafts
            );

            UiAnimations.validationSuccess(titleField, descriptionArea);

            if (onWorksheetSaved != null) {
                onWorksheetSaved.run();
            }

            closeWindow();

        } catch (IllegalArgumentException e) {
            UiAnimations.validationError(titleField, questionsContainer);
            setStatus(e.getMessage());
        } catch (SQLException e) {
            showError("Failed to save worksheet", e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    private void addQuestionRow() {
        int questionNumber = questionsContainer.getChildren().size() + 1;

        VBox row = new VBox(8);
        row.getStyleClass().add("question-card");

        Label heading = new Label("Question " + questionNumber);
        heading.getStyleClass().add("card-title");

        TextArea promptArea = new TextArea();
        promptArea.setPromptText("Question prompt");
        promptArea.setWrapText(true);
        promptArea.setPrefRowCount(3);
        promptArea.setUserData("prompt");

        TextArea markSchemeArea = new TextArea();
        markSchemeArea.setPromptText("Mark scheme");
        markSchemeArea.setWrapText(true);
        markSchemeArea.setPrefRowCount(3);
        markSchemeArea.setUserData("markScheme");

        Spinner<Integer> maxMarksSpinner = new Spinner<>(1, 100, 3);
        maxMarksSpinner.setEditable(true);
        maxMarksSpinner.setUserData("maxMarks");
        maxMarksSpinner.setMaxWidth(220);

        Button removeButton = new Button("Remove Question");
        removeButton.getStyleClass().add("danger-button");
        removeButton.setOnAction(event -> {
            UiAnimations.animateCardRemoval(row, () -> {
                questionsContainer.getChildren().remove(row);
                renumberQuestionRows();
            });
        });

        Label maxMarksLabel = new Label("Max marks");
        maxMarksLabel.getStyleClass().add("small-label");

        row.getChildren().addAll(
                heading,
                promptArea,
                markSchemeArea,
                maxMarksLabel,
                maxMarksSpinner,
                removeButton
        );

        questionsContainer.getChildren().add(row);
        UiAnimations.animateCardEntry(row);
    }

    private List<QuestionRepository.QuestionDraft> collectQuestionDrafts() {
        List<QuestionRepository.QuestionDraft> drafts = new ArrayList<>();

        for (int i = 0; i < questionsContainer.getChildren().size(); i++) {
            VBox row = (VBox) questionsContainer.getChildren().get(i);

            TextArea promptArea = null;
            TextArea markSchemeArea = null;
            Spinner<Integer> maxMarksSpinner = null;

            for (var child : row.getChildren()) {
                if (child instanceof TextArea textArea && "prompt".equals(textArea.getUserData())) {
                    promptArea = textArea;
                }

                if (child instanceof TextArea textArea && "markScheme".equals(textArea.getUserData())) {
                    markSchemeArea = textArea;
                }

                if (child instanceof Spinner<?> spinner && "maxMarks".equals(spinner.getUserData())) {
                    @SuppressWarnings("unchecked")
                    Spinner<Integer> typedSpinner = (Spinner<Integer>) spinner;
                    maxMarksSpinner = typedSpinner;
                }
            }

            if (promptArea == null || markSchemeArea == null || maxMarksSpinner == null) {
                throw new IllegalStateException("Question form row is missing fields.");
            }

            drafts.add(new QuestionRepository.QuestionDraft(
                    promptArea.getText(),
                    markSchemeArea.getText(),
                    maxMarksSpinner.getValue(),
                    null
            ));
        }

        return drafts;
    }

    private void renumberQuestionRows() {
        for (int i = 0; i < questionsContainer.getChildren().size(); i++) {
            VBox row = (VBox) questionsContainer.getChildren().get(i);

            if (!row.getChildren().isEmpty() && row.getChildren().get(0) instanceof Label label) {
                label.setText("Question " + (i + 1));
            }
        }
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void closeWindow() {
        OverlayService.closeFrom(titleField);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
