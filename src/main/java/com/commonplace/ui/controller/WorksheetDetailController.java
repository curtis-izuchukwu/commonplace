package com.commonplace.ui.controller;

import com.commonplace.model.Question;
import com.commonplace.model.Topic;
import com.commonplace.model.Worksheet;
import com.commonplace.service.WorksheetCreationService;
import com.commonplace.service.AttemptService;
import com.commonplace.service.WorksheetRecommendation;
import com.commonplace.ui.AppIcon;
import com.commonplace.ui.LevelUi;
import com.commonplace.ui.OverlayService;
import com.commonplace.ui.QuestionImageViewFactory;
import com.commonplace.ui.PdfDocumentView;
import com.commonplace.ui.UiAnimations;
import com.commonplace.util.DateUtils;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WorksheetDetailController {

    @FXML private VBox worksheetDetailRoot;
    @FXML private Label worksheetTitleLabel;
    @FXML private Label worksheetTypeIcon;
    @FXML private Label worksheetMetaLabel;
    @FXML private FlowPane worksheetMetaBox;
    @FXML private Label worksheetDescriptionLabel;
    @FXML private Button startAttemptButton;
    @FXML private VBox recommendationDetailsPanel;
    @FXML private Label recommendationDetailsLabel;
    @FXML private Label questionsHeading;
    @FXML private ScrollPane questionsScrollPane;
    @FXML private VBox questionsList;
    @FXML private VBox pdfViewerPanel;
    @FXML private TabPane pdfTabPane;
    @FXML private Button pdfFullScreenButton;
    @FXML private Spinner<Integer> pdfScoreSpinner;
    @FXML private Spinner<Integer> pdfMaxScoreSpinner;
    @FXML private Button savePdfScoreButton;
    @FXML private Label pdfScoreStatusLabel;

    private final WorksheetCreationService service = new WorksheetCreationService();
    private final AttemptService attemptService = new AttemptService();
    private final List<PdfDocumentView> pdfDocumentViews = new ArrayList<>();
    private final Map<Node, DisplayState> pdfFullScreenDisplayStates = new LinkedHashMap<>();

    private Worksheet worksheet;
    private Topic topic;
    private Runnable onWorksheetUpdated;
    private LocalDateTime worksheetOpenedAt;
    private boolean pdfFullScreen;
    private double normalMaxWidth;
    private double normalMaxHeight;
    private Insets normalPadding;
    private Node fullScreenScrim;
    private Stage fullScreenStage;
    private boolean stageWasAlreadyFullScreen;
    private ChangeListener<Boolean> fullScreenStageListener;

    @FXML
    private void initialize() {
        SpinnerValueFactory.IntegerSpinnerValueFactory scoreFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 10000, 0);
        SpinnerValueFactory.IntegerSpinnerValueFactory maxScoreFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10000, 100);
        pdfScoreSpinner.setValueFactory(scoreFactory);
        pdfMaxScoreSpinner.setValueFactory(maxScoreFactory);
        pdfScoreSpinner.setEditable(true);
        pdfMaxScoreSpinner.setEditable(true);

        pdfViewerPanel.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null && newScene == null) {
                exitPdfFullScreen(true);
                disposePdfViews();
            }
        });
    }

    public void setOnWorksheetUpdated(Runnable onWorksheetUpdated) {
        this.onWorksheetUpdated = onWorksheetUpdated;
    }

    public void setWorksheet(Worksheet worksheet, Topic topic) {
        this.worksheet = worksheet;
        this.topic = topic;
        worksheetOpenedAt = DateUtils.now();

        worksheetTitleLabel.setText(worksheet.title());
        worksheetTypeIcon.setVisible(worksheet.isPdfWorksheet());
        worksheetTypeIcon.setManaged(worksheet.isPdfWorksheet());

        String topicText = topic == null ? "Unknown topic" : topic.name();

        worksheetMetaLabel.setText("Topic  " + topicText);
        updateMetadata(null);

        boolean hasDescription =
                worksheet.description() != null && !worksheet.description().isBlank();
        worksheetDescriptionLabel.setText(hasDescription ? worksheet.description().trim() : "");
        worksheetDescriptionLabel.setVisible(hasDescription);
        worksheetDescriptionLabel.setManaged(hasDescription);

        boolean pdfWorksheet = worksheet.isPdfWorksheet();
        if (!pdfWorksheet) {
            exitPdfFullScreen(true);
        }
        startAttemptButton.setVisible(!pdfWorksheet);
        startAttemptButton.setManaged(!pdfWorksheet);
        questionsHeading.setVisible(!pdfWorksheet);
        questionsHeading.setManaged(!pdfWorksheet);
        questionsScrollPane.setVisible(!pdfWorksheet);
        questionsScrollPane.setManaged(!pdfWorksheet);
        pdfViewerPanel.setVisible(pdfWorksheet);
        pdfViewerPanel.setManaged(pdfWorksheet);

        if (pdfWorksheet) {
            loadPdfDocuments();
        } else {
            loadQuestions();
        }
    }

    public void setRecommendationDetails(WorksheetRecommendation recommendation) {
        if (recommendation == null) {
            recommendationDetailsPanel.setVisible(false);
            recommendationDetailsPanel.setManaged(false);
            recommendationDetailsLabel.setText("");
            return;
        }

        Worksheet recommendedWorksheet = recommendation.worksheet();
        Topic recommendedTopic = recommendation.topic();

        recommendationDetailsLabel.setText(
                recommendation.explanation()
                        + "\n\nMatch  "
                        + recommendation.priorityScore()
                        + "/100"
                        + "  •  Last attempt  "
                        + lastAttemptText(recommendedWorksheet)
                        + "  •  Latest  "
                        + formatScore(recommendedWorksheet.latestScorePercent())
                        + "  •  "
                        + LevelUi.displayName(recommendedTopic.confidence())
                        + " confidence");

        recommendationDetailsPanel.setVisible(true);
        recommendationDetailsPanel.setManaged(true);
        UiAnimations.popIn(recommendationDetailsPanel);
    }

    @FXML
    private void handleStartAttempt() {
        if (worksheet == null || worksheet.isPdfWorksheet()) {
            UiAnimations.validationError(worksheetTitleLabel);
            showError("Cannot start attempt", "No worksheet is loaded.");
            return;
        }

        try {
            var handle =
                    OverlayService.<AttemptWorksheetController>open(
                            worksheetTitleLabel,
                            "/com/commonplace/fxml/AttemptWorksheetView.fxml",
                            900,
                            780);

            AttemptWorksheetController controller = handle.controller();
            controller.setWorksheet(
                    worksheet,
                    topic,
                    () -> {
                        loadQuestions();

                        if (onWorksheetUpdated != null) {
                            onWorksheetUpdated.run();
                        }
                    });

        } catch (IOException e) {
            showError("Failed to open attempt screen", e.getMessage());
        }
    }

    @FXML
    private void handleSavePdfScore() {
        if (worksheet == null || !worksheet.isPdfWorksheet()) {
            return;
        }

        try {
            int score = committedSpinnerValue(pdfScoreSpinner, "Score");
            int maxScore = committedSpinnerValue(pdfMaxScoreSpinner, "Total marks");
            savePdfScoreButton.setDisable(true);

            AttemptService.SubmissionResult result =
                    attemptService.submitPdfScore(
                            worksheet.id(), worksheetOpenedAt, score, maxScore);
            worksheet = service.getWorksheet(worksheet.id()).orElse(worksheet);
            updateMetadata(null);

            int xp = result.practiceReward().xpAwarded();
            pdfScoreStatusLabel.setText(
                    "Score saved: "
                            + score
                            + "/"
                            + maxScore
                            + " ("
                            + String.format("%.0f%%", result.attempt().scorePercent())
                            + ")"
                            + (xp > 0 ? "  •  +" + xp + " XP" : ""));
            UiAnimations.validationSuccess(pdfScoreSpinner, pdfMaxScoreSpinner);
            worksheetOpenedAt = DateUtils.now();

            if (onWorksheetUpdated != null) {
                onWorksheetUpdated.run();
            }
        } catch (IllegalArgumentException e) {
            pdfScoreStatusLabel.setText(e.getMessage());
            UiAnimations.validationError(pdfScoreSpinner, pdfMaxScoreSpinner);
        } catch (SQLException e) {
            showError("Failed to save PDF worksheet score", e.getMessage());
        } finally {
            savePdfScoreButton.setDisable(false);
        }
    }

    @FXML
    private void handleTogglePdfFullScreen() {
        if (pdfFullScreen) {
            exitPdfFullScreen(true);
        } else {
            enterPdfFullScreen();
        }
    }

    private void enterPdfFullScreen() {
        if (pdfFullScreen || worksheet == null || !worksheet.isPdfWorksheet()) {
            return;
        }

        pdfFullScreen = true;
        normalMaxWidth = worksheetDetailRoot.getMaxWidth();
        normalMaxHeight = worksheetDetailRoot.getMaxHeight();
        normalPadding = worksheetDetailRoot.getPadding();

        pdfFullScreenDisplayStates.clear();
        Set<Node> nodesToHide = new LinkedHashSet<>();
        nodesToHide.addAll(worksheetDetailRoot.lookupAll(".pdf-detail-context"));
        nodesToHide.addAll(worksheetDetailRoot.lookupAll(".pdf-fullscreen-hide"));
        for (Node node : nodesToHide) {
            pdfFullScreenDisplayStates.put(
                    node, new DisplayState(node.isVisible(), node.isManaged()));
            node.setVisible(false);
            node.setManaged(false);
        }

        worksheetDetailRoot.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        worksheetDetailRoot.setPadding(new Insets(14));
        addStyleClass(worksheetDetailRoot, "pdf-fullscreen-mode");
        pdfFullScreenButton.setText("Exit full screen");

        fullScreenScrim = findAncestorWithStyleClass(worksheetDetailRoot, "overlay-scrim");
        addStyleClass(fullScreenScrim, "pdf-fullscreen-scrim");

        if (worksheetDetailRoot.getScene() != null
                && worksheetDetailRoot.getScene().getWindow() instanceof Stage stage) {
            fullScreenStage = stage;
            stageWasAlreadyFullScreen = stage.isFullScreen();
            fullScreenStageListener =
                    (observable, wasFullScreen, isFullScreen) -> {
                        if (pdfFullScreen && !isFullScreen) {
                            exitPdfFullScreen(false);
                        }
                    };
            stage.fullScreenProperty().addListener(fullScreenStageListener);
            stage.setFullScreenExitHint("Press Esc or choose Exit full screen");
            stage.setFullScreen(true);
        }

        Platform.runLater(
                () -> {
                    Tab selectedTab = pdfTabPane.getSelectionModel().getSelectedItem();
                    if (selectedTab != null && selectedTab.getContent() != null) {
                        selectedTab.getContent().requestFocus();
                    }
                });
    }

    private void exitPdfFullScreen(boolean updateStage) {
        if (!pdfFullScreen) {
            return;
        }

        pdfFullScreen = false;
        Stage stage = fullScreenStage;
        boolean restoreWindowedMode = !stageWasAlreadyFullScreen;
        if (stage != null && fullScreenStageListener != null) {
            stage.fullScreenProperty().removeListener(fullScreenStageListener);
        }

        for (Map.Entry<Node, DisplayState> entry : pdfFullScreenDisplayStates.entrySet()) {
            Node node = entry.getKey();
            DisplayState state = entry.getValue();
            node.setVisible(state.visible());
            node.setManaged(state.managed());
        }
        pdfFullScreenDisplayStates.clear();

        worksheetDetailRoot.setMaxSize(normalMaxWidth, normalMaxHeight);
        worksheetDetailRoot.setPadding(normalPadding);
        worksheetDetailRoot.getStyleClass().remove("pdf-fullscreen-mode");
        pdfFullScreenButton.setText("Full screen");
        if (fullScreenScrim != null) {
            fullScreenScrim.getStyleClass().remove("pdf-fullscreen-scrim");
        }

        fullScreenScrim = null;
        fullScreenStage = null;
        fullScreenStageListener = null;
        stageWasAlreadyFullScreen = false;

        if (updateStage && stage != null && restoreWindowedMode && stage.isFullScreen()) {
            stage.setFullScreen(false);
        }
    }

    private Node findAncestorWithStyleClass(Node node, String styleClass) {
        Node current = node == null ? null : node.getParent();
        while (current != null) {
            if (current.getStyleClass().contains(styleClass)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private void addStyleClass(Node node, String styleClass) {
        if (node != null && !node.getStyleClass().contains(styleClass)) {
            node.getStyleClass().add(styleClass);
        }
    }

    private int committedSpinnerValue(Spinner<Integer> spinner, String label) {
        if (spinner.isEditable()) {
            String text = spinner.getEditor().getText();
            try {
                spinner.getValueFactory().setValue(Integer.parseInt(text.trim()));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(label + " must be a whole number.");
            }
        }
        return spinner.getValue();
    }

    private void loadPdfDocuments() {
        disposePdfViews();
        pdfTabPane.getTabs().clear();
        pdfScoreStatusLabel.setText("");

        try {
            Path worksheetPdf =
                    service.getPdfPathForWorksheet(worksheet.id())
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "The worksheet PDF file is missing."));
            addPdfTab("Worksheet", worksheetPdf);
            service.getMarkSchemePdfPathForWorksheet(worksheet.id())
                    .ifPresent(path -> addPdfTab("Mark scheme", path));
            updateMetadata(null);
        } catch (SQLException | IllegalStateException e) {
            pdfScoreStatusLabel.setText("Could not open PDF worksheet: " + e.getMessage());
        }
    }

    private void addPdfTab(String title, Path path) {
        PdfDocumentView documentView = new PdfDocumentView(path);
        pdfDocumentViews.add(documentView);
        Tab tab = new Tab(title, documentView);
        tab.setClosable(false);
        pdfTabPane.getTabs().add(tab);
    }

    private void disposePdfViews() {
        pdfDocumentViews.forEach(PdfDocumentView::dispose);
        pdfDocumentViews.clear();
    }

    private void loadQuestions() {
        questionsList.getChildren().clear();

        try {
            List<Question> questions = service.getQuestionsForWorksheet(worksheet.id());

            updateMetadata(questions.size());

            if (questions.isEmpty()) {
                Label emptyLabel = new Label("No questions saved for this worksheet.");
                emptyLabel.getStyleClass().add("muted-text");
                questionsList.getChildren().add(emptyLabel);
                return;
            }

            for (Question question : questions) {
                questionsList.getChildren().add(createQuestionCard(question));
            }

        } catch (SQLException e) {
            Label errorLabel = new Label("Failed to load questions: " + e.getMessage());
            errorLabel.getStyleClass().add("status-text");
            questionsList.getChildren().add(errorLabel);
        }
    }

    private VBox createQuestionCard(Question question) {
        VBox card = new VBox(8);
        card.getStyleClass().add("question-card");

        Label heading =
                new Label(
                        "Question "
                                + question.questionOrder()
                                + "  •  "
                                + question.maxMarks()
                                + " marks");
        heading.getStyleClass().add("card-title");

        Label prompt = new Label(question.prompt());
        prompt.setWrapText(true);

        Node imageNode = QuestionImageViewFactory.create(question.imagePath(), 520, 320);

        Label markSchemeHeading = new Label("Mark Scheme");
        markSchemeHeading.getStyleClass().add("small-label");

        Label markScheme = new Label(question.markScheme());
        markScheme.setWrapText(true);
        markScheme.getStyleClass().add("muted-text");

        card.getChildren().addAll(heading, prompt);

        if (imageNode != null) {
            card.getChildren().add(imageNode);
        }

        card.getChildren().addAll(markSchemeHeading, markScheme);
        UiAnimations.animateCardEntry(card);

        return card;
    }

    private void updateMetadata(Integer questionCount) {
        if (worksheet.isPdfWorksheet()) {
            worksheetMetaBox
                    .getChildren()
                    .setAll(
                            LevelUi.createStatCell(
                                    "Difficulty", LevelUi.displayName(worksheet.difficulty())),
                            LevelUi.createPriorityStat(worksheet.importance()),
                            LevelUi.createStatCell("Format", "PDF • Pen and paper"),
                            LevelUi.createStatCell(
                                    "Marked attempts",
                                    Integer.toString(worksheet.timesAttempted())));
            if (worksheet.latestScorePercent() != null) {
                worksheetMetaBox
                        .getChildren()
                        .add(
                                LevelUi.createStatCell(
                                        "Latest", formatScore(worksheet.latestScorePercent())));
            }
            return;
        }

        worksheetMetaBox
                .getChildren()
                .setAll(
                        LevelUi.createStatCell(
                                "Difficulty", LevelUi.displayName(worksheet.difficulty())),
                        LevelUi.createPriorityStat(worksheet.importance()),
                        LevelUi.createStatCell(
                                "Questions",
                                questionCount == null ? "…" : Integer.toString(questionCount)),
                        LevelUi.createStatCell(
                                "Attempts", Integer.toString(worksheet.timesAttempted())));
    }

    @FXML
    private void handleClose() {
        exitPdfFullScreen(true);
        OverlayService.closeFrom(worksheetTitleLabel);
    }

    private String lastAttemptText(Worksheet worksheet) {
        if (worksheet.lastAttemptedAt() == null) {
            return "Never attempted";
        }

        long days =
                ChronoUnit.DAYS.between(worksheet.lastAttemptedAt().toLocalDate(), LocalDate.now());

        if (days <= 0) {
            return "Today";
        }

        return days + " day" + (days == 1 ? "" : "s") + " ago";
    }

    private String formatScore(Double score) {
        if (score == null) {
            return "Not attempted";
        }

        return String.format("%.0f%%", score);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        AppIcon.applyTo(alert);
        alert.showAndWait();
    }

    private record DisplayState(boolean visible, boolean managed) {}
}
