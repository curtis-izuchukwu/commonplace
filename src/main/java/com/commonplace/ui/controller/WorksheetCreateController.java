package com.commonplace.ui.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.commonplace.press.PressApiException;
import com.commonplace.press.PressGenerateRequest;
import com.commonplace.press.PressGenerateResponse;
import com.commonplace.press.PressQuestionFormat;
import com.commonplace.model.DifficultyLevel;
import com.commonplace.model.ImportanceLevel;
import com.commonplace.model.StudyModule;
import com.commonplace.model.Topic;
import com.commonplace.repository.QuestionRepository;
import com.commonplace.service.PressWorksheetGenerationService;
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
    @FXML private TextField pressSubjectField;
    @FXML private TextField pressTopicField;
    @FXML private ComboBox<DifficultyLevel> pressDifficultyCombo;
    @FXML private Spinner<Integer> pressQuestionCountSpinner;
    @FXML private ComboBox<PressQuestionFormat> pressFormatCombo;
    @FXML private Button generatePressButton;
    @FXML private Button cancelPressButton;
    @FXML private Button saveWorksheetButton;
    @FXML private ProgressIndicator pressProgressIndicator;
    @FXML private Label pressStatusLabel;
    @FXML private VBox questionsContainer;
    @FXML private Label statusLabel;

    private final WorksheetCreationService service;
    private final QuestionImageStorage imageStorage = new QuestionImageStorage();
    private final PressWorksheetGenerationService pressService;
    private Task<PressGenerateResponse> generationTask;

    private Topic topic;
    private Runnable onWorksheetSaved;

    public WorksheetCreateController() {
        this(new WorksheetCreationService(), new PressWorksheetGenerationService());
    }

    WorksheetCreateController(WorksheetCreationService service, PressWorksheetGenerationService pressService) {
        this.service = service;
        this.pressService = pressService;
    }

    @FXML
    private void initialize() {
        difficultyCombo.getItems().setAll(DifficultyLevel.values());
        difficultyCombo.setValue(DifficultyLevel.MEDIUM);
        LevelUi.applyLevelBarStyling(difficultyCombo);

        importanceCombo.getItems().setAll(ImportanceLevel.values());
        importanceCombo.setValue(ImportanceLevel.MEDIUM);
        LevelUi.applyLevelBarStyling(importanceCombo);

        pressDifficultyCombo.getItems().setAll(DifficultyLevel.values());
        pressDifficultyCombo.setValue(DifficultyLevel.MEDIUM);
        LevelUi.applyLevelBarStyling(pressDifficultyCombo);

        pressQuestionCountSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 3)
        );

        pressFormatCombo.getItems().setAll(PressQuestionFormat.values());
        pressFormatCombo.setValue(PressQuestionFormat.SHORT_ANSWER);

        addQuestionRow();
        titleField.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null && newScene == null) {
                cancelGeneration();
            }
        });
    }

    public void setTopic(Topic topic, StudyModule module, Runnable onWorksheetSaved) {
        this.topic = topic;
        this.onWorksheetSaved = onWorksheetSaved;

        String moduleText = module == null ? "Unknown module" : module.name();
        parentTopicLabel.setText("Module: " + moduleText + " - Topic: " + topic.name());
    }

    @FXML
    private void handleAddQuestion() {
        addQuestionRow();
    }

    @FXML
    private void handleGenerateWithPress() {
        if (generationTask != null) {
            return;
        }
        if (topic == null) {
            setStatus("Choose a topic before generating questions.");
            setPressStatus("No parent topic selected.");
            UiAnimations.validationError(parentTopicLabel);
            return;
        }

        PressGenerateRequest request;
        try {
            request = new PressGenerateRequest(
                    pressSubjectField.getText(),
                    pressTopicField.getText(),
                    pressDifficultyCombo.getValue(),
                    readIntegerSpinner(pressQuestionCountSpinner, 1, 10, "Press question count"),
                    pressFormatCombo.getValue()
            );
        } catch (IllegalArgumentException e) {
            setPressStatus(e.getMessage());
            UiAnimations.validationError(pressSubjectField, pressTopicField, pressQuestionCountSpinner);
            return;
        }

        setPressBusy(true);
        setPressStatus("Generating editable draft questions with Press...");
        setStatus("");

        Task<PressGenerateResponse> task = new Task<>() {
            @Override
            protected PressGenerateResponse call() throws PressApiException {
                return pressService.generate(request);
            }
        };

        task.setOnSucceeded(event -> {
            if (generationTask != task) {
                return;
            }
            generationTask = null;
            setPressBusy(false);
            applyGeneratedWorksheet(task.getValue(), request);
        });

        task.setOnFailed(event -> {
            if (generationTask != task) {
                return;
            }
            generationTask = null;
            setPressBusy(false);
            Throwable error = task.getException();
            String message = error == null || textBlank(error.getMessage())
                    ? "Press generation failed. You can still create the worksheet manually."
                    : error.getMessage();

            setPressStatus(message);
            setStatus(message);
        });

        generationTask = task;
        Thread worker = new Thread(task, "press-generate-worker");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void handleSaveWorksheet() {
        if (generationTask != null) {
            setStatus("Wait for Press to finish or stop generation before saving.");
            return;
        }
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

    @FXML
    private void handleCancelGeneration() {
        cancelGeneration();
        setPressStatus("Press generation cancelled. Your draft has been kept.");
    }

    private void cancelGeneration() {
        Task<PressGenerateResponse> task = generationTask;
        generationTask = null;
        if (task != null) {
            task.cancel(true);
        }
        setPressBusy(false);
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

        Spinner<Integer> maxMarksSpinner = new Spinner<>(1, Math.max(100, maxMarks), Math.max(1, maxMarks));
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
            PressGenerateResponse response,
            PressGenerateRequest request
    ) {

        if (response.questions().isEmpty()) {
            String message = "Press did not return any usable questions. Manual creation is still available.";
            setPressStatus(message);
            setStatus(message);
            return;
        }

        if (titleField.getText() == null || titleField.getText().isBlank()) {
            String generatedTitle = response.title().isBlank()
                    ? request.topic() + " Practice"
                    : response.title();
            titleField.setText(generatedTitle.substring(0, Math.min(generatedTitle.length(), 120)));
        }

        if (descriptionArea.getText() == null || descriptionArea.getText().isBlank()) {
            descriptionArea.setText(response.description().isBlank()
                    ? "Generated with Press. Review and edit before saving."
                    : response.description());
        }

        questionsContainer.getChildren().removeIf(child -> child instanceof VBox row && questionRowIsBlank(row));
        renumberQuestionRows();

        response.questions().forEach(question -> {
            QuestionRepository.QuestionDraft draft = question.toQuestionDraft();
            addQuestionRow(
                    draft.prompt(),
                    draft.markScheme(),
                    draft.maxMarks(),
                    draft.tags()
            );
        });

        String message = response.questions().size() != request.questionCount()
                ? "Press returned " + response.questions().size()
                        + " of " + request.questionCount() + " requested questions. Review before saving."
                : "Generated " + response.questions().size()
                        + " editable questions. Review before saving.";

        if (response.questions().stream().anyMatch(question -> question.toQuestionDraft().markScheme().isBlank())) {
            message += " Add the missing mark schemes before saving.";
        }
        String mode = response.metadata().getOrDefault("mode", "");
        if (!mode.isBlank() && !"ai".equalsIgnoreCase(mode)) {
            message += " Press generation mode: " + mode + ".";
        }

        UiAnimations.validationSuccess(questionsContainer);
        setPressStatus(message);
        setStatus(message);
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

    private void setPressBusy(boolean generating) {
        generatePressButton.setDisable(generating);
        pressSubjectField.setDisable(generating);
        pressTopicField.setDisable(generating);
        saveWorksheetButton.setDisable(generating);
        cancelPressButton.setVisible(generating);
        cancelPressButton.setManaged(generating);
        pressDifficultyCombo.setDisable(generating);
        pressQuestionCountSpinner.setDisable(generating);
        pressFormatCombo.setDisable(generating);
        pressProgressIndicator.setVisible(generating);
        pressProgressIndicator.setManaged(generating);
        generatePressButton.setText(generating ? "Generating..." : "Generate");
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

            SpinnerValueFactory.IntegerSpinnerValueFactory marksFactory =
                    (SpinnerValueFactory.IntegerSpinnerValueFactory) maxMarksSpinner.getValueFactory();
            int maxMarks = readIntegerSpinner(maxMarksSpinner, 1, marksFactory.getMax(),
                    "Question " + (i + 1) + " marks");
            validateQuestionDraftInput(
                    i + 1,
                    promptArea.getText(),
                    markSchemeArea.getText(),
                    maxMarks
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
                    maxMarks,
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

    private static int readIntegerSpinner(Spinner<Integer> spinner, int min, int max, String label) {
        try {
            int value = Integer.parseInt(spinner.getEditor().getText().trim());
            if (value >= min && value <= max) {
                spinner.getValueFactory().setValue(value);
                return value;
            }
        } catch (NumberFormatException ignored) {
            // Report malformed input instead of using the spinner's stale committed value.
        }
        throw new IllegalArgumentException(label + " must be a whole number between " + min + " and " + max + ".");
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

    private void setPressStatus(String message) {
        pressStatusLabel.setText(message == null ? "" : message);
    }

    private void closeWindow() {
        cancelGeneration();
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
