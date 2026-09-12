package com.commonplace.ui.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.commonplace.flightdeck.FlightDeckApiException;
import com.commonplace.flightdeck.FlightDeckGenerateRequest;
import com.commonplace.flightdeck.FlightDeckGenerateResponse;
import com.commonplace.flightdeck.FlightDeckQuestionFormat;
import com.commonplace.model.DifficultyLevel;
import com.commonplace.model.ImportanceLevel;
import com.commonplace.model.StudyModule;
import com.commonplace.model.Topic;
import com.commonplace.repository.QuestionRepository;
import com.commonplace.service.FlightDeckWorksheetGenerationService;
import com.commonplace.service.QuestionImageStorage;
import com.commonplace.service.WorksheetCreationService;
import com.commonplace.ui.AppIcon;
import com.commonplace.ui.LevelUi;
import com.commonplace.ui.OverlayService;
import com.commonplace.ui.QuestionImageViewFactory;
import com.commonplace.ui.UiAnimations;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

public class WorksheetCreateController {

    private static final String QUESTION_IMAGE_SOURCE_KEY = "question-image-source";
    private static final String QUESTION_TAGS_KEY = "question-tags";

    @FXML private Label parentTopicLabel;
    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private ComboBox<DifficultyLevel> difficultyCombo;
    @FXML private ComboBox<ImportanceLevel> importanceCombo;
    @FXML private ComboBox<DifficultyLevel> flightDeckDifficultyCombo;
    @FXML private Spinner<Integer> flightDeckQuestionCountSpinner;
    @FXML private ComboBox<FlightDeckQuestionFormat> flightDeckFormatCombo;
    @FXML private Button generateFlightDeckButton;
    @FXML private ProgressIndicator flightDeckProgressIndicator;
    @FXML private Label flightDeckStatusLabel;
    @FXML private VBox questionsContainer;
    @FXML private Label statusLabel;

    private final WorksheetCreationService service = new WorksheetCreationService();
    private final QuestionImageStorage imageStorage = new QuestionImageStorage();
    private final FlightDeckWorksheetGenerationService flightDeckService =
            new FlightDeckWorksheetGenerationService();

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

        flightDeckDifficultyCombo.getItems().setAll(DifficultyLevel.values());
        flightDeckDifficultyCombo.setValue(DifficultyLevel.MEDIUM);
        LevelUi.applyLevelBarStyling(flightDeckDifficultyCombo);

        flightDeckQuestionCountSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 3)
        );

        flightDeckFormatCombo.getItems().setAll(FlightDeckQuestionFormat.values());
        flightDeckFormatCombo.setValue(FlightDeckQuestionFormat.SHORT_ANSWER);

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
    private void handleGenerateWithFlightDeck() {
        if (topic == null) {
            setStatus("Choose a topic before generating questions.");
            setFlightDeckStatus("No parent topic selected.");
            UiAnimations.validationError(parentTopicLabel);
            return;
        }

        String subject = module == null || module.name().isBlank() ? "Study" : module.name();
        int requestedCount = flightDeckQuestionCountSpinner.getValue();

        FlightDeckGenerateRequest request = new FlightDeckGenerateRequest(
                subject,
                topic.name(),
                flightDeckDifficultyCombo.getValue(),
                requestedCount,
                flightDeckFormatCombo.getValue()
        );

        setFlightDeckBusy(true);
        setFlightDeckStatus("Generating editable draft questions with FlightDeck...");
        setStatus("");

        Task<FlightDeckGenerateResponse> task = new Task<>() {
            @Override
            protected FlightDeckGenerateResponse call() throws FlightDeckApiException {
                return flightDeckService.generate(request);
            }
        };

        task.setOnSucceeded(event -> {
            setFlightDeckBusy(false);
            applyGeneratedWorksheet(task.getValue(), requestedCount);
        });

        task.setOnFailed(event -> {
            setFlightDeckBusy(false);
            Throwable error = task.getException();

            if (error != null) {
                error.printStackTrace();
            }

            String message = error == null
                    ? "FlightDeck generation failed. You can still create the worksheet manually."
                    : error.getMessage();

            setFlightDeckStatus(message);
            setStatus(message);
        });

        Thread worker = new Thread(task, "flightdeck-generate-worker");
        worker.setDaemon(true);
        worker.start();
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
        addQuestionRow("", "", 3, null);
    }

    private void addQuestionRow(
            String prompt,
            String markScheme,
            int maxMarks,
            String tags
    ) {
        int questionNumber = questionsContainer.getChildren().size() + 1;

        VBox row = new VBox(8);
        row.getStyleClass().add("question-card");

        if (tags != null && !tags.isBlank()) {
            row.getProperties().put(QUESTION_TAGS_KEY, tags);
        }

        Label heading = new Label("Question " + questionNumber);
        heading.getStyleClass().add("card-title");

        TextArea promptArea = new TextArea(prompt == null ? "" : prompt);
        promptArea.setPromptText("Question prompt");
        promptArea.setWrapText(true);
        promptArea.setPrefRowCount(3);
        promptArea.setUserData("prompt");

        TextArea markSchemeArea = new TextArea(markScheme == null ? "" : markScheme);
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

        Spinner<Integer> maxMarksSpinner = new Spinner<>(1, 100, Math.max(1, maxMarks));
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

    private void applyGeneratedWorksheet(
            FlightDeckGenerateResponse response,
            int requestedCount
    ) {

        if (response.questions().isEmpty()) {
            String message = "FlightDeck did not return any usable questions. Manual creation is still available.";
            setFlightDeckStatus(message);
            setStatus(message);
            return;
        }

        if (titleField.getText() == null || titleField.getText().isBlank()) {
            titleField.setText(response.title().isBlank()
                    ? topic.name() + " Practice"
                    : response.title());
        }

        if (descriptionArea.getText() == null || descriptionArea.getText().isBlank()) {
            descriptionArea.setText(response.description().isBlank()
                    ? "Generated with FlightDeck. Review and edit before saving."
                    : response.description());
        }

        removeSingleBlankQuestionRow();

        response.questions().forEach(question -> {
            QuestionRepository.QuestionDraft draft = question.toQuestionDraft();
            addQuestionRow(
                    draft.prompt(),
                    draft.markScheme(),
                    draft.maxMarks(),
                    draft.tags()
            );
        });

        String message = response.questions().size() < requestedCount
                ? "FlightDeck returned " + response.questions().size()
                        + " of " + requestedCount + " requested questions. Review before saving."
                : "Generated " + response.questions().size()
                        + " editable questions. Review before saving.";

        UiAnimations.validationSuccess(questionsContainer);
        setFlightDeckStatus(message);
        setStatus(message);
    }

    private void removeSingleBlankQuestionRow() {
        if (questionsContainer.getChildren().size() != 1
                || !(questionsContainer.getChildren().get(0) instanceof VBox row)) {
            return;
        }

        if (!questionRowIsBlank(row)) {
            return;
        }

        questionsContainer.getChildren().clear();
    }

    private boolean questionRowIsBlank(VBox row) {
        TextArea promptArea = findQuestionTextArea(row, "prompt");
        TextArea markSchemeArea = findQuestionTextArea(row, "markScheme");

        return textBlank(promptArea.getText())
                && textBlank(markSchemeArea.getText())
                && !row.getProperties().containsKey(QUESTION_IMAGE_SOURCE_KEY);
    }

    private TextArea findQuestionTextArea(VBox row, String userData) {
        for (var child : row.getChildren()) {
            if (child instanceof TextArea textArea && userData.equals(textArea.getUserData())) {
                return textArea;
            }
        }

        throw new IllegalStateException("Question row is missing " + userData + " field.");
    }

    private boolean textBlank(String value) {
        return value == null || value.isBlank();
    }

    private void setFlightDeckBusy(boolean generating) {
        generateFlightDeckButton.setDisable(generating);
        flightDeckDifficultyCombo.setDisable(generating);
        flightDeckQuestionCountSpinner.setDisable(generating);
        flightDeckFormatCombo.setDisable(generating);
        flightDeckProgressIndicator.setVisible(generating);
        flightDeckProgressIndicator.setManaged(generating);
        generateFlightDeckButton.setText(generating ? "Generating..." : "Generate");
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

            String tags = null;
            Object savedTags = row.getProperties().get(QUESTION_TAGS_KEY);

            if (savedTags instanceof String value && !value.isBlank()) {
                tags = value;
            }

            drafts.add(new QuestionRepository.QuestionDraft(
                    promptArea.getText(),
                    markSchemeArea.getText(),
                    maxMarksSpinner.getValue(),
                    tags,
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

    private void setFlightDeckStatus(String message) {
        flightDeckStatusLabel.setText(message == null ? "" : message);
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
