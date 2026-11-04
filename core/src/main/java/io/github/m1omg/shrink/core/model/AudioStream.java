package io.github.m1omg.shrink.core.model;

/** Technical details of an audio stream. */
public final class AudioStream {
    private final int index;
    private final String codecName;
    private final String codecLongName;
    private final String profile;
    private final int sampleRate;
    private final int channels;
    private final String channelLayout;
    private final long bitrate;
    private final String language;
    private final String title;

    private AudioStream(Builder b) {
        this.index = b.index;
        this.codecName = b.codecName;
        this.codecLongName = b.codecLongName;
        this.profile = b.profile;
        this.sampleRate = b.sampleRate;
        this.channels = b.channels;
        this.channelLayout = b.channelLayout;
        this.bitrate = b.bitrate;
        this.language = b.language;
        this.title = b.title;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int index() {
        return index;
    }

    public String codecName() {
        return codecName;
    }

    public String codecLongName() {
        return codecLongName;
    }

    public String profile() {
        return profile;
    }

    public int sampleRate() {
        return sampleRate;
    }

    public int channels() {
        return channels;
    }

    public String channelLayout() {
        return channelLayout;
    }

    /** Bitrate in bits per second, or 0 when unknown. */
    public long bitrate() {
        return bitrate;
    }

    /** Bitrate, or a typical value for the codec and channel count when unknown. */
    public long bitrateOrGuess() {
        if (bitrate > 0) {
            return bitrate;
        }
        int ch = Math.max(1, channels);
        String c = codecName == null ? "" : codecName;
        if (c.startsWith("pcm_")) {
            return (long) Math.max(sampleRate, 8000) * 16L * ch;
        }
        if (c.equals("flac") || c.equals("alac") || c.equals("truehd")) {
            return (long) Math.max(sampleRate, 8000) * 9L * ch;
        }
        // Typical lossy audio: about 64 kbps per channel (128 kbps stereo).
        return 64_000L * ch;
    }

    public String language() {
        return language;
    }

    public String title() {
        return title;
    }

    /** Builder for {@link AudioStream}. */
    public static final class Builder {
        private int index;
        private String codecName = "";
        private String codecLongName = "";
        private String profile = "";
        private int sampleRate;
        private int channels = 2;
        private String channelLayout = "";
        private long bitrate;
        private String language = "";
        private String title = "";

        public Builder index(int v) { index = v; return this; }
        public Builder codecName(String v) { codecName = v == null ? "" : v; return this; }
        public Builder codecLongName(String v) { codecLongName = v == null ? "" : v; return this; }
        public Builder profile(String v) { profile = v == null ? "" : v; return this; }
        public Builder sampleRate(int v) { sampleRate = v; return this; }
        public Builder channels(int v) { channels = v; return this; }
        public Builder channelLayout(String v) { channelLayout = v == null ? "" : v; return this; }
        public Builder bitrate(long v) { bitrate = Math.max(0, v); return this; }
        public Builder language(String v) { language = v == null ? "" : v; return this; }
        public Builder title(String v) { title = v == null ? "" : v; return this; }

        public AudioStream build() {
            return new AudioStream(this);
        }
    }
}
