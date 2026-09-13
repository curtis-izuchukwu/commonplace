package com.commonplace.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.commonplace.importer.ImportIssue;
import com.commonplace.importer.ImportIssueSeverity;
import com.commonplace.importer.ImportedQuestionDraft;
import com.commonplace.importer.ImportedWorksheetDraft;
import com.commonplace.model.ConfidenceLevel;
import com.commonplace.model.DifficultyLevel;
import com.commonplace.model.ImportanceLevel;
import com.commonplace.model.StudyModule;
import com.commonplace.model.Topic;
import com.commonplace.model.User;
import com.commonplace.model.UserSettings;
import com.commonplace.model.Worksheet;
import com.commonplace.model.WorksheetAttempt;
import com.commonplace.repository.AnswerRepository;
import com.commonplace.repository.AttemptRepository;
import com.commonplace.repository.DatabaseManager;
import com.commonplace.repository.MistakeRepository;
import com.commonplace.repository.ModuleRepository;
import com.commonplace.repository.QuestionRepository;
import com.commonplace.repository.TopicRepository;
import com.commonplace.repository.UserRepository;
import com.commonplace.repository.WorksheetRepository;
import com.commonplace.service.AccountSession;
import com.commonplace.service.GamificationService;
import com.commonplace.service.LearningService;
import com.commonplace.service.ReflectionService;
import com.commonplace.service.UserSettingsService;
import com.commonplace.service.WorksheetCreationService;
import com.commonplace.service.WorksheetRecommendation;
import com.commonplace.ui.controller.AttemptWorksheetController;
import com.commonplace.ui.controller.ImportWorksheetController;
import com.commonplace.ui.controller.ReflectionController;
import com.commonplace.ui.controller.TopicDetailController;
import com.commonplace.ui.controller.WorksheetCreateController;
import com.commonplace.ui.controller.WorksheetDetailController;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

/** Produces the checked-in documentation gallery from the current application views. */
@EnabledIfSystemProperty(named = "commonplace.docs.screenshots", matches = "true")
class DocumentationScreenshotTest {

    private static final Path SCREENSHOTS = Path.of("docs", "screenshots");
    private static final String STYLESHEET = "/com/commonplace/css/app.css";

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
    void regenerateDocumentationGallery() throws Exception {
        Files.createDirectories(SCREENSHOTS);
        clearDocumentationAccount();

        Fixture fixture = seedFixture();
        try {
            captureLogin();
            captureDashboardAndModules();
            captureTopic(fixture);
            captureWorksheetCreation(fixture);
            captureWorksheetDetail(fixture);
            captureAttempt(fixture);
            captureReflection(fixture);
            captureMistakeBank();
            captureImportReview(fixture);
            captureSettings();
        } finally {
            AccountSession.signOut();
            clearDocumentationAccount();
        }

        for (String name :
                List.of(
                        "login.png",
                        "dashboard.png",
                        "modules.png",
                        "topic-detail.png",
                        "worksheet-creation.png",
                        "worksheet-detail.png",
                        "attempt-flow.png",
                        "reflection.png",
                        "mistake-bank.png",
                        "pdf-import-review.png",
                        "settings-overview.png")) {
            assertTrue(Files.size(SCREENSHOTS.resolve(name)) > 1_000, name + " was not rendered");
        }
    }

    private Fixture seedFixture() throws Exception {
        User user = new UserRepository().create("Alex", "documentation-hash", "documentation-salt");
        AccountSession.signIn(user);

        StudyModule algorithms =
                new ModuleRepository()
                        .create(
                                "Algorithms",
                                "Core data structures and algorithm analysis.",
                                dateLaterThisMonth(5),
                                ImportanceLevel.HIGH);
        StudyModule discrete =
                new ModuleRepository()
                        .create(
                                "Discrete Mathematics",
                                "Proof, graph, and counting techniques.",
                                dateLaterThisMonth(10),
                                ImportanceLevel.MEDIUM);

        Topic trees =
                new TopicRepository()
                        .create(
                                algorithms.id(),
                                "Binary Search Trees",
                                "Traversal, deletion, balancing, and AVL rotations.",
                                ImportanceLevel.HIGH,
                                ConfidenceLevel.MEDIUM);
        Topic complexity =
                new TopicRepository()
                        .create(
                                algorithms.id(),
                                "Time Complexity",
                                "Asymptotic analysis and growth rates.",
                                ImportanceLevel.MEDIUM,
                                ConfidenceLevel.MEDIUM);
        Topic graphs =
                new TopicRepository()
                        .create(
                                discrete.id(),
                                "Graph Theory",
                                "Paths, connectivity, trees, and traversal.",
                                ImportanceLevel.MEDIUM,
                                ConfidenceLevel.LOW);

        WorksheetCreationService creation = new WorksheetCreationService();
        Worksheet avl =
                creation.createWorksheetWithQuestions(
                        trees.id(),
                        "AVL Rotation Practice",
                        "Short-answer practice on identifying and applying AVL rotations.",
                        DifficultyLevel.MEDIUM,
                        ImportanceLevel.HIGH,
                        List.of(
                                new QuestionRepository.QuestionDraft(
                                        "Explain when a left rotation is required in an AVL tree.",
                                        "The right subtree is too tall.\nThe balance factor is below -1.\nThe rotation restores balance.",
                                        3,
                                        "press,short-answer"),
                                new QuestionRepository.QuestionDraft(
                                        "Compare an AVL tree with an ordinary binary search tree.",
                                        "AVL trees maintain a bounded height.\nSearch remains O(log n).\nRotations add update overhead.",
                                        3,
                                        "press,short-answer"),
                                new QuestionRepository.QuestionDraft(
                                        "Describe the double rotation used for a left-right imbalance.",
                                        "Rotate the left child left.\nRotate the unbalanced node right.\nPreserve binary-search ordering.\nUpdate heights.",
                                        4,
                                        "press,short-answer")),
                        "Computer Science",
                        "AVL rotations and balanced binary search trees");

        creation.createWorksheetWithQuestions(
                complexity.id(),
                "Asymptotic Analysis",
                "Compare common complexity classes and justify bounds.",
                DifficultyLevel.MEDIUM,
                ImportanceLevel.MEDIUM,
                List.of(
                        new QuestionRepository.QuestionDraft(
                                "Compare O(n) and O(log n).",
                                "Describe their growth rates and give one example of each.",
                                4,
                                null),
                        new QuestionRepository.QuestionDraft(
                                "Why are constants omitted in Big O notation?",
                                "Growth dominates constants for sufficiently large inputs.",
                                2,
                                null)));

        creation.createWorksheetWithQuestions(
                graphs.id(),
                "Graph Traversal Review",
                "Breadth-first and depth-first traversal practice.",
                DifficultyLevel.EASY,
                ImportanceLevel.MEDIUM,
                List.of(
                        new QuestionRepository.QuestionDraft(
                                "When does breadth-first search find a shortest path?",
                                "In an unweighted graph, or where every edge has equal weight.",
                                3,
                                null)));

        List<com.commonplace.model.Question> questions =
                new QuestionRepository().findByWorksheetId(avl.id());
        LocalDateTime completed = LocalDateTime.now().minusDays(1).withHour(19).withMinute(20);
        WorksheetAttempt attempt =
                new AttemptRepository()
                        .create(
                                avl.id(),
                                completed.minusMinutes(22),
                                completed,
                                8,
                                10,
                                80,
                                ConfidenceLevel.MEDIUM,
                                "I mixed up the order of the double rotation.",
                                "Redo the left-right and right-left cases from memory.",
                                "The single rotations are secure; double rotations need another pass.");
        new AnswerRepository()
                .createMany(
                        attempt.id(),
                        List.of(
                                new AnswerRepository.AnswerDraft(
                                        questions.get(0).id(),
                                        "When the right subtree makes the node unbalanced.",
                                        3,
                                        3,
                                        false,
                                        null,
                                        false,
                                        95,
                                        true),
                                new AnswerRepository.AnswerDraft(
                                        questions.get(1).id(),
                                        "AVL trees rebalance themselves and keep searches logarithmic.",
                                        3,
                                        3,
                                        false,
                                        null,
                                        false,
                                        110,
                                        true),
                                new AnswerRepository.AnswerDraft(
                                        questions.get(2).id(),
                                        "Rotate the unbalanced node right, then rotate its child left.",
                                        2,
                                        4,
                                        true,
                                        "I reversed the two stages.",
                                        false,
                                        140,
                                        true)));
        new MistakeRepository().createFromAttempt(attempt.id());
        new ReflectionService().updateWorksheetStats(avl, attempt);
        new LearningService().refresh(trees.id());
        new GamificationService().awardWorksheetCompletion(attempt);

        try (Connection connection = DatabaseManager.connect();
                var statement =
                        connection.prepareStatement(
                                "UPDATE user_settings SET accent_color='BURGUNDY', reduce_motion=1,"
                                        + " daily_worksheet_goal=2 WHERE user_id=?")) {
            statement.setLong(1, user.id());
            statement.executeUpdate();
        }

        Topic refreshedTopic = new TopicRepository().findById(trees.id()).orElseThrow();
        Worksheet refreshedWorksheet =
                new WorksheetRepository().findById(avl.id()).orElseThrow();
        return new Fixture(algorithms, refreshedTopic, refreshedWorksheet, attempt);
    }

    private void captureLogin() throws Exception {
        captureInChrome(
                "/com/commonplace/fxml/LoginView.fxml", "Sign In", "login.png", 980, 680, null);
    }

    private void captureDashboardAndModules() throws Exception {
        ChromeView view =
                fx(
                        () -> {
                            FXMLLoader loader = loader("/com/commonplace/fxml/DashboardView.fxml");
                            Parent content = loader.load();
                            Stage stage = new Stage(StageStyle.TRANSPARENT);
                            AppChrome chrome = AppChrome.create(stage, content);
                            Scene scene = scene(chrome, 1440, 920);
                            stage.setScene(scene);
                            AppChrome.setBreadcrumb(scene, "Dashboard");
                            applyCurrentPreferences(chrome);
                            return new ChromeView(loader, chrome, stage);
                        });

        settle();
        snapshot(view.root(), "dashboard.png", 1440, 920);
        fx(
                () -> {
                    view.stage().close();
                    return null;
                });

        captureView(
                "/com/commonplace/fxml/ModulesView.fxml",
                "modules.png",
                1440,
                920,
                loader -> {
                    VBox modules = (VBox) loader.getNamespace().get("modulesList");
                    var moduleCard = modules.getChildren().getFirst();
                    if (moduleCard.getOnMouseClicked() != null) {
                        moduleCard.getOnMouseClicked().handle(null);
                    }
                });
    }

    private void captureTopic(Fixture fixture) throws Exception {
        captureView(
                "/com/commonplace/fxml/TopicDetailView.fxml",
                "topic-detail.png",
                1080,
                780,
                loader ->
                        loader.<TopicDetailController>getController()
                                .setTopic(fixture.topic(), fixture.module()));
    }

    private void captureWorksheetCreation(Fixture fixture) throws Exception {
        captureView(
                "/com/commonplace/fxml/WorksheetCreateView.fxml",
                "worksheet-creation.png",
                1120,
                900,
                loader -> {
                    loader.<WorksheetCreateController>getController()
                            .setTopic(fixture.topic(), fixture.module(), null);
                    ((TextField) loader.getNamespace().get("titleField"))
                            .setText("Balanced Trees Extension");
                    ((TextField) loader.getNamespace().get("pressSubjectField"))
                            .setText("Computer Science");
                    ((TextField) loader.getNamespace().get("pressTopicField"))
                            .setText("AVL deletion and double rotations");
                });
    }

    private void captureWorksheetDetail(Fixture fixture) throws Exception {
        captureView(
                "/com/commonplace/fxml/WorksheetDetailView.fxml",
                "worksheet-detail.png",
                1040,
                780,
                loader -> {
                    WorksheetDetailController controller = loader.getController();
                    controller.setWorksheet(fixture.worksheet(), fixture.topic());
                    controller.setRecommendationDetails(
                            new WorksheetRecommendation(
                                    fixture.worksheet(),
                                    fixture.topic(),
                                    84,
                                    "Review is due  •  Worth reinforcing  •  Exam approaching"));
                });
    }

    private void captureAttempt(Fixture fixture) throws Exception {
        captureView(
                "/com/commonplace/fxml/AttemptWorksheetView.fxml",
                "attempt-flow.png",
                980,
                800,
                loader ->
                        loader.<AttemptWorksheetController>getController()
                                .setWorksheet(fixture.worksheet(), fixture.topic(), null));
    }

    private void captureReflection(Fixture fixture) throws Exception {
        captureView(
                "/com/commonplace/fxml/ReflectionView.fxml",
                "reflection.png",
                760,
                720,
                loader -> {
                    loader.<ReflectionController>getController()
                            .setContext(fixture.worksheet(), fixture.attempt(), 32, null);
                    @SuppressWarnings("unchecked")
                    ComboBox<ConfidenceLevel> confidence =
                            (ComboBox<ConfidenceLevel>)
                                    loader.getNamespace().get("confidenceCombo");
                    confidence.getSelectionModel().select(ConfidenceLevel.MEDIUM);
                    ((TextArea) loader.getNamespace().get("mainWeaknessArea"))
                            .setText("I reversed the order of the two rotations.");
                    ((TextArea) loader.getNamespace().get("nextActionArea"))
                            .setText("Draw both double-rotation cases from memory tomorrow.");
                    ((TextArea) loader.getNamespace().get("reflectionNotesArea"))
                            .setText("Single rotations felt secure. Revisit deletion before the exam.");
                });
    }

    private void captureMistakeBank() throws Exception {
        captureView(
                "/com/commonplace/fxml/MistakeBankView.fxml",
                "mistake-bank.png",
                980,
                800,
                null);
    }

    private void captureImportReview(Fixture fixture) throws Exception {
        captureView(
                "/com/commonplace/fxml/ImportWorksheetView.fxml",
                "pdf-import-review.png",
                1040,
                820,
                loader -> {
                    ImportWorksheetController controller = loader.getController();
                    controller.setInitialSelection(fixture.module(), fixture.topic(), null);
                    ImportedWorksheetDraft draft =
                            new ImportedWorksheetDraft(
                                    "Binary Search Trees Practice Paper",
                                    fixture.module().id(),
                                    fixture.topic().id(),
                                    List.of(
                                            new ImportedQuestionDraft(
                                                    1,
                                                    "Explain how an AVL tree restores balance after insertion.",
                                                    "Identify the imbalance, select the rotation, preserve ordering, and update heights.",
                                                    4,
                                                    1,
                                                    List.of(),
                                                    List.of()),
                                            new ImportedQuestionDraft(
                                                    2,
                                                    "Compare the worst-case search height of an AVL tree and an unbalanced BST.",
                                                    "AVL: O(log n). Unbalanced BST: O(n).",
                                                    3,
                                                    1,
                                                    List.of(),
                                                    List.of())),
                                    List.of(),
                                    List.of(
                                            new ImportIssue(
                                                    ImportIssueSeverity.INFO,
                                                    "Text and marking points were extracted locally. Review before saving.")),
                                    Path.of("Algorithms-practice-paper.pdf"));
                    Method populate =
                            ImportWorksheetController.class.getDeclaredMethod(
                                    "populateReview", ImportedWorksheetDraft.class);
                    populate.setAccessible(true);
                    populate.invoke(controller, draft);
                    ((Button) loader.getNamespace().get("saveButton")).setDisable(false);
                });
    }

    private void captureSettings() throws Exception {
        captureView(
                "/com/commonplace/fxml/SettingsView.fxml",
                "settings-overview.png",
                980,
                820,
                null);
    }

    private void captureInChrome(
            String resource,
            String breadcrumb,
            String file,
            int width,
            int height,
            ViewSetup setup)
            throws Exception {
        ChromeView view =
                fx(
                        () -> {
                            FXMLLoader loader = loader(resource);
                            Parent content = loader.load();
                            if (setup != null) setup.apply(loader);
                            Stage stage = new Stage(StageStyle.TRANSPARENT);
                            AppChrome chrome = AppChrome.create(stage, content);
                            Scene scene = scene(chrome, width, height);
                            stage.setScene(scene);
                            AppChrome.setBreadcrumb(scene, breadcrumb);
                            applyCurrentPreferences(chrome);
                            return new ChromeView(loader, chrome, stage);
                        });
        settle();
        snapshot(view.root(), file, width, height);
        fx(
                () -> {
                    view.stage().close();
                    return null;
                });
    }

    private void captureView(
            String resource, String file, int width, int height, ViewSetup setup) throws Exception {
        Parent root =
                fx(
                        () -> {
                            FXMLLoader loader = loader(resource);
                            Parent loaded = loader.load();
                            if (setup != null) setup.apply(loader);
                            scene(loaded, width, height);
                            applyCurrentPreferences(loaded);
                            return loaded;
                        });
        settle();
        snapshot(root, file, width, height);
    }

    private Scene scene(Parent root, int width, int height) {
        Scene scene = new Scene(root, width, height, Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource(STYLESHEET).toExternalForm());
        return scene;
    }

    private void applyCurrentPreferences(Parent root) throws Exception {
        UserSettings settings = new UserSettingsService().load();
        AppPreferences.apply(root, settings);
        root.applyCss();
        root.layout();
    }

    private void snapshot(Parent root, String file, int width, int height) throws Exception {
        fx(
                () -> {
                    root.applyCss();
                    root.layout();
                    WritableImage image = new WritableImage(width, height);
                    var parameters = new javafx.scene.SnapshotParameters();
                    parameters.setFill(Color.TRANSPARENT);
                    root.snapshot(parameters, image);

                    BufferedImage buffered =
                            new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                        }
                    }
                    ImageIO.write(buffered, "png", SCREENSHOTS.resolve(file).toFile());
                    return null;
                });
    }

    private static void settle() throws InterruptedException {
        Thread.sleep(350);
    }

    private FXMLLoader loader(String resource) {
        return new FXMLLoader(getClass().getResource(resource));
    }

    private static LocalDate dateLaterThisMonth(int days) {
        LocalDate today = LocalDate.now();
        return today.withDayOfMonth(
                Math.min(today.lengthOfMonth(), today.getDayOfMonth() + Math.max(0, days)));
    }

    private static void clearDocumentationAccount() throws Exception {
        AccountSession.signOut();
        try (Connection connection = DatabaseManager.connect();
                var statement =
                        connection.prepareStatement(
                                "DELETE FROM users WHERE username = ? COLLATE NOCASE")) {
            statement.setString(1, "Alex");
            statement.executeUpdate();
        }
    }

    private static <T> T fx(Callable<T> work) throws Exception {
        if (Platform.isFxApplicationThread()) return work.call();
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }

    private record Fixture(
            StudyModule module,
            Topic topic,
            Worksheet worksheet,
            WorksheetAttempt attempt) {}

    private record ChromeView(FXMLLoader loader, AppChrome root, Stage stage) {}

    @FunctionalInterface
    private interface ViewSetup {
        void apply(FXMLLoader loader) throws Exception;
    }
}
