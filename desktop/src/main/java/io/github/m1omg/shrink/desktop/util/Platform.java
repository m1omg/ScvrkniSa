package io.github.m1omg.shrink.desktop.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/** Facts about the operating system Shrink! runs on. */
public final class Platform {

    /** Supported desktop systems. */
    public enum Os { MAC, LINUX, WINDOWS }

    private static final Os OS = detectOs();
    private static final String ARCH = detectArch();

    private Platform() {
    }

    private static Os detectOs() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("mac") || name.contains("darwin")) {
            return Os.MAC;
        }
        if (name.contains("win")) {
            return Os.WINDOWS;
        }
        return Os.LINUX;
    }

    private static String detectArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return arch.equals("aarch64") || arch.equals("arm64") ? "arm64" : "x86_64";
    }

    public static Os os() {
        return OS;
    }

    public static boolean isMac() {
        return OS == Os.MAC;
    }

    public static boolean isLinux() {
        return OS == Os.LINUX;
    }

    public static boolean isWindows() {
        return OS == Os.WINDOWS;
    }

    /** {@code x86_64} or {@code arm64}. */
    public static String arch() {
        return ARCH;
    }

    /** Platform name used by the bundled FFmpeg, e.g. {@code macosx-x86_64}. */
    public static String bytedecoPlatform() {
        switch (OS) {
            case MAC:
                return "macosx-" + ARCH;
            case WINDOWS:
                return "windows-" + ARCH;
            default:
                return "linux-" + ARCH;
        }
    }

    /** File name of an executable, adding {@code .exe} on Windows. */
    public static String executable(String name) {
        return OS == Os.WINDOWS ? name + ".exe" : name;
    }

    /** The "null" output file of the system. */
    public static String nullDevice() {
        return OS == Os.WINDOWS ? "NUL" : "/dev/null";
    }

    private static Path home() {
        return Paths.get(System.getProperty("user.home", "."));
    }

    private static Path env(String variable, Path fallback) {
        String v = System.getenv(variable);
        return v == null || v.isEmpty() ? fallback : Paths.get(v);
    }

    /** Where settings are stored. */
    public static Path configDir() {
        switch (OS) {
            case MAC:
                return home().resolve("Library/Application Support/Shrink");
            case WINDOWS:
                return env("APPDATA", home().resolve("AppData/Roaming")).resolve("Shrink");
            default:
                return env("XDG_CONFIG_HOME", home().resolve(".config")).resolve("shrink");
        }
    }

    /** Where the history is stored. */
    public static Path dataDir() {
        switch (OS) {
            case MAC:
                return home().resolve("Library/Application Support/Shrink");
            case WINDOWS:
                return env("APPDATA", home().resolve("AppData/Roaming")).resolve("Shrink");
            default:
                return env("XDG_DATA_HOME", home().resolve(".local/share")).resolve("shrink");
        }
    }

    /** Where temporary and re-creatable files go (e.g. the unpacked FFmpeg). */
    public static Path cacheDir() {
        switch (OS) {
            case MAC:
                return home().resolve("Library/Caches/Shrink");
            case WINDOWS:
                return env("LOCALAPPDATA", home().resolve("AppData/Local")).resolve("Shrink").resolve("cache");
            default:
                return env("XDG_CACHE_HOME", home().resolve(".cache")).resolve("shrink");
        }
    }

    /** The user's video folder, used as a starting point in file dialogs. */
    public static Path videosDir() {
        Path movies = home().resolve(OS == Os.MAC ? "Movies" : "Videos");
        return movies.toFile().isDirectory() ? movies : home();
    }
}
