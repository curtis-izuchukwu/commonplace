package com.commonplace.ui.controller;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.commonplace.model.Question;
import com.commonplace.model.Topic;
import com.commonplace.model.Worksheet;
import com.commonplace.service.WorksheetRecommendation;
import com.commonplace.service.WorksheetCreationService;
import com.commonplace.ui.AppIcon;
import com.commonplace.ui.OverlayService;
import com.commonplace.ui.QuestionImageViewFactory;
import com.commonplace.ui.UiAnimations;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class WorksheetDetailController {

    @FXML private Label worksheetTitleLabel;
    @FXML private Label worksheetMetaLabel;
    @FXML private Label worksheetDescriptionLabel;
    @FXML private VBox recommendationDetailsPanel;
    @FXML private Label recommendationDetailsLabel;
    @FXML private VBox questionsList;

    private final WorksheetCreationService service = new WorksheetCreationService();

    private Worksheet worksheet;
    private Topic topic;
    private Runnable onWorksheetUpdated;

    public void setOnWorksheetUpdated(Runnable onWorksheetUpdated) {
        this.onWorksheetUpdated = onWorksheetUpdated;
    }

    public void setWorksheet(Worksheet worksheet, Topic topic) {
        this.worksheet = worksheet;
        this.topic = topic;

        worksheetTitleLabel.setText(worksheet.title());

        String topicText = topic == null ? "Unknown topic" : topic.name();

        worksheetMetaLabel.setText(
                "Topic: " + topicText
                        + " - Difficulty: " + worksheet.difficulty()
                        + " - Importance: " + worksheet.importance()
                        + " - Questions: loading..."
        );

        worksheetDescriptionLabel.setText(
                worksheet.description() == null || worksheet.description().isBlank()
                        ? "No description yet."
                        : worksheet.description()
        );

        loadQuestions();
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
                "Priority score: " + recommendation.priorityScore() + "/100"
                        + "\nLast attempt: " + lastAttemptText(recommendedWorksheet)
                        + "\nLatest score: " + formatScore(recommendedWorksheet.latestScorePercent())
                        + "\nTopic confidence: " + recommendedTopic.confidence()
                        + "\nDifficulty: " + recommendedWorksheet.difficulty()
                        + "\nWorksheet priority: " + recommendedWorksheet.importance()
                        + "\nTopic priority: " + recommendedTopic.importance()
                        + "\nFailure streak: " + recommendedWorksheet.failureStreak()
                        + "\n\nRecommendation breakdown: " + recommendation.explanation()
        );

        recommendationDetailsPanel.setVisible(true);
        recommendationDetailsPanel.setManaged(true);
        UiAnimations.popIn(recommendationDetailsPanel);
    }

    @FXML
    private void handleStartAttempt() {
        if (worksheet == null) {
            UiAnimations.validationError(worksheetTitleLabel);
            showError("Cannot start attempt", "No worksheet is loaded.");
            return;
        }

        try {
            var handle = OverlayService.<AttemptWorksheetController>open(
                    worksheetTitleLabel,
                    "/com/commonplace/fxml/AttemptWorksheetView.fxml",
                    900,
                    780
            );

            AttemptWorksheetController controller = handle.controller();
            controller.setWorksheet(worksheet, topic, () -> {
                loadQuestions();

                if (onWorksheetUpdated != null) {
                    onWorksheetUpdated.run();
                }
            });

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
                            + " - Difficulty: " + worksheet.difficulty()
                            + " - Importance: " + worksheet.importance()
                            + " - Questions: " + questions.size()
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

        Label heading = new Label("Question " + question.questionOrder() + " - " + question.maxMarks() + " marks");
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

    @FXML
    private void handleClose() {
        OverlayService.closeFrom(worksheetTitleLabel);
    }

    private String lastAttemptText(Worksheet worksheet) {
        if (worksheet.lastAttemptedAt() == null) {
            return "Never attempted";
        }

        long days = ChronoUnit.DAYS.between(
                worksheet.lastAttemptedAt().toLocalDate(),
                LocalDate.now()
        );

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
}
