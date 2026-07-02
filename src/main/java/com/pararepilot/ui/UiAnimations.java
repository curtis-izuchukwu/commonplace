package com.pararepilot.ui;

import java.util.ArrayList;
import java.util.List;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public final class UiAnimations {

    public enum SlideDirection {
        FROM_LEFT(-28),
        FROM_RIGHT(28),
        FROM_BOTTOM(0);

        private final double xOffset;

        SlideDirection(double xOffset) {
            this.xOffset = xOffset;
        }
    }

    private static final String GLOBAL_INSTALLED_KEY = "ui-animations-global-installed";
    private static final String CARD_INSTALLED_KEY = "ui-animations-card-installed";
    private static final String BUTTON_INSTALLED_KEY = "ui-animations-button-installed";
    private static final String MENU_INSTALLED_KEY = "ui-animations-menu-installed";
    private static final String CLOSING_KEY = "ui-animations-closing";

    private static final List<String> CARD_CLASSES = List.of(
            "entity-card",
            "question-card",
            "dashboard-mini-card",
            "mistake-card",
            "mistake-card-resolved",
            "notification-card"
    );

    private UiAnimations() {
        // Utility class
    }

    public static void installGlobalAnimations(Parent root) {
        if (root == null || Boolean.TRUE.equals(root.getProperties().get(GLOBAL_INSTALLED_KEY))) {
            return;
        }

        root.getProperties().put(GLOBAL_INSTALLED_KEY, true);
        installRecursively(root);
    }

    public static void installRecursively(Node node) {
        if (node == null) {
            return;
        }

        if (node instanceof MenuButton menuButton) {
            installMenuMotion(menuButton);
        } else if (node instanceof ButtonBase button) {
            installButtonFeedback(button);
        }

        if (isCard(node)) {
            installCardHover(node);
        }

        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                installRecursively(child);
            }
        }
    }

    public static void transitionContent(
            StackPane host,
            Node newContent,
            SlideDirection direction
    ) {

        if (host == null || newContent == null) {
            return;
        }

        if (host.getChildren().size() == 1 && host.getChildren().contains(newContent)) {
            resetPageTransitionState(newContent);
            return;
        }

        if (reducedMotion(host)) {
            resetPageTransitionState(newContent);
            host.getChildren().setAll(newContent);
            installRecursively(newContent);
            return;
        }

        List<Node> oldChildren = new ArrayList<>(host.getChildren());

        resetPageTransitionState(newContent);
        newContent.setOpacity(0.0);
        newContent.setTranslateX(direction.xOffset);
        host.getChildren().add(newContent);
        newContent.toFront();
        installRecursively(newContent);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(210), newContent);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(240), newContent);
        slideIn.setFromX(direction.xOffset);
        slideIn.setToX(0);

        ParallelTransition in = new ParallelTransition(fadeIn, slideIn);

        List<javafx.animation.Animation> outAnimations = new ArrayList<>();
        for (Node oldChild : oldChildren) {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(140), oldChild);
            fadeOut.setToValue(0.0);

            TranslateTransition slideOut = new TranslateTransition(Duration.millis(160), oldChild);
            slideOut.setToX(direction.xOffset * -0.45);

            outAnimations.add(new ParallelTransition(fadeOut, slideOut));
        }

        ParallelTransition out = new ParallelTransition();
        out.getChildren().setAll(outAnimations);
        out.setOnFinished(event -> {
            host.getChildren().removeAll(oldChildren);
            oldChildren.forEach(UiAnimations::resetPageTransitionState);
        });

        SequentialTransition sequence = new SequentialTransition(out, in);
        sequence.setOnFinished(event -> resetPageTransitionState(newContent));
        sequence.play();
    }

    public static void animateOverlayOpen(StackPane scrim, Node content) {
        if (scrim == null || content == null) {
            return;
        }

        installRecursively(content);

        if (reducedMotion(scrim)) {
            scrim.setOpacity(1.0);
            content.setOpacity(1.0);
            content.setScaleX(1.0);
            content.setScaleY(1.0);
            return;
        }

        scrim.setOpacity(0.0);
        content.setOpacity(0.0);
        content.setScaleX(0.965);
        content.setScaleY(0.965);
        content.setTranslateY(10);

        FadeTransition scrimFade = new FadeTransition(Duration.millis(150), scrim);
        scrimFade.setToValue(1.0);

        FadeTransition contentFade = new FadeTransition(Duration.millis(190), content);
        contentFade.setToValue(1.0);

        ScaleTransition contentScale = new ScaleTransition(Duration.millis(210), content);
        contentScale.setToX(1.0);
        contentScale.setToY(1.0);

        TranslateTransition contentSlide = new TranslateTransition(Duration.millis(210), content);
        contentSlide.setToY(0);

        new ParallelTransition(scrimFade, contentFade, contentScale, contentSlide).play();
    }

    public static void animateOverlayClose(StackPane scrim, Runnable afterClose) {
        if (scrim == null || Boolean.TRUE.equals(scrim.getProperties().get(CLOSING_KEY))) {
            return;
        }

        scrim.getProperties().put(CLOSING_KEY, true);

        Node content = scrim.getChildren().isEmpty() ? null : scrim.getChildren().get(0);

        if (reducedMotion(scrim) || content == null) {
            run(afterClose);
            return;
        }

        FadeTransition scrimFade = new FadeTransition(Duration.millis(130), scrim);
        scrimFade.setToValue(0.0);

        FadeTransition contentFade = new FadeTransition(Duration.millis(120), content);
        contentFade.setToValue(0.0);

        ScaleTransition contentScale = new ScaleTransition(Duration.millis(140), content);
        contentScale.setToX(0.975);
        contentScale.setToY(0.975);

        ParallelTransition close = new ParallelTransition(scrimFade, contentFade, contentScale);
        close.setOnFinished(event -> run(afterClose));
        close.play();
    }

    public static void animateCardEntry(Node node) {
        if (node == null) {
            return;
        }

        installCardHover(node);
        installRecursively(node);

        if (reducedMotion(node)) {
            node.setOpacity(1.0);
            node.setTranslateY(0);
            return;
        }

        node.setOpacity(0.0);
        node.setTranslateY(12);

        FadeTransition fade = new FadeTransition(Duration.millis(190), node);
        fade.setToValue(1.0);

        TranslateTransition slide = new TranslateTransition(Duration.millis(220), node);
        slide.setToY(0);

        new ParallelTransition(fade, slide).play();
    }

    public static void animateCardRemoval(Node node, Runnable afterRemoval) {
        if (node == null) {
            run(afterRemoval);
            return;
        }

        if (reducedMotion(node)) {
            run(afterRemoval);
            return;
        }

        FadeTransition fade = new FadeTransition(Duration.millis(150), node);
        fade.setToValue(0.0);

        ScaleTransition scale = new ScaleTransition(Duration.millis(155), node);
        scale.setToX(0.965);
        scale.setToY(0.965);

        TranslateTransition slide = new TranslateTransition(Duration.millis(155), node);
        slide.setToY(10);

        ParallelTransition transition = new ParallelTransition(fade, scale, slide);
        transition.setOnFinished(event -> run(afterRemoval));
        transition.play();
    }

    public static void installCardHover(Node node) {
        if (node == null || Boolean.TRUE.equals(node.getProperties().get(CARD_INSTALLED_KEY))) {
            return;
        }

        node.getProperties().put(CARD_INSTALLED_KEY, true);

        node.setOnMouseEntered(event -> {
            if (reducedMotion(node)) {
                return;
            }
            node.getStyleClass().add("motion-hovered");
            animateTranslateY(node, -3, 120);
        });

        node.setOnMouseExited(event -> {
            node.getStyleClass().remove("motion-hovered");
            if (!reducedMotion(node)) {
                animateTranslateY(node, 0, 130);
            }
        });
    }

    public static void installButtonFeedback(ButtonBase button) {
        if (button == null || Boolean.TRUE.equals(button.getProperties().get(BUTTON_INSTALLED_KEY))) {
            return;
        }

        button.getProperties().put(BUTTON_INSTALLED_KEY, true);

        button.setOnMouseEntered(event -> {
            if (!button.isDisabled()) {
                button.getStyleClass().add("button-hovered");
            }
        });

        button.setOnMouseExited(event -> {
            button.getStyleClass().remove("button-hovered");
            if (!reducedMotion(button)) {
                animateTranslateY(button, 0, 90);
            }
        });

        button.setOnMousePressed(event -> {
            if (!button.isDisabled() && !reducedMotion(button)) {
                animateTranslateY(button, 1, 55);
            }
        });

        button.setOnMouseReleased(event -> {
            if (!reducedMotion(button)) {
                animateTranslateY(button, button.isHover() ? -1 : 0, 90);
            }
        });
    }

    public static void installMenuMotion(MenuButton menuButton) {
        if (menuButton == null || Boolean.TRUE.equals(menuButton.getProperties().get(MENU_INSTALLED_KEY))) {
            return;
        }

        menuButton.getProperties().put(MENU_INSTALLED_KEY, true);
        menuButton.showingProperty().addListener((observable, oldValue, showing) -> {
            Node graphic = menuButton.getGraphic();

            if (graphic == null || reducedMotion(menuButton)) {
                return;
            }

            Timeline rotate = new Timeline(
                    new KeyFrame(
                            Duration.millis(150),
                            new KeyValue(graphic.rotateProperty(), showing ? 90 : 0)
                    )
            );
            rotate.play();
        });
    }

    public static void animateRecommendationRefresh(Node panel, Runnable update) {
        if (panel == null || reducedMotion(panel)) {
            run(update);
            return;
        }

        panel.getStyleClass().add("recommendation-refreshing");

        FadeTransition fadeOut = new FadeTransition(Duration.millis(90), panel);
        fadeOut.setToValue(0.68);

        PauseTransition loadingPause = new PauseTransition(Duration.millis(90));
        loadingPause.setOnFinished(event -> run(update));

        FadeTransition fadeIn = new FadeTransition(Duration.millis(160), panel);
        fadeIn.setToValue(1.0);

        SequentialTransition sequence = new SequentialTransition(fadeOut, loadingPause, fadeIn);
        sequence.setOnFinished(event -> panel.getStyleClass().remove("recommendation-refreshing"));
        sequence.play();
    }

    public static void fadeTextChange(Label label, String newText) {
        if (label == null) {
            return;
        }

        if (newText == null) {
            newText = "";
        }

        if (newText.equals(label.getText())) {
            return;
        }

        String text = newText;

        if (reducedMotion(label)) {
            label.setText(text);
            return;
        }

        FadeTransition out = new FadeTransition(Duration.millis(80), label);
        out.setToValue(0.35);
        out.setOnFinished(event -> label.setText(text));

        FadeTransition in = new FadeTransition(Duration.millis(110), label);
        in.setToValue(1.0);

        new SequentialTransition(out, in).play();
    }

    public static void animateProgress(ProgressBar progressBar, double targetProgress) {
        if (progressBar == null) {
            return;
        }

        double target = Math.max(0.0, Math.min(1.0, targetProgress));

        if (reducedMotion(progressBar)) {
            progressBar.setProgress(target);
            return;
        }

        double start = progressBar.getProgress() < 0 ? 0.0 : progressBar.getProgress();
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progressBar.progressProperty(), start)),
                new KeyFrame(Duration.millis(430), new KeyValue(progressBar.progressProperty(), target))
        );
        timeline.play();
    }

    public static void showFloatingXp(Node anchor, int xpDelta) {
        if (anchor == null || xpDelta <= 0 || reducedMotion(anchor)) {
            return;
        }

        Parent parent = anchor.getParent();
        if (!(parent instanceof Pane pane)) {
            return;
        }

        Label label = new Label("+" + xpDelta + " XP");
        label.getStyleClass().add("floating-xp");
        label.setManaged(false);
        label.setMouseTransparent(true);

        Platform.runLater(() -> {
            Bounds bounds = anchor.localToParent(anchor.getBoundsInLocal());
            label.setLayoutX(bounds.getMinX() + bounds.getWidth() - 64);
            label.setLayoutY(bounds.getMinY() - 24);
            pane.getChildren().add(label);

            FadeTransition fade = new FadeTransition(Duration.millis(850), label);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);

            TranslateTransition lift = new TranslateTransition(Duration.millis(850), label);
            lift.setByY(-24);

            ParallelTransition transition = new ParallelTransition(fade, lift);
            transition.setOnFinished(event -> pane.getChildren().remove(label));
            transition.play();
        });
    }

    public static void flashGlow(Node node, String styleClass) {
        flashGlow(node, styleClass, null);
    }

    public static void flashGlow(Node node, String styleClass, Runnable afterFlash) {
        if (node == null || styleClass == null || styleClass.isBlank()) {
            run(afterFlash);
            return;
        }

        node.getStyleClass().add(styleClass);

        if (reducedMotion(node)) {
            node.getStyleClass().remove(styleClass);
            run(afterFlash);
            return;
        }

        PauseTransition pause = new PauseTransition(Duration.millis(720));
        pause.setOnFinished(event -> {
            node.getStyleClass().remove(styleClass);
            run(afterFlash);
        });
        pause.play();
    }

    public static void popIn(Node node) {
        if (node == null) {
            return;
        }

        Platform.runLater(() -> {
            if (reducedMotion(node)) {
                node.setOpacity(1.0);
                node.setScaleX(1.0);
                node.setScaleY(1.0);
                return;
            }

            node.setOpacity(0.0);
            node.setScaleX(0.92);
            node.setScaleY(0.92);

            FadeTransition fade = new FadeTransition(Duration.millis(160), node);
            fade.setToValue(1.0);

            ScaleTransition scale = new ScaleTransition(Duration.millis(170), node);
            scale.setToX(1.0);
            scale.setToY(1.0);

            new ParallelTransition(fade, scale).play();
        });
    }

    public static void animateDifficultyIndicator(Node indicator) {
        if (indicator == null) {
            return;
        }

        Platform.runLater(() -> {
            if (reducedMotion(indicator)) {
                return;
            }

            List<Node> bars = findByStyleClass(indicator, "difficulty-bar");
            SequentialTransition sequence = new SequentialTransition();

            for (Node bar : bars) {
                bar.setScaleY(0.22);
                bar.setOpacity(0.55);

                ScaleTransition scale = new ScaleTransition(Duration.millis(115), bar);
                scale.setToY(1.0);

                FadeTransition fade = new FadeTransition(Duration.millis(115), bar);
                fade.setToValue(1.0);

                sequence.getChildren().add(new ParallelTransition(scale, fade));
            }

            sequence.play();
        });
    }

    public static void softPulse(Node node) {
        if (node == null || reducedMotion(node)) {
            return;
        }

        ScaleTransition grow = new ScaleTransition(Duration.millis(150), node);
        grow.setToX(1.018);
        grow.setToY(1.018);

        ScaleTransition settle = new ScaleTransition(Duration.millis(170), node);
        settle.setToX(1.0);
        settle.setToY(1.0);

        new SequentialTransition(grow, settle).play();
    }

    public static void validationError(Node... nodes) {
        if (nodes == null) {
            return;
        }

        for (Node node : nodes) {
            if (node == null) {
                continue;
            }

            node.getStyleClass().add("validation-error");

            if (!reducedMotion(node)) {
                Timeline shake = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(node.translateXProperty(), 0)),
                        new KeyFrame(Duration.millis(38), new KeyValue(node.translateXProperty(), -4)),
                        new KeyFrame(Duration.millis(76), new KeyValue(node.translateXProperty(), 4)),
                        new KeyFrame(Duration.millis(114), new KeyValue(node.translateXProperty(), -3)),
                        new KeyFrame(Duration.millis(152), new KeyValue(node.translateXProperty(), 3)),
                        new KeyFrame(Duration.millis(190), new KeyValue(node.translateXProperty(), 0))
                );
                shake.play();
            }

            PauseTransition pause = new PauseTransition(Duration.millis(850));
            pause.setOnFinished(event -> node.getStyleClass().remove("validation-error"));
            pause.play();
        }
    }

    public static void validationSuccess(Node... nodes) {
        if (nodes == null) {
            return;
        }

        for (Node node : nodes) {
            if (node == null) {
                continue;
            }

            node.getStyleClass().add("validation-success");

            PauseTransition pause = new PauseTransition(Duration.millis(reducedMotion(node) ? 120 : 700));
            pause.setOnFinished(event -> node.getStyleClass().remove("validation-success"));
            pause.play();
        }
    }

    public static void fadeListChange(Node list, Runnable update) {
        if (list == null || reducedMotion(list)) {
            run(update);
            return;
        }

        FadeTransition out = new FadeTransition(Duration.millis(110), list);
        out.setToValue(0.25);
        out.setOnFinished(event -> run(update));

        FadeTransition in = new FadeTransition(Duration.millis(160), list);
        in.setToValue(1.0);

        new SequentialTransition(out, in).play();
    }

    public static boolean reducedMotion(Node node) {
        Node current = node;

        while (current != null) {
            if (current.getStyleClass().contains("reduce-motion")) {
                return true;
            }
            current = current.getParent();
        }

        Scene scene = node == null ? null : node.getScene();
        return scene != null
                && scene.getRoot() != null
                && scene.getRoot().getStyleClass().contains("reduce-motion");
    }

    private static void animateTranslateY(Node node, double translateY, double millis) {
        TranslateTransition transition = new TranslateTransition(Duration.millis(millis), node);
        transition.setToY(translateY);
        transition.play();
    }

    private static void resetPageTransitionState(Node node) {
        if (node == null) {
            return;
        }

        node.setOpacity(1.0);
        node.setTranslateX(0);
        node.setTranslateY(0);
        node.setScaleX(1.0);
        node.setScaleY(1.0);
    }

    private static boolean isCard(Node node) {
        for (String styleClass : CARD_CLASSES) {
            if (node.getStyleClass().contains(styleClass)) {
                return true;
            }
        }

        return false;
    }

    private static List<Node> findByStyleClass(Node root, String styleClass) {
        List<Node> matches = new ArrayList<>();

        if (root.getStyleClass().contains(styleClass)) {
            matches.add(root);
        }

        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                matches.addAll(findByStyleClass(child, styleClass));
            }
        }

        return matches;
    }

    private static void run(Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }
}
