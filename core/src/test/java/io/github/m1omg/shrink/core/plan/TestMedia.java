package io.github.m1omg.shrink.core.plan;

import io.github.m1omg.shrink.core.model.AudioStream;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.SubtitleStream;
import io.github.m1omg.shrink.core.model.VideoStream;

/** Builds fake {@link MediaInfo} objects for planner tests. */
final class TestMedia {

    private TestMedia() {
    }

    /** The example from the feature description: 23 seconds of 720p in 3.9 MB. */
    static MediaInfo example720p() {
        return video("clip.mp4", 3_900_000, 23.0, "h264", 1280, 720, 30, 1_220_000, "aac", 128_000, 2, null, 0);
    }

    static MediaInfo phone4kHevc() {
        // 60 s of 4K 60 fps HEVC at 50 Mbps, as recorded by a modern phone.
        long size = Math.round((50_000_000 + 192_000) * 60.0 / 8);
        return video("IMG_0042.MOV", size, 60.0, "hevc", 3840, 2160, 60, 50_000_000, "aac", 192_000, 2, null, 90);
    }

    static MediaInfo video(String name, long size, double duration, String codec, int w, int h, double fps,
                           long videoBitrate, String audioCodec, long audioBitrate, int channels,
                           String transfer, int rotation) {
        MediaInfo.Builder b = MediaInfo.builder()
                .fileName(name)
                .location("/videos/" + name)
                .sizeBytes(size)
                .durationSeconds(duration)
                .formatName(name.endsWith(".mkv") ? "matroska,webm" : "mov,mp4,m4a,3gp,3g2,mj2")
                .video(VideoStream.builder().index(0).codecName(codec).width(w).height(h).frameRate(fps)
                        .bitrate(videoBitrate).pixelFormat("yuv420p").colorTransfer(transfer).rotation(rotation)
                        .build());
        if (audioCodec != null) {
            b.addAudio(AudioStream.builder().index(1).codecName(audioCodec).sampleRate(48_000).channels(channels)
                    .bitrate(audioBitrate).build());
        }
        return b.build();
    }

    static MediaInfo withSubtitles(MediaInfo base, String codec) {
        MediaInfo.Builder b = MediaInfo.builder()
                .fileName(base.fileName())
                .location(base.location())
                .sizeBytes(base.sizeBytes())
                .durationSeconds(base.durationSeconds())
                .formatName(base.formatName())
                .video(base.video());
        for (AudioStream a : base.audioStreams()) {
            b.addAudio(a);
        }
        b.addSubtitle(new SubtitleStream(2, codec, "eng"));
        return b.build();
    }
}
