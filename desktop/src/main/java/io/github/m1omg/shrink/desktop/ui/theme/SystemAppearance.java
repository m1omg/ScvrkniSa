package io.github.m1omg.shrink.desktop.ui.theme;

import io.github.m1omg.shrink.desktop.util.Platform;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Reads the system's dark mode and accent colour, so Shrink! blends in.
 * Each check runs a tiny system command (e.g. {@code defaults} on macOS or
 * {@code gsettings} on GNOME) and falls back to light/blue when unknown.
 */
public final class SystemAppearance {

    private SystemAppearance() {
    }

    /** Whether the operating system currently uses a dark appearance. */
    public static boolean isDark() {
        try {
            switch (Platform.os()) {
                case MAC:
                    return "Dark".equalsIgnoreCase(firstLine("defaults", "read", "-g", "AppleInterfaceStyle"));
                case WINDOWS: {
                    String out = String.join("\n", run("reg", "query",
                            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                            "/v", "AppsUseLightTheme"));
                    return out.contains("0x0");
                }
                default:
                    return linuxDark();
            }
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean linuxDark() throws IOException {
        String gtkTheme = System.getenv("GTK_THEME");
        if (gtkTheme != null && gtkTheme.toLowerCase(Locale.ROOT).contains("dark")) {
            return true;
        }
        try {
            String scheme = firstLine("gsettings", "get", "org.gnome.desktop.interface", "color-scheme");
            if (scheme != null && scheme.contains("dark")) {
                return true;
            }
            if (scheme != null && (scheme.contains("light") || scheme.contains("default"))) {
                String theme = firstLine("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme");
                return theme != null && theme.toLowerCase(Locale.ROOT).contains("dark");
            }
        } catch (IOException e) {
            // No gsettings (not GNOME): try KDE below.
        }
        Path kdeGlobals = Paths.get(System.getProperty("user.home", ""), ".config", "kdeglobals");
        if (Files.isRegularFile(kdeGlobals)) {
            for (String line : Files.readAllLines(kdeGlobals, StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (t.startsWith("ColorScheme=") || t.startsWith("LookAndFeelPackage=")) {
                    if (t.toLowerCase(Locale.ROOT).contains("dark")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The user's accent colour, or {@code null} to use the default. */
    public static Color accentColor() {
        try {
            if (Platform.isMac()) {
                String value = firstLine("defaults", "read", "-g", "AppleAccentColor");
                if (value == null) {
                    return null; // blue (default)
                }
                switch (value.trim()) {
                    case "-1":
                        return new Color(0x8C8C8C);
                    case "0":
                        return new Color(0xFF5257);
                    case "1":
                        return new Color(0xF7821B);
                    case "2":
                        return new Color(0xE0A800);
                    case "3":
                        return new Color(0x62BA46);
                    case "5":
                        return new Color(0xA550A7);
                    case "6":
                        return new Color(0xF74F9E);
                    default:
                        return null;
                }
            }
            if (Platform.isLinux()) {
                String value = firstLine("gsettings", "get", "org.gnome.desktop.interface", "accent-color");
                if (value == null) {
                    return null;
                }
                switch (value.replace("'", "").trim()) {
                    case "teal":
                        return new Color(0x2190A4);
                    case "green":
                        return new Color(0x3A944A);
                    case "yellow":
                        return new Color(0xC88800);
                    case "orange":
                        return new Color(0xED5B00);
                    case "red":
                        return new Color(0xE62D42);
                    case "pink":
                        return new Color(0xD56199);
                    case "purple":
                        return new Color(0x9141AC);
                    case "slate":
                        return new Color(0x6F8396);
                    case "blue":
                        return new Color(0x3584E4);
                    default:
                        return null;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private static String firstLine(String... cmd) throws IOException {
        List<String> lines = run(cmd);
        return lines.isEmpty() ? null : lines.get(0).trim();
    }

    private static List<String> run(String... cmd) throws IOException {
        Process p = new ProcessBuilder(Arrays.asList(cmd)).redirectErrorStream(true).start();
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                lines.add(line);
            }
        }
        try {
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
        if (p.exitValue() != 0) {
            return new ArrayList<>();
        }
        return lines;
    }
}
