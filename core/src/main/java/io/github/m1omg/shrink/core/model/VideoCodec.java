package io.github.m1omg.shrink.core.model;

import java.util.Locale;

/**
 * Video codecs Shrink! can encode to.
 *
 * <p>The {@code efficiency} value says how many bits this codec needs, relative
 * to H.264, for roughly the same visual quality. It is used when planning
 * bitrates, e.g. HEVC needs about 62&nbsp;% of the H.264 bitrate.
 */
public enum VideoCodec {
    H264("H.264", "h264", 1.0),
    H265("H.265 (HEVC)", "hevc", 0.62),
    VP9("VP9", "vp9", 0.65),
    AV1("AV1", "av1", 0.52);

    private final String displayName;
    private final String codecName;
    private final double efficiency;

    VideoCodec(String displayName, String codecName, double efficiency) {
        this.displayName = displayName;
        this.codecName = codecName;
        this.efficiency = efficiency;
    }

    /** Human readable name, the same in every language. */
    public String displayName() {
        return displayName;
    }

    /** Codec name as reported by ffprobe ({@code codec_name}). */
    public String codecName() {
        return codecName;
    }

    /** Bitrate needed for equal quality, relative to H.264 (1.0). */
    public double efficiency() {
        return efficiency;
    }

    /** Stable identifier used in settings files. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static VideoCodec fromId(String id, VideoCodec fallback) {
        if (id != null) {
            for (VideoCodec c : values()) {
                if (c.id().equalsIgnoreCase(id) || c.codecName.equalsIgnoreCase(id)) {
                    return c;
                }
            }
        }
        return fallback;
    }

    /** Finds the codec matching an ffprobe codec name, or {@code null}. */
    public static VideoCodec fromCodecName(String codecName) {
        if (codecName == null) {
            return null;
        }
        for (VideoCodec c : values()) {
            if (c.codecName.equalsIgnoreCase(codecName)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Relative bitrate efficiency of any source codec (by ffprobe name), used to
     * judge how "big" a source really is. Unknown codecs count as slightly worse
     * than H.264.
     */
    public static double efficiencyOf(String codecName) {
        if (codecName == null) {
            return 1.2;
        }
        switch (codecName.toLowerCase(Locale.ROOT)) {
            case "h264":
                return 1.0;
            case "hevc":
            case "h265":
                return 0.62;
            case "vp9":
                return 0.65;
            case "av1":
                return 0.52;
            case "vp8":
                return 1.15;
            case "theora":
                return 1.4;
            case "mpeg4":
            case "msmpeg4v3":
            case "msmpeg4v2":
            case "wmv3":
            case "vc1":
                return 1.5;
            case "h263":
            case "wmv2":
            case "wmv1":
            case "flv1":
                return 1.8;
            case "mpeg2video":
            case "mpeg1video":
                return 2.2;
            case "mjpeg":
            case "jpeg2000":
                return 6.0;
            case "prores":
            case "dnxhd":
            case "cfhd":
            case "ffv1":
            case "rawvideo":
            case "huffyuv":
            case "utvideo":
                return 10.0;
            default:
                return 1.2;
        }
    }
}
