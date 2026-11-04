package io.github.m1omg.shrink.android;

import android.net.Uri;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;

import io.github.m1omg.shrink.android.media.MediaProbe;
import io.github.m1omg.shrink.core.model.AudioFileFormat;
import io.github.m1omg.shrink.core.model.CompressionPreset;
import io.github.m1omg.shrink.core.model.Container;
import io.github.m1omg.shrink.core.model.Job;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.TimeRange;
import io.github.m1omg.shrink.core.model.VideoCodec;
import io.github.m1omg.shrink.core.plan.CompressionRequest;
import io.github.m1omg.shrink.core.plan.EncodingDefaults;
import io.github.m1omg.shrink.core.plan.JobPlanner;
import io.github.m1omg.shrink.core.plan.Plan;
import io.github.m1omg.shrink.core.store.Settings;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Runs every tool on a real 3-second video (640×360, 30 fps, H.264 + AAC) and
 * checks the results with Android's own media parsers.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class ToolsTest {

    private static final double SOURCE_SECONDS = 3.0;

    @Rule
    public final GrantPermissionRule notifications = TestSupport.notificationPermission();

    /** The service may only start while the app is visible, as in real use. */
    @Rule
    public final ActivityScenarioRule<MainActivity> activity = new ActivityScenarioRule<>(MainActivity.class);

    private File video;
    private Uri input;
    private MediaInfo source;

    @Before
    public void load() throws Exception {
        video = TestSupport.copyAsset("sample.mp4", "sample.mp4");
        input = Uri.fromFile(video);
        source = MediaProbe.probe(TestSupport.context(), input);
        assertEquals(640, source.video().width());
        assertTrue(source.hasAudio());
    }

    /** The planner exactly as the tool screens create it. */
    private static JobPlanner planner() {
        Settings s = ShrinkApp.get().settings();
        return new JobPlanner(new EncodingDefaults(VideoCodec.H264, Container.MP4, s.getSpeed(), false, true,
                s.isKeepMetadata()));
    }

    private TestSupport.Output run(Plan plan) throws Exception {
        assertTrue("plan feasible: " + plan, plan.feasible());
        Job job = plan.job();
        return TestSupport.run(job, input, job.kind().fileSuffix());
    }

    private static void assertDuration(TestSupport.Output o, double expected, double tolerance) {
        assertEquals(o.toString(), expected, o.durationSeconds, tolerance);
    }

    @Test
    public void compressBalanced() throws Exception {
        TestSupport.Output o = run(planner().compress(source, CompressionRequest.of(CompressionPreset.BALANCED)));
        assertEquals(o.toString(), "video/avc", o.videoMime);
        assertNotNull(o.toString(), o.audioMime);
        assertDuration(o, SOURCE_SECONDS, 0.35);
        assertTrue("smaller than the original: " + o, o.bytes < video.length());
        assertFalse(o.toString(), o.isPortrait());
    }

    @Test
    public void compressSmallFile() throws Exception {
        TestSupport.Output o = run(planner().compress(source, CompressionRequest.of(CompressionPreset.SMALL_FILE)));
        assertDuration(o, SOURCE_SECONDS, 0.35);
        assertTrue("much smaller than the original: " + o, o.bytes < video.length() * 0.8);
    }

    @Test
    public void compressToTargetSize() throws Exception {
        long target = video.length() / 3;
        Plan plan = planner().compress(source, CompressionRequest.targetSize(target));
        TestSupport.Output o = run(plan);
        assertDuration(o, SOURCE_SECONDS, 0.35);
        // Hardware encoders are less exact than software ones; allow 25 % for this tiny clip.
        assertTrue("close to the target of " + target + ": " + o, o.bytes < target * 1.25);
    }

    @Test
    public void trim() throws Exception {
        TestSupport.Output o = run(planner().trim(source, new TimeRange(1.0, 2.5), true));
        assertDuration(o, 1.5, 0.35);
        assertNotNull(o.toString(), o.videoMime);
        assertNotNull(o.toString(), o.audioMime);
    }

    @Test
    public void rotateWithoutReencoding() throws Exception {
        TestSupport.Output o = run(planner().rotate(source, 90, true));
        assertTrue("shown upright as portrait: " + o, o.isPortrait());
        assertDuration(o, SOURCE_SECONDS, 0.35);
    }

    @Test
    public void rotateByReencoding() throws Exception {
        TestSupport.Output o = run(planner().rotate(source, 270, false));
        assertTrue("shown as portrait: " + o, o.isPortrait());
        assertNotNull(o.toString(), o.audioMime);
    }

    @Test
    public void compressAPortraitVideoKeepsItPortrait() throws Exception {
        TestSupport.Output rotated = run(planner().rotate(source, 90, true));
        MediaInfo portrait = MediaProbe.probe(TestSupport.context(), rotated.uri);
        TestSupport.Output o = TestSupport.run(
                planner().compress(portrait, CompressionRequest.of(CompressionPreset.BALANCED)).job(),
                rotated.uri, "-compressed");
        assertTrue("still portrait after compression: " + o, o.isPortrait());
    }

    @Test
    public void mute() throws Exception {
        TestSupport.Output o = run(planner().mute(source));
        assertNotNull(o.toString(), o.videoMime);
        assertNull("no sound left: " + o, o.audioMime);
        assertDuration(o, SOURCE_SECONDS, 0.35);
    }

    @Test
    public void extractAudioAsM4a() throws Exception {
        TestSupport.Output o = run(planner().extractAudio(source, AudioFileFormat.M4A, 128_000, true, 0));
        assertNull(o.toString(), o.videoMime);
        assertEquals(o.toString(), "audio/mp4a-latm", o.audioMime);
        assertDuration(o, SOURCE_SECONDS, 0.35);
    }

    @Test
    public void extractAudioAsMp3() throws Exception {
        TestSupport.Output o = run(planner().extractAudio(source, AudioFileFormat.MP3, 192_000, false, 0));
        assertNull(o.toString(), o.videoMime);
        assertEquals(o.toString(), "audio/mpeg", o.audioMime);
        assertEquals(o.toString(), 2, o.audioChannels);
        assertDuration(o, SOURCE_SECONDS, 0.4);
    }

    @Test
    public void convertToMkv() throws Exception {
        TestSupport.Output o = run(planner().convert(source, Container.MKV));
        assertNotNull(o.toString(), o.videoMime);
        assertNotNull(o.toString(), o.audioMime);
        assertDuration(o, SOURCE_SECONDS, 0.35);
    }

    @Test
    public void convertToWebm() throws Exception {
        TestSupport.Output o = run(planner().convert(source, Container.WEBM));
        assertTrue(o.toString(), "video/x-vnd.on2.vp9".equals(o.videoMime) || "video/x-vnd.on2.vp8".equals(o.videoMime));
        assertEquals(o.toString(), "audio/opus", o.audioMime);
        assertDuration(o, SOURCE_SECONDS, 0.35);
    }

    @Test
    public void convertToMov() throws Exception {
        TestSupport.Output o = run(planner().convert(source, Container.MOV));
        assertEquals(o.toString(), "video/avc", o.videoMime);
        assertNotNull(o.toString(), o.audioMime);
        assertDuration(o, SOURCE_SECONDS, 0.35);
    }

    @Test
    public void changeResolution() throws Exception {
        TestSupport.Output o = run(planner().resize(source, 180));
        assertEquals(o.toString(), 180, Math.min(o.width, o.height));
        assertEquals(o.toString(), 320, Math.max(o.width, o.height));
    }

    @Test
    public void changeFrameRate() throws Exception {
        TestSupport.Output o = run(planner().changeFrameRate(source, 15));
        assertDuration(o, SOURCE_SECONDS, 0.35);
        if (o.frameCount > 0) {
            assertTrue("about 45 frames instead of 90: " + o, o.frameCount <= 50 && o.frameCount >= 40);
        }
    }
}
