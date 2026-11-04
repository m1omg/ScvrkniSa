package io.github.m1omg.shrink.desktop.engine;

import io.github.m1omg.shrink.core.model.AudioStream;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.SubtitleStream;
import io.github.m1omg.shrink.core.model.VideoStream;
import io.github.m1omg.shrink.core.store.Json;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Turns the JSON printed by {@code ffprobe -show_format -show_streams} into a {@link MediaInfo}. */
public final class ProbeParser {

    private ProbeParser() {
    }

    public static MediaInfo parse(String json, String location, String fileName, long fileSize)
            throws Json.JsonException {
        Map<String, Object> root = Json.parseObject(json);
        Map<String, Object> format = Json.getObject(root, "format");

        MediaInfo.Builder b = MediaInfo.builder()
                .location(location)
                .fileName(fileName)
                .sizeBytes(fileSize > 0 ? fileSize : Json.getLong(format, "size", 0))
                .formatName(Json.getString(format, "format_name", ""))
                .formatLongName(Json.getString(format, "format_long_name", ""))
                .overallBitrate(Json.getLong(format, "bit_rate", 0));

        Map<String, Object> formatTags = Json.getObject(format, "tags");
        for (Map.Entry<String, Object> e : formatTags.entrySet()) {
            if (e.getValue() instanceof String) {
                b.tag(e.getKey().toLowerCase(Locale.ROOT), (String) e.getValue());
            }
        }

        double duration = Json.getDouble(format, "duration", 0);
        VideoStream video = null;
        int videoPriority = -1;
        for (Object item : Json.getList(root, "streams")) {
            Map<String, Object> s = Json.asObject(item);
            String type = Json.getString(s, "codec_type", "");
            Map<String, Object> disposition = Json.getObject(s, "disposition");
            Map<String, Object> tags = Json.getObject(s, "tags");
            switch (type) {
                case "video": {
                    if (Json.getLong(disposition, "attached_pic", 0) == 1) {
                        continue; // album art, not a real video stream
                    }
                    int priority = Json.getLong(disposition, "default", 0) == 1 ? 1 : 0;
                    if (video == null || priority > videoPriority) {
                        video = parseVideo(s, tags);
                        videoPriority = priority;
                    }
                    if (duration <= 0) {
                        duration = Json.getDouble(s, "duration", 0);
                    }
                    break;
                }
                case "audio":
                    b.addAudio(AudioStream.builder()
                            .index((int) Json.getLong(s, "index", 0))
                            .codecName(Json.getString(s, "codec_name", ""))
                            .codecLongName(Json.getString(s, "codec_long_name", ""))
                            .profile(Json.getString(s, "profile", ""))
                            .sampleRate((int) Json.getLong(s, "sample_rate", 0))
                            .channels((int) Json.getLong(s, "channels", 2))
                            .channelLayout(Json.getString(s, "channel_layout", ""))
                            .bitrate(bitrate(s, tags))
                            .language(language(tags))
                            .title(Json.getString(tags, "title", ""))
                            .build());
                    if (duration <= 0) {
                        duration = Json.getDouble(s, "duration", 0);
                    }
                    break;
                case "subtitle":
                    b.addSubtitle(new SubtitleStream((int) Json.getLong(s, "index", 0),
                            Json.getString(s, "codec_name", ""), language(tags)));
                    break;
                default:
                    break;
            }
        }
        b.video(video);
        b.durationSeconds(duration);
        return b.build();
    }

    private static VideoStream parseVideo(Map<String, Object> s, Map<String, Object> tags) {
        double fps = fraction(Json.getString(s, "avg_frame_rate", "0/0"));
        if (fps <= 0 || fps > 1000) {
            fps = fraction(Json.getString(s, "r_frame_rate", "0/0"));
        }
        if (fps > 1000) {
            fps = 0;
        }
        String pixFmt = Json.getString(s, "pix_fmt", "");
        int bitDepth = (int) Json.getLong(s, "bits_per_raw_sample", 0);
        if (bitDepth <= 0) {
            bitDepth = pixFmt.contains("10") ? 10 : pixFmt.contains("12") ? 12 : 8;
        }
        long frames = Json.getLong(s, "nb_frames", 0);
        if (frames <= 0) {
            frames = Json.getLong(tags, "NUMBER_OF_FRAMES", Json.getLong(tags, "NUMBER_OF_FRAMES-eng", 0));
        }
        return VideoStream.builder()
                .index((int) Json.getLong(s, "index", 0))
                .codecName(Json.getString(s, "codec_name", ""))
                .codecLongName(Json.getString(s, "codec_long_name", ""))
                .profile(Json.getString(s, "profile", ""))
                .width((int) Json.getLong(s, "width", 0))
                .height((int) Json.getLong(s, "height", 0))
                .frameRate(fps)
                .bitrate(bitrate(s, tags))
                .pixelFormat(pixFmt)
                .bitDepth(bitDepth)
                .rotation(rotation(s, tags))
                .colorTransfer(Json.getString(s, "color_transfer", null))
                .colorPrimaries(Json.getString(s, "color_primaries", null))
                .colorSpace(Json.getString(s, "color_space", null))
                .frameCount(frames)
                .codecTag(Json.getString(s, "codec_tag_string", ""))
                .build();
    }

    /** Stream bitrate, or the "BPS" statistics tag that Matroska files carry instead. */
    private static long bitrate(Map<String, Object> s, Map<String, Object> tags) {
        long b = Json.getLong(s, "bit_rate", 0);
        if (b <= 0) {
            b = Json.getLong(tags, "BPS", 0);
        }
        if (b <= 0) {
            b = Json.getLong(tags, "BPS-eng", 0);
        }
        return b;
    }

    private static String language(Map<String, Object> tags) {
        String lang = Json.getString(tags, "language", "");
        return "und".equals(lang) ? "" : lang;
    }

    /**
     * Clockwise rotation players apply. ffprobe reports the display matrix
     * rotation counter-clockwise (an upright phone video shows -90), older files
     * use a clockwise "rotate" tag.
     */
    static int rotation(Map<String, Object> s, Map<String, Object> tags) {
        List<Object> sideData = Json.getList(s, "side_data_list");
        for (Object item : sideData) {
            Map<String, Object> d = Json.asObject(item);
            if (d.containsKey("rotation")) {
                long ccw = Math.round(Json.getDouble(d, "rotation", 0));
                return (int) ((((-ccw) % 360) + 360) % 360);
            }
        }
        long cw = Json.getLong(tags, "rotate", 0);
        return (int) (((cw % 360) + 360) % 360);
    }

    /** Parses {@code "30000/1001"} → 29.97. */
    static double fraction(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int slash = text.indexOf('/');
        try {
            if (slash < 0) {
                return Double.parseDouble(text);
            }
            double num = Double.parseDouble(text.substring(0, slash));
            double den = Double.parseDouble(text.substring(slash + 1));
            return den == 0 ? 0 : num / den;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
