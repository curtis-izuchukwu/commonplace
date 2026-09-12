package com.commonplace.ui;

import java.nio.file.Files;
import java.nio.file.Path;

import com.commonplace.service.QuestionImageStorage;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public final class QuestionImageViewFactory {

    private QuestionImageViewFactory() {
        // Utility class
    }

    public static Node create(String imagePath, double fitWidth, double fitHeight) {
        return QuestionImageStorage.resolveImagePath(imagePath)
                .map(path -> createForPath(path, fitWidth, fitHeight))
                .orElse(null);
    }

    public static ImageView createPreview(Path imagePath, double fitWidth, double fitHeight) {
        ImageView imageView = new ImageView();
        imageView.getStyleClass().add("question-image-view");
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setFitWidth(fitWidth);
        imageView.setFitHeight(fitHeight);

        if (imagePath != null && Files.isRegularFile(imagePath)) {
            Image image = new Image(imagePath.toUri().toString(), false);

            if (!image.isError()) {
                imageView.setImage(image);
            }
        }

        return imageView;
    }

    private static Node createForPath(Path imagePath, double fitWidth, double fitHeight) {
        if (!Files.isRegularFile(imagePath)) {
            return unavailableLabel();
        }

        Image image = new Image(imagePath.toUri().toString(), false);

        if (image.isError()) {
            return unavailableLabel();
        }

        ImageView imageView = new ImageView(image);
        imageView.getStyleClass().add("question-image-view");
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setFitWidth(fitWidth);
        imageView.setFitHeight(fitHeight);
        return imageView;
    }

    private static Label unavailableLabel() {
        Label label = new Label("Image unavailable");
        label.getStyleClass().addAll("muted-text", "question-image-unavailable");
        return label;
    }
}
