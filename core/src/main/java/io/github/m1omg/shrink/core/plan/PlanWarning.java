package io.github.m1omg.shrink.core.plan;

/** Things the user should know about a planned job. */
public enum PlanWarning {
    /** The source is already compressed a lot; more compression costs visible quality. */
    ALREADY_COMPRESSED,
    /** The requested target size cannot be reached. */
    TARGET_TOO_SMALL,
    /** The file is already smaller than the requested target. */
    TARGET_NOT_SMALLER,
    /** The bitrate is so low that the result will look rough. */
    LOW_QUALITY,
    /** HDR video is converted to standard dynamic range for compatibility. */
    HDR_TONE_MAPPED,
    /** The result will not be smaller than the original. */
    NOT_SMALLER,
    /** Bitmap subtitles cannot be stored in the chosen format and are dropped. */
    SUBTITLES_DROPPED;

    public String messageKey() {
        return "warning." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
