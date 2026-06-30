package com.pararepilot.ui.controller;

import java.sql.SQLException;
import java.util.List;

import com.pararepilot.model.Question;
import com.pararepilot.model.Topic;
import com.pararepilot.model.Worksheet;
import com.pararepilot.service.WorksheetCreationService;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class WorksheetDetailController {

    @FXML private Label worksheetTitleLabel;
    @FXML private Label worksheetMetaLabel;
    @FXML private Label worksheetDescriptionLabel;
    @FXML private VBox questionsList;

    private final WorksheetCreationService service = new WorksheetCreationService();

    public void setWorksheet(Worksheet worksheet, Topic topic) {
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

        loadQuestions(worksheet, topicText);
    }

    private void loadQuestions(Worksheet worksheet, String topicText) {
        questionsList.getChildren().clear();

        try {
            List<Question> questions = service.getQuestionsForWorksheet(worksheet.id());

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
}