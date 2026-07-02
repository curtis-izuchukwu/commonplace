package com.pararepilot.ui.controller;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import com.pararepilot.model.StudyModule;
import com.pararepilot.model.Topic;
import com.pararepilot.model.Worksheet;
import com.pararepilot.service.WorksheetCreationService;
import com.pararepilot.ui.AppIcon;
import com.pararepilot.ui.OverlayService;
import com.pararepilot.ui.UiAnimations;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class TopicDetailController {

    @FXML private Label topicNameLabel;
    @FXML private Label moduleNameLabel;
    @FXML private ProgressBar masteryProgressBar;
    @FXML private Label masteryLabel;
    @FXML private Label importanceLabel;
    @FXML private Label confidenceLabel;
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
                        : "Module: " + parentModule.name()
        );

        double mastery = Math.max(0, Math.min(topic.masteryScore(), 100));

        UiAnimations.animateProgress(masteryProgressBar, mastery / 100.0);
        masteryLabel.setText(String.format("%.0f%% mastery", mastery));

        importanceLabel.setText("Importance: " + topic.importance());
        confidenceLabel.setText("Confidence: " + topic.confidence());

        String description = topic.description();

        descriptionLabel.setText(
                description == null || description.isBlank()
                        ? "No description yet."
                        : description
        );

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
            var handle = OverlayService.<WorksheetCreateController>open(
                    topicNameLabel,
                    "/com/pararepilot/fxml/WorksheetCreateView.fxml",
                    820,
                    760
            );

            WorksheetCreateController controller = handle.controller();
            controller.setTopic(topic, parentModule, () -> {
                loadWorksheets();
                notifyDataChanged();
            });

        } catch (IOException e) {
            showError("Failed to open worksheet creation", e.getMessage());
        }
    }

    private void loadWorksheets() {
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
                Label emptyLabel = new Label("No worksheets yet. Add one to start building practice material.");
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

    private StackPane createWorksheetCard(Worksheet worksheet) {
        StackPane card = new StackPane();
        card.getStyleClass().addAll("entity-card", "clickable-card");
        card.setOnMouseClicked(event -> openWorksheetDetail(worksheet));

        VBox textBox = new VBox(4);
        textBox.setPadding(new Insets(0, 34, 0, 0));

        Label title = new Label(worksheet.title());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        Label meta = new Label(
                "Difficulty: " + worksheet.difficulty()
                        + " - Importance: " + worksheet.importance()
                        + " - Attempts: " + worksheet.timesAttempted()
                        + " - Latest score: " + formatScore(worksheet.latestScorePercent())
        );
        meta.getStyleClass().add("muted-text");
        meta.setWrapText(true);

        textBox.getChildren().addAll(title, meta);

        Button deleteButton = createDeleteButton();
        deleteButton.setOnMouseClicked(event -> event.consume());
        deleteButton.setOnAction(event -> {
            event.consume();
            deleteWorksheet(worksheet, card);
        });

        card.getChildren().addAll(textBox, deleteButton);
        StackPane.setAlignment(deleteButton, Pos.TOP_RIGHT);

        UiAnimations.animateCardEntry(card);
        return card;
    }

    private Button createDeleteButton() {
        Button button = new Button("x");
        button.getStyleClass().add("icon-danger-button");
        button.setFocusTraversable(false);
        return button;
    }

    private void openWorksheetDetail(Worksheet worksheet) {
        try {
            var handle = OverlayService.<WorksheetDetailController>open(
                    topicNameLabel,
                    "/com/pararepilot/fxml/WorksheetDetailView.fxml",
                    840,
                    720
            );

            WorksheetDetailController controller = handle.controller();
            controller.setWorksheet(worksheet, topic);
            controller.setOnWorksheetUpdated(() -> {
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
            UiAnimations.animateCardRemoval(card, () -> {
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
