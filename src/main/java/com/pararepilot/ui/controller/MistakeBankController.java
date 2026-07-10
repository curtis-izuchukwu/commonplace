package com.pararepilot.ui.controller;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.pararepilot.repository.MistakeRepository;
import com.pararepilot.service.MistakeBankService;
import com.pararepilot.ui.AppIcon;
import com.pararepilot.ui.OverlayService;
import com.pararepilot.ui.UiAnimations;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class MistakeBankController {

    @FXML private Label summaryLabel;
    @FXML private CheckBox showResolvedCheckBox;
    @FXML private VBox mistakesList;
    @FXML private Label statusLabel;

    private final MistakeBankService mistakeBankService = new MistakeBankService();
    private Runnable onMistakesChanged;
    private Runnable onClosed;

    public void setOnMistakesChanged(Runnable onMistakesChanged) {
        this.onMistakesChanged = onMistakesChanged;
    }

    public void setOnClosed(Runnable onClosed) {
        this.onClosed = onClosed;
    }

    @FXML
    private void initialize() {
        loadMistakes();
    }

    @FXML
    private void handleRefresh() {
        UiAnimations.fadeListChange(mistakesList, this::loadMistakes);
    }

    @FXML
    private void handleClose() {
        if (onClosed != null) {
            onClosed.run();
        }

        OverlayService.closeFrom(summaryLabel);
    }

    private void loadMistakes() {
        mistakesList.getChildren().clear();

        try {
            List<MistakeRepository.MistakeDisplayItem> mistakes =
                    mistakeBankService.getAllMistakes();

            boolean showResolved = showResolvedCheckBox.isSelected();

            List<MistakeRepository.MistakeDisplayItem> visibleMistakes = mistakes.stream()
                    .filter(mistake -> showResolved || !mistake.resolved())
                    .toList();

            int unresolvedCount = mistakeBankService.countUnresolvedMistakes();

            summaryLabel.setText(
                    unresolvedCount + " unresolved mistake"
                            + (unresolvedCount == 1 ? "" : "s")
                            + " in your bank."
            );

            if (visibleMistakes.isEmpty()) {
                Label emptyLabel = new Label(
                        showResolved
                                ? "No mistakes saved yet."
                                : "No unresolved mistakes. Nice."
                );
                emptyLabel.getStyleClass().add("muted-text");
                mistakesList.getChildren().add(emptyLabel);
                return;
            }

            for (MistakeRepository.MistakeDisplayItem mistake : visibleMistakes) {
                mistakesList.getChildren().add(createMistakeCard(mistake));
            }

        } catch (SQLException e) {
            showError("Failed to load mistake bank", e.getMessage());
        }
    }

    private VBox createMistakeCard(MistakeRepository.MistakeDisplayItem mistake) {
        VBox card = new VBox(10);
        card.getStyleClass().add(
                mistake.resolved() ? "mistake-card-resolved" : "mistake-card"
        );

        Label title = new Label(mistake.topicName() + " - " + mistake.worksheetTitle());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        Label meta = new Label(
                "Created: " + mistake.createdAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        + " - Revisited: " + mistake.timesRevisited()
                        + " - Status: " + (mistake.resolved() ? "Resolved" : "Unresolved")
        );
        meta.getStyleClass().add("muted-text");
        meta.setWrapText(true);

        Label questionHeading = new Label("Question");
        questionHeading.getStyleClass().add("small-label");

        Label question = new Label(mistake.questionPrompt());
        question.setWrapText(true);

        Label answerHeading = new Label("Your Answer");
        answerHeading.getStyleClass().add("small-label");

        Label userAnswer = new Label(mistake.userAnswer());
        userAnswer.setWrapText(true);
        userAnswer.getStyleClass().add("muted-text");

        Label markSchemeHeading = new Label("Mark Scheme");
        markSchemeHeading.getStyleClass().add("small-label");

        Label markScheme = new Label(mistake.markScheme());
        markScheme.setWrapText(true);
        markScheme.getStyleClass().add("muted-text");

        Label noteHeading = new Label("Mistake Note");
        noteHeading.getStyleClass().add("small-label");

        Label note = new Label(
                mistake.mistakeNote() == null || mistake.mistakeNote().isBlank()
                        ? "No mistake note saved."
                        : mistake.mistakeNote()
        );
        note.setWrapText(true);
        note.getStyleClass().add("muted-text");

        Button revisitButton = new Button("Mark Revisited");
        revisitButton.setOnAction(event -> handleMarkRevisited(mistake, card));

        Button resolveButton = new Button(mistake.resolved() ? "Mark Unresolved" : "Mark Resolved");
        resolveButton.setOnAction(event -> handleToggleResolved(mistake, card));

        HBox actions = new HBox(10, revisitButton, resolveButton);

        card.getChildren().addAll(
                title,
                meta,
                questionHeading,
                question,
                answerHeading,
                userAnswer,
                markSchemeHeading,
                markScheme,
                noteHeading,
                note,
                actions
        );

        UiAnimations.animateCardEntry(card);
        return card;
    }

    private void handleMarkRevisited(MistakeRepository.MistakeDisplayItem mistake, VBox card) {
        try {
            mistakeBankService.markRevisited(mistake.id());
            setStatus("Marked mistake as revisited.");
            UiAnimations.flashGlow(card, "mistake-revisited-flash", () -> {
                loadMistakes();
                notifyMistakesChanged();
            });

        } catch (SQLException e) {
            showError("Failed to mark mistake as revisited", e.getMessage());
        }
    }

    private void handleToggleResolved(MistakeRepository.MistakeDisplayItem mistake, VBox card) {
        try {
            mistakeBankService.setResolved(mistake.id(), !mistake.resolved());

            setStatus(
                    mistake.resolved()
                            ? "Mistake marked unresolved."
                            : "Mistake marked resolved."
            );

            if (!mistake.resolved()) {
                card.getStyleClass().remove("mistake-card");
                card.getStyleClass().add("mistake-card-resolved");
                UiAnimations.flashGlow(card, "mistake-resolved-flash", () -> {
                    loadMistakes();
                    notifyMistakesChanged();
                });
            } else {
                UiAnimations.fadeListChange(mistakesList, () -> {
                    loadMistakes();
                    notifyMistakesChanged();
                });
            }

        } catch (SQLException e) {
            showError("Failed to update mistake status", e.getMessage());
        }
    }

    private void notifyMistakesChanged() {
        if (onMistakesChanged != null) {
            onMistakesChanged.run();
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
}
