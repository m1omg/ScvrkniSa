package io.github.m1omg.shrink.android;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.store.HistoryStore;
import io.github.m1omg.shrink.core.store.Settings;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Application object: settings, history, the job controller and shared state. */
public final class ShrinkApp extends Application {

    public static final String CHANNEL_PROGRESS = "processing";

    private static ShrinkApp instance;

    private Settings settings;
    private HistoryStore history;
    private AppState state;
    private JobController jobs;
    private final ExecutorService background = Executors.newFixedThreadPool(3);

    public static ShrinkApp get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        settings = Settings.load(settingsFile().toPath());
        history = new HistoryStore(new File(getFilesDir(), "history.json").toPath());
        state = new AppState();
        jobs = new JobController(this);
        applyNightMode();
        updateLocale(this);
        createChannel();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateLocale(this);
    }

    private File settingsFile() {
        return new File(getFilesDir(), "settings.json");
    }

    public Settings settings() {
        return settings;
    }

    public void saveSettings() {
        try {
            settings.save(settingsFile().toPath());
        } catch (IOException e) {
            // Not fatal: settings stay in memory for this session.
        }
    }

    public HistoryStore history() {
        return history;
    }

    public AppState state() {
        return state;
    }

    public JobController jobs() {
        return jobs;
    }

    public ExecutorService background() {
        return background;
    }

    /** Applies the light/dark choice from the settings. */
    public void applyNightMode() {
        switch (settings.getTheme()) {
            case LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    /**
     * Keeps the shared (core) texts and number formats in the same language as
     * the Android resources of {@code context}.
     */
    public static void updateLocale(Context context) {
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        I18n.setLocale("sk".equals(locale.getLanguage()) ? I18n.SLOVAK : Locale.ENGLISH);
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL_PROGRESS,
                getString(R.string.android_notification_channel), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.android_notification_channel_description));
        channel.setShowBadge(false);
        nm.createNotificationChannel(channel);
    }
}
