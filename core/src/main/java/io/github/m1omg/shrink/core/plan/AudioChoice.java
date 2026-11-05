package io.github.m1omg.shrink.core.plan;

import java.util.Locale;

/** Audio option in the advanced compression settings. */
public enum AudioChoice {
    /** Let Shrink! decide (copy when it is already small enough). */
    AUTO,
    COPY,
    AAC,
    OPUS,
    MP3,
    /** Remove the audio. */
    NONE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String titleKey() {
        return "audiochoice." + id();
    }
}
