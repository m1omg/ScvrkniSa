package io.github.m1omg.shrink.core.plan;

import io.github.m1omg.shrink.core.model.AudioFileFormat;
import io.github.m1omg.shrink.core.model.AudioStream;
import io.github.m1omg.shrink.core.model.Container;
import io.github.m1omg.shrink.core.model.Job;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.RateControl;
import io.github.m1omg.shrink.core.model.VideoOutput;
import io.github.m1omg.shrink.core.model.VideoStream;

import java.util.List;

/** Predicts the size of a job's output file. */
public final class SizeEstimator {

    /** AAC and Opus produce about 47 audio packets per second at 48 kHz. */
    private static final double AUDIO_PACKETS_PER_SECOND = 47.0;

    private SizeEstimator() {
    }

    /** Estimated output size of the job in bytes. */
    public static long estimate(Job job) {
        double duration = job.outputDuration();
        if (duration <= 0) {
            return 0;
        }
        double videoBps = videoBitrate(job);
        double audioBps = audioBitrate(job);
        double payloadBytes = (videoBps + audioBps) * duration / 8.0;
        double overheadBytes = fixedOverhead(job) + perSecondOverhead(job) * duration;
        return Math.max(0L, Math.round(payloadBytes + overheadBytes));
    }

    /** Video bitrate (bits per second) the job will produce. */
    public static double videoBitrate(Job job) {
        VideoOutput v = job.video();
        MediaInfo src = job.source();
        if (v.isNone() || job.isAudioOnly() || !src.hasVideo()) {
            return 0;
        }
        if (v.isCopy()) {
            return src.videoBitrate();
        }
        if (v.rateControl() == RateControl.CONSTANT_QUALITY && !v.hardware()) {
            VideoStream vs = src.video();
            int w = job.isScaled() ? job.outputWidth() : vs.displayWidth();
            int h = job.isScaled() ? job.outputHeight() : vs.displayHeight();
            double fps = job.outputFrameRate() > 0 ? job.outputFrameRate() : vs.frameRate();
            double modelled = QualityModel.bitrateForCrf(w, h, fps, v.crf(), v.codec());
            // With constant quality the bitrate field is a cap (maxrate).
            return v.bitrate() > 0 ? Math.min(modelled, v.bitrate()) : modelled;
        }
        return v.bitrate();
    }

    /** Audio bitrate (bits per second) the job will produce, summed over all kept tracks. */
    public static double audioBitrate(Job job) {
        if (job.audio().isNone()) {
            return 0;
        }
        List<AudioStream> streams = job.source().audioStreams();
        if (streams.isEmpty()) {
            return 0;
        }
        int trackCount = job.keepAllAudio() ? streams.size() : 1;
        if (job.audio().isEncode()) {
            return (double) job.audio().bitrate() * trackCount;
        }
        if (job.keepAllAudio()) {
            return job.source().totalAudioBitrate();
        }
        int index = Math.min(Math.max(job.audioStreamIndex(), 0), streams.size() - 1);
        return streams.get(index).bitrateOrGuess();
    }

    private static double fixedOverhead(Job job) {
        if (job.isAudioOnly()) {
            return job.audioFormat() == AudioFileFormat.MP3 ? 1_200 : 1_500;
        }
        return job.container() == Container.MKV || job.container() == Container.WEBM ? 4_500 : 3_000;
    }

    /** Container bookkeeping (sample tables, block headers) per second of output. */
    private static double perSecondOverhead(Job job) {
        boolean hasAudio = !job.audio().isNone() && job.source().hasAudio();
        double audioPackets = hasAudio ? AUDIO_PACKETS_PER_SECOND : 0;
        if (job.isAudioOnly()) {
            return job.audioFormat() == AudioFileFormat.MP3 ? 0 : audioPackets * 9;
        }
        double fps = job.outputFrameRate() > 0 ? job.outputFrameRate()
                : (job.source().hasVideo() ? job.source().video().frameRate() : 0);
        if (job.video().isNone()) {
            fps = 0;
        }
        double bytesPerPacket = job.container() == Container.MKV || job.container() == Container.WEBM ? 8 : 12;
        return (fps + audioPackets) * bytesPerPacket;
    }

    /** Bytes the container adds for a payload of the given duration; used for target-size planning. */
    static double overheadBytes(Container container, double fps, boolean audio, double duration) {
        double fixed = container == Container.MKV || container == Container.WEBM ? 4_500 : 3_000;
        double bytesPerPacket = container == Container.MKV || container == Container.WEBM ? 8 : 12;
        double perSecond = (fps + (audio ? AUDIO_PACKETS_PER_SECOND : 0)) * bytesPerPacket;
        return fixed + perSecond * duration;
    }
}
