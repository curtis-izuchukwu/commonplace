package com.pararepilot.ui.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.pararepilot.model.DifficultyLevel;
import com.pararepilot.model.ImportanceLevel;
import com.pararepilot.model.StudyModule;
import com.pararepilot.model.Topic;
import com.pararepilot.repository.QuestionRepository;
import com.pararepilot.service.QuestionImageStorage;
import com.pararepilot.service.WorksheetCreationService;
import com.pararepilot.ui.AppIcon;
import com.pararepilot.ui.LevelUi;
import com.pararepilot.ui.OverlayService;
import com.pararepilot.ui.QuestionImageViewFactory;
import com.pararepilot.ui.UiAnimations;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

public class WorksheetCreateController {

    private static final String QUESTION_IMAGE_SOURCE_KEY = "question-image-source";

    @FXML private Label parentTopicLabel;
    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private ComboBox<DifficultyLevel> difficultyCombo;
    @FXML private ComboBox<ImportanceLevel> importanceCombo;
    @FXML private VBox questionsContainer;
    @FXML private Label statusLabel;

    private final WorksheetCreationService service = new WorksheetCreationService();
    private final QuestionImageStorage imageStorage = new QuestionImageStorage();

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
            validateWorksheetTitle();
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
        } catch (IOException e) {
            showError("Failed to attach question image", e.getMessage());
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

        Button imageButton = new Button("Add image");
        imageButton.getStyleClass().add("compact-button");

        Button removeImageButton = new Button("Remove image");
        removeImageButton.getStyleClass().add("danger-button");
        removeImageButton.setVisible(false);
        removeImageButton.setManaged(false);

        Label imageStatusLabel = new Label("No image selected");
        imageStatusLabel.getStyleClass().add("muted-text");
        imageStatusLabel.setWrapText(true);

        ImageView imagePreview = QuestionImageViewFactory.createPreview(null, 320, 180);
        imagePreview.setVisible(false);
        imagePreview.setManaged(false);

        imageButton.setOnAction(event -> chooseQuestionImage(
                row,
                imageButton,
                removeImageButton,
                imageStatusLabel,
                imagePreview
        ));

        removeImageButton.setOnAction(event -> removeQuestionImage(
                row,
                imageButton,
                removeImageButton,
                imageStatusLabel,
                imagePreview
        ));

        HBox imageActions = new HBox(8, imageButton, removeImageButton, imageStatusLabel);
        imageActions.getStyleClass().add("question-image-actions");
        imageActions.setAlignment(Pos.CENTER_LEFT);

        VBox imageBox = new VBox(6, imageActions, imagePreview);
        imageBox.getStyleClass().add("question-image-preview");

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
                imageBox,
                maxMarksLabel,
                maxMarksSpinner,
                removeButton
        );

        questionsContainer.getChildren().add(row);
        UiAnimations.animateCardEntry(row);
    }

    private void chooseQuestionImage(
            VBox row,
            Button imageButton,
            Button removeImageButton,
            Label imageStatusLabel,
            ImageView imagePreview
    ) {

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Question Image");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("PNG", "*.png"),
                new FileChooser.ExtensionFilter("JPEG", "*.jpg", "*.jpeg")
        );

        var selectedFile = chooser.showOpenDialog(row.getScene().getWindow());

        if (selectedFile == null) {
            return;
        }

        Path selectedPath = selectedFile.toPath();

        if (!QuestionImageStorage.isSupportedImage(selectedPath)) {
            setStatus("Question images must be PNG, JPG, or JPEG.");
            UiAnimations.validationError(row);
            return;
        }

        ImageView selectedPreview = QuestionImageViewFactory.createPreview(selectedPath, 320, 180);

        if (selectedPreview.getImage() == null) {
            setStatus("Selected image could not be loaded.");
            UiAnimations.validationError(row);
            return;
        }

        row.getProperties().put(QUESTION_IMAGE_SOURCE_KEY, selectedPath);
        imagePreview.setImage(selectedPreview.getImage());
        imagePreview.setVisible(true);
        imagePreview.setManaged(true);
        imageStatusLabel.setText(selectedPath.getFileName().toString());
        imageButton.setText("Replace image");
        removeImageButton.setVisible(true);
        removeImageButton.setManaged(true);
        setStatus("");
    }

    private void removeQuestionImage(
            VBox row,
            Button imageButton,
            Button removeImageButton,
            Label imageStatusLabel,
            ImageView imagePreview
    ) {

        row.getProperties().remove(QUESTION_IMAGE_SOURCE_KEY);
        imagePreview.setImage(null);
        imagePreview.setVisible(false);
        imagePreview.setManaged(false);
        imageStatusLabel.setText("No image selected");
        imageButton.setText("Add image");
        removeImageButton.setVisible(false);
        removeImageButton.setManaged(false);
    }

    private List<QuestionRepository.QuestionDraft> collectQuestionDrafts() throws IOException {
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

            validateQuestionDraftInput(
                    i + 1,
                    promptArea.getText(),
                    markSchemeArea.getText(),
                    maxMarksSpinner.getValue()
            );

            String imagePath = null;
            Object imageSource = row.getProperties().get(QUESTION_IMAGE_SOURCE_KEY);

            if (imageSource instanceof Path sourcePath) {
                imagePath = imageStorage.copyIntoImageStore(sourcePath);
            }

            drafts.add(new QuestionRepository.QuestionDraft(
                    promptArea.getText(),
                    markSchemeArea.getText(),
                    maxMarksSpinner.getValue(),
                    null,
                    imagePath
            ));
        }

        return drafts;
    }

    private void validateQuestionDraftInput(
            int questionNumber,
            String prompt,
            String markScheme,
            int maxMarks
    ) {

        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Question " + questionNumber + " needs a prompt.");
        }

        if (markScheme == null || markScheme.isBlank()) {
            throw new IllegalArgumentException("Question " + questionNumber + " needs a mark scheme.");
        }

        if (maxMarks <= 0) {
            throw new IllegalArgumentException("Question " + questionNumber + " must have at least 1 mark.");
        }
    }

    private void renumberQuestionRows() {
        for (int i = 0; i < questionsContainer.getChildren().size(); i++) {
            VBox row = (VBox) questionsContainer.getChildren().get(i);

            if (!row.getChildren().isEmpty() && row.getChildren().get(0) instanceof Label label) {
                label.setText("Question " + (i + 1));
            }
        }
    }

    private void validateWorksheetTitle() {
        String title = titleField.getText();

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Worksheet title cannot be empty.");
        }

        if (title.trim().length() > 120) {
            throw new IllegalArgumentException("Worksheet title must be 120 characters or fewer.");
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
        AppIcon.applyTo(alert);
        alert.showAndWait();
    }
}
