package io.github.m1omg.shrink.android.media;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Saves a video's sound as MP3: the audio is decoded with Android's MediaCodec
 * and encoded with {@link Mp3Encoder}. Runs on the calling (background) thread.
 */
public final class Mp3Extractor {

    /** Progress in percent (0..100). */
    public interface Progress {
        void update(int percent);
    }

    private static final long TIMEOUT_US = 10_000;

    private Mp3Extractor() {
    }

    /**
     * @param audioTrack index of the audio track among the file's audio tracks (0 = first)
     * @param kbps       MP3 bitrate
     */
    public static void extract(Context context, Uri input, int audioTrack, int kbps, File output,
                               Progress progress, AtomicBoolean cancelled) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(output), 1 << 16)) {
            extractor.setDataSource(context, input, null);
            int track = -1;
            int seen = 0;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    if (seen == audioTrack) {
                        track = i;
                        break;
                    }
                    seen++;
                }
            }
            if (track < 0) {
                throw new IOException("No audio track");
            }
            extractor.selectTrack(track);
            MediaFormat format = extractor.getTrackFormat(track);
            long durationUs = format.containsKey(MediaFormat.KEY_DURATION) ? format.getLong(MediaFormat.KEY_DURATION)
                    : 0;
            decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            decoder.configure(format, null, null, 0);
            decoder.start();

            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;
            Mp3Encoder encoder = null;
            short[] stereo = new short[0];
            byte[] mp3 = new byte[Mp3Encoder.outputSize(8192)];
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;
            int lastPercent = -1;

            while (!outputDone) {
                if (cancelled.get()) {
                    throw new IOException("cancelled");
                }
                if (!inputDone) {
                    int in = decoder.dequeueInputBuffer(TIMEOUT_US);
                    if (in >= 0) {
                        ByteBuffer buffer = decoder.getInputBuffer(in);
                        int size = buffer == null ? -1 : extractor.readSampleData(buffer, 0);
                        if (size < 0) {
                            decoder.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            decoder.queueInputBuffer(in, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                int outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US);
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = decoder.getOutputFormat();
                    sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (of.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        pcmEncoding = of.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    }
                } else if (outIndex >= 0) {
                    ByteBuffer pcm = decoder.getOutputBuffer(outIndex);
                    if (pcm != null && info.size > 0) {
                        if (encoder == null) {
                            encoder = new Mp3Encoder(mp3SampleRate(sampleRate), Math.min(channels, 2), kbps);
                        }
                        pcm.position(info.offset);
                        pcm.limit(info.offset + info.size);
                        int frames = toStereo(pcm, channels, pcmEncoding, stereo);
                        if (frames * 2 > stereo.length) {
                            stereo = new short[frames * 2];
                            pcm.position(info.offset);
                            frames = toStereo(pcm, channels, pcmEncoding, stereo);
                        }
                        if (mp3.length < Mp3Encoder.outputSize(frames)) {
                            mp3 = new byte[Mp3Encoder.outputSize(frames)];
                        }
                        int written = encoder.encode(stereo, frames, mp3);
                        out.write(mp3, 0, written);
                    }
                    decoder.releaseOutputBuffer(outIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                    if (durationUs > 0 && progress != null) {
                        int percent = (int) Math.min(99, info.presentationTimeUs * 100 / durationUs);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            progress.update(percent);
                        }
                    }
                }
            }
            if (encoder != null) {
                int written = encoder.flush(mp3);
                out.write(mp3, 0, written);
            }
        } finally {
            if (decoder != null) {
                try {
                    decoder.stop();
                } catch (IllegalStateException ignored) {
                    // already stopped
                }
                decoder.release();
            }
            extractor.release();
        }
    }

    /** MP3 supports only certain sample rates; higher rates are resampled by the encoder. */
    private static int mp3SampleRate(int rate) {
        return Math.min(rate, 48_000);
    }

    /**
     * Converts decoded PCM (16-bit or float, any channel count) to interleaved
     * 16-bit stereo or mono. Surround sound is mixed down so dialogue in the
     * centre channel is kept. Returns the number of frames, or the number needed
     * if {@code out} is too small.
     */
    static int toStereo(ByteBuffer pcm, int channels, int encoding, short[] out) {
        ByteBuffer b = pcm.slice().order(ByteOrder.nativeOrder());
        boolean isFloat = encoding == AudioFormat.ENCODING_PCM_FLOAT;
        int bytesPerSample = isFloat ? 4 : 2;
        int frames = b.remaining() / (bytesPerSample * Math.max(1, channels));
        int outChannels = Math.min(channels, 2);
        if (frames * outChannels > out.length) {
            return frames;
        }
        float[] s = new float[Math.max(6, channels)];
        for (int i = 0; i < frames; i++) {
            for (int c = 0; c < channels; c++) {
                s[c] = isFloat ? b.getFloat() : b.getShort() / 32768f;
            }
            if (channels <= 2) {
                out[i * outChannels] = clip(s[0]);
                if (outChannels == 2) {
                    out[i * 2 + 1] = clip(s[1]);
                }
            } else {
                // FL, FR, FC, LFE, BL, BR (Android channel order)
                float center = channels > 2 ? s[2] : 0;
                float backL = channels > 4 ? s[4] : 0;
                float backR = channels > 5 ? s[5] : 0;
                float l = (s[0] + 0.707f * center + 0.707f * backL) * 0.5f;
                float r = (s[1] + 0.707f * center + 0.707f * backR) * 0.5f;
                out[i * 2] = clip(l);
                out[i * 2 + 1] = clip(r);
            }
        }
        return frames;
    }

    private static short clip(float v) {
        float scaled = v * 32767f;
        if (scaled > 32767f) {
            return 32767;
        }
        if (scaled < -32768f) {
            return -32768;
        }
        return (short) Math.round(scaled);
    }
}
