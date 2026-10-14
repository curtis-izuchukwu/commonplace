package com.commonplace.ui.controller;

import com.commonplace.model.*;
import com.commonplace.service.NoteService;
import com.commonplace.service.NoteWorkspace;
import com.commonplace.ui.NoteEditor;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class NotesWindowController {
    @FXML private SplitPane splitPane;
    @FXML private VBox sidebar;
    @FXML private TabPane noteTabs;
    @FXML private TreeView<NotebookSection> notebookTree;
    @FXML private ListView<NoteSearchResult> searchResults;
    @FXML private TextField searchField;
    @FXML private Label navigationHint;
    @FXML private Label saveStatus, savedAtLabel, errorLabel;
    @FXML private Button retryButton, recoveryButton, newTabButton;
    private Label moduleNameLabel, sectionNameLabel;
    private NoteEditor editor;

    private final PauseTransition searchPause = new PauseTransition(Duration.millis(250));
    private final Map<NoteKey, TreeItem<NotebookSection>> leaves = new HashMap<>();
    private final Map<Tab, NotebookTab> tabViews = new LinkedHashMap<>();
    private NotebookTab currentTab, requestedTab;
    private List<NotebookSection> sections = List.of();
    private NoteWorkspace workspace;
    private NoteKey currentKey, requestedKey;
    private long accountId;
    private long searchGeneration, structureGeneration;
    private boolean changingSelection, changingTabs, busy, disposed;
    private Runnable recoveryAction;

    private static final class NotebookTab {
        final Tab tab = new Tab();
        final Map<NoteKey, NoteEditor> editors = new LinkedHashMap<>();
        final NoteEditor blankEditor = new NoteEditor();
        final Label moduleName = new Label(), sectionName = new Label();
        final Label emptyHint = new Label("Select an Overview or topic to start writing.");
        final StackPane editorHost = new StackPane();
        NoteKey key;
        String searchQuery = "";
        long navigationGeneration;
        boolean closing, loading;

        NotebookTab() {
            moduleName.getStyleClass().add("section-eyebrow");
            sectionName.getStyleClass().add("page-title");
            moduleName.setWrapText(true);
            sectionName.setWrapText(true);
            emptyHint.getStyleClass().add("muted-text");
            emptyHint.setWrapText(true);
            emptyHint.setMouseTransparent(true);
            blankEditor.setDisable(true);
            editorHost.setMinHeight(0);
            VBox content = new VBox(12, new VBox(4, moduleName, sectionName), editorHost);
            content.getStyleClass().add("notes-tab-page");
            content.setMinHeight(0);
            VBox.setVgrow(editorHost, Priority.ALWAYS);
            tab.setContent(content);
        }

        NoteEditor editor() { return key == null ? blankEditor : editors.get(key); }
        void dispose() { blankEditor.dispose(); editors.values().forEach(NoteEditor::dispose); }
    }

    private record EditorPosition(int anchor, int caret, double scrollX, double scrollY, Integer insertionStyle) {
        static EditorPosition capture(NoteEditor editor) {
            var area = editor.area();
            return new EditorPosition(area.getAnchor(), area.getCaretPosition(),
                    area.estimatedScrollXProperty().getValue(), area.estimatedScrollYProperty().getValue(), area.getTextInsertionStyle());
        }
        void restoreSelection(NoteEditor editor) {
            var area = editor.area();
            area.selectRange(Math.min(anchor, area.getLength()), Math.min(caret, area.getLength()));
            area.setTextInsertionStyle(insertionStyle);
        }
        void restoreScroll(NoteEditor editor) {
            editor.area().scrollXToPixel(scrollX);
            editor.area().scrollYToPixel(scrollY);
        }
    }

    @FXML private void initialize() {
        noteTabs.getSelectionModel().selectedItemProperty().addListener((observable, previous, tab) -> {
            if (!changingTabs && !busy && !disposed && tab != null) {
                if (workspace != null) workspace.flush();
                activateTab(tabViews.get(tab), -1, 0);
            }
        });
        newTabButton.setTooltip(new Tooltip("New tab: duplicate the current view (Ctrl+T)"));
        notebookTree.setRoot(new TreeItem<>());
        notebookTree.setCellFactory(tree -> new TreeCell<>() {
            @Override protected void updateItem(NotebookSection section, boolean empty) {
                super.updateItem(section, empty);
                setText(empty || section == null ? null : section.title());
                setGraphic(null);
                getStyleClass().remove("notebook-module");
                if (!empty && getTreeItem() != null && getTreeItem().getParent() == notebookTree.getRoot()) {
                    getStyleClass().add("notebook-module");
                }
                setAccessibleText(empty || section == null ? null : section.moduleName() + ", " + section.title());
            }
        });
        notebookTree.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, item) -> {
            if (!changingSelection && !busy && item != null && item.getValue() != null) {
                navigate(item.getValue().key(), -1, 0);
            }
        });
        searchResults.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(NoteSearchResult result, boolean empty) {
                super.updateItem(result, empty);
                if (empty || result == null) { setGraphic(null); setText(null); return; }
                Label title = new Label(result.section().moduleName() + " / " + result.section().title());
                title.setWrapText(true);
                title.getStyleClass().add("note-result-title");
                Label snippet = new Label(result.snippet());
                snippet.setWrapText(true);
                snippet.getStyleClass().add("muted-text");
                VBox content = new VBox(4, title, snippet);
                content.prefWidthProperty().bind(list.widthProperty().subtract(42));
                setGraphic(content);
                setText(null);
                setAccessibleText(title.getText() + ". " + snippet.getText());
            }
        });
        searchResults.getSelectionModel().selectedItemProperty().addListener((observable, previous, result) -> {
            if (!changingSelection && result != null && !busy) navigate(result.section().key(), result.matchOffset(), searchField.getText().trim().length());
        });
        searchResults.setPlaceholder(new Label("No matching notes"));
        searchField.textProperty().addListener((observable, previous, query) -> {
            if (currentTab != null) currentTab.searchQuery = query;
            searchGeneration++;
            searchPause.stop();
            searchResults.getSelectionModel().clearSelection();
            boolean searching = !query.isBlank();
            searchResults.setVisible(searching);
            searchResults.setManaged(searching);
            notebookTree.setVisible(!searching);
            notebookTree.setManaged(!searching);
            if (searching) searchPause.playFromStart();
        });
        searchPause.setOnFinished(event -> runSearch());
        activateTab(createTab(), -1, 0);
    }

    public void start(long userId, Scene scene) {
        accountId = userId;
        workspace = new NoteWorkspace(new NoteService(userId));
        workspace.setStatusListener(status -> Platform.runLater(() -> {
            if (!disposed) displayStatus(workspace.status());
        }));
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isShortcutDown() && event.getCode() == KeyCode.S) {
                handleSave(); event.consume();
            } else if (event.isShortcutDown() && event.getCode() == KeyCode.F) {
                searchField.requestFocus(); searchField.selectAll(); event.consume();
            } else if (event.isShortcutDown() && event.getCode() == KeyCode.T) {
                handleNewTab(); event.consume();
            } else if (event.isShortcutDown() && event.getCode() == KeyCode.W) {
                if (currentTab != null) closeTab(currentTab);
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.TAB && !busy) {
                int count = noteTabs.getTabs().size();
                if (count > 0) {
                    int index = noteTabs.getSelectionModel().getSelectedIndex();
                    noteTabs.getSelectionModel().select(Math.floorMod(index + (event.isShiftDown() ? -1 : 1), count));
                }
                event.consume();
            }
        });
    }

    public void requestNavigation(NoteKey key) {
        if (busy || disposed) return;
        requestedKey = key;
        requestedTab = currentTab;
        searchField.clear();
        refreshNavigation();
    }

    @FXML private void handleRefresh() { refreshNavigation(); }

    /** Called on window focus and after structure changes; IO never runs on the FX thread. */
    public void refreshNavigation() {
        if (workspace == null || busy || disposed) return;
        long generation = ++structureGeneration;
        workspace.sections().whenComplete((fresh, failure) -> Platform.runLater(() -> {
            if (disposed || busy || generation != structureGeneration) return;
            if (failure != null) { showError("Notebooks could not be refreshed: " + message(failure)); return; }
            boolean changed = !sections.equals(fresh);
            if (changed) rebuildTree(fresh);
            refreshTabs();
            NoteKey destination = requestedKey;
            NotebookTab target = requestedTab;
            requestedKey = null;
            requestedTab = null;
            if (destination != null) {
                if (target != null && !tabViews.containsKey(target.tab)) return;
                if (leaves.containsKey(destination)) navigate(target == null ? currentTab : target, destination, -1, 0);
                else showError("The requested module or topic is no longer available for this account.");
            }
            if (!searchField.getText().isBlank() && changed) runSearch();
        }));
    }

    private void rebuildTree(List<NotebookSection> fresh) {
        Set<Long> expanded = new HashSet<>();
        notebookTree.getRoot().getChildren().forEach(item -> {
            if (item.isExpanded()) expanded.add(item.getValue().key().moduleId());
        });
        boolean initial = sections.isEmpty();
        sections = List.copyOf(fresh);
        changingSelection = true;
        try {
            leaves.clear();
            notebookTree.getRoot().getChildren().clear();
            TreeItem<NotebookSection> module = null;
            for (NotebookSection section : sections) {
                if (section.key().isOverview()) {
                    module = new TreeItem<>(new NotebookSection(section.key(), section.moduleName(), section.moduleName()));
                    module.setExpanded(expanded.contains(section.key().moduleId()) || initial && sections.size() < 15);
                    notebookTree.getRoot().getChildren().add(module);
                }
                TreeItem<NotebookSection> leaf = new TreeItem<>(section);
                module.getChildren().add(leaf);
                leaves.put(section.key(), leaf);
            }
            if (currentKey != null && leaves.containsKey(currentKey)) selectLeaf(currentKey);
        } finally { changingSelection = false; }
        navigationHint.setText(fresh.isEmpty() ? "Create modules and topics in Commonplace."
                : "Ctrl+F to search · Ctrl+S to save");
    }

    private void navigate(NoteKey key, int matchOffset, int matchLength) {
        navigate(currentTab, key, matchOffset, matchLength);
    }

    private void navigate(NotebookTab view, NoteKey key, int matchOffset, int matchLength) {
        if (busy || disposed || view.closing || !tabViews.containsKey(view.tab)
                || !leaves.containsKey(key) && !view.editors.containsKey(key)) return;
        if (view == currentTab) selectLeaf(key);
        long generation = ++view.navigationGeneration;
        if (view.key != null && !key.equals(view.key)) workspace.flush();
        NoteEditor existing = view.editors.get(key);
        if (existing != null) {
            view.loading = false;
            showSection(view, key);
            if (view == currentTab) activateTab(view, matchOffset, matchLength);
            return;
        }
        view.loading = true;
        view.editor().setDisable(true);
        if (view == currentTab) { updateControls(); saveStatus.setText("Opening section…"); }
        workspace.open(key).whenComplete((document, failure) -> Platform.runLater(() -> {
            if (disposed || !tabViews.containsKey(view.tab) || generation != view.navigationGeneration) return;
            view.loading = false;
            if (busy) { requestedKey = key; requestedTab = view; return; }
            if (failure != null) {
                if (view == currentTab) { updateControls(); selectLeaf(view.key); }
                showError("Note could not be opened: " + message(failure));
                return;
            }
            // The parent may have disappeared during this IO operation.
            if (!leaves.containsKey(key)) { refreshNavigation(); return; }
            // Another view may have edited this note after the worker captured its snapshot.
            NoteDocument latest = tabViews.values().stream().map(tab -> tab.editors.get(key))
                    .filter(Objects::nonNull).findFirst().map(NoteEditor::document).orElse(document);
            createEditor(view, key, latest);
            showSection(view, key);
            if (view == currentTab) activateTab(view, matchOffset, matchLength);
        }));
    }

    private NotebookTab createTab() {
        NotebookTab view = new NotebookTab();
        view.tab.setOnCloseRequest(event -> { event.consume(); closeTab(view); });
        tabViews.put(view.tab, view);
        showSection(view, null);
        changingTabs = true;
        try { noteTabs.getTabs().add(view.tab); }
        finally { changingTabs = false; }
        return view;
    }

    private NoteEditor createEditor(NotebookTab view, NoteKey key, NoteDocument document) {
        NoteEditor noteEditor = new NoteEditor();
        noteEditor.restore(document);
        noteEditor.setOnChanged(updated -> {
            if (busy || disposed || view.closing) return;
            workspace.update(key, updated);
            // Tabs are views of the same notes. Keep other views, including cached topics, current.
            for (NotebookTab other : tabViews.values()) {
                NoteEditor duplicate = other.editors.get(key);
                if (duplicate != null && duplicate != noteEditor && !duplicate.document().equals(updated)) {
                    EditorPosition position = EditorPosition.capture(duplicate);
                    duplicate.restore(updated);
                    position.restoreSelection(duplicate);
                    restoreScrollLater(other, duplicate, position);
                }
            }
            if (!searchField.getText().isBlank()) { searchGeneration++; searchPause.playFromStart(); }
        });
        view.editors.put(key, noteEditor);
        return noteEditor;
    }

    @FXML private void handleNewTab() {
        if (busy || disposed || currentTab.loading || currentTab.closing) return;
        NotebookTab source = currentTab;
        NotebookTab duplicate = createTab();
        duplicate.searchQuery = source.searchQuery;
        EditorPosition position = null;
        if (source.key != null) {
            position = EditorPosition.capture(source.editor());
            NoteEditor copied = createEditor(duplicate, source.key, source.editor().document());
            position.restoreSelection(copied);
            showSection(duplicate, source.key);
        }
        activateTab(duplicate, -1, 0);
        if (position != null) restoreScrollLater(duplicate, duplicate.editor(), position);
    }

    private void restoreScrollLater(NotebookTab view, NoteEditor target, EditorPosition position) {
        Platform.runLater(() -> {
            if (disposed || !tabViews.containsKey(view.tab) || !view.editors.containsValue(target)) return;
            if (target.getScene() != null) { target.getScene().getRoot().applyCss(); target.getScene().getRoot().layout(); }
            position.restoreScroll(target);
        });
    }

    private void showSection(NotebookTab view, NoteKey key) {
        view.key = key;
        view.tab.setUserData(key);
        if (key == null) view.editorHost.getChildren().setAll(view.blankEditor, view.emptyHint);
        else view.editorHost.getChildren().setAll(view.editor());
        updateTabTitle(view);
    }

    private void activateTab(NotebookTab view, int matchOffset, int matchLength) {
        if (editor != null) {
            editor.setId(null);
            moduleNameLabel.setId(null);
            sectionNameLabel.setId(null);
            currentTab.emptyHint.setId(null);
        }
        currentTab = view;
        currentKey = view.key;
        editor = view.editor();
        moduleNameLabel = view.moduleName;
        sectionNameLabel = view.sectionName;
        editor.setId("editor");
        moduleNameLabel.setId("moduleNameLabel");
        sectionNameLabel.setId("sectionNameLabel");
        view.emptyHint.setId("emptyLabel");
        changingTabs = true;
        try { noteTabs.getSelectionModel().select(view.tab); }
        finally { changingTabs = false; }
        searchGeneration++;
        searchResults.getSelectionModel().clearSelection();
        searchField.setText(view.searchQuery);
        if (!view.searchQuery.isBlank()) searchPause.playFromStart();
        selectLeaf(view.key);
        updateControls();
        if (workspace != null) displayStatus(workspace.status());
        if (view.key != null && !leaves.containsKey(view.key)) showError("This section was deleted in Commonplace. Unsaved notes are still in this tab. Save a recovery copy before closing.");
        if (view.key != null && !view.loading) positionEditor(matchOffset, matchLength);
    }

    private void updateTabTitle(NotebookTab view) {
        TreeItem<NotebookSection> item = leaves.get(view.key);
        if (view.key == null) {
            view.moduleName.setText("YOUR STUDY NOTEBOOKS");
            view.sectionName.setText("Choose a section");
            view.tab.setText("Notebook");
            view.tab.setTooltip(new Tooltip("Choose a module or topic in this tab"));
        } else if (item != null) {
            NotebookSection section = item.getValue();
            view.moduleName.setText(section.moduleName());
            view.sectionName.setText(section.title());
            String title = section.moduleName() + " / " + section.title();
            view.tab.setText(title);
            view.tab.setTooltip(new Tooltip(title));
        }
    }

    private void refreshTabs() {
        for (NotebookTab view : tabViews.values()) {
            for (NoteKey key : List.copyOf(view.editors.keySet())) {
                if (!leaves.containsKey(key)) {
                    NoteEditor removed = view.editors.get(key);
                    removed.setDisable(true);
                    if (!workspace.hasPendingChanges() && !view.closing) {
                        if (key.equals(view.key)) showSection(view, null);
                        view.editors.remove(key);
                        if (view == currentTab) activateTab(view, -1, 0);
                        removed.dispose();
                    }
                }
            }
            updateTabTitle(view);
        }
        updateControls();
        if (currentKey != null && !leaves.containsKey(currentKey)) showError("This section was deleted in Commonplace. Unsaved notes are still in this window. Save a recovery copy before closing.");
    }

    private void closeTab(NotebookTab view) {
        if (busy || disposed || view.closing) return;
        view.navigationGeneration++;
        view.loading = false;
        view.closing = true;
        view.tab.setClosable(false);
        view.editor().setDisable(true);
        updateControls();
        workspace.flush().whenComplete((ignored, failure) -> Platform.runLater(() -> {
            if (disposed) return;
            view.closing = false;
            view.tab.setClosable(true);
            if (failure != null) {
                updateControls();
                displayStatus(workspace.status());
                showError("This tab could not be closed because notes failed to save. Your changes are kept here. " + message(failure));
            } else removeTab(view);
        }));
    }

    private void removeTab(NotebookTab view) {
        boolean active = view == currentTab;
        changingTabs = true;
        try { noteTabs.getTabs().remove(view.tab); }
        finally { changingTabs = false; }
        tabViews.remove(view.tab);
        if (active) {
            Tab selected = noteTabs.getSelectionModel().getSelectedItem();
            activateTab(selected == null ? createTab() : tabViews.get(selected), -1, 0);
        }
        view.dispose();
    }

    private void selectLeaf(NoteKey key) {
        TreeItem<NotebookSection> item = leaves.get(key);
        boolean previous = changingSelection;
        changingSelection = true;
        try {
            if (item == null) notebookTree.getSelectionModel().clearSelection();
            else {
                item.getParent().setExpanded(true);
                notebookTree.getSelectionModel().select(item);
                notebookTree.scrollTo(notebookTree.getRow(item));
            }
        } finally { changingSelection = previous; }
    }

    private void positionEditor(int offset, int length) {
        if (offset >= 0) {
            int start = Math.min(offset, editor.area().getLength());
            editor.area().selectRange(start, Math.min(start + length, editor.area().getLength()));
            editor.focus();
        } else editor.area().requestFocus();
    }
    private void runSearch() {
        if (workspace == null || busy || disposed || searchField.getText().isBlank()) return;
        long generation = ++searchGeneration;
        workspace.search(searchField.getText()).whenComplete((results, failure) -> Platform.runLater(() -> {
            if (disposed || busy || generation != searchGeneration) return;
            if (failure != null) { showError("Search failed: " + message(failure)); return; }
            changingSelection = true;
            try { searchResults.getItems().setAll(results); }
            finally { changingSelection = false; }
        }));
    }

    @FXML private void handleSave() {
        if (workspace == null || busy || disposed) return;
        workspace.flush().whenComplete((ignored, failure) -> Platform.runLater(() -> {
            if (disposed) return;
            displayStatus(workspace.status());
            if (failure != null) showError("Save failed. Your changes are kept in this window. " + message(failure));
        }));
    }

    public boolean hasPendingChanges() { return workspace != null && workspace.hasPendingChanges(); }

    /** The window remains open and drafts remain editable on failure. */
    public void prepareToLeave(Runnable saved, Runnable recovered) {
        if (busy || disposed) return;
        recoveryAction = recovered;
        setBusy(true);
        workspace.flush().whenComplete((ignored, failure) -> Platform.runLater(() -> {
            if (disposed) return;
            if (failure == null) { saved.run(); }
            else {
                setBusy(false);
                displayStatus(workspace.status());
                showError("Save failed. Your changes are kept here. " + (recovered == null
                        ? "Retry saving before continuing. " : "Retry, or save a recovery copy to close safely. ") + message(failure));
                recoveryButton.setVisible(recovered != null);
                recoveryButton.setManaged(recovered != null);
            }
        }));
    }

    public void unlock() { if (!disposed) setBusy(false); }

    private void setBusy(boolean value) {
        busy = value;
        searchPause.stop();
        updateControls();
        if (value) saveStatus.setText("Saving…");
        else { displayStatus(workspace.status()); refreshNavigation(); }
    }

    private void updateControls() {
        sidebar.setDisable(busy || currentTab.closing);
        noteTabs.setDisable(busy);
        newTabButton.setDisable(busy || currentTab.loading || currentTab.closing);
        editor.setDisable(busy || currentTab.loading || currentTab.closing || currentTab.key == null
                || !leaves.containsKey(currentTab.key));
        recoveryButton.setDisable(busy);
    }

    @FXML private void handleRecovery() {
        if (busy || disposed || recoveryAction == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save unsaved Commonplace notes");
        chooser.setInitialFileName("commonplace-notes-recovery.zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Notes recovery copy", "*.zip"));
        var file = chooser.showSaveDialog(editor.getScene().getWindow());
        if (file == null) return;
        setBusy(true);
        workspace.exportRecovery(file.toPath(), accountId).whenComplete((ignored, failure) -> Platform.runLater(() -> {
            if (disposed) return;
            if (failure != null) { setBusy(false); showError("Recovery copy failed: " + message(failure)); }
            else recoveryAction.run();
        }));
    }

    private void displayStatus(NoteWorkspace.Status status) {
        saveStatus.setText(busy || currentTab.closing ? "Saving…" : currentTab.loading ? "Opening section…"
                : status.saving() ? "Saving…" : status.failed() > 0 ? "Save failed"
                : status.unsaved() > 0 ? "Unsaved changes" : "Saved locally");
        retryButton.setVisible(status.failed() > 0);
        retryButton.setManaged(status.failed() > 0);
        if (status.lastSaved() != null) savedAtLabel.setText(DateTimeFormatter.ofPattern("HH:mm")
                .withZone(ZoneId.systemDefault()).format(status.lastSaved()));
        if (status.failed() > 0) showError(status.failed() + " note(s) could not be saved. Changes remain in this window. " + status.error());
        else if (status.unsaved() == 0) {
            errorLabel.setVisible(false); errorLabel.setManaged(false);
            recoveryButton.setVisible(false); recoveryButton.setManaged(false);
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
    private static String message(Throwable failure) {
        while (failure.getCause() != null) failure = failure.getCause();
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    public NoteKey currentKey() { return currentKey; }
    public NoteEditor editor() { return editor; }

    public void dispose(boolean recovered) {
        if (recovered) workspace.closeAfterRecovery(); else workspace.close();
        disposed = true;
        searchPause.stop();
        tabViews.values().forEach(NotebookTab::dispose);
        tabViews.clear();
    }
}
