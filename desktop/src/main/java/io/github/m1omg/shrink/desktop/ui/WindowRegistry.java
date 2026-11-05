package io.github.m1omg.shrink.desktop.ui;

/** Remembers the current main window (it is re-created when the language changes). */
public final class WindowRegistry {

    private static volatile MainWindow current;

    private WindowRegistry() {
    }

    public static MainWindow current() {
        return current;
    }

    public static void set(MainWindow window) {
        current = window;
    }
}
