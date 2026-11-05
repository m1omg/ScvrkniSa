package io.github.m1omg.shrink.desktop.engine;

import io.github.m1omg.shrink.core.model.Job;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.VideoStream;
import io.github.m1omg.shrink.core.plan.JobPlanner;
import io.github.m1omg.shrink.core.store.Json;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Everything the desktop app does with FFmpeg: reading file information,
 * running jobs with progress, and grabbing still frames.
 */
public final class Engine {

    /**
     * Progress of a whole job, all passes together. {@code correcting} is true during
     * the extra pass made when the first result came out far bigger than estimated.
     */
    public record Progress(double fraction, int pass, int passCount, double speed, double elapsedSeconds,
                           double remainingSeconds, boolean correcting) {
    }

    /** Receives job progress (on a background thread). */
    public interface Listener {
        void progress(Progress progress);

        /** Called when the hardware encoder failed and Shrink! retries in software. */
        default void fallingBackToSoftware() {
        }
    }

    /** What a finished job produced. */
    public record Result(Path output, long sizeBytes, double seconds, String encoder, boolean hardware) {
    }

    private final FfmpegTools tools;
    private final CommandBuilder builder;

    public Engine(FfmpegTools tools) {
        this.tools = tools;
        this.builder = new CommandBuilder(tools);
    }

    public FfmpegTools tools() {
        return tools;
    }

    // ------------------------------------------------------------------ probing

    /** Reads format and stream information of a file. */
    public MediaInfo probe(Path file) throws EngineException {
        if (!Files.isRegularFile(file)) {
            throw new EngineException("Not a file: " + file);
        }
        List<String> cmd = Arrays.asList(tools.ffprobe().toString(), "-v", "error", "-print_format", "json",
                "-show_format", "-show_streams", file.toString());
        byte[] out = FfmpegRunner.capture(cmd, 60);
        try {
            long size = Files.size(file);
            MediaInfo info = ProbeParser.parse(new String(out, StandardCharsets.UTF_8), file.toString(),
                    file.getFileName().toString(), size);
            if (!info.hasVideo() && !info.hasAudio()) {
                throw new EngineException("No audio or video streams");
            }
            if (info.durationSeconds() <= 0.05) {
                throw new EngineException("Not a video (no duration)");
            }
            return info;
        } catch (Json.JsonException | IOException e) {
            throw new EngineException("Could not read file information", e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ jobs

    /**
     * Runs a job. The result is first written to a hidden temporary file next to
     * {@code output} and renamed when finished, so an interrupted job never leaves
     * a broken file under the final name.
     */
    public Result run(Job job, Path input, Path output, Listener listener, FfmpegRunner.CancelToken token)
            throws EngineException, FfmpegRunner.CancelledException {
        Path dir = output.toAbsolutePath().getParent();
        Path partial = dir.resolve("." + output.getFileName() + "." + UUID.randomUUID().toString().substring(0, 8)
                + ".part");
        Path work = null;
        long started = System.nanoTime();
        try {
            Files.createDirectories(dir);
            work = Files.createTempDirectory("shrink-");
            CommandBuilder.Commands commands = builder.build(job, input, partial, true);
            try {
                runPasses(commands, job, work, listener, token, started, true, false);
            } catch (EngineException e) {
                if (!commands.usesHardware() || (token != null && token.isCancelled())) {
                    throw e;
                }
                // Hardware encoding or decoding failed for this file: retry in software.
                if (listener != null) {
                    listener.fallingBackToSoftware();
                }
                deleteQuietly(partial);
                commands = builder.build(job, input, partial, false);
                runPasses(commands, job, work, listener, token, started, true, false);
            }
            if (!Files.isRegularFile(partial) || Files.size(partial) == 0) {
                throw new EngineException("FFmpeg did not produce an output file");
            }
            commands = correctSize(job, input, partial, commands, work, listener, token);
            Files.move(partial, output, StandardCopyOption.REPLACE_EXISTING);
            double seconds = (System.nanoTime() - started) / 1e9;
            return new Result(output, Files.size(output), seconds, commands.videoEncoder(), commands.hardware());
        } catch (IOException e) {
            throw new EngineException("Could not write the result: " + e.getMessage(), "", e);
        } finally {
            deleteQuietly(partial);
            deleteTree(work);
        }
    }

    /**
     * Hardware encoders (and, rarely, one-pass software encoding) do not always keep
     * to the bitrate. When {@code partial} came out far bigger than the estimate the
     * person saw, encodes once more with a lower bitrate and keeps the smaller file.
     * Returns the commands that produced the file that is kept.
     */
    private CommandBuilder.Commands correctSize(Job job, Path input, Path partial, CommandBuilder.Commands commands,
                                                Path work, Listener listener, FfmpegRunner.CancelToken token)
            throws IOException, FfmpegRunner.CancelledException {
        Job corrected = JobPlanner.correctedForSize(job, Files.size(partial));
        if (corrected == null || (token != null && token.isCancelled())) {
            return commands;
        }
        Path second = partial.resolveSibling(partial.getFileName() + "2");
        try {
            CommandBuilder.Commands again = builder.build(corrected, input, second, commands.usesHardware());
            runPasses(again, corrected, work, listener, token, System.nanoTime(), true, true);
            if (Files.isRegularFile(second) && Files.size(second) > 0 && Files.size(second) < Files.size(partial)) {
                Files.move(second, partial, StandardCopyOption.REPLACE_EXISTING);
                return again;
            }
        } catch (EngineException e) {
            // The first result is still good.
        } finally {
            deleteQuietly(second);
        }
        return commands;
    }

    /**
     * Encodes a short preview job into {@code target} (normal priority, so it is
     * quick).
     */
    public Result renderPreview(Job previewJob, Path input, Path target, Listener listener,
                                FfmpegRunner.CancelToken token)
            throws EngineException, FfmpegRunner.CancelledException {
        Path work = null;
        long started = System.nanoTime();
        try {
            work = Files.createTempDirectory("shrink-preview-");
            CommandBuilder.Commands commands = builder.build(previewJob, input, target, true);
            try {
                runPasses(commands, previewJob, work, listener, token, started, false, false);
            } catch (EngineException e) {
                if (!commands.usesHardware() || (token != null && token.isCancelled())) {
                    throw e;
                }
                commands = builder.build(previewJob, input, target, false);
                runPasses(commands, previewJob, work, listener, token, started, false, false);
            }
            return new Result(target, Files.size(target), (System.nanoTime() - started) / 1e9,
                    commands.videoEncoder(), commands.hardware());
        } catch (IOException e) {
            throw new EngineException("Could not create the preview: " + e.getMessage(), "", e);
        } finally {
            deleteTree(work);
        }
    }

    private void runPasses(CommandBuilder.Commands commands, Job job, Path work, Listener listener,
                           FfmpegRunner.CancelToken token, long startedNanos, boolean lowPriority,
                           boolean correcting)
            throws EngineException, FfmpegRunner.CancelledException {
        double duration = Math.max(job.outputDuration(), 0.001);
        double before = 0;
        EtaSmoother eta = new EtaSmoother();
        for (CommandBuilder.Pass pass : commands.passes()) {
            final double offset = before;
            FfmpegRunner.ProgressSink sink = (outTime, speed) -> {
                if (listener == null) {
                    return;
                }
                double passFraction = Math.min(1.0, Math.max(0.0, outTime / duration));
                double fraction = offset + pass.weight() * passFraction;
                double elapsed = (System.nanoTime() - startedNanos) / 1e9;
                listener.progress(new Progress(fraction, pass.number(), pass.count(), speed, elapsed,
                        eta.update(fraction, elapsed), correcting));
            };
            FfmpegRunner.run(pass.args(), work, lowPriority, sink, token);
            before += pass.weight();
        }
    }

    /**
     * Estimates the remaining time from the progress so far and smooths it over
     * a few seconds of wall-clock time (not over a number of updates), so the
     * display stays calm however often ffmpeg reports.
     */
    static final class EtaSmoother {
        private static final double TIME_CONSTANT_SECONDS = 3.0;
        private double smoothed = -1;
        private double lastElapsed;

        double update(double fraction, double elapsed) {
            if (fraction < 0.01 || elapsed < 1.0) {
                return -1;
            }
            double raw = elapsed * (1.0 - fraction) / fraction;
            if (smoothed < 0) {
                smoothed = raw;
            } else {
                double dt = Math.max(0, elapsed - lastElapsed);
                double alpha = 1.0 - Math.exp(-dt / TIME_CONSTANT_SECONDS);
                // Count down between updates, then blend in the new estimate.
                smoothed = Math.max(0, smoothed - dt);
                smoothed += alpha * (raw - smoothed);
            }
            lastElapsed = elapsed;
            return smoothed;
        }
    }

    // ------------------------------------------------------------------ frames

    /**
     * A still frame scaled to fit {@code maxWidth x maxHeight}, or {@code null}
     * if there is no frame at that time.
     *
     * @param accurate decode exactly to {@code time} (slower) instead of the nearest keyframe
     */
    public BufferedImage frame(Path file, VideoStream video, double time, int maxWidth, int maxHeight,
                               boolean accurate) throws EngineException {
        List<String> cmd = new ArrayList<>(Arrays.asList(tools.ffmpeg().toString(), "-v", "error", "-nostdin"));
        if (!accurate) {
            cmd.add("-noaccurate_seek");
        }
        cmd.addAll(Arrays.asList("-ss", CommandBuilder.seconds(Math.max(0, time)), "-i", file.toString(),
                "-frames:v", "1", "-an", "-sn", "-dn", "-vf", frameFilters(video, maxWidth, maxHeight) + ",format=bgr24",
                "-f", "image2pipe", "-c:v", "bmp", "-"));
        byte[] bmp = FfmpegRunner.capture(cmd, 30);
        if (bmp.length == 0) {
            return null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(bmp));
        } catch (IOException e) {
            throw new EngineException("Could not decode frame", e.getMessage(), e);
        }
    }

    /** Filters that make frames look right for display: HDR tone mapping and scaling. */
    String frameFilters(VideoStream video, int maxWidth, int maxHeight) {
        StringBuilder vf = new StringBuilder();
        if (video != null && video.isHdr() && tools.canToneMap()) {
            vf.append(CommandBuilder.toneMapFilter(video)).append(',');
        }
        vf.append("scale=w=").append(Math.max(2, maxWidth)).append(":h=").append(Math.max(2, maxHeight))
                .append(":force_original_aspect_ratio=decrease:flags=bicubic");
        return vf.toString();
    }

    /** Starts streaming decoded frames, used by the preview player. */
    public FramePipe framePipe(Path file, VideoStream video, double start, double duration, int width, int height,
                               double fps) throws EngineException {
        List<String> cmd = new ArrayList<>(Arrays.asList(tools.ffmpeg().toString(), "-v", "error", "-nostdin"));
        if (start > 0) {
            cmd.addAll(Arrays.asList("-ss", CommandBuilder.seconds(start)));
        }
        cmd.addAll(Arrays.asList("-i", file.toString()));
        if (duration > 0) {
            cmd.addAll(Arrays.asList("-t", CommandBuilder.seconds(duration)));
        }
        StringBuilder vf = new StringBuilder();
        if (video != null && video.isHdr() && tools.canToneMap()) {
            vf.append(CommandBuilder.toneMapFilter(video)).append(',');
        }
        vf.append("fps=").append(CommandBuilder.frameRateExpression(fps))
                .append(",scale=").append(width).append(':').append(height).append(":flags=bicubic");
        cmd.addAll(Arrays.asList("-an", "-sn", "-dn", "-vf", vf.toString(), "-pix_fmt", "bgr24", "-f", "rawvideo",
                "pipe:1"));
        return new FramePipe(cmd, width, height);
    }

    /** Starts streaming 16-bit stereo PCM at 48 kHz, used for playback. */
    public AudioPipe audioPipe(Path file, int audioStreamIndex, double start, double duration)
            throws EngineException {
        List<String> cmd = new ArrayList<>(Arrays.asList(tools.ffmpeg().toString(), "-v", "error", "-nostdin"));
        if (start > 0) {
            cmd.addAll(Arrays.asList("-ss", CommandBuilder.seconds(start)));
        }
        cmd.addAll(Arrays.asList("-i", file.toString()));
        if (duration > 0) {
            cmd.addAll(Arrays.asList("-t", CommandBuilder.seconds(duration)));
        }
        cmd.addAll(Arrays.asList("-map", "0:" + audioStreamIndex, "-vn", "-sn", "-dn", "-ac", "2", "-ar", "48000",
                "-f", "s16le", "pipe:1"));
        return new AudioPipe(cmd);
    }

    // ------------------------------------------------------------------ files

    static void deleteQuietly(Path p) {
        if (p == null) {
            return;
        }
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // best effort
        }
    }

    static void deleteTree(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(Engine::deleteQuietly);
        } catch (IOException ignored) {
            // best effort
        }
    }
}
