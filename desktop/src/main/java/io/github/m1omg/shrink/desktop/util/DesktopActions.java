package io.github.m1omg.shrink.desktop.util;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Opening files, folders and web pages with the system's default applications. */
public final class DesktopActions {

    private DesktopActions() {
    }

    /** Opens a file with its default application (e.g. the video player). */
    public static void open(Path file) {
        if (Platform.isLinux()) {
            if (run("xdg-open", file.toString())) {
                return;
            }
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file.toFile());
                return;
            }
        } catch (IOException | IllegalArgumentException e) {
            // fall through to the command line
        }
        if (Platform.isMac()) {
            run("open", file.toString());
        } else if (Platform.isWindows()) {
            run("cmd", "/c", "start", "", file.toString());
        }
    }

    /** Shows a file selected in Finder / the file manager / Explorer. */
    public static void reveal(Path file) {
        File f = file.toFile();
        switch (Platform.os()) {
            case MAC:
                if (run("open", "-R", f.getAbsolutePath())) {
                    return;
                }
                break;
            case WINDOWS:
                if (run("explorer", "/select,", f.getAbsolutePath())) {
                    return;
                }
                break;
            default:
                // The FileManager1 D-Bus interface selects the file in Nautilus, Dolphin, Nemo, ...
                if (run("dbus-send", "--session", "--print-reply", "--dest=org.freedesktop.FileManager1",
                        "--type=method_call", "/org/freedesktop/FileManager1",
                        "org.freedesktop.FileManager1.ShowItems",
                        "array:string:" + f.toURI().toString(), "string:")) {
                    return;
                }
                break;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                Desktop.getDesktop().browseFileDirectory(f);
                return;
            }
        } catch (UnsupportedOperationException | SecurityException e) {
            // fall back to opening the folder
        }
        File parent = f.getParentFile();
        if (parent != null) {
            open(parent.toPath());
        }
    }

    public static void browse(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (IOException | IllegalArgumentException e) {
            // fall through
        }
        if (Platform.isLinux()) {
            run("xdg-open", url);
        } else if (Platform.isMac()) {
            run("open", url);
        }
    }

    /** Starts a command and waits briefly; true if it started and did not fail quickly. */
    private static boolean run(String... cmd) {
        List<String> command = Arrays.asList(cmd);
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                return p.exitValue() == 0 || command.get(0).equals("explorer");
            }
            return true; // still running (e.g. a file manager starting): assume success
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
