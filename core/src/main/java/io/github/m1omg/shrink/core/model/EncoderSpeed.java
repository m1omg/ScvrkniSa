package io.github.m1omg.shrink.core.model;

import java.util.Locale;

/**
 * Trade-off between encoding time and compression efficiency. Slower settings
 * give the same quality in a smaller file.
 */
public enum EncoderSpeed {
    FASTEST,
    FAST,
    BALANCED,
    SLOW;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String titleKey() {
        return "speed." + id();
    }

    public static EncoderSpeed fromId(String id, EncoderSpeed fallback) {
        if (id != null) {
            for (EncoderSpeed s : values()) {
                if (s.id().equalsIgnoreCase(id)) {
                    return s;
                }
            }
        }
        return fallback;
    }
}
