package io.github.m1omg.shrink.desktop.engine;

import io.github.m1omg.shrink.core.store.Settings;
import io.github.m1omg.shrink.desktop.util.Platform;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds ffmpeg and ffprobe.
 *
 * <p>Release builds of Shrink! contain FFmpeg inside a jar file (from the
 * bytedeco project). On first use the programs are unpacked into the cache
 * folder. Alternatively an FFmpeg installed on the system (Homebrew, apt, ...)
 * or a custom path from the settings is used.
 */
public final class FfmpegLocator {

    /** Version of the bundled FFmpeg build; part of the cache folder name. */
    public static final String BUNDLED_VERSION = "7.1-1.5.11";

    private static final Pattern VERSION = Pattern.compile("ffmpeg version (?:n)?([0-9][^ ]*)");

    private FfmpegLocator() {
    }

    /**
     * Finds FFmpeg according to the settings.
     *
     * @throws EngineException when no working FFmpeg was found
     */
    public static FfmpegTools locate(Settings.FfmpegMode mode, String customPath, Path cacheDir)
            throws EngineException {
        List<String> tried = new ArrayList<>();
        switch (mode) {
            case CUSTOM:
                return fromPath(Paths.get(customPath), FfmpegTools.Source.CUSTOM, tried);
            case BUNDLED:
                return bundled(cacheDir, tried);
            case SYSTEM:
                return system(tried);
            case AUTO:
            default:
                try {
                    return bundled(cacheDir, tried);
                } catch (EngineException e) {
                    return system(tried);
                }
        }
    }

    /** Whether this build of Shrink! contains FFmpeg. */
    public static boolean hasBundled() {
        return bundledResource() != null;
    }

    private static String resourceDir() {
        return "org/bytedeco/ffmpeg/" + Platform.bytedecoPlatform() + "-gpl/";
    }

    private static URL bundledResource() {
        return FfmpegLocator.class.getClassLoader().getResource(resourceDir() + Platform.executable("ffmpeg"));
    }

    private static FfmpegTools bundled(Path cacheDir, List<String> tried) throws EngineException {
        URL url = bundledResource();
        if (url == null) {
            tried.add("bundled: not included in this build");
            throw notFound(tried);
        }
        Path target = cacheDir.resolve("ffmpeg-" + BUNDLED_VERSION + "-" + Platform.bytedecoPlatform());
        try {
            extract(url, target);
        } catch (IOException e) {
            tried.add("bundled: " + e.getMessage());
            throw notFound(tried);
        }
        return fromPath(target.resolve(Platform.executable("ffmpeg")), FfmpegTools.Source.BUNDLED, tried);
    }

    /** Unpacks all files of the platform folder (programs and libraries) once. */
    private static synchronized void extract(URL url, Path target) throws IOException {
        Path marker = target.resolve(".complete");
        if (Files.isRegularFile(marker) && Files.isRegularFile(target.resolve(Platform.executable("ffmpeg")))) {
            return;
        }
        Files.createDirectories(target);
        String prefix = resourceDir();
        if ("jar".equals(url.getProtocol())) {
            JarURLConnection connection = (JarURLConnection) url.openConnection();
            connection.setUseCaches(false);
            try (JarFile jar = connection.getJarFile()) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory() || !name.startsWith(prefix)) {
                        continue;
                    }
                    String fileName = name.substring(prefix.length());
                    if (fileName.contains("/")) {
                        continue;
                    }
                    try (InputStream in = jar.getInputStream(entry)) {
                        copy(in, target.resolve(fileName));
                    }
                }
            }
        } else {
            // Running from unpacked classes (e.g. in an IDE).
            try {
                Path dir = Paths.get(url.toURI()).getParent();
                try (var files = Files.list(dir)) {
                    for (Path f : (Iterable<Path>) files::iterator) {
                        try (InputStream in = Files.newInputStream(f)) {
                            copy(in, target.resolve(f.getFileName().toString()));
                        }
                    }
                }
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
        }
        for (String program : new String[] {"ffmpeg", "ffprobe"}) {
            File f = target.resolve(Platform.executable(program)).toFile();
            if (!f.setExecutable(true, false)) {
                throw new IOException("Cannot make " + f + " executable");
            }
        }
        Files.write(marker, BUNDLED_VERSION.getBytes(StandardCharsets.UTF_8));
    }

    private static void copy(InputStream in, Path file) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".part");
        Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    private static FfmpegTools system(List<String> tried) throws EngineException {
        for (Path dir : searchDirectories()) {
            Path candidate = dir.resolve(Platform.executable("ffmpeg"));
            if (Files.isExecutable(candidate)) {
                try {
                    return fromPath(candidate, FfmpegTools.Source.SYSTEM, tried);
                } catch (EngineException e) {
                    // Keep looking.
                }
            }
        }
        tried.add("system: no ffmpeg found in PATH");
        throw notFound(tried);
    }

    /** PATH plus the usual install locations (apps started from the Dock do not get the shell PATH). */
    static List<Path> searchDirectories() {
        Set<Path> dirs = new LinkedHashSet<>();
        String path = System.getenv("PATH");
        if (path != null) {
            for (String p : path.split(File.pathSeparator)) {
                if (!p.isEmpty()) {
                    dirs.add(Paths.get(p));
                }
            }
        }
        if (Platform.isMac()) {
            dirs.add(Paths.get("/opt/homebrew/bin"));
            dirs.add(Paths.get("/usr/local/bin"));
            dirs.add(Paths.get("/opt/local/bin"));
        } else if (Platform.isLinux()) {
            dirs.add(Paths.get("/usr/bin"));
            dirs.add(Paths.get("/usr/local/bin"));
            dirs.add(Paths.get("/snap/bin"));
            dirs.add(Paths.get(System.getProperty("user.home", ""), ".local/bin"));
        } else {
            dirs.add(Paths.get("C:\\ffmpeg\\bin"));
        }
        return new ArrayList<>(dirs);
    }

    private static FfmpegTools fromPath(Path ffmpeg, FfmpegTools.Source source, List<String> tried)
            throws EngineException {
        if (Files.isDirectory(ffmpeg)) {
            ffmpeg = ffmpeg.resolve(Platform.executable("ffmpeg"));
        }
        Path ffprobe = ffmpeg.resolveSibling(Platform.executable("ffprobe"));
        if (!Files.isExecutable(ffmpeg) || !Files.isExecutable(ffprobe)) {
            tried.add(source.name().toLowerCase(Locale.ROOT) + ": " + ffmpeg + " or ffprobe missing");
            throw notFound(tried);
        }
        try {
            String versionText = String.join("\n", runAndRead(ffmpeg, "-hide_banner", "-version"));
            Matcher m = VERSION.matcher(versionText);
            String version = m.find() ? m.group(1) : "?";
            Set<String> encoders = parseCodecList(runAndRead(ffmpeg, "-hide_banner", "-encoders"));
            Set<String> filters = parseFilterList(runAndRead(ffmpeg, "-hide_banner", "-filters"));
            return new FfmpegTools(ffmpeg, ffprobe, source, version, encoders, filters);
        } catch (IOException e) {
            tried.add(source.name().toLowerCase(Locale.ROOT) + ": " + ffmpeg + " does not run (" + e.getMessage()
                    + ")");
            throw notFound(tried);
        }
    }

    /** Lines like {@code " V....D libx264  libx264 H.264 ..."} → {@code libx264}. */
    static Set<String> parseCodecList(List<String> lines) {
        Set<String> names = new HashSet<>();
        boolean started = false;
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("------")) {
                started = true;
                continue;
            }
            if (!started || t.isEmpty()) {
                continue;
            }
            String[] parts = t.split("\\s+");
            if (parts.length >= 2 && parts[0].length() == 6) {
                names.add(parts[1]);
            }
        }
        return names;
    }

    /** Lines like {@code " ..C zscale  V->V  Apply resizing..."} → {@code zscale}. */
    static Set<String> parseFilterList(List<String> lines) {
        Set<String> names = new HashSet<>();
        for (String line : lines) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 3 && parts[0].matches("[T.][S.][C.]") && parts[2].contains("->")) {
                names.add(parts[1]);
            }
        }
        return names;
    }

    private static List<String> runAndRead(Path program, String... args) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(program.toString());
        for (String a : args) {
            cmd.add(a);
        }
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                lines.add(line);
            }
        }
        try {
            if (!p.waitFor(20, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
        if (p.exitValue() != 0) {
            throw new IOException("exit code " + p.exitValue());
        }
        return lines;
    }

    private static EngineException notFound(List<String> tried) {
        return new EngineException("FFmpeg not found", String.join("\n", tried), null);
    }
}
