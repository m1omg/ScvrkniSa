package io.github.m1omg.shrink.core.model;

/**
 * What happens to the video stream of a job.
 *
 * <p>Use the static factories: {@link #copy()}, {@link #none()} or
 * {@link #encode(VideoCodec, RateControl, long, int, EncoderSpeed, boolean)}.
 */
public final class VideoOutput {

    /** Copy the stream unchanged, re-encode it, or leave it out. */
    public enum Mode { COPY, ENCODE, NONE }

    private static final VideoOutput COPY = new VideoOutput(Mode.COPY, null, null, 0, 0, null, false);
    private static final VideoOutput NONE = new VideoOutput(Mode.NONE, null, null, 0, 0, null, false);

    private final Mode mode;
    private final VideoCodec codec;
    private final RateControl rateControl;
    private final long bitrate;
    private final int crf;
    private final EncoderSpeed speed;
    private final boolean hardware;

    private VideoOutput(Mode mode, VideoCodec codec, RateControl rateControl, long bitrate, int crf,
                        EncoderSpeed speed, boolean hardware) {
        this.mode = mode;
        this.codec = codec;
        this.rateControl = rateControl;
        this.bitrate = bitrate;
        this.crf = crf;
        this.speed = speed;
        this.hardware = hardware;
    }

    public static VideoOutput copy() {
        return COPY;
    }

    public static VideoOutput none() {
        return NONE;
    }

    /**
     * @param codec       target codec
     * @param rateControl how bits are distributed
     * @param bitrate     target bitrate in bits per second (ignored for constant quality)
     * @param crf         constant rate factor (only for constant quality)
     * @param speed       encoder speed preset
     * @param hardware    use a hardware encoder when one is available
     */
    public static VideoOutput encode(VideoCodec codec, RateControl rateControl, long bitrate, int crf,
                                     EncoderSpeed speed, boolean hardware) {
        if (codec == null || rateControl == null || speed == null) {
            throw new IllegalArgumentException("codec, rate control and speed are required");
        }
        return new VideoOutput(Mode.ENCODE, codec, rateControl, Math.max(bitrate, 0), crf, speed, hardware);
    }

    public Mode mode() {
        return mode;
    }

    public boolean isCopy() {
        return mode == Mode.COPY;
    }

    public boolean isEncode() {
        return mode == Mode.ENCODE;
    }

    public boolean isNone() {
        return mode == Mode.NONE;
    }

    public VideoCodec codec() {
        return codec;
    }

    public RateControl rateControl() {
        return rateControl;
    }

    public long bitrate() {
        return bitrate;
    }

    public int crf() {
        return crf;
    }

    public EncoderSpeed speed() {
        return speed;
    }

    public boolean hardware() {
        return hardware;
    }

    public VideoOutput withBitrate(long newBitrate) {
        if (mode != Mode.ENCODE) {
            return this;
        }
        return new VideoOutput(mode, codec, rateControl, newBitrate, crf, speed, hardware);
    }

    public VideoOutput withRateControl(RateControl rc) {
        if (mode != Mode.ENCODE) {
            return this;
        }
        return new VideoOutput(mode, codec, rc, bitrate, crf, speed, hardware);
    }

    public VideoOutput withHardware(boolean hw) {
        if (mode != Mode.ENCODE) {
            return this;
        }
        return new VideoOutput(mode, codec, rateControl, bitrate, crf, speed, hw);
    }

    @Override
    public String toString() {
        if (mode != Mode.ENCODE) {
            return mode.name();
        }
        return "ENCODE " + codec + " " + rateControl + " " + bitrate + "bps crf=" + crf + " " + speed
                + (hardware ? " hw" : "");
    }
}
