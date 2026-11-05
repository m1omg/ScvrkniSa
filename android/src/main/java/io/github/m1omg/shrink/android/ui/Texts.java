package io.github.m1omg.shrink.android.ui;

import io.github.m1omg.shrink.core.i18n.Formats;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.VideoStream;
import io.github.m1omg.shrink.core.util.OutputNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Formatting shared by several screens. */
public final class Texts {

    private Texts() {
    }

    /** "0:23 · 1280 × 720 · 30 fps · 4.1 MB · MP4". */
    public static String facts(MediaInfo info) {
        Formats f = I18n.fmt();
        List<String> parts = new ArrayList<>();
        parts.add(f.duration(info.durationSeconds()));
        VideoStream v = info.video();
        if (v != null) {
            parts.add(f.resolution(v.displayWidth(), v.displayHeight()));
            if (v.frameRate() > 0) {
                parts.add(f.frameRate(v.frameRate()));
            }
        }
        parts.add(f.size(info.sizeBytes()));
        String ext = OutputNames.extension(info.fileName()).toUpperCase(Locale.ROOT);
        if (!ext.isEmpty()) {
            parts.add(ext);
        }
        return String.join("  ·  ", parts);
    }
}
