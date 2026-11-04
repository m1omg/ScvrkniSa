package io.github.m1omg.shrink.core.i18n;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * Formats sizes, durations, bitrates and percentages the way each language
 * expects: English writes {@code 2.6 MB} and {@code 62%}, Slovak writes
 * {@code 2,6 MB} and {@code 62 %}.
 *
 * <p>Sizes use decimal (SI) units like macOS, GNOME and Android: 1 MB is
 * 1,000,000 bytes.
 */
public final class Formats {

    /** Non-breaking space, keeps "2.6" and "MB" on one line. */
    public static final String NBSP = " ";
    /** The typographic minus sign. */
    public static final String MINUS = "−";

    private final Locale locale;
    private final boolean slovak;
    private final DecimalFormatSymbols symbols;

    public Formats(Locale locale) {
        this.locale = locale;
        this.slovak = "sk".equals(locale.getLanguage());
        this.symbols = DecimalFormatSymbols.getInstance(locale);
    }

    public Locale locale() {
        return locale;
    }

    /**
     * A number with exactly {@code decimals} decimal places (half-up rounding).
     *
     * <p>The value is converted to {@link BigDecimal} first: binary doubles cannot
     * store most decimal fractions exactly (2.65 is really 2.6499999...), and
     * rounding that directly would give 2.6 instead of the expected 2.7.
     */
    public String number(double value, int decimals) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "\u2013";
        }
        return number(BigDecimal.valueOf(value), decimals);
    }

    /** Exact variant of {@link #number(double, int)}. */
    public String number(BigDecimal value, int decimals) {
        StringBuilder pattern = new StringBuilder("#,##0");
        if (decimals > 0) {
            pattern.append('.');
            for (int i = 0; i < decimals; i++) {
                pattern.append('0');
            }
        }
        DecimalFormat f = new DecimalFormat(pattern.toString(), symbols);
        f.setRoundingMode(RoundingMode.HALF_UP);
        return f.format(value);
    }

    /** A number with up to {@code maxDecimals} decimal places, trailing zeros removed. */
    public String compactNumber(double value, int maxDecimals) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "\u2013";
        }
        NumberFormat f = NumberFormat.getNumberInstance(locale);
        f.setMaximumFractionDigits(maxDecimals);
        f.setMinimumFractionDigits(0);
        f.setRoundingMode(RoundingMode.HALF_UP);
        return f.format(BigDecimal.valueOf(value));
    }

    /** File size, e.g. {@code 862 KB}, {@code 2.6 MB}, {@code 153 MB}, {@code 1.25 GB}. */
    public String size(long bytes) {
        long abs = Math.abs(bytes);
        if (abs < 1_000) {
            return bytes + NBSP + "B";
        }
        // movePointLeft is exact, so 2,650,000 bytes is exactly 2.65 MB before rounding.
        BigDecimal exact = BigDecimal.valueOf(bytes);
        if (abs < 999_500) {
            return number(exact.movePointLeft(3), 0) + NBSP + "KB";
        }
        if (abs < 999_950_000) {
            BigDecimal mb = exact.movePointLeft(6);
            return number(mb, abs < 99_950_000 ? 1 : 0) + NBSP + "MB";
        }
        BigDecimal gb = exact.movePointLeft(9);
        return number(gb, abs < 99_995_000_000L ? 2 : 1) + NBSP + "GB";
    }

    /** Megabytes as a plain number for input fields, e.g. {@code 10} or {@code 2.5}. */
    public String megabytes(long bytes) {
        return compactNumber(bytes / 1_000_000.0, 1);
    }

    /** A percentage, {@code 62%} in English and {@code 62 %} in Slovak. */
    public String percent(double value) {
        String n = number(value, 0);
        return slovak ? n + NBSP + "%" : n + "%";
    }

    /**
     * How much smaller (or larger) a result is: {@code −62%} for a 62&nbsp;%
     * reduction, {@code +5%} when the result grew.
     */
    public String reduction(double reductionPercent) {
        long rounded = Math.round(reductionPercent);
        if (rounded == 0) {
            return percent(0);
        }
        return (rounded > 0 ? MINUS : "+") + percent(Math.abs(rounded));
    }

    /** Duration as {@code 0:23}, {@code 12:05} or {@code 1:02:03}. */
    public String duration(double seconds) {
        long total = Math.max(0, Math.round(seconds));
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    /** Duration with tenths of a second, e.g. {@code 0:23.4} (Slovak: {@code 0:23,4}). */
    public String durationPrecise(double seconds) {
        long tenths = Math.max(0, Math.round(seconds * 10));
        long total = tenths / 10;
        long tenth = tenths % 10;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        char sep = symbols.getDecimalSeparator();
        if (h > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d%c%d", h, m, s, sep, tenth);
        }
        return String.format(Locale.ROOT, "%d:%02d%c%d", m, s, sep, tenth);
    }

    /** A short human duration such as {@code 5 s}, {@code 2 min 5 s} or {@code 1 h 3 min}. */
    public String durationWords(double seconds) {
        long total = Math.max(0, Math.round(seconds));
        if (total < 60) {
            return total + NBSP + "s";
        }
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) {
            return h + NBSP + "h" + (m > 0 ? " " + m + NBSP + "min" : "");
        }
        return m + NBSP + "min" + (s > 0 ? " " + s + NBSP + "s" : "");
    }

    /** Bitrate, e.g. {@code 850 kbps} / {@code 1.3 Mbps} (Slovak: {@code 850 kb/s}, {@code 1,3 Mb/s}). */
    public String bitrate(long bitsPerSecond) {
        if (bitsPerSecond <= 0) {
            return "–";
        }
        String kbps = slovak ? "kb/s" : "kbps";
        String mbps = slovak ? "Mb/s" : "Mbps";
        if (bitsPerSecond < 1_000_000) {
            return number(bitsPerSecond / 1_000.0, 0) + NBSP + kbps;
        }
        double m = bitsPerSecond / 1_000_000.0;
        return number(m, m < 10 ? 1 : 0) + NBSP + mbps;
    }

    /** Frame rate, e.g. {@code 30 fps} or {@code 29.97 fps}. */
    public String frameRate(double fps) {
        if (fps <= 0) {
            return "–";
        }
        return compactNumber(fps, 2) + NBSP + "fps";
    }

    /** Sample rate, e.g. {@code 48 kHz} or {@code 44.1 kHz}. */
    public String sampleRate(int hz) {
        if (hz <= 0) {
            return "–";
        }
        return compactNumber(hz / 1000.0, 1) + NBSP + "kHz";
    }

    /** Resolution, e.g. {@code 1280 × 720}. */
    public String resolution(int width, int height) {
        return width + NBSP + "×" + NBSP + height;
    }

    /** Date and time in the local time zone, e.g. {@code Nov 4, 2026, 9:41 PM}. */
    public String dateTime(long epochMillis) {
        DateTimeFormatter f = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(ZoneId.systemDefault());
        return f.format(Instant.ofEpochMilli(epochMillis));
    }

    /** Time of day only, e.g. {@code 9:41 PM} / {@code 21:41}. */
    public String time(long epochMillis) {
        DateTimeFormatter f = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(ZoneId.systemDefault());
        return f.format(Instant.ofEpochMilli(epochMillis));
    }

    /**
     * Parses a decimal number typed by the user, accepting both {@code 2.5} and
     * {@code 2,5}. Returns {@code NaN} when the text is not a number.
     */
    public static double parseDecimal(String text) {
        if (text == null) {
            return Double.NaN;
        }
        String cleaned = text.trim().replace(NBSP, "").replace(" ", "").replace(',', '.');
        if (cleaned.isEmpty()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
