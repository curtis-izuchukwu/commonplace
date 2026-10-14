package com.commonplace.ui;

import com.commonplace.model.UserSettings;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** A small, independent startup card, shown before accounts and views are loaded. */
public final class StartupScreen extends StackPane {

    public static final double WIDTH = 480;
    public static final double HEIGHT = 340;

    private final ProgressBar progress = new ProgressBar();
    private final Label status = new Label();
    private final Button retry = new Button("Try again");

    public StartupScreen(Runnable onClose, Runnable onRetry) {
        getStyleClass().add("startup-root");
        setAccessibleText("Commonplace is opening");

        ImageView mark = new ImageView(AppIcon.image());
        mark.setFitWidth(64);
        mark.setFitHeight(64);
        mark.setPreserveRatio(true);
        mark.setSmooth(true);
        mark.setMouseTransparent(true);

        Label title = new Label("Commonplace");
        title.getStyleClass().add("startup-title");
        VBox.setMargin(title, new Insets(14, 0, 0, 0));

        Label subtitle = new Label("A little progress, every day.");
        subtitle.getStyleClass().add("startup-subtitle");
        VBox.setMargin(subtitle, new Insets(6, 0, 0, 0));

        progress.getStyleClass().add("startup-progress");
        progress.setMaxWidth(260);
        progress.setAccessibleText("Opening Commonplace");
        VBox.setMargin(progress, new Insets(28, 0, 0, 0));

        status.getStyleClass().add("startup-status");
        status.setWrapText(true);
        status.setMaxWidth(340);
        status.setAlignment(Pos.CENTER);
        VBox.setMargin(status, new Insets(12, 0, 0, 0));

        retry.getStyleClass().addAll("primary-button", "startup-retry");
        retry.setOnAction(event -> onRetry.run());
        VBox.setMargin(retry, new Insets(14, 0, 0, 0));

        VBox content = new VBox(mark, title, subtitle, progress, status, retry);
        content.setAlignment(Pos.CENTER);
        content.getStyleClass().add("startup-content");

        Button close = new Button("\u00d7");
        close.getStyleClass().add("startup-close");
        close.setAccessibleText("Close Commonplace");
        close.setOnAction(event -> onClose.run());
        StackPane.setAlignment(close, Pos.TOP_RIGHT);
        StackPane.setMargin(close, new Insets(10, 12, 0, 0));

        StackPane card = new StackPane(content, close);
        card.getStyleClass().add("startup-card");
        getChildren().add(card);
        showLoading();
    }

    public void showLoading() {
        getStyleClass().remove("startup-failed");
        retry.setVisible(false);
        retry.setManaged(false);
        progress.setVisible(true);
        progress.setManaged(true);
        progress.setProgress(UiAnimations.reducedMotion(this) ? 0.35 : -1);
        setStatus("Preparing your study space\u2026");
    }

    public void applyPreferences(UserSettings settings) {
        AppPreferences.apply(this, settings);
        setAccessibleText(status.getText());
        if (settings.reduceMotion()) {
            progress.setProgress(0.35);
        }
    }

    public void setStatus(String message) {
        status.setText(message);
        setAccessibleText(message);
    }

    public void showFailure() {
        stop();
        getStyleClass().add("startup-failed");
        progress.setVisible(false);
        progress.setManaged(false);
        setStatus("Your study space couldn\u2019t open. Please try again.");
        retry.setVisible(true);
        retry.setManaged(true);
        retry.requestFocus();
    }

    public void stop() {
        // A determinate value also stops the skin's indeterminate animation.
        progress.setProgress(1);
    }
}
