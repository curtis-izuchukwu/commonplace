package com.pararepilot.ui.controller;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.pararepilot.model.Question;
import com.pararepilot.model.Topic;
import com.pararepilot.model.Worksheet;
import com.pararepilot.model.WorksheetAttempt;
import com.pararepilot.repository.AnswerRepository;
import com.pararepilot.service.AttemptService;
import com.pararepilot.service.WorksheetCreationService;
import com.pararepilot.ui.OverlayService;
import com.pararepilot.ui.UiAnimations;
import com.pararepilot.util.DateUtils;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

public class AttemptWorksheetController {

    @FXML private Label worksheetTitleLabel;
    @FXML private Label worksheetMetaLabel;
    @FXML private VBox questionsContainer;
    @FXML private Label scorePreviewLabel;
    @FXML private Label statusLabel;

    private final WorksheetCreationService worksheetService = new WorksheetCreationService();
    private final AttemptService attemptService = new AttemptService();

    private Worksheet worksheet;
    private Topic topic;
    private LocalDateTime startedAt;
    private Runnable onAttemptSaved;

    @FXML
    private void initialize() {
        startedAt = DateUtils.now();
    }

    public void setWorksheet(Worksheet worksheet, Topic topic, Runnable onAttemptSaved) {
        this.worksheet = worksheet;
        this.topic = topic;
        this.onAttemptSaved = onAttemptSaved;

        worksheetTitleLabel.setText(worksheet.title());

        String topicText = topic == null ? "Unknown topic" : topic.name();

        worksheetMetaLabel.setText(
                "Topic: " + topicText
                        + " - Difficulty: " + worksheet.difficulty()
                        + " - Importance: " + worksheet.importance()
        );

        loadQuestions();
    }

    @FXML
    private void handleSubmitAttempt() {
        try {
            List<AnswerRepository.AnswerDraft> drafts = collectAnswerDrafts();

            WorksheetAttempt attempt = attemptService.submitAttempt(
                    worksheet.id(),
                    startedAt,
                    drafts
            );

            UiAnimations.fadeTextChange(
                    scorePreviewLabel,
                    "Score: " + attempt.score()
                            + "/" + attempt.maxScore()
                            + " (" + String.format("%.0f%%", attempt.scorePercent()) + ")"
            );

            setStatus("Attempt saved. Complete reflection to update stats.");

            openReflection(attempt);

        } catch (IllegalArgumentException e) {
            UiAnimations.validationError(questionsContainer);
            setStatus(e.getMessage());
        } catch (SQLException e) {
            showError("Failed to save attempt", e.getMessage());
        }
    }

    private void openReflection(WorksheetAttempt attempt) {
        try {
            var handle = OverlayService.<ReflectionController>open(
                    worksheetTitleLabel,
                    "/com/pararepilot/fxml/ReflectionView.fxml",
                    720,
                    680
            );

            ReflectionController controller = handle.controller();
            controller.setContext(worksheet, attempt, () -> {
                if (onAttemptSaved != null) {
                    onAttemptSaved.run();
                }

                closeWindow();
            });

        } catch (IOException e) {
            showError("Failed to open reflection screen", e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    private void loadQuestions() {
        questionsContainer.getChildren().clear();

        try {
            List<Question> questions = worksheetService.getQuestionsForWorksheet(worksheet.id());

            if (questions.isEmpty()) {
                Label emptyLabel = new Label("This worksheet has no questions.");
                emptyLabel.getStyleClass().add("muted-text");
                questionsContainer.getChildren().add(emptyLabel);
                return;
            }

            for (Question question : questions) {
                questionsContainer.getChildren().add(createQuestionAttemptCard(question));
            }

        } catch (SQLException e) {
            showError("Failed to load questions", e.getMessage());
        }
    }

    private VBox createQuestionAttemptCard(Question question) {
        VBox card = new VBox(10);
        card.getStyleClass().add("question-card");
        card.setUserData(question);

        Label heading = new Label(
                "Question " + question.questionOrder()
                        + " - " + question.maxMarks() + " marks"
        );
        heading.getStyleClass().add("card-title");

        Label promptLabel = new Label(question.prompt());
        promptLabel.setWrapText(true);

        TextArea answerArea = new TextArea();
        answerArea.setPromptText("Write your answer here...");
        answerArea.setWrapText(true);
        answerArea.setPrefRowCount(4);
        answerArea.setUserData("answer");

        Button revealButton = new Button("Reveal Mark Scheme");

        Label markSchemeHeading = new Label("Mark Scheme");
        markSchemeHeading.getStyleClass().add("small-label");
        markSchemeHeading.setVisible(false);
        markSchemeHeading.setManaged(false);

        Label markSchemeLabel = new Label(question.markScheme());
        markSchemeLabel.setWrapText(true);
        markSchemeLabel.getStyleClass().add("muted-text");
        markSchemeLabel.setVisible(false);
        markSchemeLabel.setManaged(false);

        revealButton.setOnAction(event -> {
            markSchemeHeading.setVisible(true);
            markSchemeHeading.setManaged(true);
            markSchemeLabel.setVisible(true);
            markSchemeLabel.setManaged(true);
            UiAnimations.popIn(markSchemeHeading);
            UiAnimations.popIn(markSchemeLabel);
            revealButton.setDisable(true);
        });

        Label marksLabel = new Label("Awarded marks");
        marksLabel.getStyleClass().add("small-label");

        Spinner<Integer> awardedMarksSpinner = new Spinner<>(0, question.maxMarks(), 0);
        awardedMarksSpinner.setEditable(true);
        awardedMarksSpinner.setUserData("marks");
        awardedMarksSpinner.setMaxWidth(220);

        CheckBox mistakeCheckBox = new CheckBox("Add to mistake review later");
        mistakeCheckBox.setUserData("mistake");

        TextArea mistakeNoteArea = new TextArea();
        mistakeNoteArea.setPromptText("Mistake note, e.g. forgot deletion case");
        mistakeNoteArea.setWrapText(true);
        mistakeNoteArea.setPrefRowCount(2);
        mistakeNoteArea.setUserData("mistakeNote");

        card.getChildren().addAll(
                heading,
                promptLabel,
                answerArea,
                revealButton,
                markSchemeHeading,
                markSchemeLabel,
                marksLabel,
                awardedMarksSpinner,
                mistakeCheckBox,
                mistakeNoteArea
        );

        UiAnimations.animateCardEntry(card);
        return card;
    }

    private List<AnswerRepository.AnswerDraft> collectAnswerDrafts() {
        List<AnswerRepository.AnswerDraft> drafts = new ArrayList<>();

        for (int i = 0; i < questionsContainer.getChildren().size(); i++) {
            VBox card = (VBox) questionsContainer.getChildren().get(i);

            if (!(card.getUserData() instanceof Question question)) {
                continue;
            }

            TextArea answerArea = null;
            Spinner<Integer> marksSpinner = null;
            CheckBox mistakeCheckBox = null;
            TextArea mistakeNoteArea = null;

            for (var child : card.getChildren()) {
                if (child instanceof TextArea textArea && "answer".equals(textArea.getUserData())) {
                    answerArea = textArea;
                }

                if (child instanceof Spinner<?> spinner && "marks".equals(spinner.getUserData())) {
                    @SuppressWarnings("unchecked")
                    Spinner<Integer> typedSpinner = (Spinner<Integer>) spinner;
                    marksSpinner = typedSpinner;
                }

                if (child instanceof CheckBox checkBox && "mistake".equals(checkBox.getUserData())) {
                    mistakeCheckBox = checkBox;
                }

                if (child instanceof TextArea textArea && "mistakeNote".equals(textArea.getUserData())) {
                    mistakeNoteArea = textArea;
                }
            }

            if (answerArea == null || marksSpinner == null || mistakeCheckBox == null || mistakeNoteArea == null) {
                throw new IllegalStateException("Attempt form row is missing fields.");
            }

            drafts.add(new AnswerRepository.AnswerDraft(
                    question.id(),
                    answerArea.getText(),
                    marksSpinner.getValue(),
                    question.maxMarks(),
                    mistakeCheckBox.isSelected(),
                    mistakeNoteArea.getText()
            ));
        }

        return drafts;
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void closeWindow() {
        OverlayService.closeFrom(worksheetTitleLabel);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
