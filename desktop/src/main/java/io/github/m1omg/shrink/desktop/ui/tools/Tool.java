package io.github.m1omg.shrink.desktop.ui.tools;

import io.github.m1omg.shrink.core.model.JobKind;

/** The tools in the sidebar, in display order. */
public enum Tool {
    COMPRESS("compress", JobKind.COMPRESS, "tool.compress"),
    TRIM("trim", JobKind.TRIM, "tool.trim"),
    ROTATE("rotate", JobKind.ROTATE, "tool.rotate"),
    MUTE("mute", JobKind.MUTE, "tool.mute"),
    EXTRACT_AUDIO("audio", JobKind.EXTRACT_AUDIO, "tool.extract_audio"),
    CONVERT("convert", JobKind.CONVERT, "tool.convert"),
    RESOLUTION("resolution", JobKind.RESOLUTION, "tool.resolution"),
    FRAME_RATE("framerate", JobKind.FRAME_RATE, "tool.frame_rate"),
    INFO("info", null, "tool.info");

    private final String icon;
    private final JobKind kind;
    private final String key;

    Tool(String icon, JobKind kind, String key) {
        this.icon = icon;
        this.kind = kind;
        this.key = key;
    }

    public String icon() {
        return icon;
    }

    /** The job this tool runs, or {@code null} for the information tool. */
    public JobKind kind() {
        return kind;
    }

    public String titleKey() {
        return key + ".title";
    }

    public String descriptionKey() {
        return key + ".description";
    }

    /** Whether the tool needs a video stream (all except audio extraction and information). */
    public boolean needsVideo() {
        return this != EXTRACT_AUDIO && this != INFO;
    }
}
