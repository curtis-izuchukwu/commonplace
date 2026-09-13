package com.commonplace.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.commonplace.model.*;
import com.commonplace.press.*;
import com.commonplace.repository.DatabaseManager;
import com.commonplace.repository.ModuleRepository;
import com.commonplace.repository.QuestionRepository;
import com.commonplace.repository.TopicRepository;
import com.commonplace.repository.UserRepository;
import com.commonplace.service.AccountSession;
import com.commonplace.service.PressWorksheetGenerationService;
import com.commonplace.service.WorksheetCreationService;
import com.commonplace.ui.LevelUi;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@EnabledIfSystemProperty(named = "commonplace.uiTest", matches = "true")
class WorksheetCreateControllerTest {
    private static final String RESPONSE =
            """
            {"metadata":{"mode":"ai"},"questions":[{"question":"Explain recursion.",
             "answer":"A function calls itself.","markScheme":["Self-call","Base case"],
             "marks":8,"type":"long-answer"}]}
            """;

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(
                () -> {
                    Platform.setImplicitExit(false);
                    ready.countDown();
                });
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    @Test
    void topicLearningEvidenceViewLoads() throws Exception {
        fx(
                () -> {
                    FXMLLoader loader =
                            new FXMLLoader(
                                    getClass()
                                            .getResource(
                                                    "/com/commonplace/fxml/TopicDetailView.fxml"));
                    Parent root = loader.load();
                    Scene scene = new Scene(root, 1024, 720);
                    scene.getStylesheets()
                            .add(
                                    getClass()
                                            .getResource("/com/commonplace/css/app.css")
                                            .toExternalForm());
                    root.applyCss();
                    root.layout();
                    assertNotNull(root);
                    assertNotNull(loader.getNamespace().get("learningEvidenceBox"));
                    assertNotNull(loader.getNamespace().get("masteryValueLabel"));
                    assertNotNull(loader.getNamespace().get("topicMetaBox"));
                    assertNotNull(loader.getNamespace().get("evidencePane"));
                    assertFalse(root.lookupAll(".topic-mastery-card").isEmpty());
                    return null;
                });
    }

    @Test
    void masteryStatFillTracksItsPercentage() throws Exception {
        fx(
                () -> {
                    var cell =
                            (javafx.scene.layout.StackPane)
                                    LevelUi.createMasteryStatCell(42);
                    cell.setMinSize(200, 46);
                    cell.setPrefSize(200, 46);
                    cell.setMaxSize(200, 46);
                    var host = new javafx.scene.layout.StackPane(cell);
                    Scene scene = new Scene(host, 240, 80);
                    scene.getStylesheets()
                            .add(
                                    getClass()
                                            .getResource("/com/commonplace/css/app.css")
                                            .toExternalForm());
                    host.applyCss();
                    host.layout();

                    var fill =
                            (javafx.scene.layout.Region) cell.lookup(".mastery-stat-fill");
                    assertNotNull(fill);
                    assertEquals(cell.getWidth() * 0.42, fill.getWidth(), 1.0);
                    assertEquals("Mastery 42%", cell.getAccessibleText());
                    return null;
                });
    }

    @Test
    void topicDetailsRenderAsStructuredMetadataInsteadOfDiagnosticText() throws Exception {
        User user =
                new UserRepository().create("topic-ui-" + UUID.randomUUID(), "hash", "salt");
        AccountSession.signIn(user);
        StudyModule module =
                new ModuleRepository()
                        .create("Algorithms", "", null, ImportanceLevel.HIGH);
        Topic topic =
                new TopicRepository()
                        .create(
                                module.id(),
                                "Time Complexity",
                                "",
                                ImportanceLevel.MEDIUM,
                                ConfidenceLevel.MEDIUM);
        new WorksheetCreationService()
                .createWorksheetWithQuestions(
                        topic.id(),
                        "Asymptotic analysis",
                        "",
                        DifficultyLevel.MEDIUM,
                        ImportanceLevel.MEDIUM,
                        List.of(
                                new QuestionRepository.QuestionDraft(
                                        "Compare O(n) and O(log n).", "Growth rate", 3, null)),
                        "Computer Science",
                        "Asymptotic analysis");

        try {
            fx(
                    () -> {
                        FXMLLoader loader =
                                new FXMLLoader(
                                        getClass()
                                                .getResource(
                                                        "/com/commonplace/fxml/TopicDetailView.fxml"));
                        Parent root = loader.load();
                        loader.<TopicDetailController>getController().setTopic(topic, module);
                        Scene scene = new Scene(root, 1024, 720);
                        scene.getStylesheets()
                                .add(
                                        getClass()
                                                .getResource("/com/commonplace/css/app.css")
                                                .toExternalForm());
                        root.applyCss();
                        root.layout();

                        assertTrue(root.lookupAll(".record-stat-cell").size() >= 5);
                        assertTrue(root.lookupAll(".status-badge").isEmpty());
                        assertTrue(root.lookupAll(".priority-chip").isEmpty());
                        assertFalse(root.lookupAll(".entity-card").isEmpty());
                        assertFalse(root.lookupAll(".topic-mastery-card").isEmpty());
                        writeSnapshot(root, "commonplace.topic.screenshot");
                        return null;
                    });
        } finally {
            new ModuleRepository().deleteById(module.id());
            try (var connection = DatabaseManager.connect();
                    var statement = connection.prepareStatement("DELETE FROM users WHERE id=?")) {
                statement.setLong(1, user.id());
                statement.executeUpdate();
            }
            AccountSession.signOut();
        }
    }

    @Test
    void moduleAndDashboardMetadataRolesLoadEndToEnd() throws Exception {
        User user =
                new UserRepository().create("metadata-ui-" + UUID.randomUUID(), "hash", "salt");
        AccountSession.signIn(user);
        StudyModule module =
                new ModuleRepository()
                        .create(
                                "Algorithms",
                                "Core algorithms module",
                                LocalDate.now().plusDays(14),
                                ImportanceLevel.MEDIUM);
        Topic topic =
                new TopicRepository()
                        .create(
                                module.id(),
                                "Time Complexity",
                                "Analyse algorithm growth.",
                                ImportanceLevel.MEDIUM,
                                ConfidenceLevel.MEDIUM);
        new WorksheetCreationService()
                .createWorksheetWithQuestions(
                        topic.id(),
                        "Asymptotic analysis",
                        "",
                        DifficultyLevel.MEDIUM,
                        ImportanceLevel.MEDIUM,
                        List.of(
                                new QuestionRepository.QuestionDraft(
                                        "Compare O(n) and O(log n).", "Growth rate", 3, null)),
                        "Computer Science",
                        "Asymptotic analysis");

        try {
            fx(
                    () -> {
                        FXMLLoader modulesLoader =
                                new FXMLLoader(
                                        getClass()
                                                .getResource(
                                                        "/com/commonplace/fxml/ModulesView.fxml"));
                        Parent modulesRoot = modulesLoader.load();
                        Scene modulesScene = new Scene(modulesRoot, 1180, 760);
                        modulesScene
                                .getStylesheets()
                                .add(
                                        getClass()
                                                .getResource("/com/commonplace/css/app.css")
                                                .toExternalForm());
                        modulesRoot.applyCss();
                        modulesRoot.layout();

                        assertInstanceOf(
                                javafx.scene.layout.FlowPane.class,
                                modulesLoader.getNamespace().get("selectedModuleMeta"));
                        assertInstanceOf(
                                javafx.scene.layout.FlowPane.class,
                                modulesLoader.getNamespace().get("recommendationReasonBox"));
                        VBox modulesList =
                                (VBox) modulesLoader.getNamespace().get("modulesList");
                        assertFalse(modulesList.lookupAll(".record-ledger").isEmpty());
                        assertTrue(modulesList.lookupAll(".priority-chip").isEmpty());
                        var moduleCard = modulesList.lookup(".clickable-card");
                        assertNotNull(moduleCard);
                        moduleCard.getOnMouseClicked().handle(null);
                        assertFalse(modulesRoot.lookupAll(".mastery-stat-cell").isEmpty());
                        assertFalse(modulesRoot.lookupAll(".mastery-stat-fill").isEmpty());

                        FXMLLoader dashboardLoader =
                                new FXMLLoader(
                                        getClass()
                                                .getResource(
                                                        "/com/commonplace/fxml/DashboardView.fxml"));
                        Parent dashboardRoot = dashboardLoader.load();
                        Scene dashboardScene = new Scene(dashboardRoot, 1180, 760);
                        dashboardScene
                                .getStylesheets()
                                .add(
                                        getClass()
                                                .getResource("/com/commonplace/css/app.css")
                                                .toExternalForm());
                        dashboardRoot.applyCss();
                        dashboardRoot.layout();

                        assertInstanceOf(
                                javafx.scene.layout.FlowPane.class,
                                dashboardLoader.getNamespace().get("recommendationReasonBox"));
                        assertFalse(dashboardRoot.lookupAll(".difficulty-indicator").isEmpty());
                        assertFalse(dashboardRoot.lookupAll(".priority-chip").isEmpty());
                        return null;
                    });
        } finally {
            new ModuleRepository().deleteById(module.id());
            try (var connection = DatabaseManager.connect();
                    var statement = connection.prepareStatement("DELETE FROM users WHERE id=?")) {
                statement.setLong(1, user.id());
                statement.executeUpdate();
            }
            AccountSession.signOut();
        }
    }

    @Test
    void generatesEditableQuestionsAndSavesTypedMarksAndPressTags() throws Exception {
        AtomicReference<PressGenerateRequest> sent = new AtomicReference<>();
        Harness view =
                fx(
                        () ->
                                load(
                                        request -> {
                                            sent.set(request);
                                            return response();
                                        }));
        fx(
                () -> {
                    view.<TextField>node("titleField").setText("My worksheet");
                    view.<TextArea>node("descriptionArea").setText("My description");
                    view.<TextField>node("pressSubjectField").setText(" Mathematics ");
                    view.<TextField>node("pressTopicField").setText(" Integration by parts ");
                    view.<Spinner<Integer>>node("pressQuestionCountSpinner")
                            .getEditor()
                            .setText("2");
                    view.<ComboBox<PressQuestionFormat>>node("pressFormatCombo")
                            .setValue(PressQuestionFormat.LONG_ANSWER);
                    view.<ComboBox<DifficultyLevel>>node("pressDifficultyCombo")
                            .setValue(DifficultyLevel.HARD);
                    view.<Button>node("generatePressButton").fire();
                    return null;
                });
        awaitIdle(view);
        fx(
                () -> {
                    assertEquals(2, sent.get().questionCount());
                    assertEquals("Mathematics", sent.get().subject());
                    assertEquals("Integration by parts", sent.get().topic());
                    assertEquals(DifficultyLevel.HARD, sent.get().difficulty());
                    assertEquals(PressQuestionFormat.LONG_ANSWER, sent.get().format());
                    assertEquals(1, view.rows().getChildren().size());
                    assertEquals("My worksheet", view.<TextField>node("titleField").getText());
                    assertEquals(
                            "My description", view.<TextArea>node("descriptionArea").getText());
                    assertTrue(view.<Label>node("pressStatusLabel").getText().contains("1 of 2"));
                    VBox row = (VBox) view.rows().getChildren().getFirst();
                    assertEquals("Explain recursion.", text(row, "prompt").getText());
                    assertTrue(text(row, "markScheme").getText().contains("- Base case"));
                    text(row, "prompt").setText("Edited recursion question");
                    marks(row).getEditor().setText("9");
                    view.<Button>node("saveWorksheetButton").fire();
                    assertEquals(1, view.saved.get().size());
                    assertEquals("Edited recursion question", view.saved.get().getFirst().prompt());
                    assertEquals(9, view.saved.get().getFirst().maxMarks());
                    assertEquals("press,long-answer", view.saved.get().getFirst().tags());
                    assertEquals(DifficultyLevel.HARD, view.saved.get().getFirst().difficulty());
                    assertEquals(2L, view.savedTopicId.get());
                    return null;
                });
    }

    @Test
    void pressFieldsStartBlankAndAreNotNeededForManualCreation() throws Exception {
        Harness view =
                fx(
                        () ->
                                load(
                                        request -> {
                                            fail("Manual creation must not call Press");
                                            return response();
                                        },
                                        false));
        fx(
                () -> {
                    assertEquals("", view.<TextField>node("pressSubjectField").getText());
                    assertEquals("", view.<TextField>node("pressTopicField").getText());
                    view.<TextField>node("titleField").setText("Manual worksheet");
                    VBox row = (VBox) view.rows().getChildren().getFirst();
                    text(row, "prompt").setText("Manual question");
                    text(row, "markScheme").setText("Manual marking guidance");
                    view.<Button>node("saveWorksheetButton").fire();
                    assertEquals("Manual question", view.saved.get().getFirst().prompt());
                    assertEquals(2L, view.savedTopicId.get());
                    return null;
                });
    }

    @Test
    void generatedTitleUsesSpecificPressTopicButSavingKeepsLocalParent() throws Exception {
        AtomicReference<PressGenerateRequest> sent = new AtomicReference<>();
        Harness view =
                fx(
                        () ->
                                load(
                                        request -> {
                                            sent.set(request);
                                            return response();
                                        },
                                        false));
        fx(
                () -> {
                    view.<TextField>node("pressSubjectField").setText("Computer Science");
                    view.<TextField>node("pressTopicField")
                            .setText("Base cases in recursive tree traversal");
                    view.<Button>node("generatePressButton").fire();
                    return null;
                });
        awaitIdle(view);
        fx(
                () -> {
                    assertEquals("Base cases in recursive tree traversal", sent.get().topic());
                    assertEquals(
                            "Base cases in recursive tree traversal Practice",
                            view.<TextField>node("titleField").getText());
                    assertTrue(
                            view.<Label>node("parentTopicLabel")
                                    .getText()
                                    .endsWith("Topic  Recursion"));
                    assertFalse(sent.get().toJson().contains("Private module"));
                    view.<Button>node("saveWorksheetButton").fire();
                    assertEquals(2L, view.savedTopicId.get());
                    return null;
                });
    }

    @Test
    void validatesExplicitPressSubjectAndTopicBeforeSending() throws Exception {
        Harness view =
                fx(
                        () ->
                                load(
                                        request -> {
                                            fail("Invalid fields must not be sent");
                                            return response();
                                        },
                                        false));
        fx(
                () -> {
                    for (String[] input :
                            List.of(
                                    new String[] {"", "Recursion", "Subject"},
                                    new String[] {"   ", "Recursion", "Subject"},
                                    new String[] {"Computer Science", "", "Topic"},
                                    new String[] {"Computer Science", "   ", "Topic"},
                                    new String[] {"S".repeat(81), "Recursion", "80 characters"},
                                    new String[] {
                                        "Computer Science", "T".repeat(121), "120 characters"
                                    })) {
                        view.<TextField>node("pressSubjectField").setText(input[0]);
                        view.<TextField>node("pressTopicField").setText(input[1]);
                        view.<Button>node("generatePressButton").fire();
                        assertTrue(
                                view.<Label>node("pressStatusLabel").getText().contains(input[2]));
                        assertFalse(view.<Button>node("generatePressButton").isDisabled());
                        assertFalse(view.<Button>node("saveWorksheetButton").isDisabled());
                    }
                    return null;
                });
    }

    @Test
    void failedGenerationKeepsManualDraftAndAllowsRetry() throws Exception {
        java.util.concurrent.atomic.AtomicInteger calls =
                new java.util.concurrent.atomic.AtomicInteger();
        Harness view =
                fx(
                        () ->
                                load(
                                        request -> {
                                            if (calls.getAndIncrement() == 0)
                                                throw new PressApiException(
                                                        "Press returned HTTP 429. Wait a little.");
                                            return response();
                                        }));
        fx(
                () -> {
                    text((VBox) view.rows().getChildren().getFirst(), "prompt")
                            .setText("Manual question");
                    view.<Button>node("generatePressButton").fire();
                    return null;
                });
        awaitIdle(view);
        fx(
                () -> {
                    assertTrue(view.<Label>node("pressStatusLabel").getText().contains("429"));
                    assertEquals(
                            "Manual question",
                            text((VBox) view.rows().getChildren().getFirst(), "prompt").getText());
                    assertFalse(view.<Button>node("saveWorksheetButton").isDisabled());
                    view.<Button>node("generatePressButton").fire();
                    return null;
                });
        awaitIdle(view);
        fx(
                () -> {
                    assertEquals(2, view.rows().getChildren().size());
                    assertEquals(
                            "Manual question",
                            text((VBox) view.rows().getChildren().getFirst(), "prompt").getText());
                    return null;
                });
    }

    @Test
    void rejectsInvalidCountWithoutSendingRequest() throws Exception {
        Harness view =
                fx(
                        () ->
                                load(
                                        request -> {
                                            fail("Invalid input must not be sent");
                                            return response();
                                        }));
        fx(
                () -> {
                    for (String input : List.of("0", "11", "abc", "1.5", "")) {
                        view.<Spinner<Integer>>node("pressQuestionCountSpinner")
                                .getEditor()
                                .setText(input);
                        view.<Button>node("generatePressButton").fire();
                        assertTrue(
                                view.<Label>node("pressStatusLabel")
                                        .getText()
                                        .contains("whole number between 1 and 10"));
                        assertFalse(view.<Button>node("generatePressButton").isDisabled());
                    }
                    return null;
                });
    }

    @Test
    void cancellationKeepsDraftAndInterruptsWorker() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        Harness view =
                fx(
                        () ->
                                load(
                                        request -> {
                                            entered.countDown();
                                            try {
                                                new CountDownLatch(1).await(5, TimeUnit.SECONDS);
                                            } catch (InterruptedException e) {
                                                interrupted.countDown();
                                                Thread.currentThread().interrupt();
                                            }
                                            return response();
                                        }));
        fx(
                () -> {
                    view.<Button>node("generatePressButton").fire();
                    return null;
                });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        fx(
                () -> {
                    assertTrue(view.<Button>node("saveWorksheetButton").isDisabled());
                    assertTrue(view.<TextField>node("pressSubjectField").isDisabled());
                    assertTrue(view.<TextField>node("pressTopicField").isDisabled());
                    assertTrue(view.<ProgressIndicator>node("pressProgressIndicator").isVisible());
                    view.<Button>node("cancelPressButton").fire();
                    assertFalse(view.<Button>node("saveWorksheetButton").isDisabled());
                    assertFalse(view.<TextField>node("pressSubjectField").isDisabled());
                    assertFalse(view.<TextField>node("pressTopicField").isDisabled());
                    assertFalse(view.<ProgressIndicator>node("pressProgressIndicator").isVisible());
                    return null;
                });
        assertTrue(interrupted.await(5, TimeUnit.SECONDS));
        fx(
                () -> {
                    assertEquals(
                            "",
                            text((VBox) view.rows().getChildren().getFirst(), "prompt").getText());
                    assertTrue(
                            view.<Label>node("pressStatusLabel").getText().contains("cancelled"));
                    return null;
                });
    }

    @Test
    void malformedConfigurationDoesNotBreakOfflineEditor() throws Exception {
        String previous = System.getProperty(PressApiConfig.BASE_URL_PROPERTY);
        try {
            System.setProperty(PressApiConfig.BASE_URL_PROPERTY, "invalid URL");
            fx(
                    () -> {
                        FXMLLoader loader =
                                new FXMLLoader(
                                        getClass()
                                                .getResource(
                                                        "/com/commonplace/fxml/WorksheetCreateView.fxml"));
                        assertNotNull(loader.load());
                        return null;
                    });
            assertThrows(
                    PressApiException.class,
                    () ->
                            new PressWorksheetGenerationService()
                                    .generate(
                                            new PressGenerateRequest(
                                                    "Maths", "Algebra", null, 1, null)));
        } finally {
            if (previous == null) System.clearProperty(PressApiConfig.BASE_URL_PROPERTY);
            else System.setProperty(PressApiConfig.BASE_URL_PROPERTY, previous);
        }
    }

    @Test
    void loadsAndLaysOutRenamedFxml() throws Exception {
        Harness view = fx(() -> load(request -> response()));
        CompletableFuture<Void> settled = new CompletableFuture<>();
        fx(
                () -> {
                    view.loader
                            .<WorksheetCreateController>getController()
                            .setTopic(
                                    new Topic(
                                            2,
                                            1,
                                            "Binary Search Trees",
                                            "",
                                            ImportanceLevel.MEDIUM,
                                            ConfidenceLevel.LOW,
                                            0,
                                            null,
                                            null),
                                    new StudyModule(
                                            1,
                                            "Algorithms",
                                            "",
                                            null,
                                            ImportanceLevel.MEDIUM,
                                            null,
                                            null),
                                    null);
                    view.<TextField>node("pressTopicField")
                            .setText("BST deletion and AVL rotations");
                    Scene scene = new Scene(view.root, 1120, 760);
                    scene.getStylesheets()
                            .add(
                                    getClass()
                                            .getResource("/com/commonplace/css/app.css")
                                            .toExternalForm());
                    view.root.applyCss();
                    view.root.layout();
                    Button save = view.node("saveWorksheetButton");
                    assertTrue(
                            save.localToScene(save.getBoundsInLocal()).getMaxY()
                                    <= scene.getHeight());
                    assertTrue(
                            view.<ScrollPane>node("worksheetDetailsScroll")
                                            .getViewportBounds()
                                            .getHeight()
                                    > 0);
                    assertTrue(
                            view.root.lookupAll(".section-title").stream()
                                    .anyMatch(
                                            node ->
                                                    node instanceof Label label
                                                            && label.getText()
                                                                    .equals(
                                                                            "Generate with"
                                                                                    + " Press")));
                    ImageView headingIcon = view.node("pressHeadingIcon");
                    assertNotNull(headingIcon.getImage());
                    assertEquals(20, headingIcon.getFitWidth());
                    assertEquals(20, headingIcon.getFitHeight());
                    assertTrue(headingIcon.isPreserveRatio());
                    assertEquals(
                            List.of(
                                    PressQuestionFormat.SHORT_ANSWER,
                                    PressQuestionFormat.LONG_ANSWER),
                            view.<ComboBox<PressQuestionFormat>>node("pressFormatCombo")
                                    .getItems());
                    view.<ScrollPane>node("worksheetDetailsScroll").setVvalue(1);
                    view.root.layout();
                    var generateBounds =
                            view.<Button>node("generatePressButton")
                                    .localToScene(
                                            view.<Button>node("generatePressButton")
                                                    .getBoundsInLocal());
                    assertTrue(
                            generateBounds.getMinY() >= 0
                                    && generateBounds.getMaxY()
                                            < save.localToScene(save.getBoundsInLocal()).getMinY());
                    var pause =
                            new javafx.animation.PauseTransition(javafx.util.Duration.millis(400));
                    pause.setOnFinished(event -> settled.complete(null));
                    pause.play();
                    return null;
                });
        settled.get(3, TimeUnit.SECONDS);
        fx(
                () -> {
                    writeSnapshot(view.root, "commonplace.press.screenshot");
                    return null;
                });
    }

    private static void writeSnapshot(Parent root, String property) throws Exception {
        String screenshot = System.getProperty(property);
        if (screenshot == null) return;
        var image = root.snapshot(null, null);
        var buffered =
                new java.awt.image.BufferedImage(
                        (int) image.getWidth(),
                        (int) image.getHeight(),
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < buffered.getHeight(); y++) {
            for (int x = 0; x < buffered.getWidth(); x++) {
                buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            }
        }
        javax.imageio.ImageIO.write(buffered, "png", new java.io.File(screenshot));
    }

    private Harness load(Generation generation) throws Exception {
        return load(generation, true);
    }

    private Harness load(Generation generation, boolean enterPressFields) throws Exception {
        AtomicReference<List<QuestionRepository.QuestionDraft>> saved = new AtomicReference<>();
        AtomicReference<Long> savedTopicId = new AtomicReference<>();
        WorksheetCreationService saving =
                new WorksheetCreationService() {
                    @Override
                    public Worksheet createWorksheetWithQuestions(
                            long topicId,
                            String title,
                            String description,
                            DifficultyLevel difficulty,
                            ImportanceLevel importance,
                            List<QuestionRepository.QuestionDraft> drafts,
                            String subject,
                            String scope) {
                        saved.set(List.copyOf(drafts));
                        savedTopicId.set(topicId);
                        return null;
                    }
                };
        PressWorksheetGenerationService service =
                new PressWorksheetGenerationService() {
                    @Override
                    public PressGenerateResponse generate(PressGenerateRequest request)
                            throws PressApiException {
                        return generation.generate(request);
                    }
                };
        var controller = new WorksheetCreateController(saving, service);
        FXMLLoader loader =
                new FXMLLoader(
                        getClass().getResource("/com/commonplace/fxml/WorksheetCreateView.fxml"));
        loader.setControllerFactory(type -> controller);
        Parent root = loader.load();
        controller.setTopic(
                new Topic(
                        2,
                        1,
                        "Recursion",
                        "",
                        ImportanceLevel.MEDIUM,
                        ConfidenceLevel.LOW,
                        0,
                        null,
                        null),
                new StudyModule(1, "Private module", "", null, ImportanceLevel.MEDIUM, null, null),
                null);
        Harness view = new Harness(loader, root, saved, savedTopicId);
        if (enterPressFields) {
            view.<TextField>node("pressSubjectField").setText("Computer Science");
            view.<TextField>node("pressTopicField").setText("Recursion");
        }
        return view;
    }

    private static PressGenerateResponse response() {
        return PressGenerateResponse.fromJson(RESPONSE);
    }

    private static TextArea text(VBox row, String name) {
        return (TextArea)
                row.getChildren().stream()
                        .filter(node -> name.equals(node.getUserData()))
                        .findFirst()
                        .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Spinner<Integer> marks(VBox row) {
        return (Spinner<Integer>)
                row.getChildren().stream()
                        .filter(node -> "maxMarks".equals(node.getUserData()))
                        .findFirst()
                        .orElseThrow();
    }

    private static void awaitIdle(Harness view) throws Exception {
        CompletableFuture<Void> idle = new CompletableFuture<>();
        fx(
                () -> {
                    Button button = view.node("generatePressButton");
                    if (!button.isDisabled()) idle.complete(null);
                    else
                        button.disabledProperty()
                                .addListener(
                                        (observable, old, disabled) -> {
                                            if (!disabled) idle.complete(null);
                                        });
                    return null;
                });
        idle.get(5, TimeUnit.SECONDS);
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    @FunctionalInterface
    private interface Generation {
        PressGenerateResponse generate(PressGenerateRequest request) throws PressApiException;
    }

    private record Harness(
            FXMLLoader loader,
            Parent root,
            AtomicReference<List<QuestionRepository.QuestionDraft>> saved,
            AtomicReference<Long> savedTopicId) {
        @SuppressWarnings("unchecked")
        <T> T node(String id) {
            return (T) loader.getNamespace().get(id);
        }

        VBox rows() {
            return node("questionsContainer");
        }
    }
}
