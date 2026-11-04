package io.github.m1omg.shrink.core.plan;

/**
 * Width and height of a video frame in pixels.
 *
 * @param width  width in pixels
 * @param height height in pixels
 */
public record Dimensions(int width, int height) {

    /** The standard "p" sizes offered in the resolution list, largest first. */
    private static final int[] STANDARD_SHORT_SIDES = {2160, 1440, 1080, 720, 540, 480, 360, 240};

    public int shortSide() {
        return Math.min(width, height);
    }

    public boolean isPortrait() {
        return height > width;
    }

    /** Copy of the standard short sides (2160, 1440, 1080, 720, 540, 480, 360, 240). */
    public static int[] standardShortSides() {
        return STANDARD_SHORT_SIDES.clone();
    }

    /**
     * Scales {@code width x height} so that the shorter side becomes
     * {@code shortSide}, keeping the aspect ratio. Both results are even numbers
     * because most encoders require that.
     */
    public static Dimensions scaleToShortSide(int width, int height, int shortSide) {
        int currentShort = Math.min(width, height);
        if (currentShort <= 0) {
            return new Dimensions(even(width), even(height));
        }
        double factor = (double) shortSide / currentShort;
        return new Dimensions(even(width * factor), even(height * factor));
    }

    /** Rounds to the nearest even number, at least 2. */
    public static int even(double value) {
        long rounded = Math.round(value / 2.0) * 2L;
        return (int) Math.max(2L, rounded);
    }

    /** Label such as {@code 1280 × 720}. */
    public String label() {
        return width + " × " + height;
    }

    /** Common name of a short side, e.g. 2160 → "4K", 1080 → "1080p". */
    public static String nameOf(int shortSide) {
        switch (shortSide) {
            case 4320:
                return "8K";
            case 2160:
                return "4K";
            case 1440:
                return "1440p";
            default:
                return shortSide + "p";
        }
    }
}
