package io.github.m1omg.shrink.core.store;

import io.github.m1omg.shrink.core.i18n.Language;
import io.github.m1omg.shrink.core.model.EncoderSpeed;
import io.github.m1omg.shrink.core.model.JobKind;
import io.github.m1omg.shrink.core.model.VideoCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreTest {

    @TempDir
    Path dir;

    @Test
    void settingsRoundTrip() throws Exception {
        Settings s = new Settings();
        s.setLanguage(Language.SLOVAK);
        s.setVideoCodec(VideoCodec.H265);
        s.setSpeed(EncoderSpeed.SLOW);
        s.setTwoPass(false);
        s.setCustomOutputFolder("/home/user/Videá");
        s.setOutputLocation(Settings.OutputLocation.CUSTOM_FOLDER);
        Path file = dir.resolve("conf/settings.json");
        s.save(file);
        Settings loaded = Settings.load(file);
        assertEquals(Language.SLOVAK, loaded.getLanguage());
        assertEquals(VideoCodec.H265, loaded.getVideoCodec());
        assertEquals(EncoderSpeed.SLOW, loaded.getSpeed());
        assertFalse(loaded.isTwoPass());
        assertEquals("/home/user/Videá", loaded.getCustomOutputFolder());
        assertEquals(Settings.OutputLocation.CUSTOM_FOLDER, loaded.getOutputLocation());
    }

    @Test
    void brokenSettingsGiveDefaults() throws Exception {
        Path file = dir.resolve("settings.json");
        Files.write(file, "{ not json".getBytes());
        Settings loaded = Settings.load(file);
        assertEquals(Language.SYSTEM, loaded.getLanguage());
        assertTrue(loaded.isTwoPass());
    }

    @Test
    void historyKeepsNewestFirstAndPersists() {
        Path file = dir.resolve("history.json");
        HistoryStore store = new HistoryStore(file);
        AtomicInteger changes = new AtomicInteger();
        store.addListener(changes::incrementAndGet);
        store.add(entry("a", 1000));
        store.add(entry("b", 2000));
        assertEquals("b", store.entries().get(0).id());
        assertEquals(2, changes.get());

        HistoryStore reloaded = new HistoryStore(file);
        assertEquals(2, reloaded.entries().size());
        assertEquals(62.0, reloaded.entries().get(0).reductionPercent(), 0.01);
        reloaded.remove("a");
        assertEquals(1, new HistoryStore(file).entries().size());
        reloaded.clear();
        assertTrue(new HistoryStore(file).isEmpty());
    }

    private static HistoryEntry entry(String id, long time) {
        return new HistoryEntry(id, time, JobKind.COMPRESS, HistoryEntry.Status.DONE, "in.mp4", "/tmp/in.mp4",
                3_900_000, "in-compressed.mp4", "/tmp/in-compressed.mp4", 1_482_000, 23.0, "H.264", "");
    }
}
