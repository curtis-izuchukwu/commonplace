package com.commonplace.ui;

import com.commonplace.model.DifficultyLevel;
import com.commonplace.model.ImportanceLevel;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class LevelUi {

    private static final String LEVEL_LOW = "level-low";
    private static final String LEVEL_MEDIUM = "level-medium";
    private static final String LEVEL_HIGH = "level-high";

    private LevelUi() {
        // Utility class
    }

    public static <T> void applyLevelBarStyling(ComboBox<T> comboBox) {
        comboBox.setCellFactory(listView -> createLevelCell());
        comboBox.setButtonCell(createLevelCell());
    }

    public static Node createDifficultyIndicator(DifficultyLevel difficulty) {
        HBox indicator = new HBox(8);
        indicator.setAlignment(Pos.CENTER_LEFT);
        indicator.getStyleClass().add("difficulty-indicator");

        Label caption = new Label("Difficulty");
        caption.getStyleClass().add("indicator-label");

        HBox bars = new HBox(3);
        bars.setAlignment(Pos.CENTER_LEFT);

        int filledBars = difficultyBars(difficulty);
        String levelClass = levelClass(difficulty);

        for (int i = 1; i <= 3; i++) {
            Region bar = new Region();
            bar.getStyleClass().add("difficulty-bar");

            if (i <= filledBars) {
                bar.getStyleClass().add(levelClass);
            } else {
                bar.getStyleClass().add("level-empty");
            }

            bars.getChildren().add(bar);
        }

        Label valueLabel = new Label(titleCase(difficulty == null ? "Medium" : difficulty.name()));
        valueLabel.getStyleClass().add("indicator-value");

        indicator.getChildren().addAll(caption, bars, valueLabel);
        UiAnimations.animateDifficultyIndicator(indicator);
        return indicator;
    }

    public static Node createPriorityChip(ImportanceLevel importance) {
        Label chip =
                new Label(
                        "Priority  "
                                + displayName(importance == null ? "Medium" : importance.name()));
        chip.getStyleClass().addAll("priority-chip", levelClass(importance));
        UiAnimations.popIn(chip);
        return chip;
    }

    public static Node createStatCell(String caption, String value) {
        return createStatCell(caption, value, false);
    }

    public static Node createStatCell(String caption, String value, boolean emphasized) {
        Label label = new Label(caption == null ? "" : caption.toUpperCase());
        label.getStyleClass().add("record-stat-label");
        Label amount = new Label(value == null || value.isBlank() ? "—" : value.trim());
        amount.getStyleClass().add("record-stat-value");
        VBox cell = new VBox(2, label, amount);
        cell.getStyleClass().add("record-stat-cell");
        if (emphasized) cell.getStyleClass().add("record-emphasis");
        return cell;
    }

    public static Node createPriorityStat(ImportanceLevel importance) {
        ImportanceLevel resolved = importance == null ? ImportanceLevel.MEDIUM : importance;
        return createStatCell("Priority", displayName(resolved), resolved == ImportanceLevel.HIGH);
    }

    public static Node createMasteryStatCell(double percentage) {
        double mastery = Double.isFinite(percentage) ? Math.max(0, Math.min(100, percentage)) : 0;

        Region fill = new Region();
        fill.getStyleClass().add("mastery-stat-fill");
        fill.setMinWidth(0);
        fill.setMaxWidth(Region.USE_PREF_SIZE);
        fill.setMaxHeight(Double.MAX_VALUE);

        Label label = new Label("MASTERY");
        label.getStyleClass().add("record-stat-label");
        Label amount = new Label(String.format("%.0f%%", mastery));
        amount.getStyleClass().add("record-stat-value");
        VBox content = new VBox(2, label, amount);
        content.getStyleClass().add("mastery-stat-content");
        content.setMouseTransparent(true);

        StackPane cell = new StackPane(fill, content);
        cell.getStyleClass().addAll("record-stat-cell", "mastery-stat-cell");
        cell.setAlignment(Pos.CENTER_LEFT);
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);
        StackPane.setAlignment(content, Pos.CENTER_LEFT);
        fill.prefWidthProperty().bind(cell.widthProperty().multiply(mastery / 100.0));
        cell.setAccessibleText(String.format("Mastery %.0f%%", mastery));
        return cell;
    }

    public static Node createStatusBadge(String value) {
        Label badge = new Label(value == null || value.isBlank() ? "Status" : value.trim());
        badge.getStyleClass().add("status-badge");
        String normalized = badge.getText().toLowerCase();
        if (normalized.contains("due")
                || normalized.contains("weak")
                || normalized.contains("fading")) {
            badge.getStyleClass().add("status-badge-attention");
        } else if (normalized.contains("exam") || normalized.contains("priority")) {
            badge.getStyleClass().add("status-badge-accent");
        }
        return badge;
    }

    public static Node createLedgerRow(String caption, String value) {
        return createLedgerRow(caption, value, false);
    }

    public static Node createLedgerRow(String caption, String value, boolean emphasized) {
        Label label = new Label(caption == null ? "" : caption);
        label.getStyleClass().add("record-ledger-label");
        Label amount = new Label(value == null || value.isBlank() ? "—" : value.trim());
        amount.getStyleClass().add("record-ledger-value");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(10, label, spacer, amount);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("record-ledger-row");
        if (emphasized) amount.getStyleClass().add("record-ledger-value-accent");
        return row;
    }

    public static String displayName(Object value) {
        return titleCase(value == null ? "" : value.toString().replace('_', ' '));
    }

    private static <T> ListCell<T> createLevelCell() {
        return new ListCell<>() {
            private final Region bar = new Region();
            private final Label label = new Label();
            private final HBox content = new HBox(9, bar, label);

            {
                content.setAlignment(Pos.CENTER_LEFT);
                content.getStyleClass().add("level-combo-cell");
                bar.getStyleClass().add("level-combo-bar");
                label.getStyleClass().add("level-combo-label");
            }

            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                resetLevelClass(bar);
                bar.getStyleClass().add(levelClass(item));
                label.setText(item.toString());

                setText(null);
                setGraphic(content);
            }
        };
    }

    private static int difficultyBars(DifficultyLevel difficulty) {
        if (difficulty == null) {
            return 2;
        }

        return switch (difficulty) {
            case EASY -> 1;
            case MEDIUM -> 2;
            case HARD -> 3;
        };
    }

    private static String levelClass(Object value) {
        if (value == null) {
            return LEVEL_MEDIUM;
        }

        return switch (value.toString()) {
            case "LOW", "EASY" -> LEVEL_LOW;
            case "HIGH", "HARD" -> LEVEL_HIGH;
            default -> LEVEL_MEDIUM;
        };
    }

    private static void resetLevelClass(Node node) {
        node.getStyleClass().removeAll(LEVEL_LOW, LEVEL_MEDIUM, LEVEL_HIGH);
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String lower = value.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
