package com.commonplace.ui;

import com.commonplace.model.NoteDocument;
import com.commonplace.model.NoteDocument.Block;
import com.commonplace.model.NoteDocument.Paragraph;
import com.commonplace.model.NoteDocument.Span;

import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.StyledTextArea;
import org.fxmisc.richtext.model.*;
import org.reactfx.Subscription;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/** Small adapter around RichTextFX; the library owns typing, selection, wrapping and history. */
public final class NoteEditor extends BorderPane {
    private final StyledTextArea<Block, Integer> area;
    private final Map<Integer, ToggleButton> markButtons = new LinkedHashMap<>();
    private final Map<Block, ToggleButton> blockButtons = new LinkedHashMap<>();
    private final Subscription changes;
    private Consumer<NoteDocument> onChanged = ignored -> {};
    private boolean restoring;

    public NoteEditor() {
        area = new StyledTextArea<>(Block.NORMAL, (flow, block) ->
                flow.getStyleClass().setAll("note-paragraph", "note-block-" + block.name().toLowerCase(Locale.ROOT)),
                0, (text, marks) -> {
                    text.getStyleClass().setAll("note-text");
                    if ((marks & NoteDocument.BOLD) != 0) text.getStyleClass().add("note-bold");
                    if ((marks & NoteDocument.ITALIC) != 0) text.getStyleClass().add("note-italic");
                    if ((marks & NoteDocument.UNDERLINE) != 0) text.getStyleClass().add("note-underline");
                    if ((marks & NoteDocument.CODE) != 0) text.getStyleClass().add("note-code");
                }, true);
        area.getStyleClass().add("note-editor-area");
        area.setAccessibleText("Rich text notes. Control B bold, Control I italic, Control U underline.");
        area.setWrapText(true);
        area.setParagraphGraphicFactory(this::listMarker);
        area.setStyleCodecs(Codec.enumCodec(Block.class), Codec.styledTextCodec(new Codec<Integer>() {
            @Override public String getName() { return "commonplace-note-marks-v1"; }
            @Override public void encode(DataOutputStream out, Integer marks) throws IOException { out.writeInt(marks); }
            @Override public Integer decode(DataInputStream in) throws IOException {
                int marks = in.readInt();
                if ((marks & ~NoteDocument.ALL_MARKS) != 0) throw new IOException("Invalid note formatting.");
                return marks;
            }
        }));
        setCenter(new VirtualizedScrollPane<>(area));
        getStyleClass().add("note-editor");
        setTop(createToolbar());
        changes = area.multiRichChanges().subscribe(events -> {
            if (!restoring) onChanged.accept(document());
            refreshToolbar();
        });
        area.caretPositionProperty().addListener((observable, previous, current) -> {
            area.setTextInsertionStyle(null);
            refreshToolbar();
        });
        area.selectionProperty().addListener((observable, previous, current) -> refreshToolbar());
        area.addEventFilter(KeyEvent.KEY_PRESSED, this::shortcut);
        ContextMenu menu = new ContextMenu();
        addMenu(menu, "Undo", area::undo);
        addMenu(menu, "Redo", area::redo);
        menu.getItems().add(new SeparatorMenuItem());
        addMenu(menu, "Cut", area::cut);
        addMenu(menu, "Copy", area::copy);
        addMenu(menu, "Paste", area::paste);
        addMenu(menu, "Select all", area::selectAll);
        area.setContextMenu(menu);
    }

    private FlowPane createToolbar() {
        FlowPane toolbar = new FlowPane(4, 4);
        toolbar.getStyleClass().add("note-toolbar");
        addMark(toolbar, "B", "Bold (Ctrl+B)", NoteDocument.BOLD);
        addMark(toolbar, "I", "Italic (Ctrl+I)", NoteDocument.ITALIC);
        addMark(toolbar, "U", "Underline (Ctrl+U)", NoteDocument.UNDERLINE);
        addMark(toolbar, "</>", "Monospaced code (Ctrl+E)", NoteDocument.CODE);
        addBlock(toolbar, "¶", "Normal paragraph (Ctrl+Alt+0)", Block.NORMAL);
        addBlock(toolbar, "H1", "Heading 1 (Ctrl+Alt+1)", Block.HEADING_1);
        addBlock(toolbar, "H2", "Heading 2 (Ctrl+Alt+2)", Block.HEADING_2);
        addBlock(toolbar, "•", "Bulleted list (Ctrl+Shift+7)", Block.BULLET);
        addBlock(toolbar, "1.", "Numbered list (Ctrl+Shift+8)", Block.NUMBERED);
        Button undo = button("↶", "Undo (Ctrl+Z)", area::undo);
        Button redo = button("↷", "Redo (Ctrl+Y)", area::redo);
        undo.disableProperty().bind(area.undoAvailableProperty().map(available -> !available));
        redo.disableProperty().bind(area.redoAvailableProperty().map(available -> !available));
        toolbar.getChildren().addAll(undo, redo);
        return toolbar;
    }

    private void addMark(FlowPane toolbar, String text, String label, int mark) {
        ToggleButton button = toggle(text, label, () -> toggleMark(mark));
        markButtons.put(mark, button);
        toolbar.getChildren().add(button);
    }
    private void addBlock(FlowPane toolbar, String text, String label, Block block) {
        ToggleButton button = toggle(text, label, () -> formatBlock(block));
        blockButtons.put(block, button);
        toolbar.getChildren().add(button);
    }
    private ToggleButton toggle(String text, String label, Runnable action) {
        ToggleButton button = new ToggleButton(text);
        configureButton(button, label, action);
        return button;
    }
    private Button button(String text, String label, Runnable action) {
        Button button = new Button(text);
        configureButton(button, label, action);
        return button;
    }
    private void configureButton(ButtonBase button, String label, Runnable action) {
        button.setAccessibleText(label);
        button.setTooltip(new Tooltip(label));
        button.setFocusTraversable(false);
        button.getStyleClass().add("note-format-button");
        button.setOnAction(event -> { action.run(); area.requestFocus(); });
    }
    private static void addMenu(ContextMenu menu, String label, Runnable action) {
        MenuItem item = new MenuItem(label);
        item.setOnAction(event -> action.run());
        menu.getItems().add(item);
    }

    public void toggleMark(int mark) {
        var selection = area.getSelection();
        if (selection.getLength() == 0) {
            int current = area.getTextStyleForInsertionAt(area.getCaretPosition());
            area.setTextInsertionStyle(current ^ mark);
        } else {
            var spans = area.getStyleSpans(selection);
            boolean remove = spans.styleStream().allMatch(style -> (style & mark) != 0);
            area.setStyleSpans(selection.getStart(), spans.mapStyles(style -> remove ? style & ~mark : style | mark));
        }
        refreshToolbar();
    }

    public void formatBlock(Block block) {
        var selection = area.getSelection();
        int first = area.offsetToPosition(selection.getStart(), TwoDimensional.Bias.Forward).getMajor();
        int last = selection.getLength() == 0 ? first
                : area.offsetToPosition(selection.getEnd(), TwoDimensional.Bias.Backward).getMajor();
        for (int i = first; i <= last; i++) area.setParagraphStyle(i, block);
        refreshToolbar();
    }

    private Label listMarker(int index) {
        Label marker = new Label();
        marker.getStyleClass().add("note-list-marker");
        marker.setAlignment(Pos.TOP_RIGHT);
        marker.textProperty().bind(Bindings.createStringBinding(() -> {
            if (index >= area.getParagraphs().size()) return "";
            Block block = area.getParagraphs().get(index).getParagraphStyle();
            if (block == Block.BULLET) return "•";
            if (block != Block.NUMBERED) return "";
            int first = index;
            while (first > 0 && area.getParagraphs().get(first - 1).getParagraphStyle() == Block.NUMBERED) first--;
            return (index - first + 1) + ".";
        }, area.getParagraphs()));
        return marker;
    }

    private void shortcut(KeyEvent event) {
        if (!area.isEditable()) return;
        if (event.isShortcutDown() && !event.isAltDown()) {
            if (!event.isShiftDown()) {
                Integer mark = switch (event.getCode()) {
                    case B -> NoteDocument.BOLD; case I -> NoteDocument.ITALIC;
                    case U -> NoteDocument.UNDERLINE; case E -> NoteDocument.CODE; default -> null;
                };
                if (mark != null) { toggleMark(mark); event.consume(); }
            } else if (event.getCode() == KeyCode.DIGIT7 || event.getCode() == KeyCode.DIGIT8) {
                formatBlock(event.getCode() == KeyCode.DIGIT7 ? Block.BULLET : Block.NUMBERED);
                event.consume();
            }
        } else if (event.isShortcutDown() && event.isAltDown()) {
            Block block = switch (event.getCode()) {
                case DIGIT0 -> Block.NORMAL; case DIGIT1 -> Block.HEADING_1; case DIGIT2 -> Block.HEADING_2;
                default -> null;
            };
            if (block != null) { formatBlock(block); event.consume(); }
        } else if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
            var paragraph = area.getParagraphs().get(area.getCurrentParagraph());
            Block block = paragraph.getParagraphStyle();
            if (block == Block.HEADING_1 || block == Block.HEADING_2) {
                // Insert the paragraph break and its styles as one undoable document change.
                var selection = area.getSelection();
                var end = area.offsetToPosition(selection.getEnd(), TwoDimensional.Bias.Forward);
                var tail = area.getParagraphs().get(end.getMajor()).subSequence(end.getMinor());
                var builder = new ReadOnlyStyledDocumentBuilder<Block, String, Integer>(SegmentOps.styledTextOps(), block)
                        .addParagraph("", area.getTextStyleForInsertionAt(selection.getStart()), block);
                if (tail.length() == 0) builder.addParagraph("", 0, Block.NORMAL);
                else builder.addParagraph(tail.getStyledSegments(), Block.NORMAL);
                // Include the remaining text so an empty insertion cannot inherit its heading style.
                area.replace(selection.getStart(), selection.getEnd() + tail.length(), builder.build());
                area.moveTo(selection.getStart() + 1);
                area.setTextInsertionStyle(0);
                area.requestFollowCaret();
                event.consume();
            } else if (paragraph.length() == 0 && (paragraph.getParagraphStyle() == Block.BULLET
                    || paragraph.getParagraphStyle() == Block.NUMBERED)) {
                formatBlock(Block.NORMAL);
                event.consume();
            }
        }
    }

    private void refreshToolbar() {
        int marks = area.getTextStyleForInsertionAt(area.getCaretPosition());
        markButtons.forEach((mark, button) -> button.setSelected((marks & mark) != 0));
        Block block = area.getParagraphs().get(area.getCurrentParagraph()).getParagraphStyle();
        blockButtons.forEach((style, button) -> button.setSelected(style == block));
    }

    public NoteDocument document() {
        return new NoteDocument(area.getParagraphs().stream().map(paragraph -> new Paragraph(
                paragraph.getParagraphStyle(), paragraph.getStyledSegments().stream()
                .map(segment -> new Span(segment.getSegment(), segment.getStyle())).toList())).toList());
    }

    public void restore(NoteDocument document) {
        restoring = true;
        try {
            var builder = new ReadOnlyStyledDocumentBuilder<Block, String, Integer>(SegmentOps.styledTextOps(), Block.NORMAL);
            for (Paragraph paragraph : document.paragraphs()) {
                builder.addParagraph(paragraph.spans().stream()
                        .map(span -> new StyledSegment<>(span.text(), span.marks())).toList(), paragraph.block());
            }
            area.replace(builder.build());
            area.getUndoManager().forgetHistory();
            area.setTextInsertionStyle(null);
            area.moveTo(0);
            refreshToolbar();
        } finally { restoring = false; }
    }

    public void setOnChanged(Consumer<NoteDocument> listener) { onChanged = listener; }
    public StyledTextArea<Block, Integer> area() { return area; }
    public void focus() { area.requestFocus(); area.requestFollowCaret(); }
    public void dispose() { changes.unsubscribe(); area.dispose(); }
}
