package io.github.m1omg.shrink.core.mkv;

import io.github.m1omg.shrink.core.store.Json;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Writes real Matroska/WebM files from ffmpeg-encoded elementary streams and
 * checks them with ffprobe and a full ffmpeg decode. Skipped when ffmpeg is not
 * installed.
 */
class MatroskaWriterTest {

    private static String ffmpeg;
    private static String ffprobe;

    @TempDir
    Path dir;

    @BeforeAll
    static void findTools() {
        ffmpeg = find("ffmpeg");
        ffprobe = find("ffprobe");
    }

    private static String find(String tool) {
        String configured = System.getProperty("shrink.ffmpeg", "");
        if (!configured.isEmpty()) {
            File f = new File(new File(configured).getParentFile(), tool);
            if (f.canExecute()) {
                return f.getAbsolutePath();
            }
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String p : path.split(File.pathSeparator)) {
                File f = new File(p, tool);
                if (f.canExecute()) {
                    return f.getAbsolutePath();
                }
            }
        }
        return null;
    }

    private static byte[] run(String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            in.transferTo(out);
        }
        assertTrue(p.waitFor(60, TimeUnit.SECONDS));
        if (p.exitValue() != 0) {
            throw new IOException(String.join(" ", cmd) + " failed:\n" + out.toString(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ H.264 + AAC in Matroska

    @Test
    void writesPlayableMatroskaWithH264AndAac() throws Exception {
        assumeTrue(ffmpeg != null && ffprobe != null, "ffmpeg not installed");
        Path h264 = dir.resolve("v.h264");
        Path aac = dir.resolve("a.aac");
        run(ffmpeg, "-v", "error", "-f", "lavfi", "-i", "testsrc2=size=320x240:rate=25", "-t", "3",
                "-c:v", "libx264", "-bf", "0", "-g", "25", "-pix_fmt", "yuv420p",
                "-bsf:v", "h264_metadata=aud=insert", "-f", "h264", h264.toString());
        run(ffmpeg, "-v", "error", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000", "-t", "3",
                "-c:a", "aac", "-b:a", "96k", "-f", "adts", aac.toString());

        List<AccessUnit> video = h264AccessUnits(Files.readAllBytes(h264), 25.0);
        List<AccessUnit> audio = new ArrayList<>();
        byte[] asc = adtsFrames(Files.readAllBytes(aac), audio);

        Path out = dir.resolve("out.mkv");
        try (MatroskaWriter w = new MatroskaWriter(out, false, "Shrink! test")) {
            int v = w.addVideoTrack(MatroskaWriter.CODEC_H264, NalUnits.avcDecoderConfigurationRecord(
                    Arrays.asList(video.get(0).data)), 320, 240, 25.0);
            int a = w.addAudioTrack(MatroskaWriter.CODEC_AAC, asc, 48_000, 1, 0, 0);
            writeInterleaved(w, v, video, a, audio);
        }
        Map<String, Object> probe = probe(out);
        List<Object> streams = Json.getList(probe, "streams");
        assertEquals(2, streams.size());
        Map<String, Object> vs = Json.asObject(streams.get(0));
        assertEquals("h264", Json.getString(vs, "codec_name", ""));
        assertEquals(320L, Json.getLong(vs, "width", 0));
        Map<String, Object> as = Json.asObject(streams.get(1));
        assertEquals("aac", Json.getString(as, "codec_name", ""));
        double duration = Json.getDouble(Json.getObject(probe, "format"), "duration", 0);
        assertEquals(3.0, duration, 0.15);
        assertDecodesCleanly(out);
    }

    // ------------------------------------------------------------------ VP9 + Opus in WebM

    @Test
    void writesPlayableWebmWithVp9AndOpus() throws Exception {
        assumeTrue(ffmpeg != null && ffprobe != null, "ffmpeg not installed");
        Path ivf = dir.resolve("v.ivf");
        Path ogg = dir.resolve("a.opus");
        run(ffmpeg, "-v", "error", "-f", "lavfi", "-i", "testsrc2=size=320x240:rate=25", "-t", "2",
                "-c:v", "libvpx-vp9", "-deadline", "realtime", "-cpu-used", "8", "-g", "25", "-f", "ivf",
                ivf.toString());
        run(ffmpeg, "-v", "error", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000", "-t", "2",
                "-c:a", "libopus", "-b:a", "64k", "-f", "ogg", ogg.toString());

        List<AccessUnit> video = ivfFrames(Files.readAllBytes(ivf));
        List<byte[]> packets = oggPackets(Files.readAllBytes(ogg));
        byte[] opusHead = packets.get(0);
        int preSkip = (opusHead[10] & 0xFF) | ((opusHead[11] & 0xFF) << 8);
        List<AccessUnit> audio = new ArrayList<>();
        long t = 0;
        for (int i = 2; i < packets.size(); i++) {
            byte[] p = packets.get(i);
            audio.add(new AccessUnit(p, t, true));
            t += opusDurationUs(p);
        }
        // Some encoders end the stream with an empty packet; it must not reach the file.
        audio.add(new AccessUnit(new byte[0], t, true));
        Path out = dir.resolve("out.webm");
        try (MatroskaWriter w = new MatroskaWriter(out, true, "Shrink! test")) {
            int v = w.addVideoTrack(MatroskaWriter.CODEC_VP9, null, 320, 240, 25.0);
            int a = w.addAudioTrack(MatroskaWriter.CODEC_OPUS, opusHead, 48_000, 1,
                    preSkip * 1_000_000_000L / 48_000, 80_000_000L);
            writeInterleaved(w, v, video, a, audio);
        }
        Map<String, Object> probe = probe(out);
        assertTrue(Json.getString(Json.getObject(probe, "format"), "format_name", "").contains("webm"));
        List<Object> streams = Json.getList(probe, "streams");
        assertEquals("vp9", Json.getString(Json.asObject(streams.get(0)), "codec_name", ""));
        assertEquals("opus", Json.getString(Json.asObject(streams.get(1)), "codec_name", ""));
        assertEquals(2.0, Json.getDouble(Json.getObject(probe, "format"), "duration", 0), 0.15);
        try {
            assertDecodesCleanly(out);
        } catch (AssertionError e) {
            // Helps tell an encoder or FFmpeg version quirk from a writer bug.
            StringBuilder sizes = new StringBuilder();
            for (int i = 2; i < packets.size(); i++) {
                byte[] p = packets.get(i);
                sizes.append(p.length).append(p.length > 0 ? String.format("/%02x ", p[0] & 0xFF) : " ");
            }
            throw new AssertionError(e.getMessage() + "\nOpus packets (size/TOC): " + sizes, e);
        }
    }

    @Test
    void annexBConversion() {
        byte[] annexB = {0, 0, 0, 1, 0x67, 1, 2, 0, 0, 1, 0x68, 3, 0, 0, 0, 1, 0x65, 4, 5, 6};
        byte[] avcc = NalUnits.toLengthPrefixed(annexB, 0, annexB.length);
        byte[] expected = {0, 0, 0, 3, 0x67, 1, 2, 0, 0, 0, 2, 0x68, 3, 0, 0, 0, 4, 0x65, 4, 5, 6};
        assertTrue(Arrays.equals(expected, avcc), Arrays.toString(avcc));
        byte[] record = NalUnits.avcDecoderConfigurationRecord(Arrays.asList(
                new byte[] {0, 0, 0, 1, 0x67, 0x64, 0x00, 0x1F, 9}, new byte[] {0, 0, 0, 1, 0x68, 7}));
        assertEquals(1, record[0]);
        assertEquals(0x64, record[1]);
        assertEquals(0x1F, record[3]);
        assertEquals((byte) 0xE1, record[5]);
    }

    // ------------------------------------------------------------------ helpers

    private static final class AccessUnit {
        final byte[] data;
        final long timeUs;
        final boolean key;

        AccessUnit(byte[] data, long timeUs, boolean key) {
            this.data = data;
            this.timeUs = timeUs;
            this.key = key;
        }
    }

    private static void writeInterleaved(MatroskaWriter w, int v, List<AccessUnit> video, int a,
                                         List<AccessUnit> audio) throws IOException {
        int i = 0;
        int j = 0;
        while (i < video.size() || j < audio.size()) {
            boolean takeVideo = j >= audio.size() || (i < video.size() && video.get(i).timeUs <= audio.get(j).timeUs);
            if (takeVideo) {
                AccessUnit u = video.get(i++);
                w.writeSample(v, u.data, 0, u.data.length, u.timeUs, u.key);
            } else {
                AccessUnit u = audio.get(j++);
                w.writeSample(a, u.data, 0, u.data.length, u.timeUs, true);
            }
        }
    }

    /** Splits an Annex B stream at access unit delimiters (NAL type 9). */
    private static List<AccessUnit> h264AccessUnits(byte[] stream, double fps) {
        List<byte[]> nals = NalUnits.split(stream, 0, stream.length);
        List<AccessUnit> units = new ArrayList<>();
        ByteArrayOutputStream current = null;
        boolean key = false;
        for (byte[] nal : nals) {
            int type = NalUnits.h264Type(nal);
            if (type == 9) {
                if (current != null && current.size() > 0) {
                    units.add(new AccessUnit(current.toByteArray(), Math.round(units.size() * 1e6 / fps), key));
                }
                current = new ByteArrayOutputStream();
                key = false;
                continue;
            }
            if (current == null) {
                current = new ByteArrayOutputStream();
            }
            if (type == 5) {
                key = true;
            }
            current.write(0);
            current.write(0);
            current.write(0);
            current.write(1);
            current.write(nal, 0, nal.length);
        }
        if (current != null && current.size() > 0) {
            units.add(new AccessUnit(current.toByteArray(), Math.round(units.size() * 1e6 / fps), key));
        }
        return units;
    }

    /** Parses ADTS frames; returns the AudioSpecificConfig. */
    private static byte[] adtsFrames(byte[] s, List<AccessUnit> out) {
        int pos = 0;
        byte[] asc = null;
        long n = 0;
        while (pos + 7 <= s.length) {
            boolean crc = (s[pos + 1] & 1) == 0;
            int profile = (s[pos + 2] >> 6) & 3;
            int freqIndex = (s[pos + 2] >> 2) & 0xF;
            int channels = ((s[pos + 2] & 1) << 2) | ((s[pos + 3] >> 6) & 3);
            int frameLength = ((s[pos + 3] & 3) << 11) | ((s[pos + 4] & 0xFF) << 3) | ((s[pos + 5] >> 5) & 7);
            int header = crc ? 9 : 7;
            if (asc == null) {
                int objectType = profile + 1;
                asc = new byte[] {(byte) ((objectType << 3) | (freqIndex >> 1)),
                    (byte) (((freqIndex & 1) << 7) | (channels << 3))};
            }
            out.add(new AccessUnit(Arrays.copyOfRange(s, pos + header, pos + frameLength),
                    Math.round(n * 1024 * 1e6 / 48_000), true));
            n++;
            pos += frameLength;
        }
        return asc;
    }

    private static List<AccessUnit> ivfFrames(byte[] s) {
        int headerSize = (s[6] & 0xFF) | ((s[7] & 0xFF) << 8);
        int den = le32(s, 16);
        int num = le32(s, 20);
        int pos = headerSize;
        List<AccessUnit> frames = new ArrayList<>();
        while (pos + 12 <= s.length) {
            int size = le32(s, pos);
            long pts = (le32(s, pos + 4) & 0xFFFFFFFFL) | ((long) le32(s, pos + 8) << 32);
            byte[] data = Arrays.copyOfRange(s, pos + 12, pos + 12 + size);
            boolean key = ((data[0] >> 2) & 1) == 0 && ((data[0] >> 3) & 1) == 0;
            frames.add(new AccessUnit(data, pts * num * 1_000_000L / den, key));
            pos += 12 + size;
        }
        return frames;
    }

    private static int le32(byte[] s, int i) {
        return (s[i] & 0xFF) | ((s[i + 1] & 0xFF) << 8) | ((s[i + 2] & 0xFF) << 16) | ((s[i + 3] & 0xFF) << 24);
    }

    private static List<byte[]> oggPackets(byte[] s) {
        List<byte[]> packets = new ArrayList<>();
        ByteArrayOutputStream current = new ByteArrayOutputStream();
        int pos = 0;
        while (pos + 27 <= s.length) {
            int segments = s[pos + 26] & 0xFF;
            int dataPos = pos + 27 + segments;
            for (int i = 0; i < segments; i++) {
                int len = s[pos + 27 + i] & 0xFF;
                current.write(s, dataPos, len);
                dataPos += len;
                if (len < 255) {
                    packets.add(current.toByteArray());
                    current.reset();
                }
            }
            pos = dataPos;
        }
        return packets;
    }

    private static long opusDurationUs(byte[] packet) {
        int toc = packet[0] & 0xFF;
        int config = toc >> 3;
        double frameMs;
        if (config < 12) {
            frameMs = new double[] {10, 20, 40, 60}[config % 4];
        } else if (config < 16) {
            frameMs = config % 2 == 0 ? 10 : 20;
        } else {
            frameMs = new double[] {2.5, 5, 10, 20}[config % 4];
        }
        int code = toc & 3;
        int frames = code == 0 ? 1 : (code == 3 ? packet[1] & 0x3F : 2);
        return Math.round(frames * frameMs * 1000);
    }

    private static Map<String, Object> probe(Path file) throws Exception {
        byte[] json = run(ffprobe, "-v", "error", "-show_format", "-show_streams", "-of", "json", file.toString());
        return Json.parseObject(new String(json, StandardCharsets.UTF_8));
    }

    private static void assertDecodesCleanly(Path file) throws Exception {
        byte[] log = run(ffmpeg, "-v", "error", "-i", file.toString(), "-f", "null", "-");
        String text = new String(log, StandardCharsets.UTF_8).trim();
        assertTrue(text.isEmpty(), "ffmpeg reported problems:\n" + text);
    }
}
