package io.github.m1omg.shrink.core.model;

import java.util.Locale;

/** What a job does; also used to label history entries. */
public enum JobKind {
    COMPRESS,
    TRIM,
    ROTATE,
    MUTE,
    EXTRACT_AUDIO,
    CONVERT,
    RESOLUTION,
    FRAME_RATE,
    PREVIEW;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Translation key for the tool title, e.g. {@code tool.trim.title}. */
    public String titleKey() {
        return "tool." + id() + ".title";
    }

    /** Suffix appended to output file names. */
    public String fileSuffix() {
        switch (this) {
            case COMPRESS:
                return "-compressed";
            case TRIM:
                return "-trimmed";
            case ROTATE:
                return "-rotated";
            case MUTE:
                return "-muted";
            case EXTRACT_AUDIO:
                return "-audio";
            case CONVERT:
                return "";
            case RESOLUTION:
                return "-resized";
            case FRAME_RATE:
                return "-fps";
            case PREVIEW:
            default:
                return "-preview";
        }
    }

    public static JobKind fromId(String id, JobKind fallback) {
        if (id != null) {
            for (JobKind k : values()) {
                if (k.id().equalsIgnoreCase(id)) {
                    return k;
                }
            }
        }
        return fallback;
    }
}
