package io.github.m1omg.shrink.core.model;

import java.util.Locale;

/** Formats for extracted audio. */
public enum AudioFileFormat {
    /** AAC audio in an MPEG-4 container. */
    M4A("m4a", "ipod", "audio/mp4", AudioCodec.AAC),
    MP3("mp3", "mp3", "audio/mpeg", AudioCodec.MP3);

    private final String extension;
    private final String ffmpegMuxer;
    private final String mimeType;
    private final AudioCodec codec;

    AudioFileFormat(String extension, String ffmpegMuxer, String mimeType, AudioCodec codec) {
        this.extension = extension;
        this.ffmpegMuxer = ffmpegMuxer;
        this.mimeType = mimeType;
        this.codec = codec;
    }

    public String extension() {
        return extension;
    }

    public String ffmpegMuxer() {
        return ffmpegMuxer;
    }

    public String mimeType() {
        return mimeType;
    }

    public AudioCodec codec() {
        return codec;
    }

    public String displayName() {
        return name();
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AudioFileFormat fromId(String id, AudioFileFormat fallback) {
        if (id != null) {
            for (AudioFileFormat f : values()) {
                if (f.id().equalsIgnoreCase(id)) {
                    return f;
                }
            }
        }
        return fallback;
    }
}
