package com.commonplace.ui;

import com.commonplace.repository.MistakeRepository;
import com.commonplace.service.MistakeBankService;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Shared learning UI actions that do not require manual syllabus maintenance. */
public final class LearningUi {

    private LearningUi() {}

    public static void reviewMistake(
            Node owner, MistakeRepository.MistakeDisplayItem mistake, Runnable changed) {
        Dialog<Void> dialog = dialog(owner, "Recall: " + mistake.topicName());
        Label prompt = new Label(mistake.questionPrompt());
        prompt.setWrapText(true);

        TextArea answer = new TextArea();
        answer.setPromptText("Answer from memory before revealing the scheme.");
        answer.setWrapText(true);

        Label scheme = new Label(mistake.markScheme());
        scheme.setWrapText(true);
        scheme.setVisible(false);
        scheme.setManaged(false);

        Button lock = new Button("Lock answer and reveal scheme");
        Button hint = new Button("Use hint (assisted review)");
        boolean[] assisted = {false};
        CheckBox success = new CheckBox("My locked answer meets the mark scheme");
        success.setDisable(true);
        Label status = new Label();
        status.setWrapText(true);
        Node save = dialog.getDialogPane().lookupButton(ButtonType.OK);
        save.setDisable(true);

        hint.setOnAction(
                event -> {
                    assisted[0] = true;
                    scheme.setVisible(true);
                    scheme.setManaged(true);
                    hint.setDisable(true);
                });
        lock.setOnAction(
                event -> {
                    if (answer.getText().isBlank()) {
                        status.setText("Write an answer first.");
                        return;
                    }
                    answer.setEditable(false);
                    scheme.setVisible(true);
                    scheme.setManaged(true);
                    lock.setDisable(true);
                    hint.setDisable(true);
                    success.setDisable(false);
                    save.setDisable(false);
                });

        dialog.getDialogPane()
                .setContent(
                        new VBox(
                                10,
                                prompt,
                                answer,
                                new HBox(8, lock, hint),
                                scheme,
                                success,
                                status));
        save.addEventFilter(
                javafx.event.ActionEvent.ACTION,
                event -> {
                    try {
                        new MistakeBankService()
                                .review(
                                        mistake.id(),
                                        answer.getText(),
                                        success.isSelected(),
                                        assisted[0]);
                        changed.run();
                    } catch (Exception exception) {
                        status.setText(exception.getMessage());
                        event.consume();
                    }
                });
        dialog.showAndWait();
    }

    private static Dialog<Void> dialog(Node owner, String title) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        if (owner.getScene() != null) {
            dialog.initOwner(owner.getScene().getWindow());
        }
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(720);
        if (owner.getScene() != null) {
            dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        return dialog;
    }

}
