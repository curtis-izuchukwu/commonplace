package com.pararepilot.ui;

import com.pararepilot.model.DifficultyLevel;
import com.pararepilot.model.ImportanceLevel;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

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
        Label chip = new Label("Priority: " + titleCase(importance == null ? "Medium" : importance.name()));
        chip.getStyleClass().addAll("priority-chip", levelClass(importance));
        UiAnimations.popIn(chip);
        return chip;
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
