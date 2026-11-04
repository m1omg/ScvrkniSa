package io.github.m1omg.shrink.desktop.engine;

import io.github.m1omg.shrink.core.model.AudioStream;
import io.github.m1omg.shrink.core.model.CompressionPreset;
import io.github.m1omg.shrink.core.model.Container;
import io.github.m1omg.shrink.core.model.EncoderSpeed;
import io.github.m1omg.shrink.core.model.Job;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.VideoCodec;
import io.github.m1omg.shrink.core.model.VideoStream;
import io.github.m1omg.shrink.core.plan.CompressionRequest;
import io.github.m1omg.shrink.core.plan.EncodingDefaults;
import io.github.m1omg.shrink.core.plan.JobPlanner;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the commands for hardware acceleration (as used on Macs) without needing the hardware. */
class HardwareCommandTest {

    private static FfmpegTools macTools() {
        return new FfmpegTools(Path.of("/opt/ffmpeg"), Path.of("/opt/ffprobe"), FfmpegTools.Source.BUNDLED, "7.1",
                new HashSet<>(Arrays.asList("libx264", "libx265", "aac", "h264_videotoolbox", "hevc_videotoolbox")),
                new HashSet<>(Arrays.asList("zscale", "tonemap", "scale")),
                new HashSet<>(Arrays.asList("h264_videotoolbox", "hevc_videotoolbox")), "videotoolbox");
    }

    private static Job hardwareJob() {
        MediaInfo src = MediaInfo.builder().fileName("clip.mov").location("/v/clip.mov").sizeBytes(50_000_000)
                .durationSeconds(20).formatName("mov,mp4,m4a,3gp,3g2,mj2")
                .video(VideoStream.builder().index(0).codecName("hevc").width(1920).height(1080).frameRate(30)
                        .bitrate(19_000_000).build())
                .addAudio(AudioStream.builder().index(1).codecName("aac").sampleRate(48_000).channels(2)
                        .bitrate(128_000).build())
                .build();
        EncodingDefaults hw = new EncodingDefaults(VideoCodec.H264, Container.MP4, EncoderSpeed.BALANCED, true,
                true, true);
        return new JobPlanner(hw).compress(src, CompressionRequest.of(CompressionPreset.BALANCED)).job();
    }

    @Test
    void usesVideoToolboxForEncodingAndDecoding() throws Exception {
        Job job = hardwareJob();
        CommandBuilder.Commands c = new CommandBuilder(macTools()).build(job, Path.of("/v/clip.mov"),
                Path.of("/v/out.mp4"), true);
        assertTrue(c.hardware());
        assertTrue(c.hardwareDecode());
        assertEquals(1, c.passes().size(), "hardware encoders work in one pass");
        List<String> args = c.passes().get(0).args();
        int hwaccel = args.indexOf("-hwaccel");
        assertTrue(hwaccel >= 0 && args.get(hwaccel + 1).equals("videotoolbox"));
        assertTrue(hwaccel < args.indexOf("-i"), "-hwaccel is an input option");
        assertEquals("h264_videotoolbox", args.get(args.indexOf("-c:v") + 1));
    }

    @Test
    void softwareRetryHasNoHardware() throws Exception {
        Job job = hardwareJob();
        CommandBuilder.Commands c = new CommandBuilder(macTools()).build(job, Path.of("/v/clip.mov"),
                Path.of("/v/out.mp4"), false);
        assertFalse(c.usesHardware());
        List<String> args = c.passes().get(0).args();
        assertFalse(args.contains("-hwaccel"));
        assertEquals("libx264", args.get(args.indexOf("-c:v") + 1));
    }

    @Test
    void unprobedEncodersAreNotUsed() {
        FfmpegTools listedButBroken = new FfmpegTools(Path.of("/f"), Path.of("/p"), FfmpegTools.Source.SYSTEM, "7.1",
                new HashSet<>(Arrays.asList("libx264", "h264_nvenc")), new HashSet<>(), new HashSet<>(), null);
        assertEquals(null, listedButBroken.hardwareEncoder(VideoCodec.H264));
    }
}
