package com.pararepilot.ui.controller;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import com.pararepilot.model.StudyModule;
import com.pararepilot.model.Topic;
import com.pararepilot.model.Worksheet;
import com.pararepilot.service.WorksheetCreationService;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

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

        masteryProgressBar.setProgress(mastery / 100.0);
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
            setStatus("No topic selected.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/pararepilot/fxml/WorksheetCreateView.fxml")
            );

            Parent root = loader.load();

            WorksheetCreateController controller = loader.getController();
            controller.setTopic(topic, parentModule, this::loadWorksheets);

            Stage stage = new Stage();
            stage.setTitle("Create Worksheet - " + topic.name());
            stage.initModality(Modality.APPLICATION_MODAL);

            Scene scene = new Scene(root, 760, 720);
            scene.getStylesheets().add(
                    getClass().getResource("/com/pararepilot/css/app.css").toExternalForm()
            );

            stage.setScene(scene);
            stage.showAndWait();

            loadWorksheets();

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

    private HBox createWorksheetCard(Worksheet worksheet) {
        HBox card = new HBox(12);
        card.getStyleClass().add("entity-card");

        VBox textBox = new VBox(4);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Label title = new Label(worksheet.title());
        title.getStyleClass().add("card-title");

        Label meta = new Label(
                "Difficulty: " + worksheet.difficulty()
                        + " • Importance: " + worksheet.importance()
                        + " • Attempts: " + worksheet.timesAttempted()
                        + " • Latest score: " + formatScore(worksheet.latestScorePercent())
        );
        meta.getStyleClass().add("muted-text");
        meta.setWrapText(true);

        textBox.getChildren().addAll(title, meta);

        Button viewButton = new Button("View");
        viewButton.setOnAction(event -> openWorksheetDetail(worksheet));

        Button deleteButton = new Button("Delete");
        deleteButton.getStyleClass().add("danger-button");
        deleteButton.setOnAction(event -> deleteWorksheet(worksheet));

        card.getChildren().addAll(textBox, viewButton, deleteButton);

        return card;
    }

    private void openWorksheetDetail(Worksheet worksheet) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/pararepilot/fxml/WorksheetDetailView.fxml")
            );

            Parent root = loader.load();

            WorksheetDetailController controller = loader.getController();
            controller.setWorksheet(worksheet, topic);

            Stage stage = new Stage();
            stage.setTitle("Worksheet Details - " + worksheet.title());
            stage.initModality(Modality.APPLICATION_MODAL);

            Scene scene = new Scene(root, 760, 680);
            scene.getStylesheets().add(
                    getClass().getResource("/com/pararepilot/css/app.css").toExternalForm()
            );

            stage.setScene(scene);
            stage.showAndWait();
            
            loadWorksheets();

        } catch (IOException e) {
            showError("Failed to open worksheet detail", e.getMessage());
        }
    }

    private void deleteWorksheet(Worksheet worksheet) {
        try {
            worksheetService.deleteWorksheet(worksheet.id());
            loadWorksheets();
            setStatus("Worksheet deleted: " + worksheet.title());

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

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) topicNameLabel.getScene().getWindow();
        stage.close();
    }
}