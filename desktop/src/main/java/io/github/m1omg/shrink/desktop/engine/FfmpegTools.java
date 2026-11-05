package io.github.m1omg.shrink.desktop.engine;

import io.github.m1omg.shrink.core.model.VideoCodec;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

/**
 * A usable ffmpeg/ffprobe pair together with what it can do (encoders and
 * filters), as found by {@link FfmpegLocator}.
 */
public final class FfmpegTools {

    /** Where the programs come from. */
    public enum Source { BUNDLED, SYSTEM, CUSTOM }

    private final Path ffmpeg;
    private final Path ffprobe;
    private final Source source;
    private final String version;
    private final Set<String> encoders;
    private final Set<String> filters;
    private final Set<String> workingHardware;
    private final String hardwareDecoder;

    FfmpegTools(Path ffmpeg, Path ffprobe, Source source, String version, Set<String> encoders, Set<String> filters,
                Set<String> workingHardware, String hardwareDecoder) {
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
        this.source = source;
        this.version = version;
        this.encoders = Collections.unmodifiableSet(encoders);
        this.filters = Collections.unmodifiableSet(filters);
        this.workingHardware = Collections.unmodifiableSet(workingHardware);
        this.hardwareDecoder = hardwareDecoder;
    }

    public Path ffmpeg() {
        return ffmpeg;
    }

    public Path ffprobe() {
        return ffprobe;
    }

    public Source source() {
        return source;
    }

    /** Version number such as {@code 7.1}. */
    public String version() {
        return version;
    }

    public boolean hasEncoder(String name) {
        return encoders.contains(name);
    }

    public boolean hasFilter(String name) {
        return filters.contains(name);
    }

    /** Whether HDR video can be converted to SDR (needs the zscale filter). */
    public boolean canToneMap() {
        return hasFilter("zscale") && hasFilter("tonemap");
    }

    /** Name of the software encoder for a codec, or {@code null} if this ffmpeg has none. */
    public String softwareEncoder(VideoCodec codec) {
        switch (codec) {
            case H264:
                return hasEncoder("libx264") ? "libx264" : (hasEncoder("libopenh264") ? "libopenh264" : null);
            case H265:
                return hasEncoder("libx265") ? "libx265" : null;
            case VP9:
                return hasEncoder("libvpx-vp9") ? "libvpx-vp9" : null;
            case AV1:
                if (hasEncoder("libsvtav1")) {
                    return "libsvtav1";
                }
                return hasEncoder("libaom-av1") ? "libaom-av1" : null;
            default:
                return null;
        }
    }

    /**
     * Name of a hardware encoder for a codec that was tested and works on this
     * computer (VideoToolbox on Macs, NVENC or Quick Sync elsewhere), or {@code null}.
     */
    public String hardwareEncoder(VideoCodec codec) {
        for (String name : hardwareCandidates(codec)) {
            if (workingHardware.contains(name)) {
                return name;
            }
        }
        return null;
    }

    /** Hardware encoders worth trying for a codec on this operating system, best first. */
    static java.util.List<String> hardwareCandidates(VideoCodec codec) {
        String prefix;
        switch (codec) {
            case H264:
                prefix = "h264";
                break;
            case H265:
                prefix = "hevc";
                break;
            case AV1:
                prefix = "av1";
                break;
            default:
                return java.util.Collections.emptyList();
        }
        // Only encoders that passed the start-up test are used, so the list can
        // name every vendor: VideoToolbox (Mac), NVENC (NVIDIA), Quick Sync (Intel), AMF (AMD).
        java.util.List<String> names = new java.util.ArrayList<>();
        if (codec != VideoCodec.AV1) {
            names.add(prefix + "_videotoolbox");
        }
        names.add(prefix + "_nvenc");
        names.add(prefix + "_qsv");
        names.add(prefix + "_amf");
        return names;
    }

    /** The {@code -hwaccel} method for decoding on the graphics chip, or {@code null}. */
    public String hardwareDecoder() {
        return hardwareDecoder;
    }

    /** Whether any codec can be encoded at all (in software). */
    public boolean canEncode(VideoCodec codec) {
        return softwareEncoder(codec) != null || hardwareEncoder(codec) != null;
    }

    @Override
    public String toString() {
        return "FFmpeg " + version + " (" + source.name().toLowerCase(java.util.Locale.ROOT) + ", " + ffmpeg + ")";
    }
}
