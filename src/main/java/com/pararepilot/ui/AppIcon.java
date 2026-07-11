package com.pararepilot.ui;

import java.awt.Taskbar;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class AppIcon {

    public static final String PNG_RESOURCE = "/com/pararepilot/assets/parare_pilot_icon.png";
    public static final String WINDOWS_ICON_RESOURCE = "/com/pararepilot/assets/parare_pilot_icon.ico";
    public static final String MAC_ICON_RESOURCE = "/com/pararepilot/assets/parare_pilot_icon.icns";

    private static final String TASKBAR_ICON_RESOURCE =
            "/com/pararepilot/assets/parare_pilot_icon_256.png";

    private static final String[] WINDOW_ICON_RESOURCES = {
            "/com/pararepilot/assets/parare_pilot_icon_16.png",
            "/com/pararepilot/assets/parare_pilot_icon_24.png",
            "/com/pararepilot/assets/parare_pilot_icon_32.png",
            "/com/pararepilot/assets/parare_pilot_icon_48.png",
            "/com/pararepilot/assets/parare_pilot_icon_64.png",
            "/com/pararepilot/assets/parare_pilot_icon_128.png",
            "/com/pararepilot/assets/parare_pilot_icon_256.png"
    };

    private static List<Image> javafxIcons;
    private static BufferedImage awtIcon;
    private static boolean attemptedTaskbarIcon;

    private AppIcon() {
        // Utility class
    }

    public static void applyRuntimeIcons(Stage stage) {
        applyTo(stage);
        applyToTaskbar();
    }

    public static void applyTo(Stage stage) {
        List<Image> icons = images();

        if (stage != null && !icons.isEmpty()) {
            stage.getIcons().setAll(icons);
        }
    }

    public static void applyTo(Window window) {
        if (window instanceof Stage stage) {
            applyTo(stage);
        }
    }

    public static void applyTo(Dialog<?> dialog) {
        if (dialog == null) {
            return;
        }

        applyToDialogWindow(dialog);

        var existingOnShown = dialog.getOnShown();
        dialog.setOnShown(event -> {
            if (existingOnShown != null) {
                existingOnShown.handle(event);
            }

            applyToDialogWindow(dialog);
        });
    }

    public static Image image() {
        List<Image> icons = images();
        return icons.isEmpty() ? null : icons.get(icons.size() - 1);
    }

    public static void applyToTaskbar() {
        if (attemptedTaskbarIcon) {
            return;
        }

        attemptedTaskbarIcon = true;

        try {
            if (!Taskbar.isTaskbarSupported()) {
                return;
            }

            Taskbar taskbar = Taskbar.getTaskbar();

            if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                return;
            }

            BufferedImage image = awtImage();

            if (image != null) {
                taskbar.setIconImage(image);
            }

        } catch (IOException | LinkageError | RuntimeException ignored) {
            // Some desktop environments do not expose a taskbar/dock icon API.
        }
    }

    private static void applyToDialogWindow(Dialog<?> dialog) {
        Scene scene = dialog.getDialogPane().getScene();

        if (scene == null) {
            return;
        }

        applyTo(scene.getWindow());
    }

    private static BufferedImage awtImage() throws IOException {
        if (awtIcon != null) {
            return awtIcon;
        }

        URL url = iconUrl(TASKBAR_ICON_RESOURCE);

        if (url == null) {
            url = iconUrl(PNG_RESOURCE);
        }

        if (url == null) {
            return null;
        }

        awtIcon = ImageIO.read(url);
        return awtIcon;
    }

    private static List<Image> images() {
        if (javafxIcons != null) {
            return javafxIcons;
        }

        List<Image> icons = new ArrayList<>();

        for (String resource : WINDOW_ICON_RESOURCES) {
            URL url = iconUrl(resource);

            if (url == null) {
                continue;
            }

            Image icon = new Image(url.toExternalForm(), false);

            if (!icon.isError()) {
                icons.add(icon);
            }
        }

        if (icons.isEmpty()) {
            URL url = iconUrl(PNG_RESOURCE);

            if (url != null) {
                Image icon = new Image(url.toExternalForm(), false);

                if (!icon.isError()) {
                    icons.add(icon);
                }
            }
        }

        javafxIcons = List.copyOf(icons);
        return javafxIcons;
    }

    private static URL iconUrl(String resource) {
        return AppIcon.class.getResource(resource);
    }
}
