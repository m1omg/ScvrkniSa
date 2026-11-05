package io.github.m1omg.shrink.android.media;

import android.content.Context;
import android.media.MediaCodecInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.FrameDropEffect;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.media3.muxer.Muxer;
import androidx.media3.transformer.AudioEncoderSettings;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.DefaultMuxer;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.VideoEncoderSettings;

import io.github.m1omg.shrink.core.model.AudioCodec;
import io.github.m1omg.shrink.core.model.AudioFileFormat;
import io.github.m1omg.shrink.core.model.AudioOutput;
import io.github.m1omg.shrink.core.model.AudioStream;
import io.github.m1omg.shrink.core.model.Container;
import io.github.m1omg.shrink.core.model.Job;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.RateControl;
import io.github.m1omg.shrink.core.model.VideoCodec;
import io.github.m1omg.shrink.core.model.VideoOutput;
import io.github.m1omg.shrink.core.model.VideoStream;
import io.github.m1omg.shrink.core.plan.QualityModel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs a shared {@link Job} with Media3 Transformer, which uses the phone's
 * hardware encoders. MP3 extraction uses {@link Mp3Extractor} instead.
 *
 * <p>All methods must be called on the main thread; results are delivered there too.
 */
@OptIn(markerClass = UnstableApi.class)
public final class Media3Runner {

    private static final String TAG = "Shrink";

    /** Receives progress and the outcome on the main thread. */
    public interface Listener {
        void progress(int percent);

        void completed(File output);

        void failed(String message);
    }

    private static final long POLL_MS = 400;

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private Transformer transformer;
    private Runnable poller;

    public Media3Runner(Context context) {
        this.context = context.getApplicationContext();
    }

    public void start(Job job, Uri input, File output, Listener listener) {
        start(job, input, output, listener, false);
    }

    /**
     * @param constantBitrate encode video with a constant bitrate (CBR) instead of the
     *                        default variable one; used when a first try came out too big
     */
    public void start(Job job, Uri input, File output, Listener listener, boolean constantBitrate) {
        Job adapted = adapt(job);
        if (adapted.isAudioOnly() && adapted.audioFormat() == AudioFileFormat.MP3) {
            runMp3(adapted, input, output, listener);
        } else {
            runTransformer(adapted, input, output, listener, constantBitrate);
        }
    }

    public void cancel() {
        cancelled.set(true);
        if (poller != null) {
            main.removeCallbacks(poller);
        }
        if (transformer != null) {
            transformer.cancel();
        }
    }

    // ------------------------------------------------------------------ MP3

    private void runMp3(Job job, Uri input, File output, Listener listener) {
        int kbps = (int) Math.max(64, job.audio().isEncode() ? job.audio().bitrate() / 1000 : 192);
        Thread worker = new Thread(() -> {
            try {
                Mp3Extractor.extract(context, input, job.audioStreamIndex(), kbps, output,
                        percent -> main.post(() -> listener.progress(percent)), cancelled);
                main.post(() -> {
                    if (!cancelled.get()) {
                        listener.completed(output);
                    }
                });
            } catch (IOException | RuntimeException e) {
                main.post(() -> {
                    if (!cancelled.get()) {
                        listener.failed(e.getMessage() == null ? e.toString() : e.getMessage());
                    }
                });
            }
        }, "shrink-mp3");
        worker.start();
    }

    // ------------------------------------------------------------------ Transformer

    private void runTransformer(Job job, Uri input, File output, Listener listener, boolean constantBitrate) {
        MediaItem.Builder itemBuilder = new MediaItem.Builder().setUri(input);
        if (job.trim() != null) {
            itemBuilder.setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(Math.round(job.trim().start() * 1000))
                    .setEndPositionMs(Math.round(job.trim().end() * 1000))
                    .build());
        }
        boolean video = !job.isAudioOnly() && !job.video().isNone() && job.source().hasVideo();
        boolean audio = !job.audio().isNone() && job.source().hasAudio();
        boolean encodeVideo = video && job.video().isEncode();

        List<Effect> effects = new ArrayList<>();
        if (encodeVideo) {
            if (job.isScaled()) {
                effects.add(Presentation.createForWidthAndHeight(job.outputWidth(), job.outputHeight(),
                        Presentation.LAYOUT_SCALE_TO_FIT));
            }
            if (job.rotation() != 0 && !job.rotationByMetadata()) {
                // Media3 rotates counter-clockwise; the job's rotation is clockwise.
                effects.add(new ScaleAndRotateTransformation.Builder()
                        .setRotationDegrees(360 - job.rotation())
                        .build());
            }
            if (job.outputFrameRate() > 0) {
                effects.add(FrameDropEffect.createDefaultFrameDropEffect((float) job.outputFrameRate()));
            }
        }
        EditedMediaItem item = new EditedMediaItem.Builder(itemBuilder.build())
                .setRemoveAudio(!audio)
                .setRemoveVideo(!video)
                .setEffects(new Effects(Collections.emptyList(), effects))
                .build();
        List<EditedMediaItem> items = Collections.singletonList(item);
        EditedMediaItemSequence sequence;
        if (video && audio) {
            sequence = EditedMediaItemSequence.withAudioAndVideoFrom(items);
        } else if (video) {
            sequence = EditedMediaItemSequence.withVideoFrom(items);
        } else {
            sequence = EditedMediaItemSequence.withAudioFrom(items);
        }
        Composition composition = new Composition.Builder(sequence)
                .setTransmuxVideo(video && job.video().isCopy())
                .setTransmuxAudio(audio && job.audio().isCopy())
                .setHdrMode(job.toneMapHdr() ? Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
                        : Composition.HDR_MODE_KEEP_HDR)
                .build();

        Transformer.Builder builder = new Transformer.Builder(context)
                .addListener(new Transformer.Listener() {
                    @Override
                    public void onCompleted(Composition c, ExportResult result) {
                        stopPolling();
                        if (cancelled.get()) {
                            return;
                        }
                        Log.i(TAG, "Exported " + result.fileSizeBytes + " B; video: " + result.videoEncoderName
                                + " " + result.averageVideoBitrate + " bit/s (asked "
                                + (job.video().isEncode() ? job.video().bitrate() : 0)
                                + (constantBitrate ? ", constant" : "") + "); audio: "
                                + result.audioEncoderName + " " + result.averageAudioBitrate + " bit/s");
                        try {
                            if (job.container() == Container.MOV) {
                                MovBrand.apply(output);
                            }
                            listener.completed(output);
                        } catch (IOException e) {
                            listener.failed(e.getMessage());
                        }
                    }

                    @Override
                    public void onError(Composition c, ExportResult result, ExportException e) {
                        stopPolling();
                        if (!cancelled.get()) {
                            listener.failed(e.getErrorCodeName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
                        }
                    }
                });

        DefaultEncoderFactory.Builder encoders = new DefaultEncoderFactory.Builder(context).setEnableFallback(true);
        if (encodeVideo) {
            builder.setVideoMimeType(EncoderSupport.mimeOf(job.video().codec()));
            long bitrate = Math.min(Integer.MAX_VALUE, Math.max(64_000, job.video().bitrate()));
            VideoEncoderSettings.Builder settings = new VideoEncoderSettings.Builder().setBitrate((int) bitrate);
            if (constantBitrate) {
                settings.setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
            }
            encoders.setRequestedVideoEncoderSettings(settings.build());
        }
        if (audio && job.audio().isEncode()) {
            builder.setAudioMimeType(EncoderSupport.mimeOf(job.audio().codec()));
            encoders.setRequestedAudioEncoderSettings(new AudioEncoderSettings.Builder()
                    .setBitrate((int) Math.max(32_000, job.audio().bitrate()))
                    .build());
        }
        builder.setEncoderFactory(encoders.build());

        Muxer.Factory muxer;
        Container container = job.isAudioOnly() ? Container.MP4 : job.container();
        if (container == Container.MKV || container == Container.WEBM) {
            muxer = new MatroskaMuxer.Factory(container == Container.WEBM);
            // Matroska has no standard rotation flag: encode portrait video upright.
            builder.setPortraitEncodingEnabled(true);
        } else {
            muxer = new DefaultMuxer.Factory();
        }
        if (job.rotationByMetadata() && job.source().hasVideo()) {
            int total = (job.source().video().rotation() + job.rotation()) % 360;
            muxer = new RotationMuxer.Factory(muxer, total);
        }
        builder.setMuxerFactory(muxer);

        transformer = builder.build();
        transformer.start(composition, output.getAbsolutePath());
        ProgressHolder holder = new ProgressHolder();
        poller = new Runnable() {
            @Override
            public void run() {
                if (transformer == null || cancelled.get()) {
                    return;
                }
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    listener.progress(holder.progress);
                }
                main.postDelayed(this, POLL_MS);
            }
        };
        main.postDelayed(poller, POLL_MS);
    }

    private void stopPolling() {
        if (poller != null) {
            main.removeCallbacks(poller);
        }
    }

    // ------------------------------------------------------------------ Android limits

    /**
     * Adjusts a job to what Media3 and this phone can do: no two-pass or CRF
     * (MediaCodec targets a bitrate), Matroska/WebM support only some codecs,
     * MP4 needs AAC or Opus audio, and unsupported encoders fall back to H.264.
     */
    public static Job adapt(Job job) {
        Job.Builder b = job.toBuilder();
        MediaInfo src = job.source();
        VideoStream v = src.video();
        Container container = job.container();

        VideoOutput video = job.video();
        if (!job.isAudioOnly() && v != null && video.isCopy() && container != null
                && !canCopyVideo(container, v.codecName())) {
            VideoCodec codec = container == Container.WEBM ? VideoCodec.VP9 : VideoCodec.H264;
            video = VideoOutput.encode(codec, RateControl.ONE_PASS, transparentBitrate(src, codec), 0,
                    io.github.m1omg.shrink.core.model.EncoderSpeed.BALANCED, true);
            b.rotationByMetadata(false);
        }
        if (video.isEncode()) {
            VideoCodec codec = video.codec();
            if (!EncoderSupport.canEncode(codec)) {
                codec = container == Container.WEBM ? VideoCodec.VP9 : VideoCodec.H264;
            }
            if (container == Container.MKV && codec != VideoCodec.H264 && codec != VideoCodec.VP9) {
                codec = VideoCodec.H264;
            }
            long bitrate = video.bitrate();
            if (codec != video.codec()) {
                bitrate = Math.round(bitrate / video.codec().efficiency() * codec.efficiency());
            }
            video = VideoOutput.encode(codec, RateControl.ONE_PASS, bitrate, 0, video.speed(), true);
            if (v != null && v.isHdr() && codec == VideoCodec.H264) {
                b.toneMapHdr(true);
            }
        }
        b.video(video);

        AudioOutput audio = job.audio();
        AudioStream a = src.audio();
        if (a != null && audio.isCopy()) {
            boolean ok;
            if (job.isAudioOnly()) {
                ok = job.audioFormat() == AudioFileFormat.M4A ? "aac".equals(a.codecName())
                        : "mp3".equals(a.codecName());
            } else {
                ok = canCopyAudio(container, a.codecName());
            }
            if (!ok) {
                AudioCodec codec = container == Container.WEBM ? AudioCodec.OPUS : AudioCodec.AAC;
                audio = AudioOutput.encode(codec, Math.min(192_000, Math.max(96_000, a.bitrateOrGuess())), 0);
            }
        }
        b.audio(audio);
        return b.build();
    }

    private static boolean canCopyVideo(Container container, String codec) {
        String c = codec == null ? "" : codec.toLowerCase(Locale.ROOT);
        switch (container) {
            case MKV:
                return c.equals("h264") || c.equals("vp9") || c.equals("vp8");
            case WEBM:
                return c.equals("vp9") || c.equals("vp8");
            default:
                return c.equals("h264") || c.equals("hevc") || c.equals("av1") || c.equals("mpeg4")
                        || c.equals("vp9");
        }
    }

    private static boolean canCopyAudio(Container container, String codec) {
        String c = codec == null ? "" : codec.toLowerCase(Locale.ROOT);
        if (container == Container.WEBM) {
            return c.equals("opus");
        }
        return c.equals("aac") || c.equals("opus");
    }

    /** A bitrate that keeps the source's quality when the codec must change. */
    private static long transparentBitrate(MediaInfo src, VideoCodec codec) {
        VideoStream v = src.video();
        double h264 = src.videoBitrate() / VideoCodec.efficiencyOf(v.codecName());
        double cap = QualityModel.h264Bitrate(v.displayWidth(), v.displayHeight(),
                v.frameRate() > 0 ? v.frameRate() : 30, QualityModel.BPP_EXCELLENT * 1.3);
        return Math.round(Math.min(h264, cap) * codec.efficiency() * 1.2);
    }
}
