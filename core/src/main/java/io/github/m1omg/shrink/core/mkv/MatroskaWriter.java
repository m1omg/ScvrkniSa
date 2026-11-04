package io.github.m1omg.shrink.core.mkv;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Writes Matroska (.mkv) and WebM (.webm) files from already encoded samples.
 *
 * <p>Android has no built-in Matroska writer, so the Android app feeds encoded
 * frames from Media3 into this class. It is plain Java and is unit-tested on the
 * desktop by checking the files with ffprobe.
 *
 * <p>Layout of the file: EBML header, then one Segment containing SeekHead,
 * Info, Tracks, Clusters (the frames) and Cues (the seek index). Sizes that are
 * only known at the end (segment size, duration, seek positions) are patched
 * when {@link #close()} is called.
 */
public final class MatroskaWriter implements Closeable {

    // Element IDs from the Matroska specification.
    private static final int EBML = 0x1A45DFA3;
    private static final int EBML_VERSION = 0x4286;
    private static final int EBML_READ_VERSION = 0x42F7;
    private static final int EBML_MAX_ID_LENGTH = 0x42F2;
    private static final int EBML_MAX_SIZE_LENGTH = 0x42F3;
    private static final int DOC_TYPE = 0x4282;
    private static final int DOC_TYPE_VERSION = 0x4287;
    private static final int DOC_TYPE_READ_VERSION = 0x4285;
    private static final int SEGMENT = 0x18538067;
    private static final int SEEK_HEAD = 0x114D9B74;
    private static final int SEEK = 0x4DBB;
    private static final int SEEK_ID = 0x53AB;
    private static final int SEEK_POSITION = 0x53AC;
    private static final int VOID = 0xEC;
    private static final int INFO = 0x1549A966;
    private static final int TIMESTAMP_SCALE = 0x2AD7B1;
    private static final int DURATION = 0x4489;
    private static final int MUXING_APP = 0x4D80;
    private static final int WRITING_APP = 0x5741;
    private static final int SEGMENT_UID = 0x73A4;
    private static final int TRACKS = 0x1654AE6B;
    private static final int TRACK_ENTRY = 0xAE;
    private static final int TRACK_NUMBER = 0xD7;
    private static final int TRACK_UID = 0x73C5;
    private static final int TRACK_TYPE = 0x83;
    private static final int FLAG_LACING = 0x9C;
    private static final int LANGUAGE = 0x22B59C;
    private static final int CODEC_ID = 0x86;
    private static final int CODEC_PRIVATE = 0x63A2;
    private static final int DEFAULT_DURATION = 0x23E383;
    private static final int CODEC_DELAY = 0x56AA;
    private static final int SEEK_PRE_ROLL = 0x56BB;
    private static final int VIDEO = 0xE0;
    private static final int PIXEL_WIDTH = 0xB0;
    private static final int PIXEL_HEIGHT = 0xBA;
    private static final int AUDIO = 0xE1;
    private static final int SAMPLING_FREQUENCY = 0xB5;
    private static final int CHANNELS = 0x9F;
    private static final int CLUSTER = 0x1F43B675;
    private static final int TIMESTAMP = 0xE7;
    private static final int SIMPLE_BLOCK = 0xA3;
    private static final int CUES = 0x1C53BB6B;
    private static final int CUE_POINT = 0xBB;
    private static final int CUE_TIME = 0xB3;
    private static final int CUE_TRACK_POSITIONS = 0xB7;
    private static final int CUE_TRACK = 0xF7;
    private static final int CUE_CLUSTER_POSITION = 0xF1;

    /** Codec IDs used by Shrink!. */
    public static final String CODEC_H264 = "V_MPEG4/ISO/AVC";
    public static final String CODEC_HEVC = "V_MPEGH/ISO/HEVC";
    public static final String CODEC_VP8 = "V_VP8";
    public static final String CODEC_VP9 = "V_VP9";
    public static final String CODEC_AAC = "A_AAC";
    public static final String CODEC_OPUS = "A_OPUS";
    public static final String CODEC_VORBIS = "A_VORBIS";

    private static final long TIMESTAMP_SCALE_NS = 1_000_000L; // timestamps in milliseconds
    private static final int SEEK_HEAD_RESERVED = 128;
    private static final long MAX_CLUSTER_MS = 5_000;
    private static final int MAX_CLUSTER_BYTES = 5 * 1024 * 1024;
    /** Samples are held back at most this long to interleave tracks by time. */
    private static final long INTERLEAVE_WINDOW_US = 1_000_000L;

    private final RandomAccessFile file;
    private final boolean webm;
    private final String appName;
    private final List<Track> tracks = new ArrayList<>();
    private final List<long[]> cues = new ArrayList<>(); // {timestampMs, clusterPositionRelativeToSegment}
    private final EbmlBuffer cluster = new EbmlBuffer(1 << 16);
    private final Random random = new Random();

    private boolean headerWritten;
    private boolean closed;
    private long segmentSizePosition;
    private long segmentDataStart;
    private long seekHeadPosition;
    private long infoPosition;
    private long tracksPosition;
    private long durationValuePosition;
    private long clusterTimestampMs = -1;
    private boolean clusterHasKeyframe;
    private long maxEndUs;
    private long newestTimestampUs = Long.MIN_VALUE;
    private int videoTrackNumber;

    /** A track and its queue of samples waiting to be interleaved. */
    private static final class Track {
        final int number;
        final boolean video;
        final String codecId;
        final byte[] codecPrivate;
        final int width;
        final int height;
        final double frameRate;
        final int sampleRate;
        final int channels;
        final long codecDelayNs;
        final long seekPreRollNs;
        final ArrayDeque<Sample> queue = new ArrayDeque<>();
        long lastTimestampUs = Long.MIN_VALUE;
        long frameDurationUs;

        Track(int number, boolean video, String codecId, byte[] codecPrivate, int width, int height,
              double frameRate, int sampleRate, int channels, long codecDelayNs, long seekPreRollNs) {
            this.number = number;
            this.video = video;
            this.codecId = codecId;
            this.codecPrivate = codecPrivate;
            this.width = width;
            this.height = height;
            this.frameRate = frameRate;
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.codecDelayNs = codecDelayNs;
            this.seekPreRollNs = seekPreRollNs;
            if (video) {
                frameDurationUs = frameRate > 0 ? Math.round(1_000_000.0 / frameRate) : 33_333;
            } else {
                frameDurationUs = sampleRate > 0 ? Math.round(1024 * 1_000_000.0 / sampleRate) : 21_333;
            }
        }

        boolean lengthPrefixNals() {
            return CODEC_H264.equals(codecId) || CODEC_HEVC.equals(codecId);
        }
    }

    private static final class Sample {
        final byte[] data;
        final long timestampUs;
        final boolean keyframe;

        Sample(byte[] data, long timestampUs, boolean keyframe) {
            this.data = data;
            this.timestampUs = timestampUs;
            this.keyframe = keyframe;
        }
    }

    /**
     * @param path    output file (overwritten)
     * @param webm    write a WebM file (DocType "webm") instead of Matroska
     * @param appName written into the file as muxing/writing application
     */
    public MatroskaWriter(Path path, boolean webm, String appName) throws IOException {
        this.file = new RandomAccessFile(path.toFile(), "rw");
        this.file.setLength(0);
        this.webm = webm;
        this.appName = appName == null ? "Shrink!" : appName;
    }

    /**
     * Adds a video track. Must be called before the first sample is written.
     *
     * @return the track index to pass to {@link #writeSample}
     */
    public int addVideoTrack(String codecId, byte[] codecPrivate, int width, int height, double frameRate) {
        checkCanAddTrack();
        Track t = new Track(tracks.size() + 1, true, codecId, codecPrivate, width, height, frameRate, 0, 0, 0, 0);
        tracks.add(t);
        if (videoTrackNumber == 0) {
            videoTrackNumber = t.number;
        }
        return tracks.size() - 1;
    }

    /**
     * Adds an audio track. For Opus pass the codec delay and seek pre-roll in
     * nanoseconds (otherwise 0).
     */
    public int addAudioTrack(String codecId, byte[] codecPrivate, int sampleRate, int channels,
                             long codecDelayNs, long seekPreRollNs) {
        checkCanAddTrack();
        Track t = new Track(tracks.size() + 1, false, codecId, codecPrivate, 0, 0, 0, sampleRate, channels,
                codecDelayNs, seekPreRollNs);
        tracks.add(t);
        return tracks.size() - 1;
    }

    private void checkCanAddTrack() {
        if (headerWritten) {
            throw new IllegalStateException("Tracks must be added before writing samples");
        }
        if (tracks.size() >= 126) {
            throw new IllegalStateException("Too many tracks");
        }
    }

    public int trackCount() {
        return tracks.size();
    }

    /** Writes one encoded frame. The buffer is read from its position to its limit. */
    public void writeSample(int trackIndex, ByteBuffer data, long timestampUs, boolean keyframe) throws IOException {
        byte[] bytes = new byte[data.remaining()];
        data.duplicate().get(bytes);
        writeSample(trackIndex, bytes, 0, bytes.length, timestampUs, keyframe);
    }

    /** Writes one encoded frame. */
    public void writeSample(int trackIndex, byte[] data, int offset, int length, long timestampUs,
                            boolean keyframe) throws IOException {
        if (closed) {
            throw new IllegalStateException("Writer is closed");
        }
        Track track = tracks.get(trackIndex);
        if (!headerWritten) {
            writeHeader();
        }
        byte[] payload;
        if (track.lengthPrefixNals()) {
            payload = NalUnits.toLengthPrefixed(data, offset, length);
        } else {
            payload = new byte[length];
            System.arraycopy(data, offset, payload, 0, length);
        }
        if (payload.length == 0) {
            // An empty frame is not valid for any supported codec, and players reject
            // it. Encoders and muxers sometimes emit one to mark the end of a stream.
            return;
        }
        track.queue.add(new Sample(payload, timestampUs, keyframe || !track.video));
        if (track.lastTimestampUs != Long.MIN_VALUE && timestampUs > track.lastTimestampUs) {
            long delta = timestampUs - track.lastTimestampUs;
            if (delta > 0 && delta < 1_000_000) {
                track.frameDurationUs = delta;
            }
        }
        track.lastTimestampUs = Math.max(track.lastTimestampUs, timestampUs);
        newestTimestampUs = Math.max(newestTimestampUs, timestampUs);
        drain(false);
    }

    /** Writes queued samples in timestamp order across tracks. */
    private void drain(boolean all) throws IOException {
        while (true) {
            Track next = null;
            for (Track t : tracks) {
                Sample head = t.queue.peek();
                if (head != null && (next == null || head.timestampUs < next.queue.peek().timestampUs)) {
                    next = t;
                }
            }
            if (next == null) {
                return;
            }
            Sample head = next.queue.peek();
            if (!all && !safeToWrite(next, head)) {
                return;
            }
            next.queue.poll();
            writeBlock(next, head);
        }
    }

    /**
     * A sample may be written once no other track can still deliver an earlier
     * sample, or once it has waited longer than the interleave window.
     */
    private boolean safeToWrite(Track owner, Sample head) {
        if (newestTimestampUs - head.timestampUs > INTERLEAVE_WINDOW_US) {
            return true;
        }
        for (Track t : tracks) {
            if (t == owner) {
                continue;
            }
            if (t.queue.isEmpty() && t.lastTimestampUs < head.timestampUs) {
                return false;
            }
        }
        return true;
    }

    private void writeBlock(Track track, Sample sample) throws IOException {
        // Slightly negative timestamps (audio priming) are clamped to zero.
        long timestampMs = Math.max(0, sample.timestampUs / 1000);
        boolean videoKeyframe = track.video && sample.keyframe;
        boolean audioOnly = videoTrackNumber == 0;

        boolean startNew = clusterTimestampMs < 0;
        if (!startNew) {
            long relative = timestampMs - clusterTimestampMs;
            if (relative > Short.MAX_VALUE || relative < Short.MIN_VALUE) {
                startNew = true;
            } else if (videoKeyframe && relative >= 1_000) {
                startNew = true;
            } else if ((audioOnly || !clusterHasKeyframe) && relative >= MAX_CLUSTER_MS) {
                startNew = true;
            } else if (cluster.length() > MAX_CLUSTER_BYTES && (videoKeyframe || audioOnly)) {
                startNew = true;
            }
        }
        if (startNew) {
            flushCluster();
            clusterTimestampMs = timestampMs;
            clusterHasKeyframe = videoKeyframe;
            cluster.reset();
            cluster.writeUInt(TIMESTAMP, clusterTimestampMs);
            if (videoKeyframe || audioOnly) {
                cues.add(new long[] {timestampMs, file.getFilePointer() - segmentDataStart});
            }
        }
        int relative = (int) (timestampMs - clusterTimestampMs);
        cluster.writeId(SIMPLE_BLOCK);
        cluster.writeSize(4L + sample.data.length);
        cluster.writeByte(0x80 | track.number);  // track number as 1-byte EBML varint
        cluster.writeByte(relative >> 8);
        cluster.writeByte(relative);
        cluster.writeByte(sample.keyframe ? 0x80 : 0x00);
        cluster.writeBytes(sample.data);
        maxEndUs = Math.max(maxEndUs, Math.max(0, sample.timestampUs) + track.frameDurationUs);
    }

    private void flushCluster() throws IOException {
        if (clusterTimestampMs < 0 || cluster.length() == 0) {
            return;
        }
        EbmlBuffer header = new EbmlBuffer(16);
        header.writeId(CLUSTER);
        header.writeSize(cluster.length());
        file.write(header.array(), 0, header.length());
        file.write(cluster.array(), 0, cluster.length());
        cluster.reset();
    }

    private void writeHeader() throws IOException {
        headerWritten = true;
        EbmlBuffer out = new EbmlBuffer(1024);

        EbmlBuffer ebml = new EbmlBuffer();
        ebml.writeUInt(EBML_VERSION, 1);
        ebml.writeUInt(EBML_READ_VERSION, 1);
        ebml.writeUInt(EBML_MAX_ID_LENGTH, 4);
        ebml.writeUInt(EBML_MAX_SIZE_LENGTH, 8);
        ebml.writeString(DOC_TYPE, webm ? "webm" : "matroska");
        ebml.writeUInt(DOC_TYPE_VERSION, 4);
        ebml.writeUInt(DOC_TYPE_READ_VERSION, 2);
        out.writeMaster(EBML, ebml);

        out.writeId(SEGMENT);
        segmentSizePosition = out.length();
        out.writeSize(0, 8); // patched in close()
        segmentDataStart = out.length();

        // Space for the SeekHead, written in close() once positions are known.
        seekHeadPosition = out.length();
        out.writeId(VOID);
        out.writeSize(SEEK_HEAD_RESERVED - 2, 1);
        while (out.length() < seekHeadPosition + SEEK_HEAD_RESERVED) {
            out.writeByte(0);
        }

        infoPosition = out.length();
        EbmlBuffer info = new EbmlBuffer();
        info.writeUInt(TIMESTAMP_SCALE, TIMESTAMP_SCALE_NS);
        int durationOffset = info.length() + 3; // DURATION id (2 bytes) + size (1 byte)
        info.writeFloat(DURATION, 0.0);
        info.writeString(MUXING_APP, appName);
        info.writeString(WRITING_APP, appName);
        if (!webm) {
            byte[] uid = new byte[16];
            random.nextBytes(uid);
            info.writeBinary(SEGMENT_UID, uid);
        }
        int infoHeaderLength = 4 + EbmlBuffer.sizeLength(info.length());
        out.writeMaster(INFO, info);
        durationValuePosition = infoPosition + infoHeaderLength + durationOffset;

        tracksPosition = out.length();
        EbmlBuffer tracksBuffer = new EbmlBuffer();
        for (Track t : tracks) {
            EbmlBuffer entry = new EbmlBuffer();
            entry.writeUInt(TRACK_NUMBER, t.number);
            entry.writeUInt(TRACK_UID, (random.nextLong() & 0x7FFFFFFFFFFFFFFFL) | 1L);
            entry.writeUInt(TRACK_TYPE, t.video ? 1 : 2);
            entry.writeUInt(FLAG_LACING, 0);
            entry.writeString(LANGUAGE, "und");
            entry.writeString(CODEC_ID, t.codecId);
            if (t.codecPrivate != null && t.codecPrivate.length > 0) {
                entry.writeBinary(CODEC_PRIVATE, t.codecPrivate);
            }
            if (t.codecDelayNs > 0) {
                entry.writeUInt(CODEC_DELAY, t.codecDelayNs);
            }
            if (t.seekPreRollNs > 0) {
                entry.writeUInt(SEEK_PRE_ROLL, t.seekPreRollNs);
            }
            if (t.video) {
                if (t.frameRate > 0) {
                    entry.writeUInt(DEFAULT_DURATION, Math.round(1_000_000_000.0 / t.frameRate));
                }
                EbmlBuffer video = new EbmlBuffer();
                video.writeUInt(PIXEL_WIDTH, t.width);
                video.writeUInt(PIXEL_HEIGHT, t.height);
                entry.writeMaster(VIDEO, video);
            } else {
                EbmlBuffer audio = new EbmlBuffer();
                audio.writeFloat(SAMPLING_FREQUENCY, t.sampleRate);
                audio.writeUInt(CHANNELS, Math.max(1, t.channels));
                entry.writeMaster(AUDIO, audio);
            }
            tracksBuffer.writeMaster(TRACK_ENTRY, entry);
        }
        out.writeMaster(TRACKS, tracksBuffer);

        file.seek(0);
        file.write(out.array(), 0, out.length());
    }

    /** Finishes the file: writes remaining samples, the seek index and patches sizes. */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (!headerWritten) {
                writeHeader();
            }
            drain(true);
            flushCluster();

            long cuesPosition = file.getFilePointer();
            if (!cues.isEmpty()) {
                EbmlBuffer cuesBuffer = new EbmlBuffer();
                int cueTrack = videoTrackNumber != 0 ? videoTrackNumber : (tracks.isEmpty() ? 1 : tracks.get(0).number);
                for (long[] cue : cues) {
                    EbmlBuffer positions = new EbmlBuffer();
                    positions.writeUInt(CUE_TRACK, cueTrack);
                    positions.writeUInt(CUE_CLUSTER_POSITION, cue[1]);
                    EbmlBuffer point = new EbmlBuffer();
                    point.writeUInt(CUE_TIME, cue[0]);
                    point.writeMaster(CUE_TRACK_POSITIONS, positions);
                    cuesBuffer.writeMaster(CUE_POINT, point);
                }
                EbmlBuffer cuesElement = new EbmlBuffer(cuesBuffer.length() + 16);
                cuesElement.writeMaster(CUES, cuesBuffer);
                file.write(cuesElement.array(), 0, cuesElement.length());
            }
            long end = file.getFilePointer();

            // Duration in milliseconds (the timestamp scale).
            file.seek(durationValuePosition);
            file.writeLong(Double.doubleToLongBits(maxEndUs / 1000.0));

            // SeekHead inside the reserved space, followed by a Void element.
            EbmlBuffer seekHead = new EbmlBuffer();
            addSeek(seekHead, INFO, infoPosition - segmentDataStart);
            addSeek(seekHead, TRACKS, tracksPosition - segmentDataStart);
            if (!cues.isEmpty()) {
                addSeek(seekHead, CUES, cuesPosition - segmentDataStart);
            }
            EbmlBuffer reserved = new EbmlBuffer(SEEK_HEAD_RESERVED);
            reserved.writeMaster(SEEK_HEAD, seekHead);
            int remaining = SEEK_HEAD_RESERVED - reserved.length();
            if (remaining < 2) {
                throw new IOException("SeekHead does not fit in reserved space");
            }
            reserved.writeId(VOID);
            int voidPayload = remaining - 2;
            reserved.writeSize(voidPayload, 1);
            while (reserved.length() < SEEK_HEAD_RESERVED) {
                reserved.writeByte(0);
            }
            file.seek(seekHeadPosition);
            file.write(reserved.array(), 0, reserved.length());

            // Segment size.
            EbmlBuffer size = new EbmlBuffer(8);
            size.writeSize(end - segmentDataStart, 8);
            file.seek(segmentSizePosition);
            file.write(size.array(), 0, size.length());
        } finally {
            file.close();
        }
    }

    private static void addSeek(EbmlBuffer seekHead, int id, long position) {
        EbmlBuffer idBytes = new EbmlBuffer(4);
        idBytes.writeId(id);
        EbmlBuffer seek = new EbmlBuffer();
        seek.writeBinary(SEEK_ID, idBytes.toByteArray());
        seek.writeUInt(SEEK_POSITION, position);
        seekHead.writeMaster(SEEK, seek);
    }

    /** Duration written so far, in microseconds. */
    public long durationUs() {
        return maxEndUs;
    }
}
