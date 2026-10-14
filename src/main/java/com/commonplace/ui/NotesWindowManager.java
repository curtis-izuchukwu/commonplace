package com.commonplace.ui;

import com.commonplace.model.NoteKey;
import com.commonplace.model.User;
import com.commonplace.model.UserSettings;
import com.commonplace.service.AccountSession;
import com.commonplace.service.UserSettingsService;
import com.commonplace.ui.controller.NotesWindowController;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/** One nonmodal writing window, with account and main-window close guards. */
public final class NotesWindowManager {
    private static final NotesWindowManager INSTANCE = new NotesWindowManager();
    private final Map<Long, NoteKey> lastSelections = new HashMap<>();
    private volatile NotesWindowController controller;
    private volatile long accountId;
    private Stage stage, mainStage;
    private EventHandler<WindowEvent> mainCloseGuard;

    private NotesWindowManager() {
        AccountSession.addBeforeChangeListener(this::beforeAccountChange);
    }

    public static void open(Node source, NoteKey destination) throws IOException, SQLException {
        INSTANCE.show(source, destination);
    }

    private void show(Node source, NoteKey destination) throws IOException, SQLException {
        requireFxThread();
        long userId = AccountSession.currentUserId();
        if (controller != null && accountId != userId) {
            throw new IllegalStateException("Close the previous account's Notes window before switching accounts.");
        }
        if (stage == null) {
            UserSettings settings = new UserSettingsService().load();
            FXMLLoader loader = new FXMLLoader(NotesWindowManager.class.getResource("/com/commonplace/fxml/NotesWindowView.fxml"));
            Parent root = loader.load();
            NotesWindowController createdController = loader.getController();
            Stage created = new Stage(StageStyle.TRANSPARENT);
            AppChrome chrome = AppChrome.create(created, root);
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            Scene scene = new Scene(chrome, Math.min(1120, bounds.getWidth()), Math.min(790, bounds.getHeight()));
            scene.setFill(Color.TRANSPARENT);
            scene.getStylesheets().add(NotesWindowManager.class.getResource("/com/commonplace/css/app.css").toExternalForm());
            AppPreferences.apply(scene, settings);
            AppChrome.setBreadcrumb(scene, "Notes");
            AppChrome.setCommands(scene, java.util.List.of(new AppChrome.Command("Search notes", "Find text in your notebooks.",
                    "find search notes", () -> root.lookup("#searchField").requestFocus())));
            created.setTitle("Commonplace / Notes");
            created.setMinWidth(Math.min(660, bounds.getWidth()));
            created.setMinHeight(Math.min(440, bounds.getHeight()));
            created.setScene(scene);
            AppIcon.applyTo(created);
            createdController.start(userId, scene);
            accountId = userId;
            controller = createdController;
            stage = created;
            stage.setOnCloseRequest(event -> {
                event.consume();
                withSavedNotes(true, () -> {});
            });
            stage.focusedProperty().addListener((observable, previous, focused) -> {
                if (focused && controller != null) controller.refreshNavigation();
            });
            installMainCloseGuard(source);
            created.show();
        }
        stage.setIconified(false);
        stage.toFront();
        stage.requestFocus();
        controller.requestNavigation(destination == null ? lastSelections.get(userId) : destination);
    }

    private void installMainCloseGuard(Node source) {
        if (source == null || source.getScene() == null || !(source.getScene().getWindow() instanceof Stage primary)) return;
        mainStage = primary;
        mainCloseGuard = event -> {
            event.consume();
            withSavedNotes(true, () -> primary.fireEvent(new WindowEvent(primary, WindowEvent.WINDOW_CLOSE_REQUEST)));
        };
        primary.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, mainCloseGuard);
    }

    /** Continuation runs on the FX thread only after every queued write finishes successfully. */
    public static void withSavedNotes(boolean close, Runnable continuation) {
        requireFxThread();
        if (INSTANCE.controller == null) { continuation.run(); return; }
        INSTANCE.stage.setIconified(false);
        if (close) INSTANCE.stage.toFront();
        NotesWindowController active = INSTANCE.controller;
        active.prepareToLeave(() -> {
            if (close) INSTANCE.closeWindow(false);
            try { continuation.run(); }
            finally { if (!close && INSTANCE.controller == active) active.unlock(); }
        }, close ? () -> {
            INSTANCE.closeWindow(true);
            continuation.run();
        } : null);
    }

    public static void refreshOpenWindow() {
        if (INSTANCE.controller != null) INSTANCE.controller.refreshNavigation();
    }

    public static void applyPreferences(UserSettings settings) {
        if (INSTANCE.stage != null) AppPreferences.apply(INSTANCE.stage.getScene(), settings);
    }

    private void beforeAccountChange(User nextUser) {
        NotesWindowController active = controller;
        if (active == null || nextUser != null && nextUser.id() == accountId) return;
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Close Notes before switching accounts from background work.");
        }
        if (active.hasPendingChanges()) {
            throw new IllegalStateException("Notes have unsaved changes. Save them or close Notes with a recovery copy before switching accounts.");
        }
        // Normal UI transitions use withSavedNotes. This also guards direct AccountSession calls.
        closeWindow(false);
    }

    private void closeWindow(boolean recovered) {
        if (controller == null) return;
        if (controller.currentKey() != null) lastSelections.put(accountId, controller.currentKey());
        controller.dispose(recovered);
        if (mainStage != null && mainCloseGuard != null) mainStage.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, mainCloseGuard);
        stage.hide();
        stage = null;
        controller = null;
        mainStage = null;
        mainCloseGuard = null;
    }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Notes window operations require the JavaFX thread.");
    }
}
