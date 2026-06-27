package com.pararepilot;

import java.io.IOException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                Main.class.getResource("/com/pararepilot/fxml/ModulesView.fxml")
        );

        Scene scene = new Scene(loader.load(), 1100, 720);

        String stylesheet = Main.class
                .getResource("/com/pararepilot/css/app.css")
                .toExternalForm();

        scene.getStylesheets().add(stylesheet);

        stage.setTitle("ParārePilot");
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}