package io.github.m1omg.shrink.android.media;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;

import io.github.m1omg.shrink.core.model.AudioStream;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.SubtitleStream;
import io.github.m1omg.shrink.core.model.VideoStream;

import java.io.IOException;
import java.util.Locale;

/**
 * Reads a video's details with Android's MediaMetadataRetriever and
 * MediaExtractor and describes them with the shared {@link MediaInfo} model,
 * so the same planner as on desktop can be used.
 */
public final class MediaProbe {

    private MediaProbe() {
    }

    public static MediaInfo probe(Context context, Uri uri) throws IOException {
        String name = displayName(context, uri);
        long size = size(context, uri);
        MediaInfo.Builder b = MediaInfo.builder().location(uri.toString()).fileName(name).sizeBytes(size);

        long durationMs = 0;
        int rotation = 0;
        long frameCount = 0;
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(context, uri);
            durationMs = parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            rotation = (int) parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
            b.overallBitrate(parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)));
            frameCount = parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT));
            String mime = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            b.formatName(containerName(mime, name)).formatLongName(mime == null ? "" : mime);
            b.tag("creation_time", mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE));
            b.tag("location", mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION));
        } catch (RuntimeException e) {
            throw new IOException("Cannot read this file", e);
        } finally {
            try {
                mmr.release();
            } catch (IOException | RuntimeException ignored) {
                // nothing to do
            }
        }

        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(context, uri, null);
            boolean haveVideo = false;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime == null) {
                    continue;
                }
                if (mime.startsWith("video/") && !haveVideo) {
                    haveVideo = true;
                    double fps = number(f, MediaFormat.KEY_FRAME_RATE);
                    if ((fps <= 0 || fps > 1000) && frameCount > 0 && durationMs > 0) {
                        fps = frameCount * 1000.0 / durationMs;
                    }
                    int transfer = (int) number(f, MediaFormat.KEY_COLOR_TRANSFER);
                    String transferName = transfer == MediaFormat.COLOR_TRANSFER_ST2084 ? "smpte2084"
                            : transfer == MediaFormat.COLOR_TRANSFER_HLG ? "arib-std-b67" : null;
                    int bitDepth = transferName != null ? 10 : 8;
                    b.video(VideoStream.builder()
                            .index(i)
                            .codecName(codecName(mime))
                            .codecLongName(mime)
                            .width((int) number(f, MediaFormat.KEY_WIDTH))
                            .height((int) number(f, MediaFormat.KEY_HEIGHT))
                            .frameRate(fps)
                            .bitrate((long) number(f, MediaFormat.KEY_BIT_RATE))
                            .rotation(rotation)
                            .colorTransfer(transferName)
                            .bitDepth(bitDepth)
                            .frameCount(frameCount)
                            .pixelFormat(bitDepth > 8 ? "yuv420p10" : "yuv420p")
                            .build());
                    if (durationMs <= 0 && f.containsKey(MediaFormat.KEY_DURATION)) {
                        durationMs = f.getLong(MediaFormat.KEY_DURATION) / 1000;
                    }
                } else if (mime.startsWith("audio/")) {
                    b.addAudio(AudioStream.builder()
                            .index(i)
                            .codecName(codecName(mime))
                            .codecLongName(mime)
                            .sampleRate((int) number(f, MediaFormat.KEY_SAMPLE_RATE))
                            .channels(Math.max(1, (int) number(f, MediaFormat.KEY_CHANNEL_COUNT)))
                            .bitrate((long) number(f, MediaFormat.KEY_BIT_RATE))
                            .language(f.containsKey(MediaFormat.KEY_LANGUAGE)
                                    ? f.getString(MediaFormat.KEY_LANGUAGE) : "")
                            .build());
                    if (durationMs <= 0 && f.containsKey(MediaFormat.KEY_DURATION)) {
                        durationMs = f.getLong(MediaFormat.KEY_DURATION) / 1000;
                    }
                } else if (mime.startsWith("text/") || mime.contains("subrip") || mime.contains("ttml")) {
                    b.addSubtitle(new SubtitleStream(i, "subrip", f.containsKey(MediaFormat.KEY_LANGUAGE)
                            ? f.getString(MediaFormat.KEY_LANGUAGE) : ""));
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new IOException("Cannot read this file", e);
        } finally {
            extractor.release();
        }
        b.durationSeconds(durationMs / 1000.0);
        MediaInfo info = b.build();
        if (!info.hasVideo() && !info.hasAudio()) {
            throw new IOException("No audio or video");
        }
        return info;
    }

    /** A frame for the thumbnail, scaled to fit {@code maxWidth x maxHeight}. */
    public static Bitmap thumbnail(Context context, Uri uri, long timeUs, int maxWidth, int maxHeight) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(context, uri);
            Bitmap frame = mmr.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    maxWidth, maxHeight);
            if (frame == null) {
                frame = mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            }
            return frame;
        } catch (RuntimeException e) {
            return null;
        } finally {
            try {
                mmr.release();
            } catch (IOException | RuntimeException ignored) {
                // nothing to do
            }
        }
    }

    private static double number(MediaFormat f, String key) {
        if (!f.containsKey(key)) {
            return 0;
        }
        try {
            Number n = f.getNumber(key);
            return n == null ? 0 : n.doubleValue();
        } catch (ClassCastException e) {
            return 0;
        }
    }

    private static long parseLong(String s) {
        if (s == null) {
            return 0;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** MIME type to the ffprobe-style codec name used by the shared model. */
    public static String codecName(String mime) {
        switch (mime.toLowerCase(Locale.ROOT)) {
            case "video/avc":
                return "h264";
            case "video/hevc":
                return "hevc";
            case "video/x-vnd.on2.vp9":
                return "vp9";
            case "video/x-vnd.on2.vp8":
                return "vp8";
            case "video/av01":
                return "av1";
            case "video/mp4v-es":
                return "mpeg4";
            case "video/3gpp":
                return "h263";
            case "video/mpeg2":
                return "mpeg2video";
            case "audio/mp4a-latm":
                return "aac";
            case "audio/opus":
                return "opus";
            case "audio/vorbis":
                return "vorbis";
            case "audio/mpeg":
                return "mp3";
            case "audio/flac":
                return "flac";
            case "audio/raw":
                return "pcm_s16le";
            case "audio/ac3":
                return "ac3";
            case "audio/eac3":
                return "eac3";
            case "audio/3gpp":
                return "amr_nb";
            case "audio/amr-wb":
                return "amr_wb";
            default:
                int slash = mime.indexOf('/');
                return slash >= 0 ? mime.substring(slash + 1) : mime;
        }
    }

    private static String containerName(String mime, String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (mime == null) {
            return "";
        }
        switch (mime) {
            case "video/x-matroska":
            case "video/webm":
                return lower.endsWith(".webm") ? "webm" : "matroska,webm";
            case "video/mp4":
            case "video/quicktime":
            case "video/3gpp":
                return "mov,mp4,m4a,3gp,3g2,mj2";
            default:
                return mime;
        }
    }

    public static String displayName(Context context, Uri uri) {
        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            return uri.getLastPathSegment();
        }
        try (Cursor c = context.getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) {
                return c.getString(0);
            }
        } catch (RuntimeException ignored) {
            // fall back below
        }
        String last = uri.getLastPathSegment();
        return last == null ? "video.mp4" : last;
    }

    public static long size(Context context, Uri uri) {
        try (Cursor c = context.getContentResolver().query(uri, new String[] {OpenableColumns.SIZE},
                null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) {
                return c.getLong(0);
            }
        } catch (RuntimeException ignored) {
            // fall back below
        }
        try (android.content.res.AssetFileDescriptor fd = context.getContentResolver()
                .openAssetFileDescriptor(uri, "r")) {
            return fd == null ? 0 : fd.getLength();
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }
}
