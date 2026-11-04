package io.github.m1omg.shrink.desktop.ui.tools;

import io.github.m1omg.shrink.core.model.Job;
import io.github.m1omg.shrink.desktop.engine.Engine;

import java.awt.Window;
import java.awt.image.BufferedImage;

/** What tool panels may ask of the main window. */
public interface ToolHost {

    /** The FFmpeg engine, or {@code null} if it is not available. */
    Engine engine();

    /** A frame from the middle of the video, or {@code null} while loading. */
    BufferedImage thumbnail();

    /** Opens the 5-second quality preview for a compression job. */
    void showPreview(Job job);

    /** Runs the tool's main action (like pressing the main button). */
    void runCurrentTool();

    Window window();
}
