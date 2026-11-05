package io.github.m1omg.shrink.core.plan;

import io.github.m1omg.shrink.core.model.VideoCodec;

/**
 * A simple model of how many bits a video needs.
 *
 * <p>Quality levels are expressed as "bits per pixel" (bpp) of H.264 video at
 * 1080p and 30 frames per second. Bigger frames and higher frame rates need more
 * bits, but less than proportionally (neighbouring pixels and frames are similar),
 * which is why the scaling uses exponents below 1.
 */
public final class QualityModel {

    /** Pixels in one 1080p frame. */
    static final double REFERENCE_PIXELS = 1920.0 * 1080.0;
    static final double REFERENCE_FPS = 30.0;
    static final double SPATIAL_EXPONENT = 0.75;
    static final double TEMPORAL_EXPONENT = 0.6;

    /** About what streaming services use for good 1080p H.264. */
    public static final double BPP_EXCELLENT = 0.12;
    public static final double BPP_GOOD = 0.075;
    public static final double BPP_ACCEPTABLE = 0.03;
    /** Below this quality a lower resolution looks better than a starved high one. */
    public static final double BPP_KEEP_RESOLUTION = 0.04;

    private QualityModel() {
    }

    /**
     * Bitrate in bits per second that gives the requested quality.
     *
     * @param width        frame width in pixels
     * @param height       frame height in pixels
     * @param fps          frames per second
     * @param bppAt1080p30 quality level, see the {@code BPP_*} constants
     * @param codec        output codec (more efficient codecs need fewer bits)
     */
    public static double bitrateFor(int width, int height, double fps, double bppAt1080p30, VideoCodec codec) {
        return h264Bitrate(width, height, fps, bppAt1080p30) * codec.efficiency();
    }

    /** Like {@link #bitrateFor} but always for H.264. */
    public static double h264Bitrate(int width, int height, double fps, double bppAt1080p30) {
        double pixels = Math.max(1.0, (double) width * height);
        double frameRate = fps > 0 ? fps : REFERENCE_FPS;
        double spatial = Math.pow(pixels / REFERENCE_PIXELS, SPATIAL_EXPONENT);
        double temporal = Math.pow(frameRate / REFERENCE_FPS, TEMPORAL_EXPONENT);
        return bppAt1080p30 * REFERENCE_PIXELS * REFERENCE_FPS * spatial * temporal;
    }

    /**
     * The inverse of {@link #h264Bitrate}: the quality level (normalised bpp) that
     * an H.264 bitrate gives at this size and frame rate.
     */
    public static double normalizedBpp(double h264Bitrate, int width, int height, double fps) {
        double unit = h264Bitrate(width, height, fps, 1.0);
        return unit <= 0 ? 0 : h264Bitrate / unit;
    }

    /**
     * Scales a bitrate measured at one size/frame rate to another size/frame rate
     * while keeping the same quality.
     */
    public static double rescale(double bitrate, int fromW, int fromH, double fromFps,
                                 int toW, int toH, double toFps) {
        double from = h264Bitrate(fromW, fromH, fromFps, 1.0);
        double to = h264Bitrate(toW, toH, toFps, 1.0);
        return from <= 0 ? bitrate : bitrate * to / from;
    }

    /** CRF value that roughly matches {@link #BPP_GOOD} for each codec. */
    public static int referenceCrf(VideoCodec codec) {
        switch (codec) {
            case H265:
                return 28;
            case VP9:
                return 33;
            case AV1:
                return 35;
            case H264:
            default:
                return 23;
        }
    }

    /** Highest CRF value the encoder accepts. */
    public static int maxCrf(VideoCodec codec) {
        return codec == VideoCodec.VP9 || codec == VideoCodec.AV1 ? 63 : 51;
    }

    /**
     * Estimated bitrate for constant-quality encoding. Each 6 CRF steps roughly
     * halve or double the bitrate.
     */
    public static double bitrateForCrf(int width, int height, double fps, int crf, VideoCodec codec) {
        double bpp = BPP_GOOD * Math.pow(2.0, (referenceCrf(codec) - crf) / 6.0);
        return bitrateFor(width, height, fps, bpp, codec);
    }
}
