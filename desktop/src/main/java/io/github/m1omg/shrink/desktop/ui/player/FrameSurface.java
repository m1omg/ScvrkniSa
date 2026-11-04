package io.github.m1omg.shrink.desktop.ui.player;

import java.awt.image.BufferedImage;

/** Something that can show decoded video frames (one per source). */
public interface FrameSurface {

    /**
     * Shows new frames. Called from the playback thread; implementations copy
     * the pixels, because the images are reused for the next frame.
     *
     * @param frames one frame per playback source
     * @param time   position of these frames in seconds from the playback start
     */
    void showFrames(BufferedImage[] frames, double time);

    /** Playback reached the end (or failed). Called from the playback thread. */
    void playbackFinished();
}
