package com.commonplace;

import java.io.IOException;
import java.sql.SQLException;

import com.commonplace.model.UserSettings;
import com.commonplace.service.AccountService;
import com.commonplace.service.UserSettingsService;
import com.commonplace.ui.AppChrome;
import com.commonplace.ui.AppIcon;
import com.commonplace.ui.AppPreferences;
import com.commonplace.ui.StartupScreen;
import com.commonplace.ui.UiAnimations;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class Main extends Application {

    private static final String STYLESHEET = Main.class
            .getResource("/com/commonplace/css/app.css").toExternalForm();
    private static final long MINIMUM_STARTUP_MILLIS = 650;

    private StartupScreen startupScreen;
    private Task<StartupContext> startupTask;
    private PauseTransition startupPause;
    private FadeTransition openingFade;
    private long startupShownAt;
    private boolean stopped;

    @Override
    public void start(Stage stage) {
        startupScreen = new StartupScreen(stage::close, () -> prepareStartup(stage));
        Scene scene = new Scene(startupScreen, StartupScreen.WIDTH, StartupScreen.HEIGHT);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(STYLESHEET);

        stage.setTitle("Commonplace");
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setResizable(false);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.setOnHidden(event -> stop());
        AppIcon.applyTo(stage);
        stage.show();
        prepareStartup(stage);
    }

    private void prepareStartup(Stage stage) {
        if (stopped || !stage.isShowing()) {
            return;
        }

        startupScreen.showLoading();
        startupShownAt = System.nanoTime();
        startupTask = new Task<>() {
            @Override
            protected StartupContext call() {
                String resourcePath = "/com/commonplace/fxml/LoginView.fxml";
                String breadcrumb = "Sign In";
                UserSettings settings = UserSettings.defaults();
                try {
                    if (new AccountService().restoreRememberedAccount().isPresent()) {
                        resourcePath = "/com/commonplace/fxml/DashboardView.fxml";
                        breadcrumb = "Dashboard";
                        updateMessage("Restoring your study space\u2026");
                        try {
                            settings = new UserSettingsService().load();
                        } catch (SQLException ignored) {
                            // The dashboard can still open with default preferences.
                        }
                    }
                } catch (SQLException ignored) {
                    // Preserve startup's existing fallback to the sign-in screen.
                }
                return new StartupContext(resourcePath, breadcrumb, settings);
            }
        };
        startupTask.messageProperty().addListener((observable, previous, message) -> {
            if (!message.isBlank()) {
                startupScreen.setStatus(message);
            }
        });
        startupTask.setOnSucceeded(event -> {
            if (stopped || !stage.isShowing()) {
                return;
            }
            StartupContext context = startupTask.getValue();
            startupScreen.applyPreferences(context.settings());
            startupScreen.setStatus("Opening your study space\u2026");
            long elapsedMillis = (System.nanoTime() - startupShownAt) / 1_000_000;
            // Give quick starts a readable card and let the status render before FXML work.
            startupPause = new PauseTransition(Duration.millis(
                    Math.max(32, MINIMUM_STARTUP_MILLIS - elapsedMillis)));
            startupPause.setOnFinished(done -> openApplication(stage, context));
            startupPause.play();
        });
        startupTask.setOnFailed(event -> {
            if (!stopped && stage.isShowing()) {
                showStartupFailure(startupTask.getException());
            }
        });

        Thread startupThread = new Thread(startupTask, "commonplace-startup");
        startupThread.setDaemon(true);
        startupThread.start();
    }

    private void openApplication(Stage stage, StartupContext context) {
        if (stopped || !stage.isShowing()) {
            return;
        }
        try {
            // Controllers create controls and timelines, so FXML stays on the FX thread.
            InitialView initialView = loadInitialView(context);
            showApplication(stage, initialView, context.settings());
        } catch (IOException | RuntimeException e) {
            showStartupFailure(e);
        }
    }

    private void showApplication(Stage stage, InitialView initialView, UserSettings settings) {
        AppChrome chrome = AppChrome.create(stage, initialView.root());
        Rectangle2D workArea = Screen.getPrimary().getVisualBounds();
        // Reserve room for the taller Modules page from startup, including login.
        // Smaller displays use the page's scrollbars rather than off-screen controls.
        double windowHeight = Math.min(1000, workArea.getHeight());
        double windowWidth = Math.min(1480, workArea.getWidth());
        Scene scene = new Scene(chrome, windowWidth, windowHeight);
        scene.setFill(Color.TRANSPARENT);

        scene.getStylesheets().add(STYLESHEET);
        AppChrome.setBreadcrumb(scene, initialView.breadcrumb());
        AppPreferences.apply(chrome, settings);

        startupScreen.stop();
        stage.setScene(scene);
        stage.setResizable(true);
        stage.setMinWidth(Math.min(1000, workArea.getWidth()));
        stage.setMinHeight(windowHeight);
        stage.setWidth(windowWidth);
        stage.setHeight(windowHeight);
        stage.setX(workArea.getMinX() + (workArea.getWidth() - windowWidth) / 2);
        stage.setY(workArea.getMinY() + (workArea.getHeight() - windowHeight) / 2);
        AppIcon.applyRuntimeIcons(stage);

        if (!settings.reduceMotion()) {
            openingFade = new FadeTransition(Duration.millis(180), chrome);
            openingFade.setFromValue(0.65);
            openingFade.setToValue(1);
            openingFade.play();
        }
    }

    private InitialView loadInitialView(StartupContext context) throws IOException {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource(context.resourcePath()));
        Parent root = loader.load();
        UiAnimations.installGlobalAnimations(root);
        return new InitialView(root, context.breadcrumb());
    }

    private void showStartupFailure(Throwable failure) {
        failure.printStackTrace();
        startupScreen.showFailure();
    }

    @Override
    public void stop() {
        stopped = true;
        if (startupTask != null) {
            startupTask.cancel();
        }
        if (startupPause != null) {
            startupPause.stop();
        }
        if (openingFade != null) {
            openingFade.stop();
        }
        if (startupScreen != null) {
            startupScreen.stop();
        }
    }

    public static void main(String[] args) {
        launch();
    }

    private record StartupContext(String resourcePath, String breadcrumb, UserSettings settings) {}

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
