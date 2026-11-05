package io.github.m1omg.shrink.core.plan;

import io.github.m1omg.shrink.core.model.AudioCodec;
import io.github.m1omg.shrink.core.model.AudioFileFormat;
import io.github.m1omg.shrink.core.model.CompressionPreset;
import io.github.m1omg.shrink.core.model.Container;
import io.github.m1omg.shrink.core.model.Job;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.RateControl;
import io.github.m1omg.shrink.core.model.TimeRange;
import io.github.m1omg.shrink.core.model.VideoCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobPlannerTest {

    private final JobPlanner planner = new JobPlanner(EncodingDefaults.standard());

    @Test
    void presetsAreOrderedAndSmallerThanTheOriginal() {
        MediaInfo src = TestMedia.example720p();
        List<Plan> plans = planner.presetPlans(src, 0, null);
        Plan high = plans.get(CompressionPreset.HIGH_QUALITY.ordinal());
        Plan balanced = plans.get(CompressionPreset.BALANCED.ordinal());
        Plan small = plans.get(CompressionPreset.SMALL_FILE.ordinal());

        assertTrue(high.estimatedBytes() < src.sizeBytes(), "high quality must still shrink the file");
        assertTrue(balanced.estimatedBytes() < high.estimatedBytes());
        assertTrue(small.estimatedBytes() < balanced.estimatedBytes());
        assertTrue(high.reductionPercent() > 10 && high.reductionPercent() < 50, "high: " + high.reductionPercent());
        assertTrue(balanced.reductionPercent() > 30 && balanced.reductionPercent() < 70,
                "balanced: " + balanced.reductionPercent());
        assertTrue(small.reductionPercent() > 55 && small.reductionPercent() < 92, "small: " + small.reductionPercent());
        assertEquals(RateControl.TWO_PASS, balanced.job().video().rateControl());
        assertEquals(VideoCodec.H264, balanced.job().video().codec());
        assertFalse(balanced.job().isScaled(), "720p stays 720p");
    }

    @Test
    void targetSizeIsHonoured() {
        MediaInfo src = TestMedia.example720p();
        long target = 2_000_000;
        Plan plan = planner.compress(src, CompressionRequest.targetSize(target));
        assertTrue(plan.feasible());
        assertTrue(plan.estimatedBytes() <= target, "estimate " + plan.estimatedBytes());
        assertTrue(plan.estimatedBytes() > target * 0.93, "estimate " + plan.estimatedBytes());
        assertEquals(RateControl.TWO_PASS, plan.job().video().rateControl());
    }

    @Test
    void impossibleTargetsAreReported() {
        MediaInfo src = TestMedia.example720p();
        Plan tooSmall = planner.compress(src, CompressionRequest.targetSize(60_000));
        assertFalse(tooSmall.feasible());
        assertTrue(tooSmall.has(PlanWarning.TARGET_TOO_SMALL));
        assertTrue(tooSmall.minimumBytes() > 60_000);

        Plan notSmaller = planner.compress(src, CompressionRequest.targetSize(5_000_000));
        assertFalse(notSmaller.feasible());
        assertTrue(notSmaller.has(PlanWarning.TARGET_NOT_SMALLER));
    }

    @Test
    void tightTargetsLowerTheResolution() {
        MediaInfo src = TestMedia.phone4kHevc();
        Plan plan = planner.compress(src, CompressionRequest.targetSize(25_000_000));
        assertTrue(plan.feasible());
        Job job = plan.job();
        assertTrue(job.isScaled());
        assertTrue(Math.min(job.outputWidth(), job.outputHeight()) <= 1080, job.toString());
        assertEquals(30.0, job.outputFrameRate(), 0.01);
    }

    @Test
    void phoneVideosShrinkALot() {
        MediaInfo src = TestMedia.phone4kHevc();
        Plan balanced = planner.compress(src, CompressionRequest.of(CompressionPreset.BALANCED));
        long bitrate = balanced.job().video().bitrate();
        assertTrue(bitrate > 8_000_000 && bitrate < 30_000_000, "bitrate " + bitrate);
        assertTrue(balanced.reductionPercent() > 40);

        Plan small = planner.compress(src, CompressionRequest.of(CompressionPreset.SMALL_FILE));
        assertEquals(1080, Math.min(small.job().outputWidth(), small.job().outputHeight()));
        // The source is rotated by 90°: the output is portrait.
        assertTrue(small.job().outputHeight() > small.job().outputWidth());
        assertEquals(30.0, small.job().outputFrameRate(), 0.01);
    }

    @Test
    void hdrIsToneMappedForH264Only() {
        MediaInfo hdr = TestMedia.video("hdr.mov", 100_000_000, 20, "hevc", 1920, 1080, 30, 39_000_000, "aac",
                128_000, 2, "arib-std-b67", 0);
        Plan h264 = planner.compress(hdr, CompressionRequest.of(CompressionPreset.BALANCED));
        assertTrue(h264.job().toneMapHdr());
        assertTrue(h264.has(PlanWarning.HDR_TONE_MAPPED));
        Plan hevc = planner.compress(hdr, CompressionRequest.builder(CompressionPreset.BALANCED)
                .codec(VideoCodec.H265).build());
        assertFalse(hevc.job().toneMapHdr());
    }

    @Test
    void webmForcesWebCodecs() {
        MediaInfo src = TestMedia.example720p();
        Plan plan = planner.compress(src, CompressionRequest.builder(CompressionPreset.BALANCED)
                .container(Container.WEBM).build());
        assertEquals(VideoCodec.VP9, plan.job().video().codec());
        assertEquals(AudioCodec.OPUS, plan.job().audio().codec());
    }

    @Test
    void lightToolsCopyStreams() {
        MediaInfo src = TestMedia.example720p();
        assertTrue(planner.mute(src).job().video().isCopy());
        assertTrue(planner.mute(src).job().audio().isNone());

        Plan mkv = planner.convert(src, Container.MKV);
        assertTrue(mkv.job().video().isCopy());
        assertTrue(mkv.job().audio().isCopy());
        assertEquals(src.sizeBytes(), mkv.estimatedBytes(), src.sizeBytes() * 0.05);

        Plan webm = planner.convert(src, Container.WEBM);
        assertTrue(webm.job().video().isEncode());
        assertEquals(VideoCodec.VP9, webm.job().video().codec());
        assertEquals(AudioCodec.OPUS, webm.job().audio().codec());

        Plan rotate = planner.rotate(src, 90, true);
        assertTrue(rotate.job().rotationByMetadata());
        assertTrue(rotate.job().video().isCopy());
        assertEquals(90, rotate.job().rotation());

        Plan fastTrim = planner.trim(src, new TimeRange(2, 12), false);
        assertTrue(fastTrim.job().video().isCopy());
        assertEquals(10.0, fastTrim.job().outputDuration(), 1e-9);
        Plan preciseTrim = planner.trim(src, new TimeRange(2, 12), true);
        assertTrue(preciseTrim.job().video().isEncode());
    }

    @Test
    void mkvRotationNeedsReencoding() {
        MediaInfo mkv = TestMedia.video("clip.mkv", 3_900_000, 23.0, "h264", 1280, 720, 30, 1_220_000, "aac",
                128_000, 2, null, 0);
        Plan rotate = planner.rotate(mkv, 270, true);
        assertFalse(rotate.job().rotationByMetadata());
        assertTrue(rotate.job().video().isEncode());
        assertEquals(Container.MKV, rotate.job().container());
    }

    @Test
    void audioExtraction() {
        MediaInfo src = TestMedia.example720p();
        Plan m4a = planner.extractAudio(src, AudioFileFormat.M4A, 0, true, 0);
        assertTrue(m4a.job().audio().isCopy());
        assertTrue(m4a.job().video().isNone());
        assertTrue(m4a.estimatedBytes() > 300_000 && m4a.estimatedBytes() < 450_000, "" + m4a.estimatedBytes());

        Plan mp3 = planner.extractAudio(src, AudioFileFormat.MP3, 192_000, true, 0);
        assertTrue(mp3.job().audio().isEncode());
        assertEquals(AudioCodec.MP3, mp3.job().audio().codec());
        assertEquals("mp3", mp3.job().outputExtension());
    }

    @Test
    void resizeAndFrameRate() {
        MediaInfo src = TestMedia.phone4kHevc();
        Plan p = planner.resize(src, 1080);
        assertEquals(1080, p.job().outputWidth()); // portrait: 1080 x 1920
        assertEquals(1920, p.job().outputHeight());
        assertTrue(p.estimatedBytes() < src.sizeBytes());

        Plan odd = planner.resize(TestMedia.example720p(), 853, 479);
        assertEquals(0, odd.job().outputWidth() % 2);
        assertEquals(0, odd.job().outputHeight() % 2);

        Plan fps = planner.changeFrameRate(src, 30);
        assertEquals(30.0, fps.job().outputFrameRate(), 1e-9);
        assertTrue(fps.estimatedBytes() < src.sizeBytes());
    }

    @Test
    void previewIsAFiveSecondExcerpt() {
        MediaInfo src = TestMedia.example720p();
        Job job = planner.compress(src, CompressionRequest.of(CompressionPreset.BALANCED)).job();
        double start = JobPlanner.defaultPreviewStart(job, 5);
        Job preview = JobPlanner.preview(job, start, 5);
        assertEquals(5.0, preview.trim().duration(), 1e-9);
        assertEquals(9.0, preview.trim().start(), 1e-9);
        assertEquals(job.video().bitrate(), preview.video().bitrate());
    }

    @Test
    void frameRateHalving() {
        assertEquals(30, JobPlanner.reducedFrameRate(60), 1e-9);
        assertEquals(29.97, JobPlanner.reducedFrameRate(59.94), 1e-9);
        assertEquals(25, JobPlanner.reducedFrameRate(50), 1e-9);
        assertEquals(30, JobPlanner.reducedFrameRate(120), 1e-9);
        assertEquals(24, JobPlanner.reducedFrameRate(24), 1e-9);
    }

    @Test
    void bitmapSubtitlesAreDroppedOutsideMkv() {
        MediaInfo src = TestMedia.withSubtitles(TestMedia.example720p(), "hdmv_pgs_subtitle");
        Plan mp4 = planner.convert(src, Container.MP4);
        assertTrue(mp4.has(PlanWarning.SUBTITLES_DROPPED));
        Plan mkv = planner.convert(src, Container.MKV);
        assertFalse(mkv.has(PlanWarning.SUBTITLES_DROPPED));
        assertTrue(mkv.job().keepSubtitles());
    }

    @Test
    void overshootingEncodersGetOneCorrectedPass() {
        MediaInfo src = TestMedia.example720p();
        Job job = planner.compress(src, CompressionRequest.of(CompressionPreset.BALANCED)).job();
        long estimate = job.estimatedBytes();

        // Close to the estimate, or only slightly above it: nothing to correct.
        assertEquals(null, JobPlanner.correctedForSize(job, estimate));
        assertEquals(null, JobPlanner.correctedForSize(job, Math.round(estimate * 1.1)));

        // Three times too big, as the Android emulator's encoder did: a much lower bitrate.
        Job corrected = JobPlanner.correctedForSize(job, estimate * 3);
        assertTrue(corrected != null);
        assertTrue(corrected.video().bitrate() < job.video().bitrate() * 0.4,
                corrected.video().bitrate() + " vs " + job.video().bitrate());
        assertTrue(corrected.video().bitrate() >= job.video().bitrate() * 0.25, "never below a quarter");
        assertEquals(estimate, corrected.estimatedBytes(), "the goal stays what the person saw");
        assertEquals(job.video().codec(), corrected.video().codec());
        assertEquals(job.outputWidth(), corrected.outputWidth());

        // Moderately too big: a moderate correction.
        Job mild = JobPlanner.correctedForSize(job, Math.round(estimate * 1.3));
        assertTrue(mild != null);
        assertTrue(mild.video().bitrate() > corrected.video().bitrate());
        assertTrue(mild.video().bitrate() <= job.video().bitrate() * 0.9);

        // Only compression is corrected; copies and other tools are left alone.
        Job trim = planner.trim(src, new TimeRange(1, 5), false).job();
        assertEquals(null, JobPlanner.correctedForSize(trim, trim.estimatedBytes() * 3 + 1_000_000));
    }
}
