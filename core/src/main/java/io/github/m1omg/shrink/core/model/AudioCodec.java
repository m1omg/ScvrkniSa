package io.github.m1omg.shrink.core.model;

import java.util.Locale;

/** Audio codecs Shrink! can encode to. */
public enum AudioCodec {
    AAC("AAC", "aac"),
    OPUS("Opus", "opus"),
    MP3("MP3", "mp3");

    private final String displayName;
    private final String codecName;

    AudioCodec(String displayName, String codecName) {
        this.displayName = displayName;
        this.codecName = codecName;
    }

    public String displayName() {
        return displayName;
    }

    /** Codec name as reported by ffprobe. */
    public String codecName() {
        return codecName;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AudioCodec fromId(String id, AudioCodec fallback) {
        if (id != null) {
            for (AudioCodec c : values()) {
                if (c.id().equalsIgnoreCase(id) || c.codecName.equalsIgnoreCase(id)) {
                    return c;
                }
            }
        }
        return fallback;
    }

    /** Opus sounds as good as AAC at roughly three quarters of the bitrate. */
    public double efficiency() {
        return this == OPUS ? 0.75 : (this == MP3 ? 1.15 : 1.0);
    }
}
