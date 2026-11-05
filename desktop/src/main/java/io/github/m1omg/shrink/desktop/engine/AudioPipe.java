package io.github.m1omg.shrink.desktop.engine;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Decoded audio (16-bit little-endian stereo PCM at 48 kHz) streamed from ffmpeg. */
public final class AudioPipe implements AutoCloseable {

    public static final int SAMPLE_RATE = 48_000;
    public static final int CHANNELS = 2;
    public static final int BYTES_PER_FRAME = 4;

    private final Process process;
    private final InputStream in;

    AudioPipe(List<String> command) throws EngineException {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            process = pb.start();
        } catch (IOException e) {
            throw new EngineException("Could not start the audio decoder: " + e.getMessage(), "", e);
        }
        in = process.getInputStream();
    }

    /** Reads up to {@code length} bytes; returns -1 at the end. */
    public int read(byte[] buffer, int offset, int length) throws IOException {
        return in.read(buffer, offset, length);
    }

    @Override
    public void close() {
        process.destroy();
        try {
            in.close();
        } catch (IOException ignored) {
            // closing anyway
        }
    }
}
