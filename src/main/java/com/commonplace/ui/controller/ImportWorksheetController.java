package com.commonplace.ui.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.commonplace.importer.ExtractedPdfImage;
import com.commonplace.importer.ImportIssue;
import com.commonplace.importer.ImportedQuestionDraft;
import com.commonplace.importer.ImportedWorksheetDraft;
import com.commonplace.importer.PdfImportService;
import com.commonplace.model.DifficultyLevel;
import com.commonplace.model.ImportanceLevel;
import com.commonplace.model.StudyModule;
import com.commonplace.model.Topic;
import com.commonplace.repository.QuestionRepository;
import com.commonplace.service.QuestionImageStorage;
import com.commonplace.service.StudyStructureService;
import com.commonplace.service.WorksheetCreationService;
import com.commonplace.ui.AppIcon;
import com.commonplace.ui.LevelUi;
import com.commonplace.ui.OverlayService;
import com.commonplace.ui.QuestionImageViewFactory;
import com.commonplace.ui.UiAnimations;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

public class ImportWorksheetController {

    private static final String QUESTION_IMAGE_PATH_KEY = "import-question-image-path";

    @FXML private StackPane importRoot;
    @FXML private TextField pdfPathField;
    @FXML private ComboBox<StudyModule> moduleCombo;
    @FXML private ComboBox<Topic> topicCombo;
    @FXML private Button importButton;
    @FXML private Button reviewDraftButton;
    @FXML private StackPane reviewOverlay;
    @FXML private VBox reviewPanel;
    @FXML private TextField titleField;
    @FXML private ComboBox<DifficultyLevel> difficultyCombo;
    @FXML private ComboBox<ImportanceLevel> importanceCombo;
    @FXML private VBox issuesList;
    @FXML private VBox questionsContainer;
    @FXML private VBox unattachedImagesContainer;
    @FXML private Label statusLabel;
    @FXML private Label reviewStatusLabel;
    @FXML private Button saveButton;

    private final PdfImportService pdfImportService = new PdfImportService();
    private final StudyStructureService studyStructureService = new StudyStructureService();
    private final WorksheetCreationService worksheetCreationService = new WorksheetCreationService();
    private final QuestionImageStorage imageStorage = new QuestionImageStorage();

    private File selectedPdfFile;
    private StudyModule pendingModule;
    private Topic pendingTopic;
    private Runnable onWorksheetSaved;

    @FXML
    private void initialize() {
        configureComboBoxes();

        difficultyCombo.getItems().setAll(DifficultyLevel.values());
        difficultyCombo.setValue(DifficultyLevel.MEDIUM);
        LevelUi.applyLevelBarStyling(difficultyCombo);

        importanceCombo.getItems().setAll(ImportanceLevel.values());
        importanceCombo.setValue(ImportanceLevel.MEDIUM);
        LevelUi.applyLevelBarStyling(importanceCombo);

        reviewOverlay.setVisible(false);
        reviewOverlay.setManaged(false);
        reviewDraftButton.setVisible(false);
        reviewDraftButton.setManaged(false);
        saveButton.setDisable(true);
        loadModules();
    }

    public void setInitialSelection(
            StudyModule module,
            Topic topic,
            Runnable onWorksheetSaved
    ) {
        this.pendingModule = module;
        this.pendingTopic = topic;
        this.onWorksheetSaved = onWorksheetSaved;
        applyPendingSelection();
    }

    @FXML
    private void handleChoosePdf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Worksheet PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));

        File selectedFile = chooser.showOpenDialog(importRoot.getScene().getWindow());

        if (selectedFile == null) {
            return;
        }

        selectedPdfFile = selectedFile;
        pdfPathField.setText(selectedFile.getAbsolutePath());
        setStatus("");
    }

    @FXML
    private void handleImportPdf() {
        if (selectedPdfFile == null) {
            setStatus("Choose a PDF file first.");
            UiAnimations.validationError(pdfPathField);
            return;
        }

        if (selectedTopic() == null) {
            setStatus("Choose the module and topic for this worksheet.");
            UiAnimations.validationError(moduleCombo, topicCombo);
            return;
        }

        importButton.setDisable(true);
        saveButton.setDisable(true);
        reviewDraftButton.setVisible(false);
        reviewDraftButton.setManaged(false);
        reviewOverlay.setVisible(false);
        reviewOverlay.setManaged(false);
        setStatus("Importing PDF locally...");

        StudyModule module = moduleCombo.getValue();
        Topic topic = topicCombo.getValue();

        Task<ImportedWorksheetDraft> task = new Task<>() {
            @Override
            protected ImportedWorksheetDraft call() {
                return pdfImportService.importPdf(
                        selectedPdfFile.toPath(),
                        module == null ? null : module.id(),
                        topic.id()
                );
            }
        };

        task.setOnSucceeded(event -> {
            populateReview(task.getValue());
            importButton.setDisable(false);
            saveButton.setDisable(false);
            setStatus("Review the imported draft before saving.");
        });

        task.setOnFailed(event -> {
            importButton.setDisable(false);
            saveButton.setDisable(true);
            Throwable error = task.getException();
            setStatus("PDF import failed: " + (error == null ? "Unknown error" : error.getMessage()));
        });

        Thread worker = new Thread(task, "pdf-import-worker");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void handleAddQuestion() {
        addQuestionRow(new ImportedQuestionDraft(
                questionsContainer.getChildren().size() + 1,
                "",
                "",
                1,
                1,
                List.of(),
                List.of()
        ));
        renumberQuestionRows();
    }

    @FXML
    private void handleShowReview() {
        if (questionsContainer.getChildren().isEmpty()) {
            setStatus("Import a PDF before reviewing the draft.");
            return;
        }

        showReviewOverlay();
    }

    @FXML
    private void handleCloseReview() {
        reviewOverlay.setVisible(false);
        reviewOverlay.setManaged(false);
        reviewOverlay.setOpacity(1.0);
        reviewPanel.setOpacity(1.0);
        reviewPanel.setScaleX(1.0);
        reviewPanel.setScaleY(1.0);
        reviewPanel.setTranslateY(0);
        setStatus("Draft ready. Use Review Draft to keep editing it.");
    }

    @FXML
    private void handleSaveImportedWorksheet() {
        Topic topic = selectedTopic();

        if (topic == null) {
            setStatus("Choose a topic before saving.");
            UiAnimations.validationError(topicCombo);
            return;
        }

        try {
            if (titleField.getText() == null || titleField.getText().isBlank()) {
                throw new IllegalArgumentException("Worksheet title cannot be empty.");
            }

            List<QuestionRepository.QuestionDraft> questionDrafts = collectQuestionDrafts();

            worksheetCreationService.createWorksheetWithQuestions(
                    topic.id(),
                    titleField.getText(),
                    "Imported from PDF",
                    difficultyCombo.getValue(),
                    importanceCombo.getValue(),
                    questionDrafts
            );

            UiAnimations.validationSuccess(titleField, questionsContainer);

            if (onWorksheetSaved != null) {
                onWorksheetSaved.run();
            }

            OverlayService.closeFrom(importRoot);

        } catch (IllegalArgumentException e) {
            setStatus(e.getMessage());
            UiAnimations.validationError(titleField, questionsContainer);
        } catch (SQLException e) {
            showError("Failed to save imported worksheet", e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        OverlayService.closeFrom(importRoot);
    }

    private void configureComboBoxes() {
        moduleCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(StudyModule module) {
                return module == null ? "" : module.name();
            }

            @Override
            public StudyModule fromString(String string) {
                return null;
            }
        });

        topicCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Topic topic) {
                return topic == null ? "" : topic.name();
            }

            @Override
            public Topic fromString(String string) {
                return null;
            }
        });

        moduleCombo.setCellFactory(list -> moduleCell());
        moduleCombo.setButtonCell(moduleCell());
        topicCombo.setCellFactory(list -> topicCell());
        topicCombo.setButtonCell(topicCell());

        moduleCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                loadTopics(newValue.id());
            } else {
                topicCombo.getItems().clear();
            }
        });
    }

    private ListCell<StudyModule> moduleCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(StudyModule item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.name());
            }
        };
    }

    private ListCell<Topic> topicCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Topic item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.name());
            }
        };
    }

    private void loadModules() {
        try {
            moduleCombo.getItems().setAll(studyStructureService.getModules());
            applyPendingSelection();
        } catch (SQLException e) {
            showError("Failed to load modules", e.getMessage());
        }
    }

    private void loadTopics(long moduleId) {
        try {
            topicCombo.getItems().setAll(studyStructureService.getTopicsForModule(moduleId));

            if (pendingTopic != null && pendingTopic.moduleId() == moduleId) {
                topicCombo.getSelectionModel().select(findTopic(pendingTopic.id()));
            } else if (!topicCombo.getItems().isEmpty()) {
                topicCombo.getSelectionModel().selectFirst();
            }

        } catch (SQLException e) {
            showError("Failed to load topics", e.getMessage());
        }
    }

    private void applyPendingSelection() {
        if (moduleCombo == null || moduleCombo.getItems().isEmpty()) {
            return;
        }

        if (pendingModule != null) {
            StudyModule module = findModule(pendingModule.id());

            if (module != null) {
                moduleCombo.getSelectionModel().select(module);
                loadTopics(module.id());
                return;
            }
        }

        moduleCombo.getSelectionModel().selectFirst();
    }

    private StudyModule findModule(long moduleId) {
        return moduleCombo.getItems().stream()
                .filter(module -> module.id() == moduleId)
                .findFirst()
                .orElse(null);
    }

    private Topic findTopic(long topicId) {
        return topicCombo.getItems().stream()
                .filter(topic -> topic.id() == topicId)
                .findFirst()
                .orElse(null);
    }

    private void populateReview(ImportedWorksheetDraft draft) {
        titleField.setText(draft.suggestedTitle());
        issuesList.getChildren().clear();
        questionsContainer.getChildren().clear();
        unattachedImagesContainer.getChildren().clear();

        if (draft.issues().isEmpty()) {
            addIssue(new ImportIssue(
                    com.commonplace.importer.ImportIssueSeverity.INFO,
                    "PDF text was imported. Review every question before saving."
            ));
        } else {
            draft.issues().forEach(this::addIssue);
        }

        List<ImageReviewItem> unattachedImages = new ArrayList<>();
        draft.unattachedImages().forEach(image ->
                unattachedImages.add(new ImageReviewItem(image.imagePath(), image.suggestedAltText())));

        for (ImportedQuestionDraft question : draft.questions()) {
            addQuestionRow(question);

            if (question.imagePaths().size() > 1) {
                for (int i = 1; i < question.imagePaths().size(); i++) {
                    unattachedImages.add(new ImageReviewItem(
                            question.imagePaths().get(i),
                            "Additional image from question " + question.questionNumber()
                    ));
                }
            }
        }

        if (questionsContainer.getChildren().isEmpty()) {
            handleAddQuestion();
        }

        refreshUnattachedImages(unattachedImages);
        reviewDraftButton.setVisible(true);
        reviewDraftButton.setManaged(true);
        showReviewOverlay();
        setStatus("Review the imported draft before saving.");
    }

    private void showReviewOverlay() {
        reviewOverlay.setVisible(true);
        reviewOverlay.setManaged(true);
        reviewOverlay.toFront();
        UiAnimations.animateOverlayOpen(reviewOverlay, reviewPanel);
        Platform.runLater(() -> titleField.requestFocus());
    }

    private void addIssue(ImportIssue issue) {
        Label label = new Label(issue.severity() + ": " + issue.message());
        label.getStyleClass().add("muted-text");
        label.setWrapText(true);
        issuesList.getChildren().add(label);
    }

    private void addQuestionRow(ImportedQuestionDraft question) {
        VBox row = new VBox(8);
        row.getStyleClass().add("question-card");

        Label heading = new Label("Question " + (questionsContainer.getChildren().size() + 1));
        heading.getStyleClass().add("card-title");

        TextArea promptArea = new TextArea(question.questionText());
        promptArea.setUserData("prompt");
        promptArea.setPromptText("Question prompt");
        promptArea.setWrapText(true);
        promptArea.setPrefRowCount(4);

        TextArea markSchemeArea = new TextArea(question.markScheme());
        markSchemeArea.setUserData("markScheme");
        markSchemeArea.setPromptText("Mark scheme (optional)");
        markSchemeArea.setWrapText(true);
        markSchemeArea.setPrefRowCount(3);

        Spinner<Integer> marksSpinner = new Spinner<>(1, 100, Math.max(1, question.maxMarks()));
        marksSpinner.setEditable(true);
        marksSpinner.setMaxWidth(220);
        marksSpinner.setUserData("maxMarks");

        VBox imageBox = new VBox(6);
        imageBox.getStyleClass().add("question-image-preview");
        setQuestionImage(row, imageBox, question.imagePaths().isEmpty() ? null : question.imagePaths().get(0));

        Button chooseImageButton = new Button(question.imagePaths().isEmpty() ? "Add image" : "Replace image");
        chooseImageButton.getStyleClass().add("compact-button");
        chooseImageButton.setOnAction(event -> chooseQuestionImage(row, imageBox, chooseImageButton));

        Button clearImageButton = new Button("Remove image");
        clearImageButton.getStyleClass().addAll("danger-button", "compact-button");
        clearImageButton.setOnAction(event -> {
            setQuestionImage(row, imageBox, null);
            chooseImageButton.setText("Add image");
        });

        HBox imageActions = new HBox(8, chooseImageButton, clearImageButton);
        imageActions.setAlignment(Pos.CENTER_LEFT);

        Button removeButton = new Button("Remove Question");
        removeButton.getStyleClass().add("danger-button");
        removeButton.setOnAction(event -> UiAnimations.animateCardRemoval(row, () -> {
            questionsContainer.getChildren().remove(row);
            renumberQuestionRows();
        }));

        Label marksLabel = new Label("Max marks");
        marksLabel.getStyleClass().add("small-label");

        row.getChildren().setAll(
                heading,
                promptArea,
                markSchemeArea,
                imageBox,
                imageActions,
                marksLabel,
                marksSpinner,
                removeButton
        );

        questionsContainer.getChildren().add(row);
        UiAnimations.animateCardEntry(row);
    }

    private void chooseQuestionImage(VBox row, VBox imageBox, Button chooseImageButton) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Question Image");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("PNG", "*.png"),
                new FileChooser.ExtensionFilter("JPEG", "*.jpg", "*.jpeg")
        );

        File selectedFile = chooser.showOpenDialog(importRoot.getScene().getWindow());

        if (selectedFile == null) {
            return;
        }

        Path selectedPath = selectedFile.toPath();

        if (!QuestionImageStorage.isSupportedImage(selectedPath)) {
            setStatus("Question images must be PNG, JPG, or JPEG.");
            UiAnimations.validationError(row);
            return;
        }

        try {
            String storedPath = imageStorage.copyIntoImageStore(selectedPath);
            setQuestionImage(row, imageBox, storedPath);
            chooseImageButton.setText("Replace image");
            UiAnimations.validationSuccess(row);
            setStatus("");
        } catch (IOException e) {
            setStatus("Image attachment failed: " + e.getMessage());
            UiAnimations.validationError(row);
        }
    }

    private void setQuestionImage(VBox questionRow, VBox imageBox, String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            questionRow.getProperties().remove(QUESTION_IMAGE_PATH_KEY);
        } else {
            questionRow.getProperties().put(QUESTION_IMAGE_PATH_KEY, imagePath);
        }

        imageBox.getChildren().clear();

        if (imagePath == null || imagePath.isBlank()) {
            Label label = new Label("No image attached");
            label.getStyleClass().add("muted-text");
            imageBox.getChildren().add(label);
            return;
        }

        Node preview = QuestionImageViewFactory.create(imagePath, 320, 180);

        if (preview == null) {
            Label label = new Label("Image unavailable");
            label.getStyleClass().add("muted-text");
            imageBox.getChildren().add(label);
        } else {
            imageBox.getChildren().add(preview);
        }
    }

    private void refreshUnattachedImages(List<ImageReviewItem> images) {
        unattachedImagesContainer.getChildren().clear();

        if (images.isEmpty()) {
            Label empty = new Label("No unattached images.");
            empty.getStyleClass().add("muted-text");
            unattachedImagesContainer.getChildren().add(empty);
            return;
        }

        for (ImageReviewItem image : images) {
            unattachedImagesContainer.getChildren().add(createUnattachedImageCard(image, images));
        }
    }

    private Node createUnattachedImageCard(ImageReviewItem image, List<ImageReviewItem> allImages) {
        VBox card = new VBox(8);
        card.getStyleClass().add("entity-card");

        Label title = new Label(image.label());
        title.getStyleClass().add("card-title");

        Node preview = QuestionImageViewFactory.create(image.imagePath(), 260, 160);

        ComboBox<QuestionRowOption> questionCombo = new ComboBox<>();
        questionCombo.setPromptText("Attach to question");
        questionCombo.getItems().setAll(questionOptions());

        Button attachButton = new Button("Attach");
        attachButton.getStyleClass().add("compact-button");
        attachButton.setOnAction(event -> {
            QuestionRowOption option = questionCombo.getValue();

            if (option == null) {
                setStatus("Choose a question for the image.");
                return;
            }

            setQuestionImage(option.row(), option.imageBox(), image.imagePath());
            allImages.remove(image);
            refreshUnattachedImages(allImages);
        });

        Button discardButton = new Button("Discard");
        discardButton.getStyleClass().addAll("danger-button", "compact-button");
        discardButton.setOnAction(event -> {
            allImages.remove(image);
            refreshUnattachedImages(allImages);
        });

        HBox actions = new HBox(8, questionCombo, attachButton, discardButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        if (preview == null) {
            Label unavailable = new Label("Image unavailable");
            unavailable.getStyleClass().add("muted-text");
            card.getChildren().setAll(title, unavailable, actions);
        } else {
            card.getChildren().setAll(title, preview, actions);
        }

        return card;
    }

    private List<QuestionRowOption> questionOptions() {
        List<QuestionRowOption> options = new ArrayList<>();

        for (int i = 0; i < questionsContainer.getChildren().size(); i++) {
            VBox row = (VBox) questionsContainer.getChildren().get(i);
            VBox imageBox = findImageBox(row);
            options.add(new QuestionRowOption("Question " + (i + 1), row, imageBox));
        }

        return options;
    }

    private VBox findImageBox(VBox row) {
        return row.getChildren().stream()
                .filter(VBox.class::isInstance)
                .map(VBox.class::cast)
                .filter(vbox -> vbox.getStyleClass().contains("question-image-preview"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Question image box not found."));
    }

    private List<QuestionRepository.QuestionDraft> collectQuestionDrafts() {
        List<QuestionRepository.QuestionDraft> drafts = new ArrayList<>();

        for (int i = 0; i < questionsContainer.getChildren().size(); i++) {
            VBox row = (VBox) questionsContainer.getChildren().get(i);
            TextArea promptArea = findQuestionTextArea(row, "prompt");
            TextArea markSchemeArea = findQuestionTextArea(row, "markScheme");
            Spinner<Integer> maxMarksSpinner = findQuestionMarksSpinner(row);

            if (promptArea.getText() == null || promptArea.getText().isBlank()) {
                throw new IllegalArgumentException("Question " + (i + 1) + " needs a prompt.");
            }

            Object imagePath = row.getProperties().get(QUESTION_IMAGE_PATH_KEY);

            drafts.add(new QuestionRepository.QuestionDraft(
                    promptArea.getText(),
                    markSchemeArea.getText() == null ? "" : markSchemeArea.getText(),
                    maxMarksSpinner.getValue(),
                    null,
                    imagePath instanceof String value && !value.isBlank() ? value : null
            ));
        }

        return drafts;
    }

    private TextArea findQuestionTextArea(VBox row, String userData) {
        return row.getChildren().stream()
                .filter(TextArea.class::isInstance)
                .map(TextArea.class::cast)
                .filter(textArea -> userData.equals(textArea.getUserData()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Question field missing."));
    }

    private Spinner<Integer> findQuestionMarksSpinner(VBox row) {
        for (Node child : row.getChildren()) {
            if (child instanceof Spinner<?> spinner && "maxMarks".equals(spinner.getUserData())) {
                @SuppressWarnings("unchecked")
                Spinner<Integer> typedSpinner = (Spinner<Integer>) spinner;
                return typedSpinner;
            }
        }

        throw new IllegalStateException("Question marks spinner missing.");
    }

    private void renumberQuestionRows() {
        for (int i = 0; i < questionsContainer.getChildren().size(); i++) {
            VBox row = (VBox) questionsContainer.getChildren().get(i);

            if (!row.getChildren().isEmpty() && row.getChildren().get(0) instanceof Label label) {
                label.setText("Question " + (i + 1));
            }
        }
    }

    private Topic selectedTopic() {
        return topicCombo.getValue();
    }

    private void setStatus(String message) {
        String text = message == null ? "" : message;
        statusLabel.setText(text);

        if (reviewStatusLabel != null) {
            reviewStatusLabel.setText(text);
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        AppIcon.applyTo(alert);
        alert.showAndWait();
    }

    private record ImageReviewItem(String imagePath, String label) {
    }

    private record QuestionRowOption(String label, VBox row, VBox imageBox) {
        @Override
        public String toString() {
            return label;
        }
    }
}
