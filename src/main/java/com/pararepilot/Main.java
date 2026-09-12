package com.pararepilot;

import java.io.IOException;
import java.sql.SQLException;

import com.pararepilot.service.AccountService;
import com.pararepilot.ui.AppChrome;
import com.pararepilot.ui.AppIcon;
import com.pararepilot.ui.UiAnimations;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        InitialView initialView = loadInitialView();
        AppChrome chrome = AppChrome.create(stage, initialView.root());
        Rectangle2D workArea = Screen.getPrimary().getVisualBounds();
        // Reserve room for the taller Modules page from startup, including login.
        // Smaller displays use the page's scrollbars rather than off-screen controls.
        double windowHeight = Math.min(1000, workArea.getHeight());
        double windowWidth = Math.min(1480, workArea.getWidth());
        Scene scene = new Scene(chrome, windowWidth, windowHeight);
        scene.setFill(Color.TRANSPARENT);

        String stylesheet = Main.class
                .getResource("/com/pararepilot/css/app.css")
                .toExternalForm();

        scene.getStylesheets().add(stylesheet);
        AppChrome.setBreadcrumb(scene, initialView.breadcrumb());

        stage.setTitle("PararePilot");
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setScene(scene);
        stage.setMinWidth(Math.min(1000, workArea.getWidth()));
        stage.setMinHeight(windowHeight);
        stage.setX(workArea.getMinX() + (workArea.getWidth() - windowWidth) / 2);
        stage.setY(workArea.getMinY() + (workArea.getHeight() - windowHeight) / 2);
        AppIcon.applyRuntimeIcons(stage);
        stage.show();
    }

    private InitialView loadInitialView() throws IOException {
        String resourcePath = "/com/pararepilot/fxml/LoginView.fxml";
        String breadcrumb = "Sign In";

        try {
            if (new AccountService().restoreRememberedAccount().isPresent()) {
                resourcePath = "/com/pararepilot/fxml/DashboardView.fxml";
                breadcrumb = "Dashboard";
            }
        } catch (SQLException e) {
            resourcePath = "/com/pararepilot/fxml/LoginView.fxml";
            breadcrumb = "Sign In";
        }

        FXMLLoader loader = new FXMLLoader(Main.class.getResource(resourcePath));
        Parent root = loader.load();
        UiAnimations.installGlobalAnimations(root);
        return new InitialView(root, breadcrumb);
    }

    public static void main(String[] args) {
        launch();
    }

    private static final class InitialView {
        private final Parent root;
        private final String breadcrumb;

        private InitialView(Parent root, String breadcrumb) {
            this.root = root;
            this.breadcrumb = breadcrumb;
        }

        private Parent root() {
            return root;
        }

        private String breadcrumb() {
            return breadcrumb;
        }
    }
}
