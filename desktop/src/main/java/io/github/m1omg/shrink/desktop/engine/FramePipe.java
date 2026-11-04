package io.github.m1omg.shrink.desktop.engine;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Decoded video frames streamed from an ffmpeg process as raw BGR pixels.
 * Each call to {@link #read} fills one frame.
 */
public final class FramePipe implements AutoCloseable {

    private final Process process;
    private final InputStream in;
    private final int width;
    private final int height;

    FramePipe(List<String> command, int width, int height) throws EngineException {
        this.width = width;
        this.height = height;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            process = pb.start();
        } catch (IOException e) {
            throw new EngineException("Could not start the decoder: " + e.getMessage(), "", e);
        }
        in = process.getInputStream();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** A new image of the right size and type for {@link #read}. */
    public BufferedImage createImage() {
        return new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
    }

    /**
     * Reads the next frame into {@code image} (which must come from
     * {@link #createImage()}). Returns {@code false} at the end of the video.
     */
    public boolean read(BufferedImage image) throws IOException {
        byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        int needed = width * height * 3;
        int offset = 0;
        while (offset < needed) {
            int n = in.read(pixels, offset, needed - offset);
            if (n < 0) {
                return false;
            }
            offset += n;
        }
        return true;
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
