package io.github.m1omg.shrink.core.plan;

import io.github.m1omg.shrink.core.model.CompressionPreset;
import io.github.m1omg.shrink.core.model.Container;
import io.github.m1omg.shrink.core.model.EncoderSpeed;
import io.github.m1omg.shrink.core.model.RateControl;
import io.github.m1omg.shrink.core.model.TimeRange;
import io.github.m1omg.shrink.core.model.VideoCodec;

/**
 * What the user asked for on the compression screen. Everything except the
 * preset is optional; {@code null} means "choose automatically".
 */
public final class CompressionRequest {
    private final CompressionPreset preset;
    private final long targetBytes;
    private final VideoCodec codec;
    private final Container container;
    private final RateControl rateControl;
    private final Long videoBitrate;
    private final Integer crf;
    private final EncoderSpeed speed;
    private final Integer shortSide;
    private final Double frameRate;
    private final AudioChoice audio;
    private final Long audioBitrate;
    private final Boolean hardware;
    private final TimeRange trim;

    private CompressionRequest(Builder b) {
        this.preset = b.preset;
        this.targetBytes = b.targetBytes;
        this.codec = b.codec;
        this.container = b.container;
        this.rateControl = b.rateControl;
        this.videoBitrate = b.videoBitrate;
        this.crf = b.crf;
        this.speed = b.speed;
        this.shortSide = b.shortSide;
        this.frameRate = b.frameRate;
        this.audio = b.audio;
        this.audioBitrate = b.audioBitrate;
        this.hardware = b.hardware;
        this.trim = b.trim;
    }

    public static Builder builder(CompressionPreset preset) {
        return new Builder(preset);
    }

    public static CompressionRequest of(CompressionPreset preset) {
        return new Builder(preset).build();
    }

    public static CompressionRequest targetSize(long bytes) {
        return new Builder(CompressionPreset.TARGET_SIZE).targetBytes(bytes).build();
    }

    public Builder toBuilder() {
        Builder b = new Builder(preset);
        b.targetBytes = targetBytes;
        b.codec = codec;
        b.container = container;
        b.rateControl = rateControl;
        b.videoBitrate = videoBitrate;
        b.crf = crf;
        b.speed = speed;
        b.shortSide = shortSide;
        b.frameRate = frameRate;
        b.audio = audio;
        b.audioBitrate = audioBitrate;
        b.hardware = hardware;
        b.trim = trim;
        return b;
    }

    public CompressionPreset preset() { return preset; }
    public long targetBytes() { return targetBytes; }
    public VideoCodec codec() { return codec; }
    public Container container() { return container; }
    public RateControl rateControl() { return rateControl; }
    public Long videoBitrate() { return videoBitrate; }
    public Integer crf() { return crf; }
    public EncoderSpeed speed() { return speed; }
    /** Requested short side in pixels (e.g. 720), or {@code null} for automatic. */
    public Integer shortSide() { return shortSide; }
    public Double frameRate() { return frameRate; }
    public AudioChoice audio() { return audio == null ? AudioChoice.AUTO : audio; }
    public Long audioBitrate() { return audioBitrate; }
    public Boolean hardware() { return hardware; }
    public TimeRange trim() { return trim; }

    /** True when any advanced setting overrides the automatic choice. */
    public boolean isCustomized() {
        return codec != null || container != null || rateControl != null || videoBitrate != null || crf != null
                || speed != null || shortSide != null || frameRate != null
                || (audio != null && audio != AudioChoice.AUTO) || audioBitrate != null || hardware != null;
    }

    /** Builder for {@link CompressionRequest}. */
    public static final class Builder {
        private CompressionPreset preset;
        private long targetBytes;
        private VideoCodec codec;
        private Container container;
        private RateControl rateControl;
        private Long videoBitrate;
        private Integer crf;
        private EncoderSpeed speed;
        private Integer shortSide;
        private Double frameRate;
        private AudioChoice audio;
        private Long audioBitrate;
        private Boolean hardware;
        private TimeRange trim;

        private Builder(CompressionPreset preset) {
            this.preset = preset == null ? CompressionPreset.BALANCED : preset;
        }

        public Builder preset(CompressionPreset v) { preset = v; return this; }
        public Builder targetBytes(long v) { targetBytes = v; return this; }
        public Builder codec(VideoCodec v) { codec = v; return this; }
        public Builder container(Container v) { container = v; return this; }
        public Builder rateControl(RateControl v) { rateControl = v; return this; }
        public Builder videoBitrate(Long v) { videoBitrate = v; return this; }
        public Builder crf(Integer v) { crf = v; return this; }
        public Builder speed(EncoderSpeed v) { speed = v; return this; }
        public Builder shortSide(Integer v) { shortSide = v; return this; }
        public Builder frameRate(Double v) { frameRate = v; return this; }
        public Builder audio(AudioChoice v) { audio = v; return this; }
        public Builder audioBitrate(Long v) { audioBitrate = v; return this; }
        public Builder hardware(Boolean v) { hardware = v; return this; }
        public Builder trim(TimeRange v) { trim = v; return this; }

        /** Removes all advanced overrides, keeping preset, target size and trim. */
        public Builder resetAdvanced() {
            codec = null;
            container = null;
            rateControl = null;
            videoBitrate = null;
            crf = null;
            speed = null;
            shortSide = null;
            frameRate = null;
            audio = null;
            audioBitrate = null;
            hardware = null;
            return this;
        }

        public CompressionRequest build() {
            return new CompressionRequest(this);
        }
    }
}
