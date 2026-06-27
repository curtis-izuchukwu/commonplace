package com.pararepilot.ui.controller;

import com.pararepilot.model.StudyModule;
import com.pararepilot.model.Topic;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;

public class TopicDetailController {

    @FXML private Label topicNameLabel;
    @FXML private Label moduleNameLabel;
    @FXML private ProgressBar masteryProgressBar;
    @FXML private Label masteryLabel;
    @FXML private Label importanceLabel;
    @FXML private Label confidenceLabel;
    @FXML private Label descriptionLabel;

    public void setTopic(Topic topic, StudyModule parentModule) {
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
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) topicNameLabel.getScene().getWindow();
        stage.close();
    }
}