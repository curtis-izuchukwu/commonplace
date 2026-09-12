package com.commonplace.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeType;
import javafx.stage.Stage;
import javafx.stage.Window;

public class AppChrome extends StackPane {

    private static final double RESIZE_BORDER = 7;

    private final Stage stage;
    private final StackPane frame = new StackPane();
    private final BorderPane shell = new BorderPane();
    private final Rectangle shellClip = new Rectangle();
    private final Label breadcrumbLabel = new Label("Dashboard");
    private final Label dailyChipLabel = new Label("");
    private final Button maximizeButton = new Button();
    private final List<Command> commands = new ArrayList<>();

    private double dragOffsetX;
    private double dragOffsetY;
    private ResizeMode resizeMode = ResizeMode.NONE;
    private double resizeStartScreenX;
    private double resizeStartScreenY;
    private double resizeStartStageX;
    private double resizeStartStageY;
    private double resizeStartWidth;
    private double resizeStartHeight;
    private StackPane commandOverlay;
    private TextField commandSearchField;
    private VBox commandResultsBox;
    private List<Command> visibleCommands = List.of();

    private AppChrome(Stage stage, Parent content) {
        this.stage = stage;
        getStyleClass().addAll("app-chrome-root", "app-root");
        frame.getStyleClass().add("app-chrome-frame");
        shell.getStyleClass().add("app-chrome-shell");
        shell.setTop(createTitleBar());
        shell.setCenter(content);
        shell.setClip(shellClip);
        frame.getChildren().add(shell);
        getChildren().add(frame);
        shell.layoutBoundsProperty().addListener((observable, oldValue, newValue) -> updateShellClip());
        installResizeHandlers();

        stage.maximizedProperty().addListener((observable, oldValue, maximized) -> {
            maximizeButton.setTooltip(new Tooltip(maximized ? "Restore" : "Maximize"));
            setMaximizedStyle(maximized);
        });
        setMaximizedStyle(stage.isMaximized());
    }

    public static AppChrome create(Stage stage, Parent content) {
        return new AppChrome(stage, content);
    }

    private void setMaximizedStyle(boolean maximized) {
        getStyleClass().remove("app-chrome-maximized");

        if (maximized) {
            getStyleClass().add("app-chrome-maximized");
        }

        updateShellClip();
    }

    private void updateShellClip() {
        shellClip.setWidth(shell.getLayoutBounds().getWidth());
        shellClip.setHeight(shell.getLayoutBounds().getHeight());

        double arc = getStyleClass().contains("app-chrome-maximized") ? 0 : 26;
        shellClip.setArcWidth(arc);
        shellClip.setArcHeight(arc);
    }

    public static void setContent(Scene scene, Parent content) {
        if (scene == null || content == null) {
            return;
        }

        if (scene.getRoot() instanceof AppChrome chrome) {
            chrome.shell.setCenter(content);
            UiAnimations.installGlobalAnimations(content);
            return;
        }

        Window window = scene.getWindow();

        if (window instanceof Stage stage) {
            scene.setRoot(new AppChrome(stage, content));
            UiAnimations.installGlobalAnimations(scene.getRoot());
        } else {
            scene.setRoot(content);
            UiAnimations.installGlobalAnimations(content);
        }
    }

    public static void setBreadcrumb(Scene scene, String breadcrumb) {
        AppChrome chrome = from(scene);

        if (chrome != null) {
            chrome.breadcrumbLabel.setText(
                    breadcrumb == null || breadcrumb.isBlank() ? "Commonplace" : breadcrumb
            );
        }
    }

    public static void setDailyChip(Scene scene, String text) {
        AppChrome chrome = from(scene);

        if (chrome == null) {
            return;
        }

        boolean visible = text != null && !text.isBlank();
        chrome.dailyChipLabel.setText(visible ? text : "");
        chrome.dailyChipLabel.setVisible(visible);
        chrome.dailyChipLabel.setManaged(visible);
    }

    public static void setCommands(Scene scene, List<Command> commands) {
        AppChrome chrome = from(scene);

        if (chrome == null) {
            return;
        }

        chrome.commands.clear();
        chrome.commands.addAll(commands == null ? List.of() : commands);

        if (chrome.commandOverlay != null) {
            chrome.updateCommandResults();
        }
    }

    private static AppChrome from(Scene scene) {
        if (scene != null && scene.getRoot() instanceof AppChrome chrome) {
            return chrome;
        }

        return null;
    }

    private HBox createTitleBar() {
        HBox titleBar = new HBox(10);
        titleBar.getStyleClass().add("app-chrome-title-bar");
        titleBar.setAlignment(Pos.CENTER_LEFT);

        ImageView brandImage = new ImageView(AppIcon.titleBarImage());
        brandImage.setFitWidth(24);
        brandImage.setFitHeight(24);
        brandImage.setPreserveRatio(true);
        brandImage.setSmooth(true);

        StackPane brandMark = new StackPane(brandImage);
        brandMark.getStyleClass().add("app-chrome-icon");
        brandMark.setMinSize(28, 28);
        brandMark.setPrefSize(28, 28);
        brandMark.setMaxSize(28, 28);
        brandMark.setAccessibleText("Commonplace");
        brandMark.setMouseTransparent(true);

        VBox titleText = new VBox(0);
        titleText.getStyleClass().add("app-chrome-title-text");
        titleText.setAlignment(Pos.CENTER_LEFT);

        Label wordmark = new Label("Commonplace");
        wordmark.getStyleClass().add("app-chrome-wordmark");
        configureTitleLabel(wordmark);

        breadcrumbLabel.getStyleClass().add("app-chrome-breadcrumb");
        configureTitleLabel(breadcrumbLabel);
        titleText.getChildren().addAll(wordmark, breadcrumbLabel);

        Region dragRegion = new Region();
        HBox.setHgrow(dragRegion, Priority.ALWAYS);

        dailyChipLabel.getStyleClass().add("app-chrome-chip");
        dailyChipLabel.setVisible(false);
        dailyChipLabel.setManaged(false);

        Button commandButton = new Button("Search");
        commandButton.getStyleClass().add("app-chrome-command-button");
        commandButton.setTooltip(new Tooltip("Quick search"));
        commandButton.setFocusTraversable(true);
        commandButton.setOnAction(event -> openCommandPalette());

        maximizeButton.setGraphic(createMaximizeIcon());
        maximizeButton.getStyleClass().addAll("app-chrome-window-button", "app-chrome-maximize-button");
        maximizeButton.setAccessibleText("Maximize or restore");
        maximizeButton.setTooltip(new Tooltip("Maximize"));
        maximizeButton.setFocusTraversable(false);
        maximizeButton.setOnAction(event -> stage.setMaximized(!stage.isMaximized()));

        HBox controls = new HBox(1);
        controls.getStyleClass().add("app-chrome-window-controls");
        controls.getChildren().addAll(
                createWindowButton(createMinimizeIcon(), "Minimize", () -> stage.setIconified(true)),
                maximizeButton,
                createWindowButton(createCloseIcon(), "Close", stage::close)
        );

        HBox utilities = new HBox(6, dailyChipLabel, commandButton);
        utilities.getStyleClass().add("app-chrome-utilities");
        utilities.setAlignment(Pos.CENTER_LEFT);

        titleBar.getChildren().addAll(
                brandMark,
                titleText,
                dragRegion,
                utilities,
                controls
        );

        installDragHandlers(titleBar);
        installDragHandlers(dragRegion);
        installDragHandlers(titleText);

        return titleBar;
    }

    private void openCommandPalette() {
        if (commandOverlay != null) {
            Platform.runLater(this::focusCommandSearch);
            return;
        }

        commandSearchField = new TextField();
        commandSearchField.getStyleClass().add("command-search-field");
        commandSearchField.setPromptText("Search pages and actions");

        Label title = new Label("Quick navigation");
        title.getStyleClass().add("command-palette-title");

        Label hint = new Label("Type to filter. Press Enter to open the first result.");
        hint.getStyleClass().add("command-palette-hint");
        hint.setWrapText(true);

        commandResultsBox = new VBox(8);
        commandResultsBox.getStyleClass().add("command-results");

        ScrollPane resultsPane = new ScrollPane(commandResultsBox);
        resultsPane.getStyleClass().add("command-results-scroll");
        resultsPane.setFitToWidth(true);
        resultsPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        resultsPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        resultsPane.setPrefViewportHeight(360);
        resultsPane.setMaxHeight(360);

        VBox content = new VBox(12, title, commandSearchField, hint, resultsPane);
        content.getStyleClass().add("command-palette");
        content.setMaxWidth(560);
        content.setMaxHeight(560);

        StackPane scrim = new StackPane(content);
        scrim.getStyleClass().addAll("overlay-scrim", "command-palette-scrim");
        scrim.setOnMouseClicked(event -> {
            if (event.getTarget() == scrim) {
                closeCommandPalette();
            }
        });

        commandOverlay = scrim;
        getChildren().add(scrim);

        commandSearchField.textProperty().addListener((observable, oldValue, newValue) -> updateCommandResults());
        commandSearchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                closeCommandPalette();
                event.consume();
                return;
            }

            if (event.getCode() == KeyCode.ENTER && !visibleCommands.isEmpty()) {
                runCommand(visibleCommands.getFirst());
                event.consume();
            }
        });

        updateCommandResults();
        UiAnimations.animateOverlayOpen(scrim, content);
        Platform.runLater(this::focusCommandSearch);
    }

    private void focusCommandSearch() {
        if (commandSearchField != null) {
            commandSearchField.requestFocus();
        }
    }

    private void updateCommandResults() {
        if (commandOverlay == null || commandSearchField == null) {
            return;
        }

        if (commandResultsBox == null) {
            return;
        }

        String query = commandSearchField.getText();
        visibleCommands = commands.stream()
                .filter(command -> command.matches(query))
                .limit(8)
                .toList();

        commandResultsBox.getChildren().clear();

        if (commands.isEmpty()) {
            commandResultsBox.getChildren().add(createCommandEmptyLabel("No commands are available on this screen."));
            return;
        }

        if (visibleCommands.isEmpty()) {
            commandResultsBox.getChildren().add(createCommandEmptyLabel("No matching commands."));
            return;
        }

        for (Command command : visibleCommands) {
            commandResultsBox.getChildren().add(createCommandRow(command));
        }
    }

    private Label createCommandEmptyLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("command-empty");
        label.setWrapText(true);
        return label;
    }

    private Button createCommandRow(Command command) {
        Label title = new Label(command.title());
        title.getStyleClass().add("command-result-title");
        title.setWrapText(true);

        Label description = new Label(command.description());
        description.getStyleClass().add("command-result-description");
        description.setWrapText(true);
        description.setVisible(!command.description().isBlank());
        description.setManaged(!command.description().isBlank());

        VBox text = new VBox(3, title, description);
        text.setAlignment(Pos.CENTER_LEFT);

        Button row = new Button();
        row.getStyleClass().add("command-result-row");
        row.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        row.setGraphic(text);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setMinHeight(64);
        row.setPrefHeight(64);
        row.setMaxHeight(64);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setFocusTraversable(false);
        row.setOnAction(event -> runCommand(command));

        return row;
    }

    private void runCommand(Command command) {
        closeCommandPalette();
        Platform.runLater(() -> {
            try {
                command.action().run();
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        });
    }

    private void closeCommandPalette() {
        if (commandOverlay == null) {
            return;
        }

        StackPane overlay = commandOverlay;
        commandOverlay = null;
        commandSearchField = null;
        commandResultsBox = null;
        visibleCommands = List.of();
        UiAnimations.animateOverlayClose(overlay, () -> getChildren().remove(overlay));
    }

    private void configureTitleLabel(Label label) {
        label.setWrapText(false);
        label.setTextOverrun(OverrunStyle.ELLIPSIS);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setMaxHeight(Region.USE_PREF_SIZE);
    }

    private Button createWindowButton(Node icon, String accessibleText, Runnable action) {
        Button button = new Button();
        button.setGraphic(icon);
        button.getStyleClass().add("app-chrome-window-button");
        button.setAccessibleText(accessibleText);
        button.setTooltip(new Tooltip(accessibleText));
        button.setFocusTraversable(false);
        button.setOnAction(event -> action.run());

        if ("Close".equals(accessibleText)) {
            button.getStyleClass().add("app-chrome-close-button");
        }

        return button;
    }

    private Node createMinimizeIcon() {
        StackPane icon = new StackPane();
        icon.getStyleClass().add("window-control-icon");

        Line line = new Line(2, 7, 12, 7);
        line.getStyleClass().add("window-control-minimize-icon");
        line.setStrokeLineCap(StrokeLineCap.BUTT);
        icon.getChildren().add(line);
        icon.setMouseTransparent(true);

        return icon;
    }

    private Node createMaximizeIcon() {
        StackPane icon = new StackPane();
        icon.getStyleClass().add("window-control-icon");

        Rectangle box = new Rectangle(9, 9);
        box.getStyleClass().add("window-control-maximize-icon");
        box.setStrokeType(StrokeType.INSIDE);
        icon.getChildren().add(box);
        icon.setMouseTransparent(true);

        return icon;
    }

    private Node createCloseIcon() {
        StackPane icon = new StackPane();
        icon.getStyleClass().add("window-control-icon");

        Line firstLine = new Line(3, 3, 11, 11);
        firstLine.getStyleClass().add("window-control-close-line");
        firstLine.setStrokeLineCap(StrokeLineCap.BUTT);

        Line secondLine = new Line(11, 3, 3, 11);
        secondLine.getStyleClass().add("window-control-close-line");
        secondLine.setStrokeLineCap(StrokeLineCap.BUTT);

        icon.getChildren().addAll(firstLine, secondLine);
        icon.setMouseTransparent(true);

        return icon;
    }

    private void installDragHandlers(Node node) {
        node.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY || resizeMode != ResizeMode.NONE) {
                return;
            }

            dragOffsetX = event.getScreenX() - stage.getX();
            dragOffsetY = event.getScreenY() - stage.getY();
        });

        node.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            if (event.getButton() != MouseButton.PRIMARY
                    || resizeMode != ResizeMode.NONE
                    || stage.isMaximized()) {
                return;
            }

            stage.setX(event.getScreenX() - dragOffsetX);
            stage.setY(event.getScreenY() - dragOffsetY);
        });

        node.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                stage.setMaximized(!stage.isMaximized());
                event.consume();
            }
        });
    }

    private void installResizeHandlers() {
        addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
            if (stage.isMaximized()) {
                setCursor(Cursor.DEFAULT);
                return;
            }

            setCursor(detectResizeMode(event.getX(), event.getY()).cursor);
        });

        addEventFilter(MouseEvent.MOUSE_EXITED, event -> {
            if (resizeMode == ResizeMode.NONE) {
                setCursor(Cursor.DEFAULT);
            }
        });

        addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY || stage.isMaximized()) {
                return;
            }

            resizeMode = detectResizeMode(event.getX(), event.getY());

            if (resizeMode == ResizeMode.NONE) {
                return;
            }

            resizeStartScreenX = event.getScreenX();
            resizeStartScreenY = event.getScreenY();
            resizeStartStageX = stage.getX();
            resizeStartStageY = stage.getY();
            resizeStartWidth = stage.getWidth();
            resizeStartHeight = stage.getHeight();
            event.consume();
        });

        addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (resizeMode == ResizeMode.NONE || stage.isMaximized()) {
                return;
            }

            resizeWindow(event.getScreenX(), event.getScreenY());
            event.consume();
        });

        addEventFilter(MouseEvent.MOUSE_RELEASED, event -> resizeMode = ResizeMode.NONE);
    }

    private ResizeMode detectResizeMode(double x, double y) {
        boolean left = x <= RESIZE_BORDER;
        boolean right = x >= getWidth() - RESIZE_BORDER;
        boolean top = y <= RESIZE_BORDER;
        boolean bottom = y >= getHeight() - RESIZE_BORDER;

        if (top && left) {
            return ResizeMode.NORTH_WEST;
        }

        if (top && right) {
            return ResizeMode.NORTH_EAST;
        }

        if (bottom && left) {
            return ResizeMode.SOUTH_WEST;
        }

        if (bottom && right) {
            return ResizeMode.SOUTH_EAST;
        }

        if (left) {
            return ResizeMode.WEST;
        }

        if (right) {
            return ResizeMode.EAST;
        }

        if (top) {
            return ResizeMode.NORTH;
        }

        if (bottom) {
            return ResizeMode.SOUTH;
        }

        return ResizeMode.NONE;
    }

    private void resizeWindow(double screenX, double screenY) {
        double deltaX = screenX - resizeStartScreenX;
        double deltaY = screenY - resizeStartScreenY;

        if (resizeMode.east) {
            setStageWidth(resizeStartWidth + deltaX);
        }

        if (resizeMode.south) {
            setStageHeight(resizeStartHeight + deltaY);
        }

        if (resizeMode.west) {
            double requestedWidth = resizeStartWidth - deltaX;
            double minWidth = stage.getMinWidth() > 0 ? stage.getMinWidth() : 1;
            double newWidth = Math.max(minWidth, requestedWidth);
            stage.setX(resizeStartStageX + resizeStartWidth - newWidth);
            stage.setWidth(newWidth);
        }

        if (resizeMode.north) {
            double requestedHeight = resizeStartHeight - deltaY;
            double minHeight = stage.getMinHeight() > 0 ? stage.getMinHeight() : 1;
            double newHeight = Math.max(minHeight, requestedHeight);
            stage.setY(resizeStartStageY + resizeStartHeight - newHeight);
            stage.setHeight(newHeight);
        }
    }

    private void setStageWidth(double width) {
        double minWidth = stage.getMinWidth() > 0 ? stage.getMinWidth() : 1;
        stage.setWidth(Math.max(minWidth, width));
    }

    private void setStageHeight(double height) {
        double minHeight = stage.getMinHeight() > 0 ? stage.getMinHeight() : 1;
        stage.setHeight(Math.max(minHeight, height));
    }

    private enum ResizeMode {
        NONE(Cursor.DEFAULT, false, false, false, false),
        NORTH(Cursor.N_RESIZE, false, false, true, false),
        SOUTH(Cursor.S_RESIZE, false, false, false, true),
        EAST(Cursor.E_RESIZE, false, true, false, false),
        WEST(Cursor.W_RESIZE, true, false, false, false),
        NORTH_EAST(Cursor.NE_RESIZE, false, true, true, false),
        NORTH_WEST(Cursor.NW_RESIZE, true, false, true, false),
        SOUTH_EAST(Cursor.SE_RESIZE, false, true, false, true),
        SOUTH_WEST(Cursor.SW_RESIZE, true, false, false, true);

        private final Cursor cursor;
        private final boolean west;
        private final boolean east;
        private final boolean north;
        private final boolean south;

        ResizeMode(Cursor cursor, boolean west, boolean east, boolean north, boolean south) {
            this.cursor = cursor;
            this.west = west;
            this.east = east;
            this.north = north;
            this.south = south;
        }
    }

    public record Command(
            String title,
            String description,
            String keywords,
            Runnable action
    ) {
        public Command {
            title = clean(title);
            description = clean(description);
            keywords = clean(keywords);
            action = action == null ? () -> {
            } : action;
        }

        private boolean matches(String query) {
            String normalizedQuery = clean(query).toLowerCase(Locale.ROOT);

            if (normalizedQuery.isBlank()) {
                return true;
            }

            String haystack = (title + " " + description + " " + keywords).toLowerCase(Locale.ROOT);
            return haystack.contains(normalizedQuery);
        }

        private static String clean(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
