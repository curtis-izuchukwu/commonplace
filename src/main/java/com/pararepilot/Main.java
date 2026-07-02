package com.pararepilot;

import java.io.IOException;
import java.sql.SQLException;

import com.pararepilot.service.AccountService;
import com.pararepilot.ui.AppIcon;
import com.pararepilot.ui.UiAnimations;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        Scene scene = new Scene(loadInitialRoot(), 1480, 760);

        String stylesheet = Main.class
                .getResource("/com/pararepilot/css/app.css")
                .toExternalForm();

        scene.getStylesheets().add(stylesheet);

        stage.setTitle("ParārePilot");
        stage.setScene(scene);
        stage.setMinWidth(1000);
        stage.setMinHeight(680);
        AppIcon.applyRuntimeIcons(stage);
        stage.show();
    }

    private Parent loadInitialRoot() throws IOException {
        String resourcePath = "/com/pararepilot/fxml/LoginView.fxml";

        try {
            if (new AccountService().restoreRememberedAccount().isPresent()) {
                resourcePath = "/com/pararepilot/fxml/DashboardView.fxml";
            }
        } catch (SQLException e) {
            resourcePath = "/com/pararepilot/fxml/LoginView.fxml";
        }

        FXMLLoader loader = new FXMLLoader(Main.class.getResource(resourcePath));
        Parent root = loader.load();
        UiAnimations.installGlobalAnimations(root);
        return root;
    }

    public static void main(String[] args) {
        launch();
    }
}
