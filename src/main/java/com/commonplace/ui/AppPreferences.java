package com.commonplace.ui;

import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.util.List;
import java.util.Locale;

import com.commonplace.model.UserSettings;

import javafx.scene.Parent;
import javafx.scene.Scene;

public final class AppPreferences {

    private static final List<String> STYLE_CLASSES = List.of(
            "theme-dark",
            "theme-light",
            "accent-cyan",
            "accent-blue",
            "accent-mint",
            "accent-rose",
            "accent-brass",
            "accent-graphite",
            "accent-forest",
            "accent-burgundy",
            "reduce-motion",
            "compact-layout",
            "font-small",
            "font-large",
            "high-contrast",
            "larger-controls",
            "keyboard-hints",
            "screen-reader-labels"
    );

    private AppPreferences() {
        // Utility class
    }

    public static void apply(Scene scene, UserSettings settings) {
        if (scene == null || scene.getRoot() == null || settings == null) {
            return;
        }

        apply(scene.getRoot(), settings);
    }

    public static void apply(Parent root, UserSettings settings) {
        if (root == null || settings == null) {
            return;
        }

        root.getStyleClass().removeAll(STYLE_CLASSES);
        root.getStyleClass().add(themeClass(settings.theme()));
        root.getStyleClass().add(accentClass(settings.accentColor()));

        if (settings.reduceMotion()) {
            root.getStyleClass().add("reduce-motion");
        }

        if (settings.compactLayout()) {
            root.getStyleClass().add("compact-layout");
        }

        if ("SMALL".equalsIgnoreCase(settings.fontSize())) {
            root.getStyleClass().add("font-small");
        } else if ("LARGE".equalsIgnoreCase(settings.fontSize())) {
            root.getStyleClass().add("font-large");
        }

        if (settings.higherContrast()) {
            root.getStyleClass().add("high-contrast");
        }

        if (settings.largerControls()) {
            root.getStyleClass().add("larger-controls");
        }

        if (settings.keyboardHintsEnabled()) {
            root.getStyleClass().add("keyboard-hints");
        }

        if (settings.screenReaderLabelsEnabled()) {
            root.getStyleClass().add("screen-reader-labels");
            root.setAccessibleText("Commonplace study tracker");
        } else {
            root.setAccessibleText(null);
        }

        UiAnimations.installGlobalAnimations(root);
    }

    private static String themeClass(String theme) {
        if ("LIGHT".equalsIgnoreCase(theme)) {
            return "theme-light";
        }

        if ("SYSTEM".equalsIgnoreCase(theme) && systemPrefersLightTheme()) {
            return "theme-light";
        }

        return "theme-dark";
    }

    private static boolean systemPrefersLightTheme() {
        try {
            Object darkMode = Toolkit.getDefaultToolkit()
                    .getDesktopProperty("win.darkMode");

            if (darkMode instanceof Boolean enabled) {
                return !enabled;
            }

            Object lightTheme = Toolkit.getDefaultToolkit()
                    .getDesktopProperty("win.lightTheme");

            return lightTheme instanceof Boolean enabled && enabled;
        } catch (HeadlessException | SecurityException e) {
            return false;
        }
    }

    private static String accentClass(String accentColor) {
        return "accent-" + accentDisplayName(accentColor).toLowerCase(Locale.ROOT);
    }

    /** Accepts saved legacy accents without requiring a database migration. */
    public static String accentDisplayName(String accentColor) {
        if (accentColor == null) {
            return "Brass";
        }
        return switch (accentColor.toUpperCase(Locale.ROOT)) {
            case "BLUE", "GRAPHITE" -> "Graphite";
            case "MINT", "FOREST" -> "Forest";
            case "ROSE", "BURGUNDY" -> "Burgundy";
            default -> "Brass";
        };
    }
}
