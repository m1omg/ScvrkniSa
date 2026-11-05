package io.github.m1omg.shrink.desktop.ui.player;

import io.github.m1omg.shrink.core.model.VideoStream;
import io.github.m1omg.shrink.desktop.engine.AudioPipe;
import io.github.m1omg.shrink.desktop.engine.Engine;
import io.github.m1omg.shrink.desktop.engine.EngineException;
import io.github.m1omg.shrink.desktop.engine.FramePipe;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

/**
 * Plays one or more videos in sync (the preview compares two), optionally with
 * sound.
 *
 * <p>Timing never depends on the display: each frame has a due time
 * ({@code index / fps}) that is compared with a clock. With sound the clock is
 * the audio device's playback position, otherwise the system's monotonic
 * nanosecond clock. Late frames are skipped, early ones wait.
 */
public final class Playback {

    /** One video to play. */
    public record Source(Path file, VideoStream video, double start) {
    }

    /** The sound to play along (audio stream index inside the file). */
    public record Sound(Path file, int streamIndex, double start) {
    }

    private final Engine engine;
    private final List<Source> sources;
    private final Sound sound;
    private final double duration;
    private final double fps;
    private final int width;
    private final int height;
    private final FrameSurface surface;
    private volatile boolean stopped;
    private Thread thread;
    private final List<AutoCloseable> resources = new ArrayList<>();

    public Playback(Engine engine, List<Source> sources, Sound sound, double duration, double fps, int width,
                    int height, FrameSurface surface) {
        this.engine = engine;
        this.sources = sources;
        this.sound = sound;
        this.duration = duration;
        this.fps = fps > 0 ? Math.min(fps, 60) : 30;
        this.width = Math.max(2, width & ~1);
        this.height = Math.max(2, height & ~1);
        this.surface = surface;
    }

    public void start() {
        thread = new Thread(this::run, "shrink-playback");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        stopped = true;
        closeResources();
        if (thread != null) {
            thread.interrupt();
        }
    }

    public boolean isStopped() {
        return stopped;
    }

    private synchronized void closeResources() {
        for (AutoCloseable r : resources) {
            try {
                r.close();
            } catch (Exception ignored) {
                // closing anyway
            }
        }
        resources.clear();
    }

    private synchronized void track(AutoCloseable r) {
        if (stopped) {
            try {
                r.close();
            } catch (Exception ignored) {
                // closing anyway
            }
        } else {
            resources.add(r);
        }
    }

    private void run() {
        List<FramePipe> pipes = new ArrayList<>();
        AudioClock clock = null;
        try {
            for (Source s : sources) {
                FramePipe pipe = engine.framePipe(s.file(), s.video(), s.start(), duration, width, height, fps);
                track(pipe);
                pipes.add(pipe);
            }
            if (sound != null) {
                clock = AudioClock.open(engine, sound, duration);
                if (clock != null) {
                    track(clock);
                }
            }
            BufferedImage[] frames = new BufferedImage[pipes.size()];
            for (int i = 0; i < pipes.size(); i++) {
                frames[i] = pipes.get(i).createImage();
            }
            long wallStart = System.nanoTime();
            double frameDuration = 1.0 / fps;
            for (long index = 0; !stopped; index++) {
                for (int i = 0; i < pipes.size(); i++) {
                    if (!pipes.get(i).read(frames[i])) {
                        return;
                    }
                }
                double due = index * frameDuration;
                double now = clock != null ? clock.seconds(wallStart) : (System.nanoTime() - wallStart) / 1e9;
                if (now - due > 1.5 * frameDuration && index > 0) {
                    continue; // running late: drop this frame
                }
                while (!stopped && now < due) {
                    LockSupport.parkNanos((long) (Math.min(due - now, 0.02) * 1e9));
                    now = clock != null ? clock.seconds(wallStart) : (System.nanoTime() - wallStart) / 1e9;
                }
                if (!stopped) {
                    surface.showFrames(frames, due);
                }
            }
        } catch (EngineException | IOException e) {
            // Decoder ended or could not start: treat as the end of playback.
        } finally {
            closeResources();
            if (!stopped) {
                surface.playbackFinished();
            }
        }
    }

    /**
     * Plays the sound and tells the current position. Falls back to the wall
     * clock when the sound ends early or no audio device is available.
     */
    private static final class AudioClock implements AutoCloseable {
        private final SourceDataLine line;
        private final AudioPipe pipe;
        private final Thread writer;
        private volatile boolean finished;
        private volatile double finishedAt = -1;
        private volatile long finishedNanos;

        private AudioClock(SourceDataLine line, AudioPipe pipe) {
            this.line = line;
            this.pipe = pipe;
            this.writer = new Thread(this::pump, "shrink-audio");
            this.writer.setDaemon(true);
        }

        static AudioClock open(Engine engine, Sound sound, double duration) {
            AudioFormat format = new AudioFormat(AudioPipe.SAMPLE_RATE, 16, AudioPipe.CHANNELS, true, false);
            SourceDataLine line;
            try {
                line = AudioSystem.getSourceDataLine(format);
                line.open(format, AudioPipe.SAMPLE_RATE * AudioPipe.BYTES_PER_FRAME / 5);
            } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
                return null; // no sound device: play silently
            }
            try {
                AudioPipe pipe = engine.audioPipe(sound.file(), sound.streamIndex(), sound.start(), duration);
                AudioClock clock = new AudioClock(line, pipe);
                line.start();
                clock.writer.start();
                return clock;
            } catch (EngineException e) {
                line.close();
                return null;
            }
        }

        private void pump() {
            byte[] buffer = new byte[AudioPipe.SAMPLE_RATE * AudioPipe.BYTES_PER_FRAME / 20];
            try {
                int n;
                while ((n = pipe.read(buffer, 0, buffer.length)) > 0) {
                    int aligned = n - (n % AudioPipe.BYTES_PER_FRAME);
                    line.write(buffer, 0, aligned);
                }
                line.drain();
            } catch (IOException | IllegalStateException e) {
                // stopped
            } finally {
                finishedAt = position();
                finishedNanos = System.nanoTime();
                finished = true;
            }
        }

        private double position() {
            return line.getLongFramePosition() / (double) AudioPipe.SAMPLE_RATE;
        }

        double seconds(long wallStart) {
            if (finished) {
                return finishedAt + (System.nanoTime() - finishedNanos) / 1e9;
            }
            return position();
        }

        @Override
        public void close() {
            pipe.close();
            line.stop();
            line.flush();
            line.close();
        }
    }
}
