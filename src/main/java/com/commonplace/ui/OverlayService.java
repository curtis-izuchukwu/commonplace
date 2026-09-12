package com.commonplace.ui;

import java.io.IOException;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

public final class OverlayService {

    private OverlayService() {
        // Utility class
    }

    public static <T> OverlayHandle<T> open(
            Node owner,
            String resourcePath,
            double maxWidth,
            double maxHeight
    ) throws IOException {

        Scene scene = owner.getScene();

        if (!(scene.getRoot() instanceof StackPane rootStack)) {
            throw new IllegalStateException("The current scene root does not support overlays.");
        }

        FXMLLoader loader = new FXMLLoader(OverlayService.class.getResource(resourcePath));
        Parent content = loader.load();

        if (content instanceof Region region) {
            region.setMaxSize(maxWidth, maxHeight);
        }

        StackPane scrim = new StackPane(content);
        scrim.getStyleClass().add("overlay-scrim");

        rootStack.getChildren().add(scrim);
        UiAnimations.animateOverlayOpen(scrim, content);

        return new OverlayHandle<>(loader.getController(), scrim);
    }

    public static void closeFrom(Node node) {
        Node current = node;

        while (current != null) {
            if (current.getStyleClass().contains("overlay-scrim")
                    && current.getParent() instanceof Pane parent) {
                Node overlay = current;
                UiAnimations.animateOverlayClose((StackPane) overlay, () -> parent.getChildren().remove(overlay));
                return;
            }

            current = current.getParent();
        }
    }

    public record OverlayHandle<T>(
            T controller,
            StackPane overlay
    ) {
    }
}
