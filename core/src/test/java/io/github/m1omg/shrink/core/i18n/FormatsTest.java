package io.github.m1omg.shrink.core.i18n;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatsTest {

    private static final String NB = Formats.NBSP;
    private final Formats en = new Formats(Locale.ENGLISH);
    private final Formats sk = new Formats(I18n.SLOVAK);

    @Test
    void sizesUseDecimalUnitsAndLocaleSeparators() {
        assertEquals("862" + NB + "KB", en.size(862_000));
        assertEquals("2.6" + NB + "MB", en.size(2_600_000));
        assertEquals("2,6" + NB + "MB", sk.size(2_600_000));
        assertEquals("153" + NB + "MB", en.size(153_400_000));
        assertEquals("1.25" + NB + "GB", en.size(1_250_000_000L));
        assertEquals("999" + NB + "B", en.size(999));
        // Half-up rounding: 2.65 MB shows as 2.7 MB, not 2.6 MB.
        assertEquals("2.7" + NB + "MB", en.size(2_650_000));
    }

    @Test
    void percentagesFollowLanguageRules() {
        assertEquals("62%", en.percent(62));
        assertEquals("62" + NB + "%", sk.percent(62));
        assertEquals(Formats.MINUS + "62%", en.reduction(61.6));
        assertEquals("+5%", en.reduction(-5));
        assertEquals("0%", en.reduction(0.2));
    }

    @Test
    void durations() {
        assertEquals("0:23", en.duration(23));
        assertEquals("1:02:03", en.duration(3723));
        assertEquals("0:23.4", en.durationPrecise(23.42));
        assertEquals("0:23,4", sk.durationPrecise(23.42));
        assertEquals("5" + NB + "s", en.durationWords(5));
        assertEquals("2" + NB + "min 5" + NB + "s", en.durationWords(125));
    }

    @Test
    void bitratesAndFrameRates() {
        assertEquals("850" + NB + "kbps", en.bitrate(850_000));
        assertEquals("1,3" + NB + "Mb/s", sk.bitrate(1_300_000));
        assertEquals("29.97" + NB + "fps", en.frameRate(29.97002997));
        assertEquals("30" + NB + "fps", en.frameRate(30));
        assertEquals("44,1" + NB + "kHz", sk.sampleRate(44_100));
    }

    @Test
    void parsesBothDecimalSeparators() {
        assertEquals(2.5, Formats.parseDecimal("2,5"), 1e-9);
        assertEquals(2.5, Formats.parseDecimal(" 2.5 "), 1e-9);
        assertEquals(-1.25, Formats.parseDecimal("-1,25"), 1e-9);
        assertTrue(Double.isNaN(Formats.parseDecimal("abc")));
        assertTrue(Double.isNaN(Formats.parseDecimal("")));
    }
}
