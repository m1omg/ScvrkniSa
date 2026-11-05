package io.github.m1omg.shrink.desktop.engine;

/** Something went wrong while running ffmpeg or ffprobe. */
public class EngineException extends Exception {
    private static final long serialVersionUID = 1L;

    private final String details;

    public EngineException(String message) {
        this(message, "", null);
    }

    public EngineException(String message, String details, Throwable cause) {
        super(message, cause);
        this.details = details == null ? "" : details;
    }

    /** The last lines ffmpeg printed, useful for bug reports. */
    public String details() {
        return details;
    }
}
