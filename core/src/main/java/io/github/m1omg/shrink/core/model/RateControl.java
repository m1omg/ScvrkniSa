package io.github.m1omg.shrink.core.model;

import java.util.Locale;

/** How the video encoder decides how many bits to spend. */
public enum RateControl {
    /** Two passes over the video: the file size matches the estimate closely. */
    TWO_PASS,
    /** One pass with an average bitrate: faster, size is a little less exact. */
    ONE_PASS,
    /** Constant quality (CRF): quality is fixed, the size is only approximate. */
    CONSTANT_QUALITY;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String titleKey() {
        return "ratecontrol." + id();
    }

    public static RateControl fromId(String id, RateControl fallback) {
        if (id != null) {
            for (RateControl r : values()) {
                if (r.id().equalsIgnoreCase(id)) {
                    return r;
                }
            }
        }
        return fallback;
    }
}
