package com.commonplace.ui;

import com.commonplace.model.*;
import com.commonplace.repository.*;
import com.commonplace.service.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Real JavaFX windows and controls, opt-in like the existing UI suite. No production DB. */
@EnabledIfSystemProperty(named = "commonplace.uiTest", matches = "true")
class NotesWindowTest {
    private User previous, owner, other;
    private StudyModule module;
    private Topic topic;
    private Stage main;
    private Label source;

    @BeforeAll static void toolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Runnable start = () -> { Platform.setImplicitExit(false); ready.countDown(); };
        try { Platform.startup(start); } catch (IllegalStateException alreadyRunning) { Platform.runLater(start); }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    @BeforeEach void setup() throws Exception {
        previous = AccountSession.currentUser().orElse(null);
        UserRepository users = new UserRepository();
        owner = users.create("notes-ui-" + UUID.randomUUID(), "hash", "salt");
        other = users.create("notes-ui-other-" + UUID.randomUUID(), "hash", "salt");
        AccountSession.signIn(owner);
        module = new ModuleRepository().create("Data Engineering", "", null, ImportanceLevel.MEDIUM);
        topic = new TopicRepository().create(module.id(), "Relational Databases", "Keep description", ImportanceLevel.MEDIUM, ConfidenceLevel.MEDIUM);
        fx(() -> {
            main = new Stage();
            source = new Label("Commonplace test window");
            main.setScene(new Scene(new VBox(source), 640, 480));
            main.show();
            return null;
        });
    }

    @AfterEach void cleanup() throws Exception {
        // Restore the deliberately deleted parent in the failure test so even a failed assertion can save safely.
        try (Connection conn = DatabaseManager.connect(); var stmt = conn.prepareStatement("""
                INSERT OR IGNORE INTO topics (id,module_id,name,created_at,updated_at)
                VALUES(?,?,?,'2026-01-01T00:00','2026-01-01T00:00')
                """)) {
            stmt.setLong(1, topic.id()); stmt.setLong(2, module.id()); stmt.setString(3, topic.name()); stmt.executeUpdate();
        }
        closeNotes();
        fx(() -> { main.hide(); return null; });
        try (Connection conn = DatabaseManager.connect(); var stmt = conn.prepareStatement("DELETE FROM users WHERE id IN (?,?)")) {
            stmt.setLong(1, owner.id()); stmt.setLong(2, other.id()); stmt.executeUpdate();
        }
        if (previous == null) AccountSession.signOut(); else AccountSession.signIn(previous);
    }

    @Test void richFormattingKeyboardClipboardHistoryAndPersistenceWorkInActualWindow() throws Exception {
        open(NoteKey.topic(module.id(), topic.id()), topic.name());
        NoteDocument expected = fx(() -> {
            NoteEditor editor = editor();
            var area = editor.area();
            area.replaceSelection("PRIMARY KEYS\nA primary key uniquely identifies a record.\nSELECT * FROM student;\nFirst item\nSecond item\n");
            area.selectRange(0, 12);
            toolbar(editor, "Bold").fire();
            assertEquals(NoteDocument.BOLD, area.getStyleOfChar(0));
            area.undo(); assertEquals(0, area.getStyleOfChar(0));
            area.redo(); assertEquals(NoteDocument.BOLD, area.getStyleOfChar(0));
            editor.formatBlock(NoteDocument.Block.HEADING_1);
            area.selectRange(13, 26);
            area.fireEvent(key(KeyCode.I));
            area.fireEvent(key(KeyCode.U));
            assertEquals(NoteDocument.ITALIC | NoteDocument.UNDERLINE, area.getStyleOfChar(13));
            int code = area.getText().indexOf("SELECT");
            area.selectRange(code, code + 22);
            toolbar(editor, "Monospaced").fire();
            int first = area.getText().indexOf("First item");
            area.selectRange(first, area.getLength() - 1);
            editor.formatBlock(NoteDocument.Block.NUMBERED);
            area.moveTo(area.getLength());
            ClipboardContent clipboard = new ClipboardContent();
            clipboard.putString("Paste: café 中文 <&>\n");
            Clipboard.getSystemClipboard().setContent(clipboard);
            area.paste();
            assertTrue(area.getText().endsWith("Paste: café 中文 <&>\n"));
            assertTrue(area.isWrapText());
            screenshot(notesStage(), "notes-ui-dark.png");
            area.fireEvent(key(KeyCode.S));
            return editor.document();
        });
        NoteService notes = new NoteService(owner.id());
        await(() -> expected.equals(load(notes, NoteKey.topic(module.id(), topic.id()))));
        closeNotes();
        open(null, topic.name());
        assertEquals(expected, fx(() -> editor().document()));
        assertEquals("Keep description", new TopicRepository().findById(topic.id()).orElseThrow().description());
    }

    @Test void navigationSearchDuplicateWindowPreventionAndPreferencesWork() throws Exception {
        NoteKey topicKey = NoteKey.topic(module.id(), topic.id());
        open(topicKey, topic.name());
        fx(() -> { editor().area().replaceSelection("A foreign key links records. 中文"); return null; });
        // Switch immediately, before the 900 ms timer, then return to the retained draft.
        open(NoteKey.overview(module.id()), "Overview");
        fx(() -> { editor().area().replaceSelection("Module overview"); return null; });
        open(topicKey, topic.name());
        assertEquals("A foreign key links records. 中文", fx(() -> editor().document().plainText()));
        assertEquals(1L, fx(() -> Window.getWindows().stream().filter(w -> w instanceof Stage s && "Commonplace / Notes".equals(s.getTitle())).count()));
        fx(() -> { ((TextField) notesStage().getScene().lookup("#searchField")).setText("foreign key"); return null; });
        await(() -> fxUnchecked(() -> ((ListView<?>) notesStage().getScene().lookup("#searchResults")).getItems().size() == 1));
        fx(() -> {
            ListView<?> results = (ListView<?>) notesStage().getScene().lookup("#searchResults");
            results.getSelectionModel().select(0);
            assertEquals("foreign key", editor().area().getSelectedText());
            return null;
        });
        try (Connection conn = DatabaseManager.connect(); var stmt = conn.prepareStatement("""
                UPDATE user_settings SET theme='LIGHT',accent_color='FOREST',font_size='LARGE',
                    higher_contrast=1,larger_controls=1,compact_layout=1,reduce_motion=1 WHERE user_id=?
                """)) { stmt.setLong(1, owner.id()); stmt.executeUpdate(); }
        UserSettings settings = new UserSettingsService().load();
        fx(() -> {
            NotesWindowManager.applyPreferences(settings);
            var root = notesStage().getScene().getRoot();
            root.applyCss(); root.layout();
            assertTrue(root.getStyleClass().containsAll(List.of("theme-light", "accent-forest", "font-large", "high-contrast", "larger-controls", "compact-layout", "reduce-motion")));
            notesStage().setWidth(700); notesStage().setHeight(500);
            root.applyCss(); root.layout();
            return null;
        });
        await(() -> fxUnchecked(() -> notesStage().getScene().getRoot().getLayoutBounds().getWidth() <= 700));
        fx(() -> {
            assertTrue(editor().getWidth() > 200);
            Button newTab = (Button) notesStage().getScene().lookup("#newTabButton");
            assertEquals("+", assertInstanceOf(javafx.scene.text.Text.class, newTab.lookup(".text")).getText());
            editor().area().fireEvent(key(KeyCode.T));
            assertEquals(2, tabs().getTabs().size());
            notesStage().getScene().getRoot().applyCss();
            notesStage().getScene().getRoot().layout();
            javafx.scene.Node tabClose = tabs().lookup(".tab:selected .tab-close-button");
            assertNotNull(tabClose);
            assertFalse(newTab.localToScene(newTab.getLayoutBounds()).intersects(tabClose.localToScene(tabClose.getLayoutBounds())),
                    "The new-tab button must not cover the selected tab's close control");
            screenshot(notesStage(), "notes-ui-light-small.png");
            return null;
        });
        closeNotes();
        assertEquals("Module overview", new NoteService(owner.id()).load(NoteKey.overview(module.id())).plainText());
    }

    @Test void signOutWaitsForNotesAndNewAccountNeverSeesOldNotebooks() throws Exception {
        NoteKey key = NoteKey.topic(module.id(), topic.id());
        open(key, topic.name());
        assertThrows(IllegalStateException.class, () -> AccountSession.signIn(other));
        fx(() -> {
            editor().area().replaceSelection("Only account A sees this.");
            assertThrows(IllegalStateException.class, () -> AccountSession.signIn(other));
            assertEquals(owner.id(), AccountSession.currentUserId());
            return null;
        });
        CountDownLatch signedOut = new CountDownLatch(1);
        fx(() -> { NotesWindowManager.withSavedNotes(true, () -> { AccountSession.signOut(); signedOut.countDown(); }); return null; });
        assertTrue(signedOut.await(10, TimeUnit.SECONDS));
        assertEquals("Only account A sees this.", new NoteService(owner.id()).load(key).plainText());
        assertFalse(fx(() -> hasNotesWindow()));
        AccountSession.signIn(other);
        fx(() -> { NotesWindowManager.open(source, null); return null; });
        await(() -> fxUnchecked(() -> ((TreeView<?>) notesStage().getScene().lookup("#notebookTree")).getRoot().getChildren().isEmpty()));
        assertTrue(fx(() -> editor().isDisabled()));
        assertEquals("Choose a section", fx(() -> ((Label) notesStage().getScene().lookup("#sectionNameLabel")).getText()));
    }

    @Test void tabsExistBeforeChoosingTopicsAndPlusDuplicatesABlankView() throws Exception {
        fx(() -> { NotesWindowManager.open(source, null); return null; });
        await(() -> fxUnchecked(() -> !((TreeView<?>) notesStage().getScene().lookup("#notebookTree")).getRoot().getChildren().isEmpty()));
        fx(() -> {
            assertEquals(1, tabs().getTabs().size());
            assertNull(tabs().getTabs().getFirst().getUserData());
            assertEquals("Notebook", tabs().getTabs().getFirst().getText());
            assertTrue(editor().isDisabled());
            assertTrue(notesStage().getScene().lookup("#newTabButton").isVisible());
            return null;
        });
        duplicateTab();
        fx(() -> {
            assertEquals(2, tabs().getTabs().size());
            assertTrue(tabs().getTabs().stream().allMatch(tab -> tab.getUserData() == null));
            screenshot(notesStage(), "notes-tabs-empty.png");
            selectSection(NoteKey.topic(module.id(), topic.id()));
            return null;
        });
        await(() -> fxUnchecked(() -> editor() != null && !editor().isDisabled()));
        fx(() -> {
            assertEquals(2, tabs().getTabs().size());
            assertNull(tabs().getTabs().getFirst().getUserData());
            assertEquals(NoteKey.topic(module.id(), topic.id()), tabs().getTabs().getLast().getUserData());
            return null;
        });
    }

    @Test void topicsReplaceTheCurrentViewAndDuplicatedTabsNavigateIndependently() throws Exception {
        NoteKey topicKey = NoteKey.topic(module.id(), topic.id());
        open(topicKey, topic.name());
        NoteEditor topicEditor = fx(() -> {
            NoteEditor note = editor();
            note.area().replaceSelection("Topic draft");
            note.area().getUndoManager().preventMerge();
            note.area().selectRange(0, 5);
            note.toggleMark(NoteDocument.BOLD);
            return note;
        });
        Tab original = fx(() -> tabs().getTabs().getFirst());
        duplicateTab();
        fx(() -> {
            assertNotSame(topicEditor, editor());
            assertEquals(topicEditor.document(), editor().document());
            assertEquals("Topic", editor().area().getSelectedText());
            assertEquals(topicKey, tabs().getTabs().getFirst().getUserData());
            assertEquals(topicKey, tabs().getTabs().getLast().getUserData());
            return null;
        });
        open(NoteKey.overview(module.id()), "Overview");
        NoteEditor overviewEditor = fx(() -> {
            editor().area().replaceSelection("Overview draft");
            return editor();
        });
        fx(() -> {
            assertEquals(2, tabs().getTabs().size());
            assertEquals(topicKey, original.getUserData());
            assertEquals(NoteKey.overview(module.id()), tabs().getTabs().getLast().getUserData());
            tabs().getSelectionModel().select(0);
            assertSame(topicEditor, editor());
            assertEquals("Topic", editor().area().getSelectedText());
            assertEquals(NoteDocument.BOLD, editor().area().getStyleOfChar(0));
            editor().area().undo();
            assertEquals("Topic draft", editor().area().getText());
            assertEquals(0, editor().area().getStyleOfChar(0));
            editor().area().redo();
            assertEquals(NoteDocument.BOLD, editor().area().getStyleOfChar(0));
            notesStage().getScene().getRoot().applyCss();
            notesStage().getScene().getRoot().layout();
            screenshot(notesStage(), "notes-tabs-dark.png");
            editor().area().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.TAB, false, true, false, false));
            assertSame(overviewEditor, editor());
            assertEquals("Overview draft", editor().area().getText());
            editor().area().undo();
            assertEquals("", editor().area().getText());
            editor().area().redo();
            assertEquals("Overview draft", editor().area().getText());
            editor().area().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.TAB, true, true, false, false));
            assertSame(topicEditor, editor());
            return null;
        });
        open(topicKey, topic.name());
        assertEquals(2, fx(() -> tabs().getTabs().size()));
        assertSame(topicEditor, fx(NotesWindowTest::editor));
        closeNotes();
        NoteService service = new NoteService(owner.id());
        assertEquals("Topic draft", service.load(topicKey).plainText());
        assertEquals(NoteDocument.BOLD, service.load(topicKey).paragraphs().getFirst().spans().getFirst().marks());
        assertEquals("Overview draft", service.load(NoteKey.overview(module.id())).plainText());
    }

    @Test void duplicateViewsShareUnsavedEditsAndCachedTopicsCannotOverwriteNewerText() throws Exception {
        NoteKey key = NoteKey.topic(module.id(), topic.id());
        open(key, topic.name());
        fx(() -> { editor().area().replaceSelection("Shared draft"); return null; });
        NoteEditor first = fx(NotesWindowTest::editor);
        duplicateTab();
        fx(() -> {
            assertEquals("Shared draft", editor().area().getText());
            editor().area().moveTo(editor().area().getLength());
            editor().area().replaceSelection(" from the duplicate");
            assertEquals(editor().document(), first.document());
            tabs().getSelectionModel().selectFirst();
            assertSame(first, editor());
            assertEquals("Shared draft from the duplicate", editor().area().getText());
            return null;
        });
        open(NoteKey.overview(module.id()), "Overview");
        fx(() -> {
            tabs().getSelectionModel().selectLast();
            assertEquals(key, tabs().getTabs().getLast().getUserData());
            editor().area().replaceText("Newest shared text");
            tabs().getSelectionModel().selectFirst();
            assertEquals(NoteKey.overview(module.id()), tabs().getTabs().getFirst().getUserData());
            selectSection(key);
            assertSame(first, editor());
            assertEquals("Newest shared text", editor().area().getText());
            editor().area().moveTo(editor().area().getLength());
            editor().area().replaceSelection("!");
            tabs().getSelectionModel().selectLast();
            assertEquals("Newest shared text!", editor().area().getText());
            assertEquals(2, tabs().getTabs().size());
            return null;
        });
        closeNotes();
        assertEquals("Newest shared text!", new NoteService(owner.id()).load(key).plainText());
    }

    @Test void newTabCopiesSelectionScrollAndSearchQuery() throws Exception {
        open(NoteKey.topic(module.id(), topic.id()), topic.name());
        fx(() -> {
            editor().area().replaceSelection("A line about notebook view positions.\n".repeat(150));
            editor().area().selectRange(800, 825);
            ((TextField) notesStage().getScene().lookup("#searchField")).setText("view positions");
            notesStage().getScene().getRoot().applyCss();
            notesStage().getScene().getRoot().layout();
            editor().area().scrollYToPixel(420);
            return null;
        });
        await(() -> fxUnchecked(() -> editor().area().estimatedScrollYProperty().getValue() > 400));
        double originalScroll = fx(() -> editor().area().estimatedScrollYProperty().getValue());
        duplicateTab();
        await(() -> fxUnchecked(() -> Math.abs(editor().area().estimatedScrollYProperty().getValue() - originalScroll) < 2));
        fx(() -> {
            assertEquals(800, editor().area().getSelection().getStart());
            assertEquals(825, editor().area().getSelection().getEnd());
            assertEquals("view positions", ((TextField) notesStage().getScene().lookup("#searchField")).getText());
            assertEquals(2, tabs().getTabs().size());
            return null;
        });
    }

    @Test void topicLoadsRemainAttachedToTheirTabWhenSwitchingDuringLoading() throws Exception {
        Topic another = new TopicRepository().create(module.id(), "Concurrency", "", ImportanceLevel.MEDIUM, ConfidenceLevel.MEDIUM);
        NoteKey anotherKey = NoteKey.topic(module.id(), another.id());
        NoteKey overview = NoteKey.overview(module.id());
        open(NoteKey.topic(module.id(), topic.id()), topic.name());
        duplicateTab();
        Tab original = fx(() -> tabs().getTabs().getFirst());
        Tab duplicate = fx(() -> tabs().getTabs().getLast());
        fx(() -> {
            selectSection(anotherKey);
            tabs().getSelectionModel().select(original);
            selectSection(overview);
            return null;
        });
        await(() -> fxUnchecked(() -> overview.equals(original.getUserData()) && anotherKey.equals(duplicate.getUserData())
                && editor() != null && !editor().isDisabled()));
        fx(() -> {
            assertSame(original, tabs().getSelectionModel().getSelectedItem());
            assertEquals("Overview", ((Label) notesStage().getScene().lookup("#sectionNameLabel")).getText());
            tabs().getSelectionModel().select(duplicate);
            assertEquals("Concurrency", ((Label) notesStage().getScene().lookup("#sectionNameLabel")).getText());
            editor().area().replaceSelection("Written in the second tab.");
            return null;
        });
        closeNotes();
        assertEquals("Written in the second tab.", new NoteService(owner.id()).load(anotherKey).plainText());
        assertEquals("", new NoteService(owner.id()).load(overview).plainText());
    }

    @Test void closingTabsSavesNotesAndTheLastTabReturnsToTheEmptyState() throws Exception {
        NoteKey topicKey = NoteKey.topic(module.id(), topic.id());
        open(topicKey, topic.name());
        fx(() -> { editor().area().replaceSelection("Saved before closing this tab."); return null; });
        duplicateTab();
        open(NoteKey.overview(module.id()), "Overview");
        Tab topicTab = fx(() -> tabs().getTabs().getFirst());
        fx(() -> {
            javafx.event.Event.fireEvent(topicTab, new javafx.event.Event(Tab.TAB_CLOSE_REQUEST_EVENT));
            return null;
        });
        await(() -> fxUnchecked(() -> tabs().getTabs().size() == 1));
        assertEquals("Saved before closing this tab.", new NoteService(owner.id()).load(topicKey).plainText());
        fx(() -> {
            assertEquals("Overview", ((Label) notesStage().getScene().lookup("#sectionNameLabel")).getText());
            editor().area().replaceSelection("Last tab also saves.");
            editor().area().fireEvent(key(KeyCode.W));
            return null;
        });
        await(() -> fxUnchecked(() -> tabs().getTabs().size() == 1 && tabs().getTabs().getFirst().getUserData() == null));
        assertEquals("Last tab also saves.", new NoteService(owner.id()).load(NoteKey.overview(module.id())).plainText());
        fx(() -> {
            assertTrue(notesStage().isShowing());
            assertTrue(editor().isDisabled());
            assertTrue(notesStage().getScene().lookup("#emptyLabel").isVisible());
            assertEquals("Choose a section", ((Label) notesStage().getScene().lookup("#sectionNameLabel")).getText());
            NotesWindowManager.refreshOpenWindow();
            return null;
        });
        // Waiting for a fresh sidebar confirms a focus refresh does not reopen a closed tab.
        await(() -> fxUnchecked(() -> !((TreeView<?>) notesStage().getScene().lookup("#notebookTree")).getRoot().getChildren().isEmpty()));
        assertEquals(1, fx(() -> tabs().getTabs().size()));
        assertNull(fx(() -> tabs().getTabs().getFirst().getUserData()));
        open(topicKey, topic.name());
        assertEquals("Saved before closing this tab.", fx(() -> editor().area().getText()));
        assertEquals(1, fx(() -> tabs().getTabs().size()));
    }

    @Test void tabTitlesFollowRenamesAndDistinguishSectionsInDifferentModules() throws Exception {
        open(NoteKey.topic(module.id(), topic.id()), topic.name());
        StudyModule second = new ModuleRepository().create("Networking", "", null, ImportanceLevel.MEDIUM);
        Topic duplicateName = new TopicRepository().create(second.id(), topic.name(), "", ImportanceLevel.MEDIUM, ConfidenceLevel.MEDIUM);
        duplicateTab();
        open(NoteKey.topic(second.id(), duplicateName.id()), duplicateName.name());
        fx(() -> {
            assertEquals(2, tabs().getTabs().size());
            assertEquals("Data Engineering / Relational Databases", tabs().getTabs().getFirst().getText());
            assertEquals("Networking / Relational Databases", tabs().getTabs().getLast().getText());
            return null;
        });
        try (Connection conn = DatabaseManager.connect(); var stmt = conn.prepareStatement("UPDATE topics SET name=? WHERE id=?")) {
            stmt.setString(1, "Database Design"); stmt.setLong(2, topic.id()); stmt.executeUpdate();
        }
        fx(() -> { NotesWindowManager.refreshOpenWindow(); return null; });
        await(() -> fxUnchecked(() -> tabs().getTabs().getFirst().getText().equals("Data Engineering / Database Design")));
        fx(() -> {
            tabs().getSelectionModel().selectFirst();
            assertEquals("Database Design", ((Label) notesStage().getScene().lookup("#sectionNameLabel")).getText());
            assertEquals(2, tabs().getTabs().size());
            return null;
        });
    }

    @Test void searchResultsReplaceTheCurrentTopicAndSearchQueriesBelongToEachTab() throws Exception {
        open(NoteKey.topic(module.id(), topic.id()), topic.name());
        fx(() -> { editor().area().replaceSelection("Find this note again."); return null; });
        duplicateTab();
        open(NoteKey.overview(module.id()), "Overview");
        fx(() -> {
            ((TextField) notesStage().getScene().lookup("#searchField")).setText("Find this note");
            return null;
        });
        await(() -> fxUnchecked(() -> ((ListView<?>) notesStage().getScene().lookup("#searchResults")).getItems().size() == 1));
        fx(() -> {
            ((ListView<?>) notesStage().getScene().lookup("#searchResults")).getSelectionModel().selectFirst();
            return null;
        });
        await(() -> fxUnchecked(() -> NoteKey.topic(module.id(), topic.id()).equals(tabs().getTabs().getLast().getUserData())));
        fx(() -> {
            assertEquals(2, tabs().getTabs().size());
            tabs().getSelectionModel().selectFirst();
            assertEquals("", ((TextField) notesStage().getScene().lookup("#searchField")).getText());
            tabs().getSelectionModel().selectLast();
            assertEquals("Find this note", ((TextField) notesStage().getScene().lookup("#searchField")).getText());
            assertEquals("Find this note", editor().area().getSelectedText());
            editor().area().fireEvent(key(KeyCode.W));
            return null;
        });
        await(() -> fxUnchecked(() -> tabs().getTabs().size() == 1));
        fx(() -> {
            ((TextField) notesStage().getScene().lookup("#searchField")).setText("Find this note");
            return null;
        });
        await(() -> fxUnchecked(() -> ((ListView<?>) notesStage().getScene().lookup("#searchResults")).getItems().size() == 1));
        fx(() -> { ((ListView<?>) notesStage().getScene().lookup("#searchResults")).getSelectionModel().selectFirst(); return null; });
        assertEquals("Find this note again.", fx(() -> editor().area().getText()));
        assertEquals("Find this note", fx(() -> editor().area().getSelectedText()));
        assertEquals(1, fx(() -> tabs().getTabs().size()));
    }

    @Test void aFailedTabCloseRetainsTheDraftUntilSavingCanBeRetried() throws Exception {
        NoteKey key = NoteKey.topic(module.id(), topic.id());
        open(key, topic.name());
        fx(() -> { editor().area().replaceSelection("Keep this tab's unsaved draft."); return null; });
        new TopicRepository().deleteById(topic.id());
        Tab tab = fx(() -> tabs().getTabs().getFirst());
        fx(() -> { javafx.event.Event.fireEvent(tab, new javafx.event.Event(Tab.TAB_CLOSE_REQUEST_EVENT)); return null; });
        await(() -> fxUnchecked(() -> ((Label) notesStage().getScene().lookup("#saveStatus")).getText().equals("Save failed") && tab.isClosable()));
        fx(() -> {
            assertEquals(1, tabs().getTabs().size());
            assertEquals("Keep this tab's unsaved draft.", editor().area().getText());
            assertTrue(notesStage().getScene().lookup("#errorLabel").isVisible());
            return null;
        });
        duplicateTab();
        open(NoteKey.overview(module.id()), "Overview");
        fx(() -> {
            assertEquals(2, tabs().getTabs().size());
            // The deleted section is absent from the refreshed sidebar, but its tab retains the draft.
            tabs().getSelectionModel().select(tab);
            assertEquals("Keep this tab's unsaved draft.", editor().area().getText());
            assertTrue(editor().isDisabled());
            return null;
        });
        try (Connection conn = DatabaseManager.connect(); var stmt = conn.prepareStatement("""
                INSERT INTO topics(id,module_id,name,created_at,updated_at) VALUES(?,?,?,'2026-01-01T00:00','2026-01-01T00:00')
                """)) {
            stmt.setLong(1, topic.id()); stmt.setLong(2, module.id()); stmt.setString(3, topic.name()); stmt.executeUpdate();
        }
        fx(() -> { javafx.event.Event.fireEvent(tab, new javafx.event.Event(Tab.TAB_CLOSE_REQUEST_EVENT)); return null; });
        await(() -> fxUnchecked(() -> tabs().getTabs().size() == 1 && !tabs().getTabs().contains(tab)));
        assertEquals("Keep this tab's unsaved draft.", new NoteService(owner.id()).load(key).plainText());
    }

    @Test void listTypingContinuesAndEmptyListReturnsToParagraph() throws Exception {
        NoteKey key = NoteKey.overview(module.id());
        open(key, "Overview");
        NoteDocument expected = fx(() -> {
            var area = editor().area();
            editor().formatBlock(NoteDocument.Block.BULLET);
            area.replaceSelection("One");
            area.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
            assertEquals(2, area.getParagraphs().size());
            assertEquals(NoteDocument.Block.BULLET, area.getParagraphs().getLast().getParagraphStyle());
            area.replaceSelection("Two");
            area.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
            area.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
            assertEquals(NoteDocument.Block.NORMAL, area.getParagraphs().getLast().getParagraphStyle());
            editor().formatBlock(NoteDocument.Block.HEADING_2);
            area.replaceSelection("Subheading");
            return editor().document();
        });
        closeNotes();
        assertEquals(expected, new NoteService(owner.id()).load(key));
    }

    @ParameterizedTest
    @EnumSource(value = NoteDocument.Block.class, names = {"HEADING_1", "HEADING_2"})
    void enterAfterHeadingStartsNormalTextAndSurvivesUndoAndReopening(NoteDocument.Block heading) throws Exception {
        NoteKey key = NoteKey.overview(module.id());
        open(key, "Overview");
        NoteDocument expected = fx(() -> {
            var area = editor().area();
            toolbar(editor(), heading == NoteDocument.Block.HEADING_1 ? "Heading 1" : "Heading 2").fire();
            editor().toggleMark(NoteDocument.BOLD);
            area.replaceSelection("My heading");
            NoteDocument beforeEnter = editor().document();
            area.getUndoManager().preventMerge();
            area.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
            assertEquals(2, area.getParagraphs().size());
            assertEquals(heading, area.getParagraphs().getFirst().getParagraphStyle());
            assertEquals(NoteDocument.Block.NORMAL, area.getParagraphs().getLast().getParagraphStyle());
            assertTrue(((ToggleButton) toolbar(editor(), "Normal paragraph")).isSelected());
            area.undo();
            assertEquals(beforeEnter, editor().document());
            area.redo();
            assertEquals(NoteDocument.Block.NORMAL, area.getParagraphs().getLast().getParagraphStyle());
            area.replaceSelection("Body text");
            assertEquals("My heading\nBody text", area.getText());
            assertEquals(NoteDocument.BOLD, area.getParagraphs().getFirst().getStyleOfChar(0));
            assertEquals(0, area.getParagraphs().getLast().getStyleOfChar(0));
            return editor().document();
        });
        closeNotes();
        open(key, "Overview");
        assertEquals(expected, fx(() -> editor().document()));
    }

    @Test void enterInMiddleOfHeadingPreservesTextAndFormatsTheFollowingParagraphNormally() throws Exception {
        open(NoteKey.overview(module.id()), "Overview");
        fx(() -> {
            var area = editor().area();
            editor().formatBlock(NoteDocument.Block.HEADING_1);
            area.replaceSelection("Heading body text");
            NoteDocument beforeEnter = editor().document();
            area.selectRange(7, 12); // Replace the selected word with the paragraph break.
            area.getUndoManager().preventMerge();
            area.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
            assertEquals("Heading\n text", area.getText());
            assertEquals(NoteDocument.Block.HEADING_1, area.getParagraphs().getFirst().getParagraphStyle());
            assertEquals(NoteDocument.Block.NORMAL, area.getParagraphs().getLast().getParagraphStyle());
            assertEquals(8, area.getCaretPosition());
            area.undo();
            assertEquals(beforeEnter, editor().document());
            area.redo();
            assertEquals("Heading\n text", area.getText());
            assertEquals(NoteDocument.Block.NORMAL, area.getParagraphs().getLast().getParagraphStyle());
            return null;
        });
    }

    @Test void enterFromEmptyHeadingStartsANormalParagraph() throws Exception {
        open(NoteKey.overview(module.id()), "Overview");
        fx(() -> {
            var area = editor().area();
            editor().formatBlock(NoteDocument.Block.HEADING_2);
            area.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
            assertEquals("\n", area.getText());
            assertEquals(NoteDocument.Block.HEADING_2, area.getParagraphs().getFirst().getParagraphStyle());
            assertEquals(NoteDocument.Block.NORMAL, area.getParagraphs().getLast().getParagraphStyle());
            area.replaceSelection("Normal text");
            assertEquals(0, area.getParagraphs().getLast().getStyleOfChar(0));
            return null;
        });
    }

    @Test void moduleAndTopicEntryPointsOpenTheSameDocumentsAndStructureRefreshes() throws Exception {
        fx(() -> {
            var loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/commonplace/fxml/ModulesView.fxml"));
            javafx.scene.Parent root = loader.load();
            main.getScene().setRoot(root);
            VBox modules = (VBox) loader.getNamespace().get("modulesList");
            var card = modules.getChildren().getFirst();
            card.fireEvent(new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                    1, 1, 1, 1, javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false,
                    true, false, false, false, false, true, null));
            ((Button) loader.getNamespace().get("openNotebookButton")).fire();
            return null;
        });
        await(() -> fxUnchecked(() -> !editor().isDisabled()
                && ((Label) notesStage().getScene().lookup("#sectionNameLabel")).getText().equals("Overview")));
        fx(() -> {
            editor().area().replaceSelection("Overview from Modules");
            var loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/commonplace/fxml/TopicDetailView.fxml"));
            javafx.scene.Parent root = loader.load();
            main.getScene().setRoot(root);
            com.commonplace.ui.controller.TopicDetailController controller = loader.getController();
            controller.setTopic(topic, module);
            root.lookupAll(".button").stream().filter(node -> node instanceof Button b && b.getText().equals("Open Notes"))
                    .map(node -> (Button) node).findFirst().orElseThrow().fire();
            return null;
        });
        await(() -> fxUnchecked(() -> !editor().isDisabled()
                && ((Label) notesStage().getScene().lookup("#sectionNameLabel")).getText().equals(topic.name())));
        fx(() -> { editor().area().replaceSelection("Notes from Topic Detail"); return null; });
        Topic added = new TopicRepository().create(module.id(), "New section", "", ImportanceLevel.LOW, ConfidenceLevel.MEDIUM);
        fx(() -> { NotesWindowManager.refreshOpenWindow(); return null; });
        await(() -> fxUnchecked(() -> {
            TreeView<?> tree = (TreeView<?>) notesStage().getScene().lookup("#notebookTree");
            return tree.getRoot().getChildren().getFirst().getChildren().size() == 3;
        }));
        closeNotes();
        NoteService service = new NoteService(owner.id());
        assertEquals("Overview from Modules", service.load(NoteKey.overview(module.id())).plainText());
        assertEquals("Notes from Topic Detail", service.load(NoteKey.topic(module.id(), topic.id())).plainText());
        assertEquals(NoteDocument.empty(), service.load(NoteKey.topic(module.id(), added.id())));
    }

    @Test void closeFailureKeepsDraftAndSuccessfulRetryClosesNormally() throws Exception {
        open(NoteKey.topic(module.id(), topic.id()), topic.name());
        fx(() -> { editor().area().replaceSelection("Recover this deleted-topic draft."); return null; });
        new TopicRepository().deleteById(topic.id());
        fx(() -> { ((Button) notesStage().getScene().lookup(".app-chrome-close-button")).fire(); return null; });
        await(() -> fxUnchecked(() -> ((Label) notesStage().getScene().lookup("#saveStatus")).getText().equals("Save failed")));
        fx(() -> {
            assertTrue(notesStage().isShowing());
            assertEquals("Recover this deleted-topic draft.", editor().document().plainText());
            assertTrue(notesStage().getScene().lookup("#errorLabel").isVisible());
            assertTrue(notesStage().getScene().lookup("#recoveryButton").isVisible());
            return null;
        });
        // Restore the record to simulate resolving the save error, then retry the real Close control.
        try (Connection conn = DatabaseManager.connect(); var stmt = conn.prepareStatement("""
                INSERT INTO topics(id,module_id,name,created_at,updated_at) VALUES(?,?,?,'2026-01-01T00:00','2026-01-01T00:00')
                """)) { stmt.setLong(1, topic.id()); stmt.setLong(2, module.id()); stmt.setString(3, topic.name()); stmt.executeUpdate(); }
        fx(() -> { ((Button) notesStage().getScene().lookup(".app-chrome-close-button")).fire(); return null; });
        await(() -> fxUnchecked(() -> !hasNotesWindow()));
        assertEquals("Recover this deleted-topic draft.", new NoteService(owner.id()).load(NoteKey.topic(module.id(), topic.id())).plainText());
    }

    @Test void mainWindowCloseFlushesPendingNotesAndClosesBothWindows() throws Exception {
        open(NoteKey.topic(module.id(), topic.id()), topic.name());
        fx(() -> {
            editor().area().replaceSelection("Saved when Commonplace closes.");
            main.fireEvent(new WindowEvent(main, WindowEvent.WINDOW_CLOSE_REQUEST));
            return null;
        });
        await(() -> fxUnchecked(() -> !main.isShowing() && !hasNotesWindow()));
        assertEquals("Saved when Commonplace closes.", new NoteService(owner.id()).load(NoteKey.topic(module.id(), topic.id())).plainText());
    }

    private void open(NoteKey key, String title) throws Exception {
        fx(() -> { NotesWindowManager.open(source, key); return null; });
        await(() -> fxUnchecked(() -> editor() != null && !editor().isDisabled()
                && ((Label) notesStage().getScene().lookup("#sectionNameLabel")).getText().equals(title)
                && (key == null || key.equals(tabs().getSelectionModel().getSelectedItem().getUserData()))));
    }
    private void duplicateTab() throws Exception {
        int count = fx(() -> tabs().getTabs().size());
        fx(() -> { ((Button) notesStage().getScene().lookup("#newTabButton")).fire(); return null; });
        await(() -> fxUnchecked(() -> tabs().getTabs().size() == count + 1 && editor() != null));
    }
    @SuppressWarnings("unchecked")
    private static void selectSection(NoteKey key) {
        TreeView<NotebookSection> tree = (TreeView<NotebookSection>) notesStage().getScene().lookup("#notebookTree");
        TreeItem<NotebookSection> item = tree.getRoot().getChildren().stream()
                .flatMap(module -> module.getChildren().stream())
                .filter(section -> section.getValue().key().equals(key)).findFirst().orElseThrow();
        tree.getSelectionModel().select(item);
    }
    private void closeNotes() throws Exception {
        CountDownLatch closed = new CountDownLatch(1);
        fx(() -> { NotesWindowManager.withSavedNotes(true, closed::countDown); return null; });
        assertTrue(closed.await(10, TimeUnit.SECONDS), "Notes did not close after saving");
    }
    private static KeyEvent key(KeyCode code) { return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, true, false, false); }
    private static ButtonBase toolbar(NoteEditor editor, String label) {
        return editor.lookupAll(".note-format-button").stream().filter(node -> node.getAccessibleText().startsWith(label))
                .map(node -> (ButtonBase) node).findFirst().orElseThrow();
    }
    private static NoteEditor editor() { return (NoteEditor) notesStage().getScene().lookup("#editor"); }
    private static TabPane tabs() { return (TabPane) notesStage().getScene().lookup("#noteTabs"); }
    private static boolean hasNotesWindow() { return Window.getWindows().stream().anyMatch(w -> w instanceof Stage s && "Commonplace / Notes".equals(s.getTitle())); }
    private static Stage notesStage() { return Window.getWindows().stream().filter(w -> w instanceof Stage s && "Commonplace / Notes".equals(s.getTitle())).map(w -> (Stage) w).findFirst().orElseThrow(); }
    private static NoteDocument load(NoteService service, NoteKey key) {
        try { return service.load(key); } catch (Exception e) { throw new RuntimeException(e); }
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) { if (condition.getAsBoolean()) return; Thread.sleep(25); }
        fail("Timed out waiting for JavaFX/notes IO");
    }
    private static <T> T fx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }
    private static <T> T fxUnchecked(Callable<T> work) {
        try { return fx(work); } catch (Exception e) { throw new RuntimeException(e); }
    }
    private static void screenshot(Stage stage, String name) throws Exception {
        WritableImage image = stage.getScene().getRoot().snapshot(null, null);
        int width = (int) image.getWidth(), height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, width, height, pixels, 0, width);
        Files.createDirectories(Path.of("target"));
        ImageIO.write(buffered, "png", Path.of("target", name).toFile());
    }
}
