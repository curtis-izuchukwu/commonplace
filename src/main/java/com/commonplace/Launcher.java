package com.commonplace;

/**
 * Plain Java entry point for native packaging.
 *
 * <p>The Java launcher treats a main class that extends JavaFX Application
 * specially and can report that JavaFX is missing when it is supplied on the
 * packaged classpath. Keeping the native launcher separate avoids that check.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Main.main(args);
    }
}
