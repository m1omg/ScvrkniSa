package io.github.m1omg.shrink.core.model;

/**
 * A complete description of one processing job: which file, which edits, how
 * to encode, and where to write the result.
 *
 * <p>Jobs are immutable. Create them with {@link io.github.m1omg.shrink.core.plan.JobPlanner},
 * which decides when streams can be copied and which bitrates to use. Each
 * platform then turns a job into real work (ffmpeg arguments on desktop,
 * Media3 Transformer settings on Android).
 */
public final class Job {
    private final JobKind kind;
    private final MediaInfo source;
    private final String output;
    private final Container container;
    private final AudioFileFormat audioFormat;
    private final TimeRange trim;
    private final boolean accurateTrim;
    private final int rotation;
    private final boolean rotationByMetadata;
    private final int outputWidth;
    private final int outputHeight;
    private final double outputFrameRate;
    private final VideoOutput video;
    private final AudioOutput audio;
    private final int audioStreamIndex;
    private final boolean toneMapHdr;
    private final boolean keepMetadata;
    private final boolean keepAllAudio;
    private final boolean keepSubtitles;
    private final long estimatedBytes;

    private Job(Builder b) {
        this.kind = b.kind;
        this.source = b.source;
        this.output = b.output;
        this.container = b.container;
        this.audioFormat = b.audioFormat;
        this.trim = b.trim;
        this.accurateTrim = b.accurateTrim;
        this.rotation = ((b.rotation % 360) + 360) % 360;
        this.rotationByMetadata = b.rotationByMetadata;
        this.outputWidth = b.outputWidth;
        this.outputHeight = b.outputHeight;
        this.outputFrameRate = b.outputFrameRate;
        this.video = b.video;
        this.audio = b.audio;
        this.audioStreamIndex = b.audioStreamIndex;
        this.toneMapHdr = b.toneMapHdr;
        this.keepMetadata = b.keepMetadata;
        this.keepAllAudio = b.keepAllAudio;
        this.keepSubtitles = b.keepSubtitles;
        this.estimatedBytes = b.estimatedBytes;
    }

    public static Builder builder(JobKind kind, MediaInfo source) {
        return new Builder(kind, source);
    }

    /** A builder pre-filled with this job's values, for making a modified copy. */
    public Builder toBuilder() {
        Builder b = new Builder(kind, source);
        b.output = output;
        b.container = container;
        b.audioFormat = audioFormat;
        b.trim = trim;
        b.accurateTrim = accurateTrim;
        b.rotation = rotation;
        b.rotationByMetadata = rotationByMetadata;
        b.outputWidth = outputWidth;
        b.outputHeight = outputHeight;
        b.outputFrameRate = outputFrameRate;
        b.video = video;
        b.audio = audio;
        b.audioStreamIndex = audioStreamIndex;
        b.toneMapHdr = toneMapHdr;
        b.keepMetadata = keepMetadata;
        b.keepAllAudio = keepAllAudio;
        b.keepSubtitles = keepSubtitles;
        b.estimatedBytes = estimatedBytes;
        return b;
    }

    public Job withOutput(String newOutput) {
        return toBuilder().output(newOutput).build();
    }

    public JobKind kind() {
        return kind;
    }

    public MediaInfo source() {
        return source;
    }

    /** Output path (desktop) or a platform-specific output reference. */
    public String output() {
        return output;
    }

    /** Output container for video jobs, {@code null} for audio extraction. */
    public Container container() {
        return container;
    }

    /** Output format for audio extraction, otherwise {@code null}. */
    public AudioFileFormat audioFormat() {
        return audioFormat;
    }

    public boolean isAudioOnly() {
        return audioFormat != null;
    }

    /** File extension of the output, without the dot. */
    public String outputExtension() {
        return audioFormat != null ? audioFormat.extension() : container.extension();
    }

    /** The part of the input to keep, or {@code null} for the whole file. */
    public TimeRange trim() {
        return trim;
    }

    /** Cut exactly at the requested times (requires re-encoding the video). */
    public boolean accurateTrim() {
        return accurateTrim;
    }

    /** Extra clockwise rotation in degrees: 0, 90, 180 or 270. */
    public int rotation() {
        return rotation;
    }

    /** Rotate by changing the orientation flag only (lossless). */
    public boolean rotationByMetadata() {
        return rotationByMetadata;
    }

    /** Output width in pixels, or 0 to keep the source size. Always even. */
    public int outputWidth() {
        return outputWidth;
    }

    /** Output height in pixels, or 0 to keep the source size. Always even. */
    public int outputHeight() {
        return outputHeight;
    }

    public boolean isScaled() {
        return outputWidth > 0 && outputHeight > 0;
    }

    /** Output frame rate, or 0 to keep the source frame rate. */
    public double outputFrameRate() {
        return outputFrameRate;
    }

    public VideoOutput video() {
        return video;
    }

    public AudioOutput audio() {
        return audio;
    }

    /** Which audio stream to use (index into {@link MediaInfo#audioStreams()}), usually 0. */
    public int audioStreamIndex() {
        return audioStreamIndex;
    }

    /** Convert HDR to SDR while encoding (needed for 8-bit codecs such as H.264). */
    public boolean toneMapHdr() {
        return toneMapHdr;
    }

    public boolean keepMetadata() {
        return keepMetadata;
    }

    /** Keep every audio track (e.g. several languages) instead of only the selected one. */
    public boolean keepAllAudio() {
        return keepAllAudio;
    }

    /** Keep subtitle tracks the output format can store. */
    public boolean keepSubtitles() {
        return keepSubtitles;
    }

    /** Estimated size of the result in bytes. */
    public long estimatedBytes() {
        return estimatedBytes;
    }

    /** Length of the output in seconds. */
    public double outputDuration() {
        return trim != null ? trim.duration() : source.durationSeconds();
    }

    @Override
    public String toString() {
        return "Job{" + kind + ", video=" + video + ", audio=" + audio + ", container=" + container
                + ", audioFormat=" + audioFormat + ", trim=" + trim + ", rotation=" + rotation
                + (rotationByMetadata ? "(metadata)" : "") + ", size=" + outputWidth + "x" + outputHeight
                + ", fps=" + outputFrameRate + ", est=" + estimatedBytes + "}";
    }

    /** Builder for {@link Job}. */
    public static final class Builder {
        private final JobKind kind;
        private final MediaInfo source;
        private String output = "";
        private Container container = Container.MP4;
        private AudioFileFormat audioFormat;
        private TimeRange trim;
        private boolean accurateTrim = true;
        private int rotation;
        private boolean rotationByMetadata;
        private int outputWidth;
        private int outputHeight;
        private double outputFrameRate;
        private VideoOutput video = VideoOutput.copy();
        private AudioOutput audio = AudioOutput.copy();
        private int audioStreamIndex;
        private boolean toneMapHdr;
        private boolean keepMetadata = true;
        private boolean keepAllAudio;
        private boolean keepSubtitles;
        private long estimatedBytes;

        private Builder(JobKind kind, MediaInfo source) {
            if (kind == null || source == null) {
                throw new IllegalArgumentException("kind and source are required");
            }
            this.kind = kind;
            this.source = source;
        }

        public Builder output(String v) { output = v == null ? "" : v; return this; }
        public Builder container(Container v) { container = v; return this; }
        public Builder audioFormat(AudioFileFormat v) { audioFormat = v; return this; }
        public Builder trim(TimeRange v) { trim = v; return this; }
        public Builder accurateTrim(boolean v) { accurateTrim = v; return this; }
        public Builder rotation(int v) { rotation = v; return this; }
        public Builder rotationByMetadata(boolean v) { rotationByMetadata = v; return this; }
        public Builder outputSize(int width, int height) {
            outputWidth = width;
            outputHeight = height;
            return this;
        }
        public Builder outputFrameRate(double v) { outputFrameRate = v; return this; }
        public Builder video(VideoOutput v) { video = v; return this; }
        public Builder audio(AudioOutput v) { audio = v; return this; }
        public Builder audioStreamIndex(int v) { audioStreamIndex = v; return this; }
        public Builder toneMapHdr(boolean v) { toneMapHdr = v; return this; }
        public Builder keepMetadata(boolean v) { keepMetadata = v; return this; }
        public Builder keepAllAudio(boolean v) { keepAllAudio = v; return this; }
        public Builder keepSubtitles(boolean v) { keepSubtitles = v; return this; }
        public Builder estimatedBytes(long v) { estimatedBytes = v; return this; }

        public Job build() {
            if (audioFormat == null && container == null) {
                throw new IllegalStateException("A video job needs a container");
            }
            return new Job(this);
        }
    }
}
