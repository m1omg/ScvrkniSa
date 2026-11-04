package io.github.m1omg.shrink.desktop;

import io.github.m1omg.shrink.core.plan.JobPlanner;
import io.github.m1omg.shrink.core.store.HistoryStore;
import io.github.m1omg.shrink.core.store.Settings;
import io.github.m1omg.shrink.desktop.engine.Engine;
import io.github.m1omg.shrink.desktop.engine.EngineException;
import io.github.m1omg.shrink.desktop.engine.FfmpegLocator;
import io.github.m1omg.shrink.desktop.engine.FfmpegTools;
import io.github.m1omg.shrink.desktop.util.Platform;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Application-wide state shared by all windows: settings, history, the FFmpeg
 * engine and a pool of background threads.
 */
public final class AppContext {

    private final Path settingsFile;
    private final Settings settings;
    private final HistoryStore history;
    private final ExecutorService background;
    private final List<Runnable> engineListeners = new CopyOnWriteArrayList<>();
    private volatile Engine engine;
    private volatile EngineException engineError;
    private volatile boolean engineLoading;

    public AppContext() {
        this.settingsFile = Platform.configDir().resolve("settings.json");
        this.settings = Settings.load(settingsFile);
        this.history = new HistoryStore(Platform.dataDir().resolve("history.json"));
        AtomicInteger counter = new AtomicInteger();
        this.background = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "shrink-worker-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    public Settings settings() {
        return settings;
    }

    public HistoryStore history() {
        return history;
    }

    /** A planner using the current encoding settings. */
    public JobPlanner planner() {
        return new JobPlanner(settings.toEncodingDefaults());
    }

    public void saveSettings() {
        try {
            settings.save(settingsFile);
        } catch (IOException e) {
            System.err.println("Could not save settings: " + e.getMessage());
        }
    }

    /** Runs work on a background thread. */
    public void background(Runnable task) {
        background.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        });
    }

    /** Runs {@code ui} on the Swing event thread. */
    public static void ui(Runnable ui) {
        if (SwingUtilities.isEventDispatchThread()) {
            ui.run();
        } else {
            SwingUtilities.invokeLater(ui);
        }
    }

    // ------------------------------------------------------------------ engine

    /** The engine, or {@code null} while it is loading or when FFmpeg is missing. */
    public Engine engine() {
        return engine;
    }

    public EngineException engineError() {
        return engineError;
    }

    public boolean isEngineLoading() {
        return engineLoading;
    }

    /** Called (on the event thread) whenever the engine was (re)loaded. */
    public void onEngineChanged(Runnable listener) {
        engineListeners.add(listener);
    }

    public void removeEngineListener(Runnable listener) {
        engineListeners.remove(listener);
    }

    /** Finds FFmpeg in the background (unpacking the built-in copy the first time). */
    public void reloadEngine() {
        engineLoading = true;
        background(() -> {
            Engine found = null;
            EngineException error = null;
            try {
                FfmpegTools tools = FfmpegLocator.locate(settings.getFfmpegMode(), settings.getFfmpegPath(),
                        Platform.cacheDir());
                found = new Engine(tools);
            } catch (EngineException e) {
                error = e;
            }
            Engine result = found;
            EngineException failure = error;
            ui(() -> {
                engine = result;
                engineError = failure;
                engineLoading = false;
                for (Runnable l : engineListeners) {
                    l.run();
                }
            });
        });
    }

    /** Runs {@code action} with the engine as soon as it is available. */
    public void withEngine(Consumer<Engine> action) {
        if (engine != null) {
            action.accept(engine);
        }
    }

    public void shutdown() {
        background.shutdownNow();
    }
}
