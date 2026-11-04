package io.github.m1omg.shrink.core.model;

import java.util.Locale;

/**
 * A subtitle stream.
 *
 * @param index     stream index inside the file
 * @param codecName ffprobe codec name, e.g. {@code subrip} or {@code hdmv_pgs_subtitle}
 * @param language  language tag, may be empty
 */
public record SubtitleStream(int index, String codecName, String language) {

    /** Text subtitles can be converted between containers; bitmap ones cannot. */
    public boolean isText() {
        String c = codecName == null ? "" : codecName.toLowerCase(Locale.ROOT);
        return c.equals("subrip") || c.equals("srt") || c.equals("ass") || c.equals("ssa")
                || c.equals("mov_text") || c.equals("webvtt") || c.equals("text");
    }
}
