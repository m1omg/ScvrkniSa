package io.github.m1omg.shrink.desktop.engine;

import io.github.m1omg.shrink.core.model.AudioFileFormat;
import io.github.m1omg.shrink.core.model.CompressionPreset;
import io.github.m1omg.shrink.core.model.Container;
import io.github.m1omg.shrink.core.model.EncoderSpeed;
import io.github.m1omg.shrink.core.model.Job;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.TimeRange;
import io.github.m1omg.shrink.core.model.VideoCodec;
import io.github.m1omg.shrink.core.plan.CompressionRequest;
import io.github.m1omg.shrink.core.plan.EncodingDefaults;
import io.github.m1omg.shrink.core.plan.JobPlanner;
import io.github.m1omg.shrink.core.plan.Plan;
import io.github.m1omg.shrink.core.store.Settings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs every tool for real with the bundled FFmpeg and checks the results with
 * ffprobe. Uses short synthetic test videos so the whole class takes about a
 * minute.
 */
class EngineIntegrationTest {

    @TempDir
    static Path dir;

    private static Engine engine;
    private static Path clip720;
    private static Path clip360;
    private static Path hdrClip;
    private static final EncodingDefaults FAST = new EncodingDefaults(VideoCodec.H264, Container.MP4,
            EncoderSpeed.FASTEST, true, false, true);
    private static final JobPlanner PLANNER = new JobPlanner(FAST);

    @BeforeAll
    static void setUp() throws Exception {
        FfmpegTools tools = FfmpegLocator.locate(Settings.FfmpegMode.AUTO, "", dir.resolve("cache"));
        engine = new Engine(tools);
        clip720 = dir.resolve("clip720.mp4");
        ffmpeg("-f", "lavfi", "-i", "testsrc2=size=1280x720:rate=30", "-f", "lavfi",
                "-i", "sine=frequency=440:sample_rate=48000", "-t", "6", "-c:v", "libx264", "-preset", "veryfast",
                "-b:v", "2500k", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "128k", "-ac", "2", clip720.toString());
        clip360 = dir.resolve("clip360.mp4");
        ffmpeg("-f", "lavfi", "-i", "testsrc2=size=640x360:rate=30", "-f", "lavfi",
                "-i", "sine=frequency=440:sample_rate=44100", "-t", "4", "-c:v", "libx264", "-preset", "veryfast",
                "-b:v", "800k", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "96k", clip360.toString());
        hdrClip = dir.resolve("hdr.mp4");
        ffmpeg("-f", "lavfi", "-i", "testsrc2=size=640x360:rate=25", "-t", "2", "-c:v", "libx265",
                "-preset", "ultrafast", "-pix_fmt", "yuv420p10le", "-color_primaries", "bt2020",
                "-color_trc", "arib-std-b67", "-colorspace", "bt2020nc",
                "-x265-params", "log-level=error:colorprim=bt2020:transfer=arib-std-b67:colormatrix=bt2020nc",
                "-tag:v", "hvc1", hdrClip.toString());
    }

    private static void ffmpeg(String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(engine.tools().ffmpeg().toString());
        cmd.addAll(Arrays.asList("-v", "error", "-y"));
        cmd.addAll(Arrays.asList(args));
        FfmpegRunner.capture(cmd, 120);
    }

    private static Engine.Result run(Plan plan, String name) throws Exception {
        Path out = dir.resolve(name);
        Job job = plan.job();
        return engine.run(job, Path.of(job.source().location()), out, null, null);
    }

    @Test
    void probeReadsStreams() throws Exception {
        MediaInfo info = engine.probe(clip720);
        assertEquals(6.0, info.durationSeconds(), 0.1);
        assertEquals("h264", info.video().codecName());
        assertEquals(1280, info.video().width());
        assertEquals(30.0, info.video().frameRate(), 0.01);
        assertEquals("aac", info.audio().codecName());
        assertEquals(2, info.audio().channels());
    }

    @Test
    void compressionMatchesTheEstimate() throws Exception {
        MediaInfo src = engine.probe(clip720);
        Plan plan = PLANNER.compress(src, CompressionRequest.of(CompressionPreset.BALANCED));
        Engine.Result result = run(plan, "balanced.mp4");
        double ratio = (double) result.sizeBytes() / plan.estimatedBytes();
        assertTrue(ratio > 0.85 && ratio < 1.15, "size " + result.sizeBytes() + " vs estimate "
                + plan.estimatedBytes());
        MediaInfo out = engine.probe(result.output());
        assertEquals("h264", out.video().codecName());
        assertEquals("yuv420p", out.video().pixelFormat());
        assertTrue(result.sizeBytes() < src.sizeBytes());
    }

    @Test
    void targetSizeIsNotExceeded() throws Exception {
        MediaInfo src = engine.probe(clip720);
        long target = 700_000;
        Plan plan = PLANNER.compress(src, CompressionRequest.targetSize(target));
        assertTrue(plan.feasible());
        Engine.Result result = run(plan, "target.mp4");
        assertTrue(result.sizeBytes() <= target * 1.02, "size " + result.sizeBytes());
        assertTrue(result.sizeBytes() >= target * 0.8, "size " + result.sizeBytes());
    }

    @Test
    void trimFastAndPrecise() throws Exception {
        MediaInfo src = engine.probe(clip360);
        Engine.Result precise = run(PLANNER.trim(src, new TimeRange(1.0, 2.5), true), "trim-precise.mp4");
        assertEquals(1.5, engine.probe(precise.output()).durationSeconds(), 0.1);
        Engine.Result fast = run(PLANNER.trim(src, new TimeRange(1.0, 2.5), false), "trim-fast.mp4");
        double d = engine.probe(fast.output()).durationSeconds();
        assertTrue(d >= 1.4 && d <= 2.7, "fast trim duration " + d);
    }

    @Test
    void rotateLosslessAndReencoded() throws Exception {
        MediaInfo src = engine.probe(clip360);
        Engine.Result lossless = run(PLANNER.rotate(src, 90, true), "rot-lossless.mp4");
        MediaInfo rotated = engine.probe(lossless.output());
        assertEquals(90, rotated.video().rotation());
        assertEquals(360, rotated.video().displayWidth());

        Engine.Result reencoded = run(PLANNER.rotate(src, 270, false), "rot-reencode.mp4");
        MediaInfo turned = engine.probe(reencoded.output());
        assertEquals(0, turned.video().rotation());
        assertEquals(360, turned.video().width());
        assertEquals(640, turned.video().height());
    }

    @Test
    void muteRemovesAudio() throws Exception {
        MediaInfo src = engine.probe(clip360);
        Engine.Result r = run(PLANNER.mute(src), "muted.mp4");
        MediaInfo out = engine.probe(r.output());
        assertFalse(out.hasAudio());
        assertEquals("h264", out.video().codecName());
    }

    @Test
    void extractAudioAsM4aAndMp3() throws Exception {
        MediaInfo src = engine.probe(clip360);
        Engine.Result m4a = run(PLANNER.extractAudio(src, AudioFileFormat.M4A, 0, true, 0), "audio.m4a");
        MediaInfo a = engine.probe(m4a.output());
        assertFalse(a.hasVideo());
        assertEquals("aac", a.audio().codecName());
        Engine.Result mp3 = run(PLANNER.extractAudio(src, AudioFileFormat.MP3, 192_000, true, 0), "audio.mp3");
        assertEquals("mp3", engine.probe(mp3.output()).audio().codecName());
    }

    @Test
    void convertToMkvCopiesAndWebmReencodes() throws Exception {
        MediaInfo src = engine.probe(clip360);
        Engine.Result mkv = run(PLANNER.convert(src, Container.MKV), "converted.mkv");
        MediaInfo m = engine.probe(mkv.output());
        assertTrue(m.formatName().contains("matroska"));
        assertEquals("h264", m.video().codecName());

        Engine.Result webm = run(PLANNER.convert(src, Container.WEBM), "converted.webm");
        MediaInfo w = engine.probe(webm.output());
        assertEquals("vp9", w.video().codecName());
        assertEquals("opus", w.audio().codecName());
    }

    @Test
    void resolutionAndFrameRate() throws Exception {
        MediaInfo src = engine.probe(clip720);
        Engine.Result small = run(PLANNER.resize(src, 360), "resized.mp4");
        MediaInfo r = engine.probe(small.output());
        assertEquals(640, r.video().width());
        assertEquals(360, r.video().height());

        Engine.Result fps = run(PLANNER.changeFrameRate(engine.probe(clip360), 15), "fps15.mp4");
        assertEquals(15.0, engine.probe(fps.output()).video().frameRate(), 0.01);
    }

    @Test
    void hdrIsToneMappedToSdr() throws Exception {
        MediaInfo src = engine.probe(hdrClip);
        assertTrue(src.video().isHdr());
        assertTrue(engine.tools().canToneMap());
        Plan plan = PLANNER.compress(src, CompressionRequest.of(CompressionPreset.HIGH_QUALITY));
        assertTrue(plan.job().toneMapHdr());
        MediaInfo out = engine.probe(run(plan, "sdr.mp4").output());
        assertEquals("yuv420p", out.video().pixelFormat());
        assertEquals("bt709", out.video().colorTransfer());
    }

    @Test
    void previewAndFrames() throws Exception {
        MediaInfo src = engine.probe(clip720);
        Job job = PLANNER.compress(src, CompressionRequest.of(CompressionPreset.SMALL_FILE)).job();
        Job preview = JobPlanner.preview(job, JobPlanner.defaultPreviewStart(job, 2), 2);
        Path target = dir.resolve("preview.mp4");
        engine.renderPreview(preview, clip720, target, null, null);
        assertEquals(2.0, engine.probe(target).durationSeconds(), 0.15);

        BufferedImage frame = engine.frame(clip720, src.video(), 3.0, 320, 320, true);
        assertNotNull(frame);
        assertEquals(320, frame.getWidth());
        assertEquals(180, frame.getHeight());

        try (FramePipe pipe = engine.framePipe(target, null, 0, 2, 160, 90, 10)) {
            BufferedImage img = pipe.createImage();
            int frames = 0;
            while (pipe.read(img)) {
                frames++;
            }
            assertEquals(20, frames, 2);
        }
    }

    @Test
    void cancellingStopsAndCleansUp() throws Exception {
        MediaInfo src = engine.probe(clip720);
        Plan plan = new JobPlanner(new EncodingDefaults(VideoCodec.H264, Container.MP4, EncoderSpeed.SLOW, true,
                false, true)).compress(src, CompressionRequest.of(CompressionPreset.HIGH_QUALITY));
        Path out = dir.resolve("cancelled.mp4");
        FfmpegRunner.CancelToken token = new FfmpegRunner.CancelToken();
        AtomicReference<Engine.Progress> last = new AtomicReference<>();
        Thread canceller = new Thread(() -> {
            try {
                Thread.sleep(700);
            } catch (InterruptedException ignored) {
                // ignore
            }
            token.cancel();
        });
        canceller.start();
        assertThrows(FfmpegRunner.CancelledException.class,
                () -> engine.run(plan.job(), clip720, out, last::set, token));
        canceller.join();
        assertFalse(Files.exists(out));
        try (Stream<Path> files = Files.list(dir)) {
            assertTrue(files.noneMatch(p -> p.getFileName().toString().endsWith(".part")));
        }
    }
}
