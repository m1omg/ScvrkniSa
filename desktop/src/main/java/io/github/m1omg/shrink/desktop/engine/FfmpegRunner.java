package io.github.m1omg.shrink.desktop.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs one ffmpeg command, reads its {@code -progress} output and reports
 * progress. Errors printed by ffmpeg are kept for the error message.
 */
public final class FfmpegRunner {

    /** Receives progress of a single ffmpeg run. */
    public interface ProgressSink {
        /**
         * @param outTimeSeconds how much of the output has been written, in seconds
         * @param speed          encoding speed relative to real time (2.0 = twice as fast), 0 if unknown
         */
        void progress(double outTimeSeconds, double speed);
    }

    /** Lets the user stop a running job. */
    public static final class CancelToken {
        private volatile boolean cancelled;
        private volatile Process process;

        public void cancel() {
            cancelled = true;
            Process p = process;
            if (p != null) {
                p.destroy();
            }
        }

        public boolean isCancelled() {
            return cancelled;
        }

        void attach(Process p) {
            process = p;
            if (cancelled) {
                p.destroy();
            }
        }
    }

    /** Thrown when the user cancelled. */
    public static final class CancelledException extends Exception {
        private static final long serialVersionUID = 1L;

        public CancelledException() {
            super("Cancelled");
        }
    }

    private static final Path NICE = Paths.get("/usr/bin/nice");

    private FfmpegRunner() {
    }

    /**
     * Runs ffmpeg and waits for it.
     *
     * @param lowPriority run with a lower CPU priority so the computer stays responsive
     */
    public static void run(List<String> command, Path workDir, boolean lowPriority, ProgressSink sink,
                           CancelToken token) throws EngineException, CancelledException {
        List<String> cmd = new ArrayList<>();
        if (lowPriority && Files.isExecutable(NICE)) {
            cmd.add(NICE.toString());
            cmd.add("-n");
            cmd.add("10");
        }
        cmd.addAll(command);
        if (token != null && token.isCancelled()) {
            throw new CancelledException();
        }
        Process process;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (workDir != null) {
                pb.directory(workDir.toFile());
            }
            process = pb.start();
        } catch (IOException e) {
            throw new EngineException("Could not start FFmpeg: " + e.getMessage(), "", e);
        }
        if (token != null) {
            token.attach(process);
        }
        Deque<String> errors = new ArrayDeque<>();
        Thread errorReader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    synchronized (errors) {
                        errors.addLast(line);
                        while (errors.size() > 40) {
                            errors.removeFirst();
                        }
                    }
                }
            } catch (IOException ignored) {
                // Process ended.
            }
        }, "ffmpeg-stderr");
        errorReader.setDaemon(true);
        errorReader.start();

        double outTime = 0;
        double speed = 0;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq);
                String value = line.substring(eq + 1).trim();
                switch (key) {
                    case "out_time_us":
                    case "out_time_ms": // despite the name, also microseconds
                        double t = parseDouble(value) / 1_000_000.0;
                        if (t >= 0) {
                            outTime = t;
                        }
                        break;
                    case "speed":
                        speed = parseDouble(value.endsWith("x") ? value.substring(0, value.length() - 1) : value);
                        break;
                    case "progress":
                        if (sink != null) {
                            sink.progress(outTime, Math.max(speed, 0));
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (IOException e) {
            // Stream closed: the process ended or was killed.
        }
        int exit;
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor();
            }
            exit = process.exitValue();
            errorReader.join(2000);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new CancelledException();
        }
        if (token != null && token.isCancelled()) {
            throw new CancelledException();
        }
        if (exit != 0) {
            String details;
            synchronized (errors) {
                details = String.join("\n", errors);
            }
            throw new EngineException(firstMeaningfulLine(details, exit), details, null);
        }
    }

    private static String firstMeaningfulLine(String details, int exit) {
        String[] lines = details.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String l = lines[i].trim();
            if (!l.isEmpty() && !l.startsWith("Conversion failed")) {
                return l;
            }
        }
        return "FFmpeg stopped with exit code " + exit;
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Runs a short command and returns its standard output bytes (used for frames and probing). */
    public static byte[] capture(List<String> command, long timeoutSeconds) throws EngineException {
        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new EngineException("Could not start " + command.get(0) + ": " + e.getMessage(), "", e);
        }
        Deque<String> errors = new ArrayDeque<>();
        Thread errorReader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    synchronized (errors) {
                        errors.addLast(line);
                        if (errors.size() > 20) {
                            errors.removeFirst();
                        }
                    }
                }
            } catch (IOException ignored) {
                // ended
            }
        }, "capture-stderr");
        errorReader.setDaemon(true);
        errorReader.start();
        byte[] out;
        try {
            out = process.getInputStream().readAllBytes();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new EngineException("Timed out");
            }
            errorReader.join(1000);
        } catch (IOException e) {
            process.destroyForcibly();
            throw new EngineException("Reading output failed: " + e.getMessage(), "", e);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new EngineException("Interrupted");
        }
        if (process.exitValue() != 0) {
            String details;
            synchronized (errors) {
                details = String.join("\n", errors);
            }
            throw new EngineException(firstMeaningfulLine(details, process.exitValue()), details, null);
        }
        return out;
    }
}
