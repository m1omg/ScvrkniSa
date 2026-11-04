package io.github.m1omg.shrink.core.model;

/**
 * A part of a video, in seconds.
 *
 * @param start start time in seconds (inclusive)
 * @param end   end time in seconds (exclusive), always greater than {@code start}
 */
public record TimeRange(double start, double end) {

    public TimeRange {
        if (Double.isNaN(start) || Double.isNaN(end) || start < 0 || end <= start) {
            throw new IllegalArgumentException("Invalid time range " + start + " - " + end);
        }
    }

    public double duration() {
        return end - start;
    }

    /** Whether this range covers (almost) the whole of a video of the given length. */
    public boolean coversWhole(double totalDuration) {
        return start < 0.05 && end > totalDuration - 0.05;
    }
}
