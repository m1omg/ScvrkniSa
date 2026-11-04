package io.github.m1omg.shrink.android.media;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;

import androidx.media3.common.MimeTypes;

import io.github.m1omg.shrink.core.model.AudioCodec;
import io.github.m1omg.shrink.core.model.Container;
import io.github.m1omg.shrink.core.model.VideoCodec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Which codecs this phone can encode (hardware or software MediaCodec encoders). */
public final class EncoderSupport {

    private static Set<String> encoderTypes;

    private EncoderSupport() {
    }

    private static synchronized Set<String> types() {
        if (encoderTypes == null) {
            Set<String> set = new HashSet<>();
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (!info.isEncoder()) {
                    continue;
                }
                for (String type : info.getSupportedTypes()) {
                    set.add(type.toLowerCase(Locale.ROOT));
                }
            }
            encoderTypes = set;
        }
        return encoderTypes;
    }

    public static String mimeOf(VideoCodec codec) {
        switch (codec) {
            case H265:
                return MimeTypes.VIDEO_H265;
            case VP9:
                return MimeTypes.VIDEO_VP9;
            case AV1:
                return MimeTypes.VIDEO_AV1;
            case H264:
            default:
                return MimeTypes.VIDEO_H264;
        }
    }

    public static String mimeOf(AudioCodec codec) {
        switch (codec) {
            case OPUS:
                return MimeTypes.AUDIO_OPUS;
            case MP3:
                return MimeTypes.AUDIO_MPEG;
            case AAC:
            default:
                return MimeTypes.AUDIO_AAC;
        }
    }

    public static boolean canEncode(VideoCodec codec) {
        return types().contains(mimeOf(codec));
    }

    public static boolean canEncode(AudioCodec codec) {
        if (codec == AudioCodec.MP3) {
            return true; // our own encoder
        }
        return types().contains(mimeOf(codec));
    }

    /** Video codecs that can be encoded, H.264 first. */
    public static List<VideoCodec> videoCodecs() {
        List<VideoCodec> list = new ArrayList<>();
        for (VideoCodec c : VideoCodec.values()) {
            if (canEncode(c)) {
                list.add(c);
            }
        }
        if (list.isEmpty()) {
            list.add(VideoCodec.H264);
        }
        return list;
    }

    /** Containers Shrink! can write on this phone. WEBM needs VP9 (or VP8) and Opus encoders. */
    public static boolean canWrite(Container container) {
        if (container == Container.WEBM) {
            return (canEncode(VideoCodec.VP9) || types().contains(MimeTypes.VIDEO_VP8)) && canEncode(AudioCodec.OPUS);
        }
        return true;
    }
}
