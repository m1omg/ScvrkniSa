package io.github.m1omg.shrink.core.model;

import java.util.Locale;

/** Technical details of a video stream, as reported by the platform probe. */
public final class VideoStream {
    private final int index;
    private final String codecName;
    private final String codecLongName;
    private final String profile;
    private final int width;
    private final int height;
    private final double frameRate;
    private final long bitrate;
    private final String pixelFormat;
    private final int bitDepth;
    private final int rotation;
    private final String colorTransfer;
    private final String colorPrimaries;
    private final String colorSpace;
    private final long frameCount;
    private final String codecTag;

    private VideoStream(Builder b) {
        this.index = b.index;
        this.codecName = b.codecName;
        this.codecLongName = b.codecLongName;
        this.profile = b.profile;
        this.width = b.width;
        this.height = b.height;
        this.frameRate = b.frameRate;
        this.bitrate = b.bitrate;
        this.pixelFormat = b.pixelFormat;
        this.bitDepth = b.bitDepth;
        this.rotation = ((b.rotation % 360) + 360) % 360;
        this.colorTransfer = b.colorTransfer;
        this.colorPrimaries = b.colorPrimaries;
        this.colorSpace = b.colorSpace;
        this.frameCount = b.frameCount;
        this.codecTag = b.codecTag;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Index of the stream inside the file. */
    public int index() {
        return index;
    }

    /** Short codec name, e.g. {@code h264}. */
    public String codecName() {
        return codecName;
    }

    public String codecLongName() {
        return codecLongName;
    }

    public String profile() {
        return profile;
    }

    /** Coded width in pixels (before rotation is applied). */
    public int width() {
        return width;
    }

    /** Coded height in pixels (before rotation is applied). */
    public int height() {
        return height;
    }

    /** Width as the video is displayed (after rotation). */
    public int displayWidth() {
        return rotation == 90 || rotation == 270 ? height : width;
    }

    /** Height as the video is displayed (after rotation). */
    public int displayHeight() {
        return rotation == 90 || rotation == 270 ? width : height;
    }

    /** The shorter side, which is what "720p" or "1080p" refer to. */
    public int shortSide() {
        return Math.min(width, height);
    }

    public double frameRate() {
        return frameRate;
    }

    /** Bitrate in bits per second, or 0 when the file does not say. */
    public long bitrate() {
        return bitrate;
    }

    public String pixelFormat() {
        return pixelFormat;
    }

    public int bitDepth() {
        return bitDepth;
    }

    /** Clockwise rotation in degrees (0, 90, 180 or 270) that players apply. */
    public int rotation() {
        return rotation;
    }

    public String colorTransfer() {
        return colorTransfer;
    }

    public String colorPrimaries() {
        return colorPrimaries;
    }

    public String colorSpace() {
        return colorSpace;
    }

    public long frameCount() {
        return frameCount;
    }

    public String codecTag() {
        return codecTag;
    }

    /** True for HDR video (PQ / HDR10 or HLG transfer characteristics). */
    public boolean isHdr() {
        if (colorTransfer == null) {
            return false;
        }
        String t = colorTransfer.toLowerCase(Locale.ROOT);
        return t.equals("smpte2084") || t.equals("arib-std-b67") || t.contains("pq") || t.contains("hlg");
    }

    /** Short label for the kind of HDR, or {@code null} for SDR video. */
    public String hdrLabel() {
        if (!isHdr()) {
            return null;
        }
        String t = colorTransfer.toLowerCase(Locale.ROOT);
        return t.equals("arib-std-b67") || t.contains("hlg") ? "HLG" : "HDR10 (PQ)";
    }

    /** Builder for {@link VideoStream}. */
    public static final class Builder {
        private int index;
        private String codecName = "";
        private String codecLongName = "";
        private String profile = "";
        private int width;
        private int height;
        private double frameRate;
        private long bitrate;
        private String pixelFormat = "";
        private int bitDepth = 8;
        private int rotation;
        private String colorTransfer;
        private String colorPrimaries;
        private String colorSpace;
        private long frameCount;
        private String codecTag = "";

        public Builder index(int v) { index = v; return this; }
        public Builder codecName(String v) { codecName = v == null ? "" : v; return this; }
        public Builder codecLongName(String v) { codecLongName = v == null ? "" : v; return this; }
        public Builder profile(String v) { profile = v == null ? "" : v; return this; }
        public Builder width(int v) { width = v; return this; }
        public Builder height(int v) { height = v; return this; }
        public Builder frameRate(double v) { frameRate = v; return this; }
        public Builder bitrate(long v) { bitrate = Math.max(0, v); return this; }
        public Builder pixelFormat(String v) { pixelFormat = v == null ? "" : v; return this; }
        public Builder bitDepth(int v) { bitDepth = v > 0 ? v : 8; return this; }
        public Builder rotation(int v) { rotation = v; return this; }
        public Builder colorTransfer(String v) { colorTransfer = v; return this; }
        public Builder colorPrimaries(String v) { colorPrimaries = v; return this; }
        public Builder colorSpace(String v) { colorSpace = v; return this; }
        public Builder frameCount(long v) { frameCount = v; return this; }
        public Builder codecTag(String v) { codecTag = v == null ? "" : v; return this; }

        public VideoStream build() {
            return new VideoStream(this);
        }
    }
}
