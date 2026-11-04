package io.github.m1omg.shrink.core.store;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    void parsesFfprobeStyleOutput() throws Exception {
        String text = "{\n  \"streams\": [ { \"index\": 0, \"codec_name\": \"h264\", \"avg_frame_rate\": \"30000/1001\","
                + " \"bit_rate\": \"1302246\", \"side_data_list\": [ {\"rotation\": -90} ] } ],\n"
                + "  \"format\": { \"duration\": \"23.000000\", \"tags\": { \"title\": \"Dovolenka \\u017dilina\" } } }";
        Map<String, Object> root = Json.parseObject(text);
        Map<String, Object> stream = Json.asObject(Json.getList(root, "streams").get(0));
        assertEquals("h264", Json.getString(stream, "codec_name", ""));
        assertEquals(1302246L, Json.getLong(stream, "bit_rate", 0));
        assertEquals(-90L, Json.getLong(Json.asObject(Json.getList(stream, "side_data_list").get(0)), "rotation", 0));
        Map<String, Object> format = Json.getObject(root, "format");
        assertEquals(23.0, Json.getDouble(format, "duration", 0), 1e-9);
        assertEquals("Dovolenka Žilina", Json.getString(Json.getObject(format, "tags"), "title", ""));
    }

    @Test
    void roundTripsValues() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("text", "line \"one\"\nline two \\ ľščťžýáíé");
        m.put("int", 42L);
        m.put("neg", -7L);
        m.put("double", 2.5);
        m.put("flag", true);
        m.put("nothing", null);
        m.put("list", Arrays.asList(1L, "two", false));
        String written = Json.write(m, true);
        Map<String, Object> back = Json.parseObject(written);
        assertEquals(m.get("text"), back.get("text"));
        assertEquals(42L, back.get("int"));
        assertEquals(-7L, back.get("neg"));
        assertEquals(2.5, back.get("double"));
        assertEquals(Boolean.TRUE, back.get("flag"));
        assertNull(back.get("nothing"));
        assertTrue(back.containsKey("nothing"));
        List<Object> list = Json.asList(back.get("list"));
        assertEquals(Arrays.asList(1L, "two", false), list);
    }

    @Test
    void numbersAndErrors() throws Exception {
        assertEquals(1.5e-3, (Double) Json.parse("1.5e-3"), 1e-12);
        assertEquals(9_000_000_000L, Json.parse("9000000000"));
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\": }"));
        assertThrows(Json.JsonException.class, () -> Json.parse("[1, 2"));
        assertThrows(Json.JsonException.class, () -> Json.parse("{} trailing"));
    }
}
