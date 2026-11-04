package io.github.m1omg.shrink.core.store;

import io.github.m1omg.shrink.core.i18n.Language;
import io.github.m1omg.shrink.core.model.Container;
import io.github.m1omg.shrink.core.model.EncoderSpeed;
import io.github.m1omg.shrink.core.model.VideoCodec;
import io.github.m1omg.shrink.core.plan.EncodingDefaults;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User preferences, stored as {@code settings.json}. The same file format is
 * used on desktop and Android; options that only make sense on one platform are
 * simply ignored by the other.
 */
public final class Settings {

    /** Light/dark appearance. */
    public enum Theme { SYSTEM, LIGHT, DARK }

    /** Where results are saved. */
    public enum OutputLocation { NEXT_TO_SOURCE, CUSTOM_FOLDER, ASK }

    /** Which ffmpeg the desktop app uses. */
    public enum FfmpegMode { AUTO, BUNDLED, SYSTEM, CUSTOM }

    private Language language = Language.SYSTEM;
    private Theme theme = Theme.SYSTEM;
    private OutputLocation outputLocation = OutputLocation.NEXT_TO_SOURCE;
    private String customOutputFolder = "";
    private VideoCodec videoCodec = VideoCodec.H264;
    private Container container = Container.MP4;
    private EncoderSpeed speed = EncoderSpeed.BALANCED;
    private boolean twoPass = true;
    private boolean hardwareEncoding;
    private boolean keepMetadata = true;
    private boolean showAdvanced;
    private boolean revealWhenDone;
    private FfmpegMode ffmpegMode = FfmpegMode.AUTO;
    private String ffmpegPath = "";
    private boolean historyEnabled = true;
    private boolean animatedBackground = true;
    private int previewSeconds = 5;

    public Language getLanguage() { return language; }
    public void setLanguage(Language v) { language = v == null ? Language.SYSTEM : v; }
    public Theme getTheme() { return theme; }
    public void setTheme(Theme v) { theme = v == null ? Theme.SYSTEM : v; }
    public OutputLocation getOutputLocation() { return outputLocation; }
    public void setOutputLocation(OutputLocation v) { outputLocation = v == null ? OutputLocation.NEXT_TO_SOURCE : v; }
    public String getCustomOutputFolder() { return customOutputFolder; }
    public void setCustomOutputFolder(String v) { customOutputFolder = v == null ? "" : v; }
    public VideoCodec getVideoCodec() { return videoCodec; }
    public void setVideoCodec(VideoCodec v) { videoCodec = v == null ? VideoCodec.H264 : v; }
    public Container getContainer() { return container; }
    public void setContainer(Container v) { container = v == null ? Container.MP4 : v; }
    public EncoderSpeed getSpeed() { return speed; }
    public void setSpeed(EncoderSpeed v) { speed = v == null ? EncoderSpeed.BALANCED : v; }
    public boolean isTwoPass() { return twoPass; }
    public void setTwoPass(boolean v) { twoPass = v; }
    public boolean isHardwareEncoding() { return hardwareEncoding; }
    public void setHardwareEncoding(boolean v) { hardwareEncoding = v; }
    public boolean isKeepMetadata() { return keepMetadata; }
    public void setKeepMetadata(boolean v) { keepMetadata = v; }
    public boolean isShowAdvanced() { return showAdvanced; }
    public void setShowAdvanced(boolean v) { showAdvanced = v; }
    public boolean isRevealWhenDone() { return revealWhenDone; }
    public void setRevealWhenDone(boolean v) { revealWhenDone = v; }
    public FfmpegMode getFfmpegMode() { return ffmpegMode; }
    public void setFfmpegMode(FfmpegMode v) { ffmpegMode = v == null ? FfmpegMode.AUTO : v; }
    public String getFfmpegPath() { return ffmpegPath; }
    public void setFfmpegPath(String v) { ffmpegPath = v == null ? "" : v; }
    public boolean isHistoryEnabled() { return historyEnabled; }
    public void setHistoryEnabled(boolean v) { historyEnabled = v; }
    public boolean isAnimatedBackground() { return animatedBackground; }
    public void setAnimatedBackground(boolean v) { animatedBackground = v; }
    public int getPreviewSeconds() { return previewSeconds; }
    public void setPreviewSeconds(int v) { previewSeconds = Math.max(2, Math.min(30, v)); }

    /** The encoding-related settings as planner input. */
    public EncodingDefaults toEncodingDefaults() {
        Container c = container;
        VideoCodec codec = c.supports(videoCodec) ? videoCodec : c.defaultVideoCodec();
        return new EncodingDefaults(codec, c, speed, twoPass, hardwareEncoding, keepMetadata);
    }

    public Settings copy() {
        Settings s = new Settings();
        s.fromMap(toMap());
        return s;
    }

    // ------------------------------------------------------------------ persistence

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", 1L);
        m.put("language", language.id());
        m.put("theme", theme.name().toLowerCase(java.util.Locale.ROOT));
        m.put("outputLocation", outputLocation.name().toLowerCase(java.util.Locale.ROOT));
        m.put("customOutputFolder", customOutputFolder);
        m.put("videoCodec", videoCodec.id());
        m.put("container", container.id());
        m.put("speed", speed.id());
        m.put("twoPass", twoPass);
        m.put("hardwareEncoding", hardwareEncoding);
        m.put("keepMetadata", keepMetadata);
        m.put("showAdvanced", showAdvanced);
        m.put("revealWhenDone", revealWhenDone);
        m.put("ffmpegMode", ffmpegMode.name().toLowerCase(java.util.Locale.ROOT));
        m.put("ffmpegPath", ffmpegPath);
        m.put("historyEnabled", historyEnabled);
        m.put("animatedBackground", animatedBackground);
        m.put("previewSeconds", (long) previewSeconds);
        return m;
    }

    public void fromMap(Map<String, Object> m) {
        setLanguage(Language.fromId(Json.getString(m, "language", "system")));
        setTheme(enumValue(Theme.class, Json.getString(m, "theme", null), Theme.SYSTEM));
        setOutputLocation(enumValue(OutputLocation.class, Json.getString(m, "outputLocation", null),
                OutputLocation.NEXT_TO_SOURCE));
        setCustomOutputFolder(Json.getString(m, "customOutputFolder", ""));
        setVideoCodec(VideoCodec.fromId(Json.getString(m, "videoCodec", null), VideoCodec.H264));
        setContainer(Container.fromId(Json.getString(m, "container", null), Container.MP4));
        setSpeed(EncoderSpeed.fromId(Json.getString(m, "speed", null), EncoderSpeed.BALANCED));
        setTwoPass(Json.getBoolean(m, "twoPass", true));
        setHardwareEncoding(Json.getBoolean(m, "hardwareEncoding", false));
        setKeepMetadata(Json.getBoolean(m, "keepMetadata", true));
        setShowAdvanced(Json.getBoolean(m, "showAdvanced", false));
        setRevealWhenDone(Json.getBoolean(m, "revealWhenDone", false));
        setFfmpegMode(enumValue(FfmpegMode.class, Json.getString(m, "ffmpegMode", null), FfmpegMode.AUTO));
        setFfmpegPath(Json.getString(m, "ffmpegPath", ""));
        setHistoryEnabled(Json.getBoolean(m, "historyEnabled", true));
        setAnimatedBackground(Json.getBoolean(m, "animatedBackground", true));
        setPreviewSeconds((int) Json.getLong(m, "previewSeconds", 5));
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name, E fallback) {
        if (name == null) {
            return fallback;
        }
        for (E e : type.getEnumConstants()) {
            if (e.name().equalsIgnoreCase(name)) {
                return e;
            }
        }
        return fallback;
    }

    /** Loads settings from a file; missing or broken files give the defaults. */
    public static Settings load(Path file) {
        Settings s = new Settings();
        try {
            String text = AtomicFiles.readString(file);
            if (text != null) {
                s.fromMap(Json.parseObject(text));
            }
        } catch (IOException | Json.JsonException e) {
            // Unreadable settings are not fatal: start with defaults.
        }
        return s;
    }

    public void save(Path file) throws IOException {
        AtomicFiles.writeString(file, Json.write(toMap(), true) + "\n");
    }
}
