package io.github.m1omg.shrink.android.media;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.muxer.BufferInfo;
import androidx.media3.muxer.Muxer;
import androidx.media3.muxer.MuxerException;

import com.google.common.collect.ImmutableList;

import io.github.m1omg.shrink.core.mkv.MatroskaWriter;
import io.github.m1omg.shrink.core.mkv.NalUnits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Connects Media3 Transformer to the shared {@link MatroskaWriter}, so Android
 * can write MKV and WebM files (Android itself has no Matroska writer).
 */
@OptIn(markerClass = UnstableApi.class)
public final class MatroskaMuxer implements Muxer {

    /** Creates Matroska ({@code webm = false}) or WebM muxers. */
    public static final class Factory implements Muxer.Factory {
        private final boolean webm;

        public Factory(boolean webm) {
            this.webm = webm;
        }

        @Override
        public Muxer create(String path) throws MuxerException {
            try {
                return new MatroskaMuxer(new MatroskaWriter(Paths.get(path), webm, "Shrink!"));
            } catch (IOException e) {
                throw new MuxerException("Cannot create " + path, e);
            }
        }

        @Override
        public ImmutableList<String> getSupportedSampleMimeTypes(int trackType) {
            if (trackType == C.TRACK_TYPE_VIDEO) {
                return webm ? ImmutableList.of(MimeTypes.VIDEO_VP9, MimeTypes.VIDEO_VP8)
                        : ImmutableList.of(MimeTypes.VIDEO_H264, MimeTypes.VIDEO_VP9, MimeTypes.VIDEO_VP8);
            }
            if (trackType == C.TRACK_TYPE_AUDIO) {
                return webm ? ImmutableList.of(MimeTypes.AUDIO_OPUS)
                        : ImmutableList.of(MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_OPUS);
            }
            return ImmutableList.of();
        }
    }

    private final MatroskaWriter writer;

    private MatroskaMuxer(MatroskaWriter writer) {
        this.writer = writer;
    }

    @Override
    public int addTrack(Format format) throws MuxerException {
        String mime = format.sampleMimeType == null ? "" : format.sampleMimeType;
        List<byte[]> init = format.initializationData;
        try {
            if (MimeTypes.isVideo(mime)) {
                String codecId;
                byte[] codecPrivate = null;
                if (MimeTypes.VIDEO_H264.equals(mime)) {
                    codecId = MatroskaWriter.CODEC_H264;
                    codecPrivate = NalUnits.avcDecoderConfigurationRecord(init);
                } else if (MimeTypes.VIDEO_VP9.equals(mime)) {
                    codecId = MatroskaWriter.CODEC_VP9;
                } else if (MimeTypes.VIDEO_VP8.equals(mime)) {
                    codecId = MatroskaWriter.CODEC_VP8;
                } else {
                    throw new MuxerException("Unsupported video format " + mime, null);
                }
                double fps = format.frameRate > 0 ? format.frameRate : 0;
                return writer.addVideoTrack(codecId, codecPrivate, format.width, format.height, fps);
            }
            if (MimeTypes.isAudio(mime)) {
                if (MimeTypes.AUDIO_AAC.equals(mime)) {
                    byte[] asc = init.isEmpty() ? new byte[0] : init.get(0);
                    return writer.addAudioTrack(MatroskaWriter.CODEC_AAC, asc, format.sampleRate,
                            format.channelCount, 0, 0);
                }
                if (MimeTypes.AUDIO_OPUS.equals(mime)) {
                    OpusConfig opus = OpusConfig.parse(init, format.sampleRate);
                    return writer.addAudioTrack(MatroskaWriter.CODEC_OPUS, opus.header, 48_000,
                            format.channelCount, opus.codecDelayNs, opus.seekPreRollNs);
                }
                throw new MuxerException("Unsupported audio format " + mime, null);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new MuxerException("Cannot add track " + mime, e);
        }
        throw new MuxerException("Unsupported track " + mime, null);
    }

    @Override
    public void writeSampleData(int trackId, ByteBuffer byteBuffer, BufferInfo bufferInfo) throws MuxerException {
        try {
            writer.writeSample(trackId, byteBuffer, bufferInfo.presentationTimeUs,
                    (bufferInfo.flags & C.BUFFER_FLAG_KEY_FRAME) != 0);
        } catch (IOException | RuntimeException e) {
            throw new MuxerException("Writing failed", e);
        }
    }

    @Override
    public void addMetadataEntry(Metadata.Entry metadataEntry) {
        // Metadata such as location is not written to Matroska files.
    }

    @Override
    public void close() throws MuxerException {
        try {
            writer.close();
        } catch (IOException e) {
            throw new MuxerException("Finishing the file failed", e);
        }
    }

    /** OpusHead plus codec delay and seek pre-roll, from either CSD layout Android uses. */
    static final class OpusConfig {
        final byte[] header;
        final long codecDelayNs;
        final long seekPreRollNs;

        private OpusConfig(byte[] header, long codecDelayNs, long seekPreRollNs) {
            this.header = header;
            this.codecDelayNs = codecDelayNs;
            this.seekPreRollNs = seekPreRollNs;
        }

        static OpusConfig parse(List<byte[]> init, int sampleRate) {
            byte[] header = null;
            long delay = -1;
            long preRoll = 80_000_000L;
            if (!init.isEmpty()) {
                byte[] first = init.get(0);
                if (startsWith(first, "AOPUSHDR")) {
                    // Android MediaCodec layout: marker, 8-byte length, payload, repeated.
                    int pos = 0;
                    while (pos + 16 <= first.length) {
                        String marker = new String(first, pos, 8, StandardCharsets.US_ASCII);
                        long length = ByteBuffer.wrap(first, pos + 8, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
                        int start = pos + 16;
                        int end = (int) Math.min(first.length, start + length);
                        if (marker.equals("AOPUSHDR")) {
                            header = Arrays.copyOfRange(first, start, end);
                        } else if (marker.equals("AOPUSDLY") && end - start >= 8) {
                            delay = ByteBuffer.wrap(first, start, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
                        } else if (marker.equals("AOPUSPRL") && end - start >= 8) {
                            preRoll = ByteBuffer.wrap(first, start, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
                        }
                        pos = end;
                    }
                } else {
                    header = first;
                    if (init.size() > 1 && init.get(1).length >= 8) {
                        delay = ByteBuffer.wrap(init.get(1)).order(ByteOrder.nativeOrder()).getLong();
                    }
                    if (init.size() > 2 && init.get(2).length >= 8) {
                        preRoll = ByteBuffer.wrap(init.get(2)).order(ByteOrder.nativeOrder()).getLong();
                    }
                }
            }
            if (header == null || header.length < 19) {
                header = defaultHeader(2, sampleRate);
            }
            if (delay < 0) {
                int preSkip = (header[10] & 0xFF) | ((header[11] & 0xFF) << 8);
                delay = preSkip * 1_000_000_000L / 48_000;
            }
            return new OpusConfig(header, delay, preRoll);
        }

        private static boolean startsWith(byte[] data, String prefix) {
            if (data.length < prefix.length()) {
                return false;
            }
            return new String(data, 0, prefix.length(), StandardCharsets.US_ASCII).equals(prefix);
        }

        private static byte[] defaultHeader(int channels, int sampleRate) {
            ByteBuffer b = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN);
            b.put("OpusHead".getBytes(StandardCharsets.US_ASCII));
            b.put((byte) 1);
            b.put((byte) channels);
            b.putShort((short) 312);
            b.putInt(sampleRate > 0 ? sampleRate : 48_000);
            b.putShort((short) 0);
            b.put((byte) 0);
            return b.array();
        }
    }
}
