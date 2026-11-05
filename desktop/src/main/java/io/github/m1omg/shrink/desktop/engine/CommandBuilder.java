package io.github.m1omg.shrink.desktop.engine;

import io.github.m1omg.shrink.core.model.AudioCodec;
import io.github.m1omg.shrink.core.model.AudioFileFormat;
import io.github.m1omg.shrink.core.model.AudioOutput;
import io.github.m1omg.shrink.core.model.AudioStream;
import io.github.m1omg.shrink.core.model.Container;
import io.github.m1omg.shrink.core.model.EncoderSpeed;
import io.github.m1omg.shrink.core.model.Job;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.RateControl;
import io.github.m1omg.shrink.core.model.SubtitleStream;
import io.github.m1omg.shrink.core.model.VideoCodec;
import io.github.m1omg.shrink.core.model.VideoOutput;
import io.github.m1omg.shrink.core.model.VideoStream;
import io.github.m1omg.shrink.desktop.util.Platform;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Translates a {@link Job} into ffmpeg command lines.
 *
 * <p>Two-pass encodes produce two commands: the first only analyses the video
 * (output goes to the null device), the second writes the file using the
 * statistics from the first. Pass log files are referenced by relative name,
 * so the commands must run with {@code workDir} as the working directory
 * (this also avoids problems with ':' in Windows paths inside x265 options).
 */
public final class CommandBuilder {

    /** One ffmpeg invocation. {@code weight} is its share of the total work (0..1). */
    public record Pass(List<String> args, double weight, int number, int count) {
        public Pass {
            args = Collections.unmodifiableList(new ArrayList<>(args));
        }
    }

    /**
     * All passes of a job plus the encoder that was chosen.
     *
     * @param hardware       a hardware encoder is used
     * @param hardwareDecode the video is decoded on the graphics chip
     */
    public record Commands(List<Pass> passes, String videoEncoder, boolean hardware, boolean hardwareDecode) {
        /** Whether any hardware acceleration is involved (so a software retry makes sense). */
        public boolean usesHardware() {
            return hardware || hardwareDecode;
        }
    }

    static final String PASS_LOG = "shrink-pass";

    private final FfmpegTools tools;

    public CommandBuilder(FfmpegTools tools) {
        this.tools = tools;
    }

    /**
     * @param allowHardware use a hardware encoder if the job asks for one and it exists
     */
    public Commands build(Job job, Path input, Path output, boolean allowHardware) throws EngineException {
        MediaInfo src = job.source();
        VideoStream v = src.video();
        VideoOutput video = job.video();
        boolean encodeVideo = video.isEncode() && v != null && !job.isAudioOnly();

        String encoder = null;
        boolean hardware = false;
        if (encodeVideo) {
            if (allowHardware && video.hardware()) {
                encoder = tools.hardwareEncoder(video.codec());
                hardware = encoder != null;
            }
            if (encoder == null) {
                encoder = tools.softwareEncoder(video.codec());
            }
            if (encoder == null) {
                throw new EngineException("This FFmpeg cannot encode " + video.codec().displayName());
            }
        }
        boolean twoPass = encodeVideo && !hardware && video.rateControl() == RateControl.TWO_PASS
                && supportsTwoPass(encoder);
        // Decoding on the graphics chip helps most with big HEVC/H.264 sources (macOS VideoToolbox).
        String hwaccel = encodeVideo && allowHardware && video.hardware() ? tools.hardwareDecoder() : null;

        List<Pass> passes = new ArrayList<>();
        if (twoPass) {
            passes.add(new Pass(firstPass(job, input, encoder, hwaccel), 0.35, 1, 2));
            passes.add(new Pass(fullCommand(job, input, output, encoder, hardware, 2, hwaccel), 0.65, 2, 2));
        } else {
            passes.add(new Pass(fullCommand(job, input, output, encoder, hardware, 0, hwaccel), 1.0, 1, 1));
        }
        return new Commands(passes, encoder, hardware, hwaccel != null);
    }

    private static boolean supportsTwoPass(String encoder) {
        return encoder.equals("libx264") || encoder.equals("libx265") || encoder.equals("libvpx-vp9")
                || encoder.equals("libaom-av1");
    }

    private List<String> start() {
        return new ArrayList<>(Arrays.asList(tools.ffmpeg().toString(), "-hide_banner", "-nostdin", "-y",
                "-loglevel", "error", "-progress", "pipe:1", "-nostats"));
    }

    private static void inputOptions(List<String> cmd, Job job, Path input, String hwaccel) {
        if (hwaccel != null) {
            cmd.add("-hwaccel");
            cmd.add(hwaccel);
        }
        if (job.trim() != null && job.trim().start() > 0) {
            cmd.add("-ss");
            cmd.add(seconds(job.trim().start()));
        }
        if (job.rotationByMetadata() && job.source().hasVideo()) {
            int total = (job.source().video().rotation() + job.rotation()) % 360;
            cmd.add("-display_rotation:v:0");
            cmd.add(Integer.toString(displayRotation(total)));
        }
        cmd.add("-i");
        cmd.add(input.toString());
        if (job.trim() != null) {
            cmd.add("-t");
            cmd.add(seconds(job.trim().duration()));
        }
    }

    /** Analysis pass of a two-pass encode: video only, written nowhere. */
    private List<String> firstPass(Job job, Path input, String encoder, String hwaccel) {
        List<String> cmd = start();
        inputOptions(cmd, job, input, hwaccel);
        cmd.add("-map");
        cmd.add("0:" + job.source().video().index());
        videoOptions(cmd, job, encoder, false, 1);
        cmd.addAll(Arrays.asList("-an", "-sn", "-dn", "-f", "null", Platform.nullDevice()));
        return cmd;
    }

    private List<String> fullCommand(Job job, Path input, Path output, String encoder, boolean hardware, int pass,
                                     String hwaccel) {
        MediaInfo src = job.source();
        VideoStream v = src.video();
        List<String> cmd = start();
        inputOptions(cmd, job, input, hwaccel);

        // Which streams go into the output.
        boolean withVideo = !job.isAudioOnly() && !job.video().isNone() && v != null;
        boolean withAudio = !job.audio().isNone() && src.hasAudio();
        if (withVideo) {
            cmd.add("-map");
            cmd.add("0:" + v.index());
        }
        if (withAudio) {
            if (job.keepAllAudio() && !job.isAudioOnly()) {
                for (AudioStream a : src.audioStreams()) {
                    cmd.add("-map");
                    cmd.add("0:" + a.index());
                }
            } else {
                int i = Math.min(Math.max(job.audioStreamIndex(), 0), src.audioStreams().size() - 1);
                cmd.add("-map");
                cmd.add("0:" + src.audioStreams().get(i).index());
            }
        }
        List<SubtitleStream> subtitles = new ArrayList<>();
        if (job.keepSubtitles() && !job.isAudioOnly() && job.container() != null) {
            for (SubtitleStream s : src.subtitleStreams()) {
                if (s.isText() || job.container() == Container.MKV) {
                    subtitles.add(s);
                    cmd.add("-map");
                    cmd.add("0:" + s.index());
                }
            }
        }

        if (job.keepMetadata()) {
            cmd.addAll(Arrays.asList("-map_metadata", "0"));
        } else {
            cmd.addAll(Arrays.asList("-map_metadata", "-1", "-map_chapters", "-1"));
        }

        // Video
        if (!withVideo) {
            cmd.add("-vn");
        } else if (job.video().isCopy()) {
            cmd.addAll(Arrays.asList("-c:v", "copy"));
            if (isMp4Family(job) && "hevc".equals(v.codecName())) {
                cmd.addAll(Arrays.asList("-tag:v", "hvc1"));
            }
        } else {
            videoOptions(cmd, job, encoder, hardware, pass);
        }

        // Audio
        if (!withAudio) {
            cmd.add("-an");
        } else {
            audioOptions(cmd, job);
        }

        // Subtitles
        if (!subtitles.isEmpty()) {
            if (job.container() == Container.MKV) {
                for (int i = 0; i < subtitles.size(); i++) {
                    String codec = "mov_text".equals(subtitles.get(i).codecName()) ? "srt" : "copy";
                    cmd.add("-c:s:" + i);
                    cmd.add(codec);
                }
            } else if (job.container() == Container.WEBM) {
                cmd.addAll(Arrays.asList("-c:s", "webvtt"));
            } else {
                cmd.addAll(Arrays.asList("-c:s", "mov_text"));
            }
        } else {
            cmd.add("-sn");
        }
        cmd.add("-dn");

        if (job.trim() != null && !job.video().isEncode()) {
            cmd.addAll(Arrays.asList("-avoid_negative_ts", "make_zero"));
        }
        cmd.addAll(Arrays.asList("-max_muxing_queue_size", "4096"));

        // Container
        if (job.isAudioOnly()) {
            if (job.audioFormat() == AudioFileFormat.M4A) {
                cmd.addAll(Arrays.asList("-movflags", "+faststart"));
            } else {
                cmd.addAll(Arrays.asList("-id3v2_version", "3", "-write_xing", "1"));
            }
            cmd.addAll(Arrays.asList("-f", job.audioFormat().ffmpegMuxer()));
        } else {
            if (isMp4Family(job)) {
                cmd.addAll(Arrays.asList("-movflags",
                        job.keepMetadata() ? "+faststart+use_metadata_tags" : "+faststart"));
            }
            cmd.addAll(Arrays.asList("-f", job.container().ffmpegMuxer()));
        }
        cmd.add(output.toString());
        return cmd;
    }

    private static boolean isMp4Family(Job job) {
        return job.container() == Container.MP4 || job.container() == Container.MOV;
    }

    // ------------------------------------------------------------------ video

    private void videoOptions(List<String> cmd, Job job, String encoder, boolean hardware, int pass) {
        VideoStream v = job.source().video();
        VideoOutput out = job.video();
        VideoCodec codec = out.codec();
        boolean toneMap = job.toneMapHdr() && tools.canToneMap();
        boolean keepHdr = v.isHdr() && !job.toneMapHdr() && codec != VideoCodec.H264;
        boolean tenBit = keepHdr || (v.bitDepth() > 8 && codec != VideoCodec.H264 && !toneMap);

        List<String> filters = new ArrayList<>();
        if (toneMap) {
            filters.add(toneMapFilter(v));
        }
        if (job.isScaled()) {
            filters.add("scale=" + job.outputWidth() + ":" + job.outputHeight() + ":flags=lanczos");
        } else if (v.width() % 2 != 0 || v.height() % 2 != 0) {
            filters.add("scale=trunc(iw/2)*2:trunc(ih/2)*2");
        }
        if (job.rotation() != 0 && !job.rotationByMetadata()) {
            filters.add(rotationFilter(job.rotation()));
        }
        if (job.outputFrameRate() > 0) {
            filters.add("fps=" + frameRateExpression(job.outputFrameRate()));
        }
        String pixelFormat;
        if (hardware && encoder.endsWith("_videotoolbox")) {
            pixelFormat = tenBit && codec == VideoCodec.H265 ? "p010le" : "nv12";
        } else {
            pixelFormat = tenBit ? "yuv420p10le" : "yuv420p";
        }
        filters.add("format=" + pixelFormat);
        cmd.add("-vf");
        cmd.add(String.join(",", filters));

        cmd.add("-c:v");
        cmd.add(encoder);

        long bitrate = Math.max(out.bitrate(), 30_000);
        String b = Long.toString(bitrate);
        RateControl rc = out.rateControl();
        int crf = out.crf();
        EncoderSpeed speed = out.speed();

        switch (encoder) {
            case "libx264":
                cmd.addAll(Arrays.asList("-preset", x264Preset(speed), "-profile:v", "high"));
                rateControl(cmd, rc, pass, b, bitrate, crf, true);
                break;
            case "libopenh264":
                cmd.addAll(Arrays.asList("-b:v", b));
                break;
            case "libx265": {
                cmd.addAll(Arrays.asList("-preset", x265Preset(speed)));
                StringBuilder params = new StringBuilder("log-level=error");
                if (rc == RateControl.TWO_PASS && pass > 0) {
                    cmd.addAll(Arrays.asList("-b:v", b));
                    params.append(":pass=").append(pass).append(":stats=").append(PASS_LOG).append(".log");
                } else if (rc == RateControl.CONSTANT_QUALITY) {
                    cmd.addAll(Arrays.asList("-crf", Integer.toString(crf)));
                    if (out.bitrate() > 0) {
                        params.append(":vbv-maxrate=").append(bitrate / 1000)
                                .append(":vbv-bufsize=").append(bitrate * 2 / 1000);
                    }
                } else {
                    cmd.addAll(Arrays.asList("-b:v", b, "-maxrate", Long.toString(bitrate * 3 / 2),
                            "-bufsize", Long.toString(bitrate * 3)));
                }
                if (keepHdr) {
                    // Spell out the colour description; -color_* alone does not always reach x265.
                    params.append(":colorprim=bt2020:colormatrix=bt2020nc:transfer=").append(v.colorTransfer());
                    params.append(":repeat-headers=1");
                    if ("smpte2084".equals(v.colorTransfer())) {
                        params.append(":hdr10-opt=1");
                    }
                }
                cmd.addAll(Arrays.asList("-x265-params", params.toString()));
                if (isMp4Family(job)) {
                    cmd.addAll(Arrays.asList("-tag:v", "hvc1"));
                }
                break;
            }
            case "libvpx-vp9":
                if (speed == EncoderSpeed.FASTEST) {
                    cmd.addAll(Arrays.asList("-deadline", "realtime", "-cpu-used", "8"));
                } else {
                    cmd.addAll(Arrays.asList("-deadline", "good", "-cpu-used", vp9CpuUsed(speed)));
                }
                cmd.addAll(Arrays.asList("-row-mt", "1", "-tile-columns", job.isScaled() && job.outputWidth() < 1000
                        ? "1" : "2", "-auto-alt-ref", "1", "-lag-in-frames", "25"));
                if (rc == RateControl.CONSTANT_QUALITY) {
                    cmd.addAll(Arrays.asList("-crf", Integer.toString(crf), "-b:v",
                            out.bitrate() > 0 ? b : "0"));
                } else {
                    cmd.addAll(Arrays.asList("-b:v", b));
                    if (rc == RateControl.TWO_PASS && pass > 0) {
                        cmd.addAll(Arrays.asList("-pass", Integer.toString(pass), "-passlogfile", PASS_LOG));
                    }
                }
                if (tenBit) {
                    cmd.addAll(Arrays.asList("-profile:v", "2"));
                }
                break;
            case "libsvtav1":
                cmd.addAll(Arrays.asList("-preset", svtPreset(speed)));
                if (rc == RateControl.CONSTANT_QUALITY) {
                    cmd.addAll(Arrays.asList("-crf", Integer.toString(Math.min(crf, 63))));
                } else {
                    cmd.addAll(Arrays.asList("-b:v", b));
                }
                break;
            case "libaom-av1":
                cmd.addAll(Arrays.asList("-cpu-used", aomCpuUsed(speed), "-row-mt", "1"));
                if (rc == RateControl.CONSTANT_QUALITY) {
                    cmd.addAll(Arrays.asList("-crf", Integer.toString(Math.min(crf, 63)), "-b:v", "0"));
                } else {
                    cmd.addAll(Arrays.asList("-b:v", b));
                    if (rc == RateControl.TWO_PASS && pass > 0) {
                        cmd.addAll(Arrays.asList("-pass", Integer.toString(pass), "-passlogfile", PASS_LOG));
                    }
                }
                break;
            case "h264_videotoolbox":
            case "hevc_videotoolbox":
                cmd.addAll(Arrays.asList("-b:v", b));
                if (encoder.startsWith("hevc")) {
                    cmd.addAll(Arrays.asList("-profile:v", tenBit ? "main10" : "main"));
                    if (isMp4Family(job)) {
                        cmd.addAll(Arrays.asList("-tag:v", "hvc1"));
                    }
                } else {
                    cmd.addAll(Arrays.asList("-profile:v", "high"));
                }
                break;
            case "h264_nvenc":
            case "hevc_nvenc":
                cmd.addAll(Arrays.asList("-preset", "p5", "-rc", "vbr", "-b:v", b,
                        "-maxrate", Long.toString(bitrate * 3 / 2), "-bufsize", Long.toString(bitrate * 2)));
                if (encoder.startsWith("hevc") && isMp4Family(job)) {
                    cmd.addAll(Arrays.asList("-tag:v", "hvc1"));
                }
                break;
            default:
                cmd.addAll(Arrays.asList("-b:v", b));
                if (encoder.startsWith("hevc") && isMp4Family(job)) {
                    cmd.addAll(Arrays.asList("-tag:v", "hvc1"));
                }
                break;
        }

        // Colour description of the output.
        if (toneMap) {
            cmd.addAll(Arrays.asList("-color_primaries", "bt709", "-color_trc", "bt709", "-colorspace", "bt709"));
        } else if (keepHdr) {
            cmd.addAll(Arrays.asList("-color_primaries", "bt2020", "-color_trc", v.colorTransfer(),
                    "-colorspace", "bt2020nc"));
        }
    }

    /** x264 rate control for the given mode. */
    private static void rateControl(List<String> cmd, RateControl rc, int pass, String b, long bitrate, int crf,
                                    boolean vbvForCrf) {
        switch (rc) {
            case TWO_PASS:
                cmd.addAll(Arrays.asList("-b:v", b));
                if (pass > 0) {
                    cmd.addAll(Arrays.asList("-pass", Integer.toString(pass), "-passlogfile", PASS_LOG));
                }
                break;
            case CONSTANT_QUALITY:
                cmd.addAll(Arrays.asList("-crf", Integer.toString(crf)));
                if (vbvForCrf && bitrate > 30_000) {
                    cmd.addAll(Arrays.asList("-maxrate", b, "-bufsize", Long.toString(bitrate * 2)));
                }
                break;
            case ONE_PASS:
            default:
                cmd.addAll(Arrays.asList("-b:v", b, "-maxrate", Long.toString(bitrate * 3 / 2),
                        "-bufsize", Long.toString(bitrate * 3)));
                break;
        }
    }

    /** Converts HDR (PQ or HLG) to SDR BT.709 with the Hable curve. */
    static String toneMapFilter(VideoStream v) {
        String transfer = "arib-std-b67".equals(v.colorTransfer()) ? "arib-std-b67" : "smpte2084";
        return "zscale=tin=" + transfer + ":pin=bt2020:min=bt2020nc:rin=tv:t=linear:npl=100,"
                + "format=gbrpf32le,zscale=p=bt709,tonemap=tonemap=hable:desat=0,"
                + "zscale=t=bt709:m=bt709:r=tv";
    }

    static String rotationFilter(int clockwise) {
        switch (clockwise) {
            case 90:
                return "transpose=clock";
            case 180:
                return "hflip,vflip";
            case 270:
                return "transpose=cclock";
            default:
                return "null";
        }
    }

    /**
     * Value for {@code -display_rotation}, which is counter-clockwise, for a
     * desired total clockwise rotation; kept in the range -180..180.
     */
    static int displayRotation(int clockwise) {
        int ccw = (360 - clockwise) % 360;
        return ccw > 180 ? ccw - 360 : ccw;
    }

    /** Exact fractions for NTSC frame rates (29.97 is really 30000/1001). */
    static String frameRateExpression(double fps) {
        double[][] ntsc = {{23.976, 24000}, {29.97, 30000}, {47.952, 48000}, {59.94, 60000}, {119.88, 120000}};
        for (double[] n : ntsc) {
            if (Math.abs(fps - n[0]) < 0.01) {
                return (long) n[1] + "/1001";
            }
        }
        if (Math.abs(fps - Math.rint(fps)) < 1e-6) {
            return Long.toString(Math.round(fps));
        }
        return String.format(Locale.ROOT, "%.3f", fps);
    }

    static String seconds(double s) {
        return String.format(Locale.ROOT, "%.3f", Math.max(0, s));
    }

    private static String x264Preset(EncoderSpeed s) {
        switch (s) {
            case FASTEST:
                return "veryfast";
            case FAST:
                return "faster";
            case SLOW:
                return "slow";
            default:
                return "medium";
        }
    }

    private static String x265Preset(EncoderSpeed s) {
        switch (s) {
            case FASTEST:
                return "ultrafast";
            case FAST:
                return "veryfast";
            case SLOW:
                return "medium";
            default:
                return "fast";
        }
    }

    private static String vp9CpuUsed(EncoderSpeed s) {
        switch (s) {
            case FAST:
                return "5";
            case SLOW:
                return "1";
            default:
                return "3";
        }
    }

    private static String svtPreset(EncoderSpeed s) {
        switch (s) {
            case FASTEST:
                return "12";
            case FAST:
                return "10";
            case SLOW:
                return "6";
            default:
                return "8";
        }
    }

    private static String aomCpuUsed(EncoderSpeed s) {
        switch (s) {
            case FASTEST:
                return "8";
            case FAST:
                return "6";
            case SLOW:
                return "4";
            default:
                return "5";
        }
    }

    // ------------------------------------------------------------------ audio

    private static void audioOptions(List<String> cmd, Job job) {
        AudioOutput out = job.audio();
        if (out.isCopy()) {
            cmd.addAll(Arrays.asList("-c:a", "copy"));
            return;
        }
        AudioStream a = job.source().audioStreams().get(
                Math.min(Math.max(job.audioStreamIndex(), 0), job.source().audioStreams().size() - 1));
        AudioCodec codec = out.codec();
        int channels = out.channels();
        int sourceChannels = a.channels();
        switch (codec) {
            case OPUS:
                cmd.addAll(Arrays.asList("-c:a", "libopus", "-b:a", Long.toString(out.bitrate()), "-vbr", "on"));
                if (channels == 0 && sourceChannels > 2) {
                    cmd.addAll(Arrays.asList("-af", "aformat=channel_layouts=7.1|5.1|stereo|mono"));
                }
                break;
            case MP3:
                cmd.addAll(Arrays.asList("-c:a", "libmp3lame", "-b:a", Long.toString(out.bitrate())));
                if (channels == 0 && sourceChannels > 2) {
                    channels = 2;
                }
                if (a.sampleRate() > 48_000) {
                    cmd.addAll(Arrays.asList("-ar", "48000"));
                }
                break;
            case AAC:
            default:
                cmd.addAll(Arrays.asList("-c:a", "aac", "-b:a", Long.toString(out.bitrate())));
                if (a.sampleRate() > 48_000) {
                    cmd.addAll(Arrays.asList("-ar", "48000"));
                }
                break;
        }
        if (channels > 0) {
            cmd.addAll(Arrays.asList("-ac", Integer.toString(channels)));
        }
    }
}
