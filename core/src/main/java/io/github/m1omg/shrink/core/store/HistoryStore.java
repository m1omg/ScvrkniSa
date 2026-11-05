package io.github.m1omg.shrink.core.store;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The list of processed files, newest first, stored as {@code history.json}.
 * Thread-safe; listeners are told about every change.
 */
public final class HistoryStore {

    /** Oldest entries are dropped beyond this count. */
    public static final int MAX_ENTRIES = 300;

    /** Notified after the history changed. */
    public interface Listener {
        void historyChanged();
    }

    private final Path file;
    private final List<HistoryEntry> entries = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public HistoryStore(Path file) {
        this.file = file;
        load();
    }

    private synchronized void load() {
        entries.clear();
        try {
            String text = AtomicFiles.readString(file);
            if (text == null) {
                return;
            }
            Map<String, Object> root = Json.parseObject(text);
            for (Object item : Json.getList(root, "entries")) {
                entries.add(HistoryEntry.fromMap(Json.asObject(item)));
            }
        } catch (IOException | Json.JsonException e) {
            // A damaged history file is not worth failing for; start empty.
            entries.clear();
        }
    }

    /** All entries, newest first. */
    public synchronized List<HistoryEntry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public synchronized boolean isEmpty() {
        return entries.isEmpty();
    }

    public void add(HistoryEntry entry) {
        synchronized (this) {
            entries.add(0, entry);
            while (entries.size() > MAX_ENTRIES) {
                entries.remove(entries.size() - 1);
            }
            saveQuietly();
        }
        fire();
    }

    public void remove(String id) {
        synchronized (this) {
            entries.removeIf(e -> e.id().equals(id));
            saveQuietly();
        }
        fire();
    }

    public void clear() {
        synchronized (this) {
            entries.clear();
            saveQuietly();
        }
        fire();
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void fire() {
        for (Listener l : listeners) {
            l.historyChanged();
        }
    }

    private void saveQuietly() {
        try {
            List<Object> list = new ArrayList<>();
            for (HistoryEntry e : entries) {
                list.add(e.toMap());
            }
            Map<String, Object> root = new java.util.LinkedHashMap<>();
            root.put("version", 1L);
            root.put("entries", list);
            AtomicFiles.writeString(file, Json.write(root, true) + "\n");
        } catch (IOException e) {
            // History is a convenience; failing to save it must not break processing.
        }
    }
}
