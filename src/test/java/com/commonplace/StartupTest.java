package com.commonplace;

import static org.junit.jupiter.api.Assertions.*;

import com.commonplace.model.User;
import com.commonplace.repository.DatabaseManager;
import com.commonplace.repository.RememberedSessionRepository;
import com.commonplace.repository.UserRepository;
import com.commonplace.service.AccountSession;
import com.commonplace.service.UserSettingsService;
import com.commonplace.ui.AppChrome;
import com.commonplace.ui.StartupScreen;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

/** Exercises the real startup handoff with the isolated Maven test profile. */
@EnabledIfSystemProperty(named = "commonplace.uiTest", matches = "true")
class StartupTest {

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                ready.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(() -> {
                Platform.setImplicitExit(false);
                ready.countDown();
            });
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    @Test
    void showsCompactCardBeforeOpeningSignIn() throws Exception {
        new RememberedSessionRepository().clear();
        AccountSession.signOut();
        CountDownLatch opened = new CountDownLatch(1);
        Stage stage = fx(() -> {
            Stage window = new Stage();
            new Main().start(window);
            assertTrue(window.isShowing());
            assertInstanceOf(StartupScreen.class, window.getScene().getRoot());
            assertEquals(StartupScreen.WIDTH, window.getWidth(), 1);
            assertEquals(StartupScreen.HEIGHT, window.getHeight(), 1);
            assertFalse(window.isResizable());
            snapshot((StartupScreen) window.getScene().getRoot(), "startup-preview.png");
            window.sceneProperty().addListener((observable, previous, scene) -> {
                if (scene.getRoot() instanceof AppChrome) opened.countDown();
            });
            return window;
        });
        try {
            assertTrue(opened.await(15, TimeUnit.SECONDS), "The sign-in window should open");
            fx(() -> {
                assertTrue(stage.isResizable());
                assertNotNull(stage.getScene().lookup("#usernameField"));
                assertTrue(stage.getWidth() > StartupScreen.WIDTH);
                assertTrue(stage.getHeight() > StartupScreen.HEIGHT);
                return null;
            });
        } finally {
            fx(() -> { stage.close(); return null; });
        }
    }

    @Test
    void rememberedAccountOpensDashboardWithSavedPreferences() throws Exception {
        User user = new UserRepository().create("startup-" + UUID.randomUUID(), "hash", "salt");
        new RememberedSessionRepository().remember(user);
        AccountSession.signIn(user);
        new UserSettingsService().load();
        try (var connection = DatabaseManager.connect();
                var statement = connection.prepareStatement(
                        "UPDATE user_settings SET theme='LIGHT', accent_color='FOREST', "
                                + "reduce_motion=1 WHERE user_id=?")) {
            statement.setLong(1, user.id());
            statement.executeUpdate();
        }
        AccountSession.signOut();
        CountDownLatch opened = new CountDownLatch(1);
        Stage stage = fx(() -> {
            Stage window = new Stage();
            new Main().start(window);
            window.sceneProperty().addListener((observable, previous, scene) -> {
                if (scene.getRoot() instanceof AppChrome) opened.countDown();
            });
            return window;
        });
        try {
            assertTrue(opened.await(15, TimeUnit.SECONDS), "The remembered dashboard should open");
            fx(() -> {
                assertNotNull(stage.getScene().lookup("#welcomeLabel"));
                assertTrue(stage.getScene().getRoot().getStyleClass()
                        .containsAll(java.util.List.of("theme-light", "accent-forest", "reduce-motion")));
                assertEquals(1, stage.getScene().getRoot().getOpacity());
                assertEquals(user.id(), AccountSession.currentUser().orElseThrow().id());
                return null;
            });
        } finally {
            fx(() -> { stage.close(); return null; });
            new RememberedSessionRepository().clear();
            AccountSession.signOut();
        }
    }

    @Test
    void closingTheCardCancelsTheHandoff() throws Exception {
        CountDownLatch settled = new CountDownLatch(1);
        Stage stage = fx(() -> {
            Stage window = new Stage();
            new Main().start(window);
            ((Button) window.getScene().lookup(".startup-close")).fire();
            PauseTransition wait = new PauseTransition(Duration.millis(900));
            wait.setOnFinished(event -> settled.countDown());
            wait.play();
            return window;
        });
        assertTrue(settled.await(5, TimeUnit.SECONDS));
        fx(() -> {
            assertFalse(stage.isShowing());
            assertInstanceOf(StartupScreen.class, stage.getScene().getRoot());
            return null;
        });
    }

    @Test
    void failedStartupOffersRetryAndStopsTheIndicator() throws Exception {
        fx(() -> {
            AtomicInteger retries = new AtomicInteger();
            StartupScreen screen = new StartupScreen(() -> {}, retries::incrementAndGet);
            scene(screen);
            screen.showFailure();
            Button retry = (Button) screen.lookup(".startup-retry");
            ProgressBar progress = (ProgressBar) screen.lookup(".startup-progress");
            assertTrue(retry.isVisible());
            assertFalse(progress.isVisible());
            assertFalse(progress.isIndeterminate());
            snapshot(screen, "startup-error-preview.png");
            retry.fire();
            assertEquals(1, retries.get());
            screen.showLoading();
            assertFalse(retry.isVisible());
            assertTrue(progress.isVisible());
            assertTrue(progress.isIndeterminate());
            screen.stop();
            return null;
        });
    }

    @Test
    void reducedMotionAndLargerTextFitInsideTheCard() throws Exception {
        User user = new UserRepository().create("startup-layout-" + UUID.randomUUID(), "hash", "salt");
        AccountSession.signIn(user);
        try {
            new UserSettingsService().load();
            try (var connection = DatabaseManager.connect();
                    var statement = connection.prepareStatement(
                            "UPDATE user_settings SET theme='LIGHT', font_size='LARGE', "
                                    + "reduce_motion=1, higher_contrast=1, larger_controls=1 WHERE user_id=?")) {
                statement.setLong(1, user.id());
                statement.executeUpdate();
            }
            var settings = new UserSettingsService().load();
            fx(() -> {
                StartupScreen screen = new StartupScreen(() -> {}, () -> {});
                scene(screen);
                screen.applyPreferences(settings);
                screen.showLoading();
                screen.applyCss();
                screen.layout();
                ProgressBar progress = (ProgressBar) screen.lookup(".startup-progress");
                assertFalse(progress.isIndeterminate());
                Label title = (Label) screen.lookup(".startup-title");
                assertTrue(title.getFont().getSize() >= 32, "The wordmark must retain its display size");
                screen.showFailure();
                snapshot(screen, "startup-light-preview.png");
                var cardBounds = screen.lookup(".startup-card").getBoundsInParent();
                var content = screen.lookup(".startup-content");
                assertTrue(content.getBoundsInParent().getHeight() <= cardBounds.getHeight());
                return null;
            });
        } finally {
            AccountSession.signOut();
        }
    }

    private static void scene(StartupScreen screen) {
        Scene scene = new Scene(screen, StartupScreen.WIDTH, StartupScreen.HEIGHT, Color.TRANSPARENT);
        scene.getStylesheets().add(Main.class.getResource("/com/commonplace/css/app.css").toExternalForm());
    }

    private static void snapshot(StartupScreen screen, String name) throws Exception {
        screen.applyCss();
        screen.layout();
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        WritableImage image = screen.snapshot(parameters, null);
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        }
        Files.createDirectories(Path.of("target"));
        ImageIO.write(buffered, "png", Path.of("target", name).toFile());
    }

    private static <T> T fx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }
}
