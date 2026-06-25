package com.pararepilot;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class Main extends Application {
    private static final String APP_TITLE = "PararePilot";
    private static final int WINDOW_WIDTH = 960;
    private static final int WINDOW_HEIGHT = 640;

    @Override
    public void start(Stage stage) {
        Label title = new Label(APP_TITLE);
        title.getStyleClass().add("app-title");

        Label subtitle = new Label("Offline-first study tracking for targeted practice.");
        subtitle.getStyleClass().add("app-subtitle");

        VBox content = new VBox(12, title, subtitle);
        content.setPadding(new Insets(32));
        content.getStyleClass().add("welcome-panel");

        BorderPane root = new BorderPane(content);
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        String stylesheet = Main.class
                .getResource("/com/pararepilot/css/app.css")
                .toExternalForm();
        scene.getStylesheets().add(stylesheet);

        stage.setTitle(APP_TITLE);
        stage.setMinWidth(720);
        stage.setMinHeight(480);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
