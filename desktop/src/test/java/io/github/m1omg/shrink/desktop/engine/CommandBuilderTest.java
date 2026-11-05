package io.github.m1omg.shrink.desktop.engine;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandBuilderTest {

    @Test
    void displayRotationIsCounterClockwise() {
        assertEquals(0, CommandBuilder.displayRotation(0));
        assertEquals(-90, CommandBuilder.displayRotation(90));
        assertEquals(180, CommandBuilder.displayRotation(180));
        assertEquals(90, CommandBuilder.displayRotation(270));
    }

    @Test
    void frameRatesUseExactFractions() {
        assertEquals("30000/1001", CommandBuilder.frameRateExpression(29.97));
        assertEquals("24000/1001", CommandBuilder.frameRateExpression(23.976));
        assertEquals("30", CommandBuilder.frameRateExpression(30));
        assertEquals("12.500", CommandBuilder.frameRateExpression(12.5));
    }

    @Test
    void secondsNeverUseLocaleCommas() {
        java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("sk-SK"));
            assertEquals("12.500", CommandBuilder.seconds(12.5));
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }

    @Test
    void rotationFilters() {
        assertEquals("transpose=clock", CommandBuilder.rotationFilter(90));
        assertEquals("transpose=cclock", CommandBuilder.rotationFilter(270));
        assertEquals("hflip,vflip", CommandBuilder.rotationFilter(180));
    }

    @Test
    void probeRotationAndFractions() {
        Map<String, Object> stream = new HashMap<>();
        Map<String, Object> side = new HashMap<>();
        side.put("rotation", -90L);
        stream.put("side_data_list", Arrays.<Object>asList(side));
        assertEquals(90, ProbeParser.rotation(stream, new HashMap<>()));
        side.put("rotation", 90L);
        assertEquals(270, ProbeParser.rotation(stream, new HashMap<>()));
        Map<String, Object> tags = new HashMap<>();
        tags.put("rotate", "180");
        assertEquals(180, ProbeParser.rotation(new HashMap<>(), tags));
        assertEquals(29.97, ProbeParser.fraction("30000/1001"), 0.001);
        assertEquals(0, ProbeParser.fraction("0/0"), 0);
    }

    @Test
    void parsesEncoderAndFilterLists() {
        List<String> encoders = Arrays.asList("Encoders:", " V..... = Video", " ------",
                " V....D libx264              libx264 H.264 / AVC", " A....D aac                  AAC");
        assertEquals(2, FfmpegLocator.parseCodecList(encoders).size());
        List<String> filters = Arrays.asList("Filters:", "  T.. = Timeline support", "  A = Audio input/output",
                " ..C zscale            V->V       Apply resizing", " T.C hflip             V->V       Flip");
        assertEquals(2, FfmpegLocator.parseFilterList(filters).size());
    }
}
