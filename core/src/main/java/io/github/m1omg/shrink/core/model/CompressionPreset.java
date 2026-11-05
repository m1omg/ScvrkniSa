package io.github.m1omg.shrink.core.model;

import java.util.Locale;

/** The four compression modes offered to the user. */
public enum CompressionPreset {
    /** Reduce the size while keeping as much quality as possible. */
    HIGH_QUALITY,
    /** Recommended: good balance between size and quality. */
    BALANCED,
    /** Smallest file, may visibly reduce quality. */
    SMALL_FILE,
    /** The user enters the size the result should have. */
    TARGET_SIZE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Key of the translated title, e.g. {@code preset.balanced.title}. */
    public String titleKey() {
        return "preset." + id() + ".title";
    }

    public String descriptionKey() {
        return "preset." + id() + ".description";
    }

    public static CompressionPreset fromId(String id, CompressionPreset fallback) {
        if (id != null) {
            for (CompressionPreset p : values()) {
                if (p.id().equalsIgnoreCase(id)) {
                    return p;
                }
            }
        }
        return fallback;
    }
}
