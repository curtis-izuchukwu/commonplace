package com.commonplace.ui.controller;

import com.commonplace.model.StudyModule;
import com.commonplace.model.Topic;
import com.commonplace.model.Worksheet;
import com.commonplace.service.WorksheetCreationService;
import com.commonplace.ui.AppIcon;
import com.commonplace.ui.LevelUi;
import com.commonplace.ui.OverlayService;
import com.commonplace.ui.UiAnimations;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TopicDetailController {

    @FXML private Label topicNameLabel;
    @FXML private Label moduleNameLabel;
    @FXML private ProgressBar masteryProgressBar;
    @FXML private Label masteryLabel;
    @FXML private Label masteryValueLabel;
    @FXML private TitledPane evidencePane;
    @FXML private VBox learningEvidenceBox;
    @FXML private FlowPane topicMetaBox;
    @FXML private Label descriptionLabel;
    @FXML private VBox worksheetsList;
    @FXML private Label statusLabel;

    private final WorksheetCreationService worksheetService = new WorksheetCreationService();

    private Topic topic;
    private StudyModule parentModule;
    private Runnable onDataChanged;

    public void setOnDataChanged(Runnable onDataChanged) {
        this.onDataChanged = onDataChanged;
    }

    public void setTopic(Topic topic, StudyModule parentModule) {
        this.topic = topic;
        this.parentModule = parentModule;

        topicNameLabel.setText(topic.name());

        moduleNameLabel.setText(
                parentModule == null
                        ? "No parent module loaded"
                        : "Module  " + parentModule.name());

        double mastery = Math.max(0, Math.min(topic.masteryScore(), 100));

        UiAnimations.animateProgress(masteryProgressBar, mastery / 100.0);
        masteryValueLabel.setText(String.format("%.0f%%", mastery));
        masteryLabel.setText("Loading evidence…");
        topicMetaBox
                .getChildren()
                .setAll(
                        LevelUi.createPriorityStat(topic.importance()),
                        LevelUi.createStatCell(
                                "Confidence", LevelUi.displayName(topic.confidence())));

        String description = topic.description();
        boolean hasDescription = description != null && !description.isBlank();
        descriptionLabel.setText(hasDescription ? description.trim() : "");
        descriptionLabel.setVisible(hasDescription);
        descriptionLabel.setManaged(hasDescription);

        loadWorksheets();
    }

    @FXML
    private void handleAddWorksheet() {
        if (topic == null) {
            UiAnimations.validationError(topicNameLabel);
            setStatus("No topic selected.");
            return;
        }

        try {
            var handle =
                    OverlayService.<WorksheetCreateController>open(
                            topicNameLabel,
                            "/com/commonplace/fxml/WorksheetCreateView.fxml",
                            1120,
                            760);

            WorksheetCreateController controller = handle.controller();
            controller.setTopic(
                    topic,
                    parentModule,
                    () -> {
                        loadWorksheets();
                        notifyDataChanged();
                    });

        } catch (IOException e) {
            showError("Failed to open worksheet creation", e.getMessage());
        }
    }

    private void loadWorksheets() {
        refreshLearning();
        worksheetsList.getChildren().clear();

        if (topic == null) {
            Label emptyLabel = new Label("No topic selected.");
            emptyLabel.getStyleClass().add("muted-text");
            worksheetsList.getChildren().add(emptyLabel);
            return;
        }

        try {
            List<Worksheet> worksheets = worksheetService.getWorksheetsForTopic(topic.id());

            if (worksheets.isEmpty()) {
                Label emptyLabel =
                        new Label(
                                "No worksheets yet. Add one to start building practice material.");
                emptyLabel.getStyleClass().add("muted-text");
                worksheetsList.getChildren().add(emptyLabel);
                return;
            }

            for (Worksheet worksheet : worksheets) {
                worksheetsList.getChildren().add(createWorksheetCard(worksheet));
            }

        } catch (SQLException e) {
            showError("Failed to load worksheets", e.getMessage());
        }
    }

    @FXML
    private void handleImportWorksheet() {
        if (topic == null) {
            UiAnimations.validationError(topicNameLabel);
            setStatus("No topic selected.");
            return;
        }

        try {
            var handle =
                    OverlayService.<ImportWorksheetController>open(
                            topicNameLabel,
                            "/com/commonplace/fxml/ImportWorksheetView.fxml",
                            900,
                            780);

            ImportWorksheetController controller = handle.controller();
            controller.setInitialSelection(
                    parentModule,
                    topic,
                    () -> {
                        loadWorksheets();
                        notifyDataChanged();
                    });

        } catch (IOException e) {
            showError("Failed to open PDF import", e.getMessage());
        }
    }

    private StackPane createWorksheetCard(Worksheet worksheet) {
        StackPane card = new StackPane();
        card.getStyleClass().addAll("entity-card", "clickable-card");
        card.setOnMouseClicked(event -> openWorksheetDetail(worksheet));

        VBox textBox = new VBox(4);
        textBox.setPadding(new Insets(0, 34, 0, 0));

        Label title = new Label(worksheet.title());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        FlowPane meta = new FlowPane(8, 8);
        meta.getStyleClass().add("record-stat-grid");
        meta.getChildren()
                .addAll(
                        LevelUi.createStatCell(
                                "Difficulty", LevelUi.displayName(worksheet.difficulty())),
                        LevelUi.createPriorityStat(worksheet.importance()),
                        LevelUi.createStatCell(
                                "Attempts", Integer.toString(worksheet.timesAttempted())));
        if (worksheet.latestScorePercent() != null) {
            meta.getChildren()
                    .add(
                            LevelUi.createStatCell(
                                    "Latest", formatScore(worksheet.latestScorePercent())));
        }

        textBox.getChildren().addAll(title, meta);

        Button deleteButton = createDeleteButton();
        deleteButton.setOnMouseClicked(event -> event.consume());
        deleteButton.setOnAction(
                event -> {
                    event.consume();
                    deleteWorksheet(worksheet, card);
                });

        card.getChildren().addAll(textBox, deleteButton);
        StackPane.setAlignment(deleteButton, Pos.TOP_RIGHT);

        UiAnimations.animateCardEntry(card);
        return card;
    }

    private Button createDeleteButton() {
        Button button = new Button("×");
        button.getStyleClass().add("icon-danger-button");
        button.setFocusTraversable(false);
        return button;
    }

    private void openWorksheetDetail(Worksheet worksheet) {
        try {
            var handle =
                    OverlayService.<WorksheetDetailController>open(
                            topicNameLabel,
                            "/com/commonplace/fxml/WorksheetDetailView.fxml",
                            840,
                            720);

            WorksheetDetailController controller = handle.controller();
            controller.setWorksheet(worksheet, topic);
            controller.setOnWorksheetUpdated(
                    () -> {
                        loadWorksheets();
                        notifyDataChanged();
                    });

        } catch (IOException e) {
            showError("Failed to open worksheet detail", e.getMessage());
        }
    }

    private void deleteWorksheet(Worksheet worksheet, Node card) {
        try {
            worksheetService.deleteWorksheet(worksheet.id());
            UiAnimations.animateCardRemoval(
                    card,
                    () -> {
                        loadWorksheets();
                        notifyDataChanged();
                        setStatus("Worksheet deleted: " + worksheet.title());
                    });

        } catch (SQLException e) {
            showError("Failed to delete worksheet", e.getMessage());
        }
    }

    private String formatScore(Double score) {
        if (score == null) {
            return "Not attempted";
        }

        return String.format("%.0f%%", score);
    }

    private void notifyDataChanged() {
        if (onDataChanged != null) {
            onDataChanged.run();
        }
    }

    private void refreshLearning() {
        if (topic == null || learningEvidenceBox == null) return;
        try {
            var progress = new com.commonplace.service.LearningService().topic(topic.id());
            UiAnimations.animateProgress(masteryProgressBar, progress.mastery() / 100);
            masteryValueLabel.setText(String.format("%.0f%%", progress.mastery()));
            learningEvidenceBox.getChildren().clear();

            var evidence = progress.estimate();
            masteryLabel.setText(evidence.certainty());
            evidencePane.setText("View supporting evidence");

            FlowPane metrics = new FlowPane(8, 8);
            metrics.getStyleClass().add("evidence-metrics");
            metrics.getChildren()
                    .addAll(
                            evidenceMetric(
                                    "Recent score",
                                    evidence.attempts() == 0
                                            ? "—"
                                            : String.format("%.0f%%", evidence.performance())),
                            evidenceMetric(
                                    "Retention",
                                    evidence.attempts() == 0
                                            ? "—"
                                            : String.format("%.0f%%", evidence.retention() * 100)),
                            evidenceMetric(
                                    "Questions", Integer.toString(evidence.uniqueQuestions())),
                            evidenceMetric(
                                    "Worksheets", Integer.toString(evidence.uniqueWorksheets())),
                            evidenceMetric("Study areas", Integer.toString(evidence.uniqueScopes())),
                            evidenceMetric("Study days", Integer.toString(evidence.studyDays())));

            Label review =
                    new Label(
                            evidence.lastAt() == null
                                    ? "Complete varied worksheets to begin building this estimate."
                                    : evidence.dueAt().isAfter(LocalDate.now())
                                            ? "Next review  "
                                                    + evidence.dueAt()
                                                            .format(
                                                                    DateTimeFormatter.ofPattern(
                                                                            "d MMM"))
                                            : "Review due now");
            review.setWrapText(true);
            review.getStyleClass().add("evidence-review");

            Label note = new Label("Repeated prompts are discounted automatically.");
            note.getStyleClass().add("muted-text");

            learningEvidenceBox.getChildren().addAll(metrics, review, note);
        } catch (SQLException e) {
            setStatus("Could not load learning evidence: " + e.getMessage());
        }
    }

    private VBox evidenceMetric(String label, String value) {
        Label caption = new Label(label.toUpperCase());
        caption.getStyleClass().add("evidence-metric-label");
        Label amount = new Label(value);
        amount.getStyleClass().add("evidence-metric-value");
        VBox metric = new VBox(2, caption, amount);
        metric.getStyleClass().add("evidence-metric");
        return metric;
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        AppIcon.applyTo(alert);
        alert.showAndWait();
    }

    @FXML
    private void handleClose() {
        OverlayService.closeFrom(topicNameLabel);
    }
}
