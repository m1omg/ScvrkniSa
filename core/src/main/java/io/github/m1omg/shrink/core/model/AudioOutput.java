package io.github.m1omg.shrink.core.model;

/** What happens to the audio of a job. */
public final class AudioOutput {

    /** Copy the stream unchanged, re-encode it, or remove it. */
    public enum Mode { COPY, ENCODE, NONE }

    private static final AudioOutput COPY = new AudioOutput(Mode.COPY, null, 0, 0);
    private static final AudioOutput NONE = new AudioOutput(Mode.NONE, null, 0, 0);

    private final Mode mode;
    private final AudioCodec codec;
    private final long bitrate;
    private final int channels;

    private AudioOutput(Mode mode, AudioCodec codec, long bitrate, int channels) {
        this.mode = mode;
        this.codec = codec;
        this.bitrate = bitrate;
        this.channels = channels;
    }

    public static AudioOutput copy() {
        return COPY;
    }

    public static AudioOutput none() {
        return NONE;
    }

    /**
     * @param codec    target codec
     * @param bitrate  bits per second
     * @param channels channel count, or 0 to keep the source layout (stereo is used above 2 channels for MP3)
     */
    public static AudioOutput encode(AudioCodec codec, long bitrate, int channels) {
        if (codec == null) {
            throw new IllegalArgumentException("codec is required");
        }
        return new AudioOutput(Mode.ENCODE, codec, bitrate, Math.max(0, channels));
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

    public AudioCodec codec() {
        return codec;
    }

    public long bitrate() {
        return bitrate;
    }

    public int channels() {
        return channels;
    }

    @Override
    public String toString() {
        return mode == Mode.ENCODE ? "ENCODE " + codec + " " + bitrate + "bps" : mode.name();
    }
}
