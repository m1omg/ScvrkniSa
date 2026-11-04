package io.github.m1omg.shrink.core.plan;

import io.github.m1omg.shrink.core.model.AudioCodec;
import io.github.m1omg.shrink.core.model.AudioFileFormat;
import io.github.m1omg.shrink.core.model.AudioOutput;
import io.github.m1omg.shrink.core.model.AudioStream;
import io.github.m1omg.shrink.core.model.CompressionPreset;
import io.github.m1omg.shrink.core.model.Container;
import io.github.m1omg.shrink.core.model.EncoderSpeed;
import io.github.m1omg.shrink.core.model.Job;
import io.github.m1omg.shrink.core.model.JobKind;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.RateControl;
import io.github.m1omg.shrink.core.model.SubtitleStream;
import io.github.m1omg.shrink.core.model.TimeRange;
import io.github.m1omg.shrink.core.model.VideoCodec;
import io.github.m1omg.shrink.core.model.VideoOutput;
import io.github.m1omg.shrink.core.model.VideoStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Turns what the user wants ("compress, balanced", "rotate 90°", ...) into a
 * concrete {@link Job} with codecs, bitrates and sizes, and estimates the
 * resulting file size.
 *
 * <p>The same planner is used by the desktop and the Android app, so both make
 * identical decisions. The class is pure Java and has no I/O, which makes it
 * easy to unit test.
 */
public final class JobPlanner {

    /** Aim this much below a requested target size, encoders overshoot a little. */
    static final double TARGET_SAFETY = 0.03;
    /** Below this video bitrate even tiny videos fall apart. */
    static final long MIN_VIDEO_BITRATE = 30_000;
    static final long MIN_AUDIO_BITRATE = 32_000;
    /** Hardware encoders need roughly this many more bits for the same quality. */
    static final double HARDWARE_PENALTY = 1.2;

    private static final long[] AUDIO_BITRATES = {
        32_000, 48_000, 64_000, 80_000, 96_000, 112_000, 128_000, 160_000, 192_000, 224_000, 256_000, 320_000
    };

    private final EncodingDefaults defaults;

    public JobPlanner(EncodingDefaults defaults) {
        this.defaults = defaults == null ? EncodingDefaults.standard() : defaults;
    }

    public EncodingDefaults defaults() {
        return defaults;
    }

    // ------------------------------------------------------------------ compression

    /** Plans for the four compression cards, in the order of {@link CompressionPreset#values()}. */
    public List<Plan> presetPlans(MediaInfo source, long targetBytes, TimeRange trim) {
        List<Plan> plans = new ArrayList<>();
        for (CompressionPreset preset : CompressionPreset.values()) {
            CompressionRequest.Builder request = CompressionRequest.builder(preset).trim(trim);
            if (preset == CompressionPreset.TARGET_SIZE) {
                request.targetBytes(targetBytes > 0 ? targetBytes : suggestedTargetBytes(source));
            }
            plans.add(compress(source, request.build()));
        }
        return plans;
    }

    /** A sensible starting value for the target size field: about 40 % of the original. */
    public static long suggestedTargetBytes(MediaInfo source) {
        long bytes = Math.round(source.sizeBytes() * 0.4);
        long mb = Math.max(1, Math.round(bytes / 1_000_000.0));
        return mb * 1_000_000L;
    }

    /** Plans a compression job. */
    public Plan compress(MediaInfo src, CompressionRequest req) {
        VideoStream v = requireVideo(src);
        List<PlanWarning> warnings = new ArrayList<>();
        CompressionPreset preset = req.preset();

        Container container = req.container() != null ? req.container() : defaults.container();
        VideoCodec codec = req.codec() != null ? req.codec() : defaults.codec();
        if (!container.supports(codec)) {
            codec = container.defaultVideoCodec();
        }
        EncoderSpeed speed = req.speed() != null ? req.speed() : defaults.speed();
        boolean hardware = req.hardware() != null ? req.hardware() : defaults.hardware();
        TimeRange trim = normalize(req.trim(), src);
        double duration = trim != null ? trim.duration() : src.durationSeconds();

        int srcW = v.displayWidth();
        int srcH = v.displayHeight();
        double srcFps = frameRate(v);
        double srcH264 = src.videoBitrate() / VideoCodec.efficiencyOf(v.codecName());

        // Frame rate: only the "smaller file" preset lowers it automatically.
        double outFps = srcFps;
        if (req.frameRate() != null && req.frameRate() > 0 && req.frameRate() < srcFps - 0.01) {
            outFps = req.frameRate();
        } else if (req.frameRate() == null && preset == CompressionPreset.SMALL_FILE && srcFps > 30.5) {
            outFps = reducedFrameRate(srcFps);
        }

        // Resolution: explicit choice, or a cap that depends on the preset.
        int srcShort = Math.min(srcW, srcH);
        int outShort = srcShort;
        if (req.shortSide() != null && req.shortSide() > 0 && req.shortSide() < srcShort) {
            outShort = req.shortSide();
        } else if (req.shortSide() == null && preset != CompressionPreset.TARGET_SIZE) {
            outShort = Math.min(srcShort, shortSideCap(preset));
        }
        Dimensions dims = outShort == srcShort ? new Dimensions(Dimensions.even(srcW), Dimensions.even(srcH))
                : Dimensions.scaleToShortSide(srcW, srcH, outShort);

        AudioOutput audio;
        long videoBitrate;
        RateControl rateControl;
        int crf = 0;
        boolean feasible = true;
        long minimumBytes = 0;

        if (preset == CompressionPreset.TARGET_SIZE) {
            long target = req.targetBytes() > 0 ? req.targetBytes() : suggestedTargetBytes(src);
            double totalBps = budgetBitrate(target, container, srcFps, src.hasAudio(), duration);

            audio = planAudio(src, container, preset, totalBps, req.audio(), req.audioBitrate());
            double audioBps = audioBitrateOf(src, audio);
            double videoBps = totalBps - audioBps;

            long minAudio = audio.isNone() ? 0 : Math.min(MIN_AUDIO_BITRATE, Math.round(audioBps));
            minimumBytes = sizeForBitrate(MIN_VIDEO_BITRATE + minAudio, container, srcFps, !audio.isNone(), duration);
            if (videoBps < MIN_VIDEO_BITRATE) {
                feasible = false;
                warnings.add(PlanWarning.TARGET_TOO_SMALL);
                videoBps = MIN_VIDEO_BITRATE;
            }
            if (trim == null && target >= src.sizeBytes()) {
                feasible = false;
                warnings.add(PlanWarning.TARGET_NOT_SMALLER);
            }

            // Not enough bits for the original frame rate: halve it.
            if (req.frameRate() == null && srcFps > 30.5
                    && videoBps < QualityModel.bitrateFor(srcW, srcH, srcFps, 0.05, codec)) {
                outFps = reducedFrameRate(srcFps);
            }
            // Pick the largest resolution that still looks acceptable at this bitrate.
            if (req.shortSide() == null) {
                dims = chooseResolution(srcW, srcH, outFps, videoBps, codec, hardware);
            }
            videoBitrate = Math.round(videoBps);
            rateControl = hardware ? RateControl.ONE_PASS : RateControl.TWO_PASS;
            if (req.rateControl() == RateControl.ONE_PASS) {
                rateControl = RateControl.ONE_PASS;
            }
            if (feasible && QualityModel.normalizedBpp(videoBps / codec.efficiency(), dims.width(), dims.height(),
                    outFps) < 0.015) {
                warnings.add(PlanWarning.LOW_QUALITY);
            }
        } else {
            audio = planAudio(src, container, preset, -1, req.audio(), req.audioBitrate());
            PresetParams p = PresetParams.of(preset);
            double srcAtOut = QualityModel.rescale(srcH264, srcW, srcH, srcFps, dims.width(), dims.height(), outFps);
            double cap = QualityModel.h264Bitrate(dims.width(), dims.height(), outFps, p.capBpp);
            double floor = QualityModel.h264Bitrate(dims.width(), dims.height(), outFps, p.floorBpp);
            double maxRatio = (1.0 + p.ratio) / 2.0;
            double h264Target = Math.min(cap, Math.max(p.ratio * srcAtOut, Math.min(floor, maxRatio * srcAtOut)));
            double target = h264Target * codec.efficiency() * (hardware ? HARDWARE_PENALTY : 1.0);

            // Never plan more bits than the source has (in absolute terms).
            double srcAbsoluteAtOut = QualityModel.rescale(src.videoBitrate(), srcW, srcH, srcFps,
                    dims.width(), dims.height(), outFps);
            target = Math.min(target, srcAbsoluteAtOut * p.absoluteCap);

            if (req.videoBitrate() != null && req.videoBitrate() > 0) {
                target = req.videoBitrate();
            }
            videoBitrate = Math.max(MIN_VIDEO_BITRATE, Math.round(target));

            double srcBpp = QualityModel.normalizedBpp(srcH264, srcW, srcH, srcFps);
            if (srcBpp < PresetParams.of(CompressionPreset.BALANCED).floorBpp) {
                warnings.add(PlanWarning.ALREADY_COMPRESSED);
            }

            rateControl = req.rateControl() != null ? req.rateControl()
                    : (defaults.twoPass() && !hardware ? RateControl.TWO_PASS : RateControl.ONE_PASS);
            if (hardware && rateControl == RateControl.TWO_PASS) {
                rateControl = RateControl.ONE_PASS;
            }
            if (rateControl == RateControl.CONSTANT_QUALITY) {
                crf = req.crf() != null ? clampCrf(req.crf(), codec) : presetCrf(preset, codec);
                // The bitrate becomes a ceiling so the result never grows past the source.
                if (req.videoBitrate() == null) {
                    videoBitrate = Math.max(MIN_VIDEO_BITRATE, Math.round(srcAbsoluteAtOut));
                }
            }
        }

        boolean toneMap = v.isHdr() && codec == VideoCodec.H264;
        if (toneMap) {
            warnings.add(PlanWarning.HDR_TONE_MAPPED);
        }
        boolean keepSubs = keepSubtitles(src, container, warnings);
        boolean sameSize = dims.width() == Dimensions.even(srcW) && dims.height() == Dimensions.even(srcH);

        Job.Builder b = Job.builder(JobKind.COMPRESS, src)
                .container(container)
                .trim(trim)
                .accurateTrim(true)
                .outputSize(sameSize ? 0 : dims.width(), sameSize ? 0 : dims.height())
                .outputFrameRate(Math.abs(outFps - srcFps) < 0.01 ? 0 : outFps)
                .video(VideoOutput.encode(codec, rateControl, videoBitrate, crf, speed, hardware))
                .audio(audio)
                .toneMapHdr(toneMap)
                .keepSubtitles(keepSubs)
                .keepMetadata(defaults.keepMetadata());
        Job job = withEstimate(b);
        if (feasible && job.estimatedBytes() >= src.sizeBytes() && trim == null) {
            warnings.add(PlanWarning.NOT_SMALLER);
        }
        return new Plan(job, warnings, feasible, minimumBytes);
    }

    /** Per-preset tuning values. */
    private static final class PresetParams {
        final double ratio;
        final double capBpp;
        final double floorBpp;
        final double absoluteCap;

        private PresetParams(double ratio, double capBpp, double floorBpp, double absoluteCap) {
            this.ratio = ratio;
            this.capBpp = capBpp;
            this.floorBpp = floorBpp;
            this.absoluteCap = absoluteCap;
        }

        static PresetParams of(CompressionPreset preset) {
            switch (preset) {
                case HIGH_QUALITY:
                    return new PresetParams(0.62, QualityModel.BPP_EXCELLENT, 0.03, 0.88);
                case SMALL_FILE:
                    return new PresetParams(0.22, 0.042, 0.011, 0.55);
                case BALANCED:
                default:
                    return new PresetParams(0.40, QualityModel.BPP_GOOD, 0.02, 0.72);
            }
        }
    }

    private static int shortSideCap(CompressionPreset preset) {
        switch (preset) {
            case SMALL_FILE:
                return 1080;
            case BALANCED:
                return 2160;
            default:
                return Integer.MAX_VALUE;
        }
    }

    private static int presetCrf(CompressionPreset preset, VideoCodec codec) {
        int reference = QualityModel.referenceCrf(codec);
        switch (preset) {
            case HIGH_QUALITY:
                return reference - 3;
            case SMALL_FILE:
                return reference + 4;
            default:
                return reference;
        }
    }

    private static int clampCrf(int crf, VideoCodec codec) {
        return Math.max(0, Math.min(QualityModel.maxCrf(codec), crf));
    }

    /** Usable bits per second for audio + video when the file must fit {@code targetBytes}. */
    static double budgetBitrate(long targetBytes, Container container, double fps, boolean audio, double duration) {
        if (duration <= 0) {
            return 0;
        }
        double usable = targetBytes * (1.0 - TARGET_SAFETY) - SizeEstimator.overheadBytes(container, fps, audio,
                duration);
        return Math.max(0, usable * 8.0 / duration);
    }

    private static long sizeForBitrate(double bps, Container container, double fps, boolean audio, double duration) {
        double bytes = bps * duration / 8.0 + SizeEstimator.overheadBytes(container, fps, audio, duration);
        return (long) Math.ceil(bytes / (1.0 - TARGET_SAFETY));
    }

    private static Dimensions chooseResolution(int srcW, int srcH, double fps, double videoBps, VideoCodec codec,
                                               boolean hardware) {
        int srcShort = Math.min(srcW, srcH);
        List<Integer> candidates = new ArrayList<>();
        candidates.add(srcShort);
        for (int s : Dimensions.standardShortSides()) {
            if (s < srcShort) {
                candidates.add(s);
            }
        }
        Dimensions chosen = null;
        for (int s : candidates) {
            Dimensions d = s == srcShort ? new Dimensions(Dimensions.even(srcW), Dimensions.even(srcH))
                    : Dimensions.scaleToShortSide(srcW, srcH, s);
            chosen = d;
            double needed = QualityModel.bitrateFor(d.width(), d.height(), fps, QualityModel.BPP_KEEP_RESOLUTION, codec)
                    * (hardware ? HARDWARE_PENALTY : 1.0);
            if (videoBps >= needed) {
                break;
            }
        }
        return chosen;
    }

    /**
     * Halves high frame rates until they are at most 30 fps
     * (60 → 30, 59.94 → 29.97, 50 → 25, 120 → 30).
     */
    public static double reducedFrameRate(double fps) {
        double result = fps;
        while (result > 30.5) {
            result /= 2.0;
        }
        return result;
    }

    // ------------------------------------------------------------------ other tools

    /** Cut the video to {@code range}. Without {@code accurate} the streams are copied (fast, cuts at keyframes). */
    public Plan trim(MediaInfo src, TimeRange range, boolean accurate) {
        TimeRange trim = normalize(range, src);
        Container container = sourceContainer(src);
        VideoStream v = src.video();
        List<PlanWarning> warnings = new ArrayList<>();
        boolean encode = accurate;
        if (v != null && !container.canCopyVideo(v.codecName())) {
            encode = true;
        }
        Job.Builder b = Job.builder(JobKind.TRIM, src)
                .container(container)
                .trim(trim)
                .accurateTrim(encode)
                .keepAllAudio(true)
                .keepSubtitles(keepSubtitles(src, container, warnings))
                .keepMetadata(defaults.keepMetadata());
        if (v == null) {
            b.video(VideoOutput.none());
        } else if (encode) {
            VideoCodec codec = codecFor(container, v);
            b.video(transparentVideo(src, v.displayWidth(), v.displayHeight(), frameRate(v), codec));
            b.toneMapHdr(v.isHdr() && codec == VideoCodec.H264);
        } else {
            b.video(VideoOutput.copy());
        }
        b.audio(copyOrEncodeAllAudio(src, container));
        return new Plan(withEstimate(b), warnings, true, 0);
    }

    /**
     * Rotate clockwise by 90, 180 or 270 degrees. With {@code preferLossless} and an
     * MP4/MOV file only the orientation flag is changed (instant, no quality loss).
     */
    public Plan rotate(MediaInfo src, int clockwise, boolean preferLossless) {
        VideoStream v = requireVideo(src);
        int rotation = ((clockwise % 360) + 360) % 360;
        Container container = sourceContainer(src);
        boolean lossless = preferLossless && (container == Container.MP4 || container == Container.MOV)
                && container.canCopyVideo(v.codecName());
        List<PlanWarning> warnings = new ArrayList<>();
        Job.Builder b = Job.builder(JobKind.ROTATE, src)
                .container(container)
                .rotation(rotation)
                .rotationByMetadata(lossless)
                .keepAllAudio(true)
                .keepSubtitles(keepSubtitles(src, container, warnings))
                .keepMetadata(defaults.keepMetadata())
                .audio(copyOrEncodeAllAudio(src, container));
        if (lossless) {
            b.video(VideoOutput.copy());
        } else {
            VideoCodec codec = codecFor(container, v);
            b.video(transparentVideo(src, v.displayWidth(), v.displayHeight(), frameRate(v), codec));
            b.toneMapHdr(v.isHdr() && codec == VideoCodec.H264);
        }
        return new Plan(withEstimate(b), warnings, true, 0);
    }

    /** Remove all audio tracks; the video is copied unchanged when possible. */
    public Plan mute(MediaInfo src) {
        VideoStream v = requireVideo(src);
        Container container = sourceContainer(src);
        List<PlanWarning> warnings = new ArrayList<>();
        Job.Builder b = Job.builder(JobKind.MUTE, src)
                .container(container)
                .audio(AudioOutput.none())
                .keepSubtitles(keepSubtitles(src, container, warnings))
                .keepMetadata(defaults.keepMetadata());
        if (container.canCopyVideo(v.codecName())) {
            b.video(VideoOutput.copy());
        } else {
            VideoCodec codec = codecFor(container, v);
            b.video(transparentVideo(src, v.displayWidth(), v.displayHeight(), frameRate(v), codec));
            b.toneMapHdr(v.isHdr() && codec == VideoCodec.H264);
        }
        return new Plan(withEstimate(b), warnings, true, 0);
    }

    /**
     * Save the audio as M4A (AAC) or MP3.
     *
     * @param bitrate    bits per second, or 0 for automatic
     * @param preferCopy copy the original audio unchanged when it already has the right codec
     * @param audioIndex which audio track (index into {@link MediaInfo#audioStreams()})
     */
    public Plan extractAudio(MediaInfo src, AudioFileFormat format, long bitrate, boolean preferCopy,
                             int audioIndex) {
        if (!src.hasAudio()) {
            throw new IllegalArgumentException("The file has no audio");
        }
        int index = Math.min(Math.max(audioIndex, 0), src.audioStreams().size() - 1);
        AudioStream a = src.audioStreams().get(index);
        String codecName = a.codecName().toLowerCase(Locale.ROOT);
        boolean sameCodec = (format == AudioFileFormat.M4A && codecName.equals("aac"))
                || (format == AudioFileFormat.MP3 && codecName.equals("mp3"));
        AudioOutput audio;
        if (preferCopy && sameCodec && bitrate <= 0) {
            audio = AudioOutput.copy();
        } else {
            long rate = bitrate > 0 ? bitrate : 192_000;
            int channels = format == AudioFileFormat.MP3 && a.channels() > 2 ? 2 : 0;
            audio = AudioOutput.encode(format.codec(), rate, channels);
        }
        Job.Builder b = Job.builder(JobKind.EXTRACT_AUDIO, src)
                .container(null)
                .audioFormat(format)
                .video(VideoOutput.none())
                .audio(audio)
                .audioStreamIndex(index)
                .keepMetadata(defaults.keepMetadata());
        return new Plan(withEstimate(b), new ArrayList<>(), true, 0);
    }

    /** Change the container. Streams are copied when the new container supports them. */
    public Plan convert(MediaInfo src, Container target) {
        VideoStream v = requireVideo(src);
        List<PlanWarning> warnings = new ArrayList<>();
        Job.Builder b = Job.builder(JobKind.CONVERT, src)
                .container(target)
                .keepAllAudio(true)
                .keepSubtitles(keepSubtitles(src, target, warnings))
                .keepMetadata(defaults.keepMetadata())
                .audio(copyOrEncodeAllAudio(src, target));
        if (target.canCopyVideo(v.codecName())) {
            b.video(VideoOutput.copy());
        } else {
            VideoCodec codec = target.defaultVideoCodec();
            b.video(transparentVideo(src, v.displayWidth(), v.displayHeight(), frameRate(v), codec));
            b.toneMapHdr(v.isHdr() && codec == VideoCodec.H264);
        }
        return new Plan(withEstimate(b), warnings, true, 0);
    }

    /** Scale so that the shorter side becomes {@code shortSide} pixels (e.g. 720). */
    public Plan resize(MediaInfo src, int shortSide) {
        VideoStream v = requireVideo(src);
        Dimensions d = Dimensions.scaleToShortSide(v.displayWidth(), v.displayHeight(), shortSide);
        return resize(src, d.width(), d.height());
    }

    /** Scale to an exact size; both values are rounded to even numbers. */
    public Plan resize(MediaInfo src, int width, int height) {
        VideoStream v = requireVideo(src);
        Container container = sourceContainer(src);
        int w = Dimensions.even(width);
        int h = Dimensions.even(height);
        VideoCodec codec = codecFor(container, v);
        List<PlanWarning> warnings = new ArrayList<>();
        Job.Builder b = Job.builder(JobKind.RESOLUTION, src)
                .container(container)
                .outputSize(w, h)
                .video(transparentVideo(src, w, h, frameRate(v), codec))
                .toneMapHdr(v.isHdr() && codec == VideoCodec.H264)
                .audio(copyOrEncodeAllAudio(src, container))
                .keepAllAudio(true)
                .keepSubtitles(keepSubtitles(src, container, warnings))
                .keepMetadata(defaults.keepMetadata());
        return new Plan(withEstimate(b), warnings, true, 0);
    }

    /** Change the number of frames per second (frames are dropped or repeated). */
    public Plan changeFrameRate(MediaInfo src, double fps) {
        VideoStream v = requireVideo(src);
        if (fps <= 0) {
            throw new IllegalArgumentException("Frame rate must be positive");
        }
        Container container = sourceContainer(src);
        VideoCodec codec = codecFor(container, v);
        List<PlanWarning> warnings = new ArrayList<>();
        Job.Builder b = Job.builder(JobKind.FRAME_RATE, src)
                .container(container)
                .outputFrameRate(fps)
                .video(transparentVideo(src, v.displayWidth(), v.displayHeight(), fps, codec))
                .toneMapHdr(v.isHdr() && codec == VideoCodec.H264)
                .audio(copyOrEncodeAllAudio(src, container))
                .keepAllAudio(true)
                .keepSubtitles(keepSubtitles(src, container, warnings))
                .keepMetadata(defaults.keepMetadata());
        return new Plan(withEstimate(b), warnings, true, 0);
    }

    /**
     * A short excerpt of {@code job} with exactly the same settings, used for the
     * 5-second quality preview.
     */
    public static Job preview(Job job, double start, double length) {
        double duration = job.source().durationSeconds();
        double offset = job.trim() != null ? job.trim().start() : 0;
        double available = job.trim() != null ? job.trim().duration() : duration;
        double len = Math.min(length, available);
        double s = Math.max(0, Math.min(start, available - len)) + offset;
        double e = Math.min(s + len, duration > 0 ? duration : s + len);
        if (e <= s) {
            e = s + Math.max(0.1, len);
        }
        Job.Builder b = job.toBuilder()
                .trim(new TimeRange(s, e))
                .accurateTrim(true)
                .keepSubtitles(false)
                .keepAllAudio(false);
        Job draft = b.build();
        return b.estimatedBytes(SizeEstimator.estimate(draft)).build();
    }

    /** Where the 5-second preview starts by default: the middle of the (trimmed) video. */
    public static double defaultPreviewStart(Job job, double length) {
        double available = job.trim() != null ? job.trim().duration() : job.source().durationSeconds();
        return Math.max(0, (available - length) / 2.0);
    }

    // ------------------------------------------------------------------ helpers

    private static VideoStream requireVideo(MediaInfo src) {
        if (!src.hasVideo()) {
            throw new IllegalArgumentException("The file has no video");
        }
        return src.video();
    }

    private static double frameRate(VideoStream v) {
        return v.frameRate() > 0 && v.frameRate() < 1000 ? v.frameRate() : 30.0;
    }

    private static TimeRange normalize(TimeRange trim, MediaInfo src) {
        if (trim == null) {
            return null;
        }
        double duration = src.durationSeconds();
        if (duration > 0 && trim.coversWhole(duration)) {
            return null;
        }
        if (duration > 0 && trim.end() > duration) {
            return trim.start() < duration ? new TimeRange(trim.start(), duration) : null;
        }
        return trim;
    }

    /**
     * Container for tools that should keep the file's format: the source format if
     * Shrink! can write it, otherwise MP4.
     */
    public static Container sourceContainer(MediaInfo src) {
        Container byName = Container.fromFileName(src.fileName());
        if (byName != null) {
            return byName;
        }
        String format = src.formatName() == null ? "" : src.formatName().toLowerCase(Locale.ROOT);
        if (format.contains("webm") && !format.contains("matroska")) {
            return Container.WEBM;
        }
        if (format.contains("matroska")) {
            return Container.MKV;
        }
        return Container.MP4;
    }

    /** Codec used when a tool must re-encode: keep H.265 when the source is H.265, otherwise the default. */
    private VideoCodec codecFor(Container container, VideoStream v) {
        VideoCodec same = VideoCodec.fromCodecName(v.codecName());
        if (same != null && container.supports(same) && same != VideoCodec.AV1) {
            return same;
        }
        VideoCodec preferred = defaults.codec();
        return container.supports(preferred) ? preferred : container.defaultVideoCodec();
    }

    /**
     * Video settings for tools that should not visibly change quality: constant
     * quality, capped at the (codec-adjusted) source bitrate.
     */
    private VideoOutput transparentVideo(MediaInfo src, int width, int height, double fps, VideoCodec codec) {
        VideoStream v = src.video();
        double srcH264 = src.videoBitrate() / VideoCodec.efficiencyOf(v.codecName());
        double atOut = QualityModel.rescale(srcH264, v.displayWidth(), v.displayHeight(), frameRate(v),
                width, height, fps);
        double cap = QualityModel.h264Bitrate(width, height, fps, QualityModel.BPP_EXCELLENT * 1.3);
        long bitrate = Math.max(MIN_VIDEO_BITRATE, Math.round(Math.min(atOut, cap) * codec.efficiency()
                * (defaults.hardware() ? HARDWARE_PENALTY : 1.0)));
        int crf = QualityModel.referenceCrf(codec) - 4;
        return VideoOutput.encode(codec, RateControl.CONSTANT_QUALITY, bitrate, crf, defaults.speed(),
                defaults.hardware());
    }

    /** Copy all audio if every track fits the container, otherwise re-encode it with the container's codec. */
    private static AudioOutput copyOrEncodeAllAudio(MediaInfo src, Container container) {
        if (!src.hasAudio()) {
            return AudioOutput.none();
        }
        boolean allCopyable = true;
        long maxBitrate = 0;
        int maxChannels = 0;
        for (AudioStream a : src.audioStreams()) {
            allCopyable &= container.canCopyAudio(a.codecName());
            maxBitrate = Math.max(maxBitrate, a.bitrateOrGuess());
            maxChannels = Math.max(maxChannels, a.channels());
        }
        if (allCopyable) {
            return AudioOutput.copy();
        }
        AudioCodec codec = container.defaultAudioCodec();
        long wanted = maxChannels > 2 ? 384_000 : (maxChannels == 1 ? 96_000 : 192_000);
        long bitrate = roundAudioBitrate(Math.min(wanted, Math.max(maxBitrate, 64_000)) * codec.efficiency());
        return AudioOutput.encode(codec, bitrate, 0);
    }

    /** Audio settings for compression. {@code totalBudget} is only used for target-size plans (else -1). */
    private static AudioOutput planAudio(MediaInfo src, Container container, CompressionPreset preset,
                                         double totalBudget, AudioChoice choice, Long requestedBitrate) {
        AudioStream a = src.audio();
        if (a == null || choice == AudioChoice.NONE) {
            return AudioOutput.none();
        }
        if (choice == AudioChoice.COPY && container.canCopyAudio(a.codecName())) {
            return AudioOutput.copy();
        }
        AudioCodec codec = container.defaultAudioCodec();
        if (choice == AudioChoice.AAC || choice == AudioChoice.OPUS || choice == AudioChoice.MP3) {
            AudioCodec wanted = AudioCodec.valueOf(choice.name());
            if (container.supports(wanted)) {
                codec = wanted;
            }
        }
        int channels = a.channels();
        int outChannels = 0;
        long stereoBitrate;
        switch (preset) {
            case HIGH_QUALITY:
                stereoBitrate = 160_000;
                break;
            case SMALL_FILE:
                stereoBitrate = 96_000;
                if (channels > 2) {
                    outChannels = 2;
                    channels = 2;
                }
                break;
            case TARGET_SIZE:
                stereoBitrate = totalBudget < 200_000 ? 48_000 : totalBudget < 600_000 ? 64_000
                        : totalBudget < 1_500_000 ? 96_000 : 128_000;
                if (channels > 2) {
                    outChannels = 2;
                    channels = 2;
                }
                break;
            case BALANCED:
            default:
                stereoBitrate = 128_000;
                break;
        }
        double channelFactor = channels <= 1 ? 0.6 : channels <= 2 ? 1.0 : channels / 2.0 * 0.75;
        long bitrate = roundAudioBitrate(stereoBitrate * channelFactor * codec.efficiency());
        if (requestedBitrate != null && requestedBitrate > 0) {
            return AudioOutput.encode(codec, requestedBitrate, outChannels);
        }
        String sourceCodec = a.codecName().toLowerCase(Locale.ROOT);
        boolean lossy = !(sourceCodec.startsWith("pcm_") || sourceCodec.equals("flac") || sourceCodec.equals("alac")
                || sourceCodec.equals("truehd"));
        if (choice == AudioChoice.AUTO && lossy && container.canCopyAudio(sourceCodec)
                && a.bitrateOrGuess() <= bitrate * 1.15 && outChannels == 0) {
            return AudioOutput.copy();
        }
        if (lossy && a.bitrate() > 0) {
            bitrate = Math.min(bitrate, roundAudioBitrate(a.bitrate()));
        }
        return AudioOutput.encode(codec, Math.max(bitrate, MIN_AUDIO_BITRATE), outChannels);
    }

    private static double audioBitrateOf(MediaInfo src, AudioOutput audio) {
        if (audio.isNone() || !src.hasAudio()) {
            return 0;
        }
        return audio.isCopy() ? src.audio().bitrateOrGuess() : audio.bitrate();
    }

    /** Rounds to the nearest common audio bitrate (32, 48, 64, ... 320 kbps). */
    static long roundAudioBitrate(double bps) {
        long best = AUDIO_BITRATES[0];
        for (long candidate : AUDIO_BITRATES) {
            if (Math.abs(candidate - bps) < Math.abs(best - bps)) {
                best = candidate;
            }
        }
        return best;
    }

    /** Common audio bitrates for selection lists. */
    public static long[] audioBitrates() {
        return Arrays.copyOf(AUDIO_BITRATES, AUDIO_BITRATES.length);
    }

    private static boolean keepSubtitles(MediaInfo src, Container container, List<PlanWarning> warnings) {
        if (src.subtitleStreams().isEmpty()) {
            return false;
        }
        boolean anyKept = false;
        boolean anyDropped = false;
        for (SubtitleStream s : src.subtitleStreams()) {
            if (s.isText() || container == Container.MKV) {
                anyKept = true;
            } else {
                anyDropped = true;
            }
        }
        if (anyDropped) {
            warnings.add(PlanWarning.SUBTITLES_DROPPED);
        }
        return anyKept;
    }

    private static Job withEstimate(Job.Builder builder) {
        Job draft = builder.build();
        return builder.estimatedBytes(SizeEstimator.estimate(draft)).build();
    }
}
