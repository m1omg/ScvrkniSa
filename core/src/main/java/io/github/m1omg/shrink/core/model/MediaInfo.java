package io.github.m1omg.shrink.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything Shrink! knows about an input file: container, duration, size and
 * its streams. Created by the platform-specific probe (ffprobe on desktop,
 * MediaExtractor on Android).
 */
public final class MediaInfo {
    private final String location;
    private final String fileName;
    private final long sizeBytes;
    private final double durationSeconds;
    private final String formatName;
    private final String formatLongName;
    private final long overallBitrate;
    private final VideoStream video;
    private final List<AudioStream> audioStreams;
    private final List<SubtitleStream> subtitleStreams;
    private final Map<String, String> tags;

    private MediaInfo(Builder b) {
        this.location = b.location;
        this.fileName = b.fileName;
        this.sizeBytes = b.sizeBytes;
        this.durationSeconds = b.durationSeconds;
        this.formatName = b.formatName;
        this.formatLongName = b.formatLongName;
        this.overallBitrate = b.overallBitrate;
        this.video = b.video;
        this.audioStreams = Collections.unmodifiableList(new ArrayList<>(b.audioStreams));
        this.subtitleStreams = Collections.unmodifiableList(new ArrayList<>(b.subtitleStreams));
        this.tags = Collections.unmodifiableMap(new LinkedHashMap<>(b.tags));
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Path (desktop) or content URI (Android) of the file. */
    public String location() {
        return location;
    }

    public String fileName() {
        return fileName;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public double durationSeconds() {
        return durationSeconds;
    }

    /** Short container name from the probe, e.g. {@code mov,mp4,m4a,3gp,3g2,mj2}. */
    public String formatName() {
        return formatName;
    }

    /** Long container name, e.g. {@code QuickTime / MOV}. */
    public String formatLongName() {
        return formatLongName;
    }

    /** Overall bitrate in bits per second (falls back to size divided by duration). */
    public long overallBitrate() {
        if (overallBitrate > 0) {
            return overallBitrate;
        }
        if (durationSeconds > 0 && sizeBytes > 0) {
            return Math.round(sizeBytes * 8.0 / durationSeconds);
        }
        return 0;
    }

    /** The main video stream, or {@code null} for audio-only files. */
    public VideoStream video() {
        return video;
    }

    public boolean hasVideo() {
        return video != null;
    }

    public List<AudioStream> audioStreams() {
        return audioStreams;
    }

    public boolean hasAudio() {
        return !audioStreams.isEmpty();
    }

    /** The first (default) audio stream, or {@code null}. */
    public AudioStream audio() {
        return audioStreams.isEmpty() ? null : audioStreams.get(0);
    }

    public List<SubtitleStream> subtitleStreams() {
        return subtitleStreams;
    }

    public Map<String, String> tags() {
        return tags;
    }

    /** Sum of all audio bitrates (guessed where unknown). */
    public long totalAudioBitrate() {
        long sum = 0;
        for (AudioStream a : audioStreams) {
            sum += a.bitrateOrGuess();
        }
        return sum;
    }

    /**
     * Video bitrate in bits per second. When the container does not store it
     * (common for MKV and WebM) it is derived from the overall bitrate.
     */
    public long videoBitrate() {
        if (video == null) {
            return 0;
        }
        if (video.bitrate() > 0) {
            return video.bitrate();
        }
        long derived = Math.round(overallBitrate() * 0.99) - totalAudioBitrate();
        return Math.max(derived, 50_000L);
    }

    /** Builder for {@link MediaInfo}. */
    public static final class Builder {
        private String location = "";
        private String fileName = "";
        private long sizeBytes;
        private double durationSeconds;
        private String formatName = "";
        private String formatLongName = "";
        private long overallBitrate;
        private VideoStream video;
        private final List<AudioStream> audioStreams = new ArrayList<>();
        private final List<SubtitleStream> subtitleStreams = new ArrayList<>();
        private final Map<String, String> tags = new LinkedHashMap<>();

        public Builder location(String v) { location = v == null ? "" : v; return this; }
        public Builder fileName(String v) { fileName = v == null ? "" : v; return this; }
        public Builder sizeBytes(long v) { sizeBytes = Math.max(0, v); return this; }
        public Builder durationSeconds(double v) { durationSeconds = Math.max(0, v); return this; }
        public Builder formatName(String v) { formatName = v == null ? "" : v; return this; }
        public Builder formatLongName(String v) { formatLongName = v == null ? "" : v; return this; }
        public Builder overallBitrate(long v) { overallBitrate = Math.max(0, v); return this; }
        public Builder video(VideoStream v) { video = v; return this; }
        public Builder addAudio(AudioStream a) { audioStreams.add(a); return this; }
        public Builder addSubtitle(SubtitleStream s) { subtitleStreams.add(s); return this; }
        public Builder tag(String key, String value) {
            if (key != null && value != null && !value.isEmpty()) {
                tags.put(key, value);
            }
            return this;
        }

        public MediaInfo build() {
            return new MediaInfo(this);
        }
    }
}
