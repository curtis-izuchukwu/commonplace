package com.pararepilot.ui.controller;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import com.pararepilot.model.Question;
import com.pararepilot.model.Topic;
import com.pararepilot.model.Worksheet;
import com.pararepilot.service.WorksheetCreationService;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class WorksheetDetailController {

    @FXML private Label worksheetTitleLabel;
    @FXML private Label worksheetMetaLabel;
    @FXML private Label worksheetDescriptionLabel;
    @FXML private VBox questionsList;

    private final WorksheetCreationService service = new WorksheetCreationService();

    private Worksheet worksheet;
    private Topic topic;

    public void setWorksheet(Worksheet worksheet, Topic topic) {
        this.worksheet = worksheet;
        this.topic = topic;

        worksheetTitleLabel.setText(worksheet.title());

        String topicText = topic == null ? "Unknown topic" : topic.name();

        worksheetMetaLabel.setText(
                "Topic: " + topicText
                        + " • Difficulty: " + worksheet.difficulty()
                        + " • Importance: " + worksheet.importance()
                        + " • Questions: loading..."
        );

        worksheetDescriptionLabel.setText(
                worksheet.description() == null || worksheet.description().isBlank()
                        ? "No description yet."
                        : worksheet.description()
        );

        loadQuestions();
    }

    @FXML
    private void handleStartAttempt() {
        if (worksheet == null) {
            showError("Cannot start attempt", "No worksheet is loaded.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/pararepilot/fxml/AttemptWorksheetView.fxml")
            );

            Parent root = loader.load();

            AttemptWorksheetController controller = loader.getController();
            controller.setWorksheet(worksheet, topic, this::loadQuestions);

            Stage stage = new Stage();
            stage.setTitle("Attempt - " + worksheet.title());
            stage.initModality(Modality.APPLICATION_MODAL);

            Scene scene = new Scene(root, 840, 760);
            scene.getStylesheets().add(
                    getClass().getResource("/com/pararepilot/css/app.css").toExternalForm()
            );

            stage.setScene(scene);
            stage.showAndWait();

        } catch (IOException e) {
            showError("Failed to open attempt screen", e.getMessage());
        }
    }

    private void loadQuestions() {
        questionsList.getChildren().clear();

        try {
            List<Question> questions = service.getQuestionsForWorksheet(worksheet.id());

            String topicText = topic == null ? "Unknown topic" : topic.name();

            worksheetMetaLabel.setText(
                    "Topic: " + topicText
                            + " • Difficulty: " + worksheet.difficulty()
                            + " • Importance: " + worksheet.importance()
                            + " • Questions: " + questions.size()
            );

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

        Label heading = new Label("Question " + question.questionOrder() + " • " + question.maxMarks() + " marks");
        heading.getStyleClass().add("card-title");

        Label prompt = new Label(question.prompt());
        prompt.setWrapText(true);

        Label markSchemeHeading = new Label("Mark Scheme");
        markSchemeHeading.getStyleClass().add("small-label");

        Label markScheme = new Label(question.markScheme());
        markScheme.setWrapText(true);
        markScheme.getStyleClass().add("muted-text");

        card.getChildren().addAll(heading, prompt, markSchemeHeading, markScheme);

        return card;
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) worksheetTitleLabel.getScene().getWindow();
        stage.close();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}