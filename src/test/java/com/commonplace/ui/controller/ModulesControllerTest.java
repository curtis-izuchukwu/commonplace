package com.commonplace.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.commonplace.model.ImportanceLevel;
import com.commonplace.model.StudyModule;
import com.commonplace.model.User;
import com.commonplace.repository.DatabaseManager;
import com.commonplace.repository.ModuleRepository;
import com.commonplace.repository.UserRepository;
import com.commonplace.service.AccountSession;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

@EnabledIfSystemProperty(named = "commonplace.uiTest", matches = "true")
class ModulesControllerTest {
    private final ModuleRepository modules = new ModuleRepository();
    private User user;

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Runnable onReady =
                () -> {
                    Platform.setImplicitExit(false);
                    ready.countDown();
                };
        try {
            Platform.startup(onReady);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(onReady);
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    @BeforeEach
    void signIn() throws Exception {
        user = new UserRepository().create("module-date-ui-" + UUID.randomUUID(), "hash", "salt");
        AccountSession.signIn(user);
    }

    @AfterEach
    void cleanUp() throws Exception {
        try (var connection = DatabaseManager.connect();
                var statement = connection.prepareStatement("DELETE FROM users WHERE id = ?")) {
            statement.setLong(1, user.id());
            statement.executeUpdate();
        } finally {
            AccountSession.signOut();
        }
    }

    @Test
    void selectedModuleDateCanBeSavedClearedAndReloaded() throws Exception {
        StudyModule module =
                modules.create(
                        "Algorithms", "", LocalDate.now().plusDays(30), ImportanceLevel.HIGH);
        StudyModule other = modules.create("Databases", "", null, ImportanceLevel.MEDIUM);
        LocalDate newDate = LocalDate.now().plusDays(60);

        fx(
                () -> {
                    FXMLLoader loader = load();
                    HBox editor = node(loader, "moduleExamDateEditor");
                    assertFalse(editor.isVisible());
                    assertFalse(editor.isManaged());

                    selectModule(loader, module.name());
                    assertTrue(editor.isVisible());
                    DatePicker picker = node(loader, "selectedModuleExamDatePicker");
                    assertEquals(module.examDate(), picker.getValue());
                    picker.setValue(newDate);
                    assertEquals(
                            module.examDate(), modules.findById(module.id()).orElseThrow().examDate());
                    ModulesControllerTest.<Button>node(loader, "saveModuleExamDateButton").fire();
                    assertEquals(newDate, modules.findById(module.id()).orElseThrow().examDate());
                    assertTrue(
                            moduleCard(loader, module.name()).lookupAll(".record-ledger-value").stream()
                                    .filter(Label.class::isInstance)
                                    .map(Label.class::cast)
                                    .anyMatch(label -> newDate.format(
                                            DateTimeFormatter.ofPattern("d MMM uuuu"))
                                            .equals(label.getText())));

                    selectModule(loader, other.name());
                    assertNull(picker.getValue());
                    selectModule(loader, module.name());
                    assertEquals(newDate, picker.getValue());

                    picker.getEditor().setText("unfinished edit");
                    ModulesControllerTest.<Button>node(loader, "clearModuleExamDateButton").fire();
                    assertNull(picker.getValue());
                    assertTrue(picker.getEditor().getText().isEmpty());
                    assertEquals(newDate, modules.findById(module.id()).orElseThrow().examDate());
                    ModulesControllerTest.<Button>node(loader, "saveModuleExamDateButton").fire();
                    assertNull(modules.findById(module.id()).orElseThrow().examDate());
                    assertNull(modules.findById(other.id()).orElseThrow().examDate());

                    FXMLLoader reloaded = load();
                    selectModule(reloaded, module.name());
                    assertNull(ModulesControllerTest.<DatePicker>node(
                            reloaded, "selectedModuleExamDatePicker").getValue());
                    return null;
                });
    }

    @Test
    void typedDatesAreCommittedAndInvalidDatesDoNotOverwriteSavedDate() throws Exception {
        LocalDate examDate = LocalDate.now().plusDays(30);
        StudyModule module =
                modules.create("Algorithms", "", examDate, ImportanceLevel.MEDIUM);
        StudyModule other =
                modules.create("Databases", "", examDate, ImportanceLevel.MEDIUM);
        LocalDate newDate = examDate.plusDays(1);

        fx(
                () -> {
                    FXMLLoader loader = load();
                    selectModule(loader, module.name());
                    DatePicker picker = node(loader, "selectedModuleExamDatePicker");
                    Button save = node(loader, "saveModuleExamDateButton");
                    picker.getEditor().setText(picker.getConverter().toString(newDate));
                    selectModule(loader, other.name());
                    assertEquals(picker.getConverter().toString(examDate), picker.getEditor().getText());
                    selectModule(loader, module.name());
                    picker.getEditor().setText("invalid date");
                    save.fire();
                    assertEquals(examDate, modules.findById(module.id()).orElseThrow().examDate());
                    assertTrue(ModulesControllerTest.<Label>node(loader, "statusLabel")
                            .getText().contains("valid exam date"));

                    picker.getEditor().setText(picker.getConverter().toString(newDate));
                    save.fire();
                    assertEquals(newDate, picker.getValue());
                    assertEquals(newDate, modules.findById(module.id()).orElseThrow().examDate());
                    return null;
                });
    }

    private FXMLLoader load() throws Exception {
        FXMLLoader loader =
                new FXMLLoader(getClass().getResource("/com/commonplace/fxml/ModulesView.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 1280, 900);
        scene.getStylesheets().add(
                getClass().getResource("/com/commonplace/css/app.css").toExternalForm());
        root.applyCss();
        root.layout();
        return loader;
    }

    private static void selectModule(FXMLLoader loader, String name) {
        moduleCard(loader, name).getOnMouseClicked().handle(null);
    }

    private static Parent moduleCard(FXMLLoader loader, String name) {
        return ModulesControllerTest.<VBox>node(loader, "modulesList").getChildren().stream()
                .map(Parent.class::cast)
                .filter(card -> card.lookupAll(".card-title").stream()
                        .anyMatch(title -> title instanceof Label label && name.equals(label.getText())))
                .findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static <T> T node(FXMLLoader loader, String id) {
        return (T) loader.getNamespace().get(id);
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
