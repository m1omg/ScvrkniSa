package io.github.m1omg.shrink.core.model;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Output video container formats and which codecs they can hold.
 *
 * <p>The copy sets decide when Shrink! can "remux" (copy the compressed streams
 * unchanged, which is fast and lossless) instead of re-encoding.
 */
public enum Container {
    MP4("mp4", "mp4", "video/mp4",
            set("h264", "hevc", "av1", "mpeg4"),
            set("aac", "mp3", "ac3", "eac3", "alac")),
    MOV("mov", "mov", "video/quicktime",
            set("h264", "hevc", "mpeg4", "prores", "mjpeg", "dnxhd"),
            set("aac", "alac", "mp3", "ac3", "pcm_s16le", "pcm_s16be", "pcm_s24le", "pcm_s24be")),
    MKV("mkv", "matroska", "video/x-matroska",
            set("h264", "hevc", "av1", "vp8", "vp9", "mpeg4", "mpeg2video", "mpeg1video", "msmpeg4v3",
                    "theora", "prores", "mjpeg", "ffv1", "dnxhd", "vc1", "h263"),
            set("aac", "mp3", "mp2", "opus", "vorbis", "flac", "ac3", "eac3", "dts", "truehd", "alac",
                    "pcm_s16le", "pcm_s24le", "pcm_s32le", "pcm_f32le")),
    WEBM("webm", "webm", "video/webm",
            set("vp8", "vp9", "av1"),
            set("opus", "vorbis"));

    private final String extension;
    private final String ffmpegMuxer;
    private final String mimeType;
    private final Set<String> copyableVideo;
    private final Set<String> copyableAudio;

    Container(String extension, String ffmpegMuxer, String mimeType,
              Set<String> copyableVideo, Set<String> copyableAudio) {
        this.extension = extension;
        this.ffmpegMuxer = ffmpegMuxer;
        this.mimeType = mimeType;
        this.copyableVideo = copyableVideo;
        this.copyableAudio = copyableAudio;
    }

    private static Set<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    public String extension() {
        return extension;
    }

    /** Name of the ffmpeg muxer ({@code -f}). */
    public String ffmpegMuxer() {
        return ffmpegMuxer;
    }

    public String mimeType() {
        return mimeType;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Display name, e.g. "MP4". */
    public String displayName() {
        return name();
    }

    /** Whether a video stream with this (ffprobe) codec can be copied into this container. */
    public boolean canCopyVideo(String codecName) {
        return codecName != null && copyableVideo.contains(codecName.toLowerCase(Locale.ROOT));
    }

    /** Whether an audio stream with this (ffprobe) codec can be copied into this container. */
    public boolean canCopyAudio(String codecName) {
        return codecName != null && copyableAudio.contains(codecName.toLowerCase(Locale.ROOT));
    }

    /** Whether this container can hold video encoded with the given codec. */
    public boolean supports(VideoCodec codec) {
        switch (this) {
            case WEBM:
                return codec == VideoCodec.VP9 || codec == VideoCodec.AV1;
            case MOV:
                return codec == VideoCodec.H264 || codec == VideoCodec.H265;
            default:
                return true;
        }
    }

    /** Whether this container can hold audio encoded with the given codec. */
    public boolean supports(AudioCodec codec) {
        switch (this) {
            case WEBM:
                return codec == AudioCodec.OPUS;
            case MOV:
                return codec == AudioCodec.AAC || codec == AudioCodec.MP3;
            case MP4:
                return codec == AudioCodec.AAC || codec == AudioCodec.MP3;
            default:
                return true;
        }
    }

    public VideoCodec defaultVideoCodec() {
        return this == WEBM ? VideoCodec.VP9 : VideoCodec.H264;
    }

    public AudioCodec defaultAudioCodec() {
        return this == WEBM ? AudioCodec.OPUS : AudioCodec.AAC;
    }

    public static Container fromId(String id, Container fallback) {
        if (id != null) {
            for (Container c : values()) {
                if (c.id().equalsIgnoreCase(id)) {
                    return c;
                }
            }
        }
        return fallback;
    }

    /** Guesses the container from a file name extension, or returns {@code null}. */
    public static Container fromFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp4") || lower.endsWith(".m4v")) {
            return MP4;
        }
        if (lower.endsWith(".mov") || lower.endsWith(".qt")) {
            return MOV;
        }
        if (lower.endsWith(".mkv")) {
            return MKV;
        }
        if (lower.endsWith(".webm")) {
            return WEBM;
        }
        return null;
    }
}
