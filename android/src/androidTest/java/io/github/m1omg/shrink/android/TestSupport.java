package io.github.m1omg.shrink.android;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.MediaStore;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import io.github.m1omg.shrink.core.i18n.Language;
import io.github.m1omg.shrink.core.model.Job;
import io.github.m1omg.shrink.core.store.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/** Helpers shared by the instrumented tests. */
final class TestSupport {

    /** Generous, because emulators encode video in software. */
    static final long JOB_TIMEOUT_MS = 240_000;

    private TestSupport() {
    }

    static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    /** Lets the app post notifications without the system asking (Android 13+). */
    static GrantPermissionRule notificationPermission() {
        return Build.VERSION.SDK_INT >= 33
                ? GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
                : GrantPermissionRule.grant();
    }

    /** Copies a video from the test APK's assets into the app's cache folder. */
    static File copyAsset(String asset, String fileName) throws IOException {
        File out = new File(context().getCacheDir(), fileName);
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(asset);
             OutputStream os = new FileOutputStream(out)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                os.write(buffer, 0, n);
            }
        }
        return out;
    }

    static void onMain(Runnable r) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(r);
    }

    static <T> T onMain(Callable<T> c) {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        onMain(() -> {
            try {
                result.set(c.call());
            } catch (Exception e) {
                error.set(e);
            }
        });
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
        return result.get();
    }

    /** Waits until {@code condition} (checked on the main thread) becomes true. */
    static void waitFor(String what, long timeoutMs, BooleanSupplier condition) throws InterruptedException {
        long end = SystemClock.uptimeMillis() + timeoutMs;
        while (!Boolean.TRUE.equals(onMain(condition::getAsBoolean))) {
            if (SystemClock.uptimeMillis() > end) {
                throw new AssertionError("Timed out waiting for " + what);
            }
            Thread.sleep(100);
        }
    }

    /** Gives the screen time to finish layout, image loading and transitions. */
    static void settle(long millis) throws InterruptedException {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Thread.sleep(millis);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    /**
     * Saves a screenshot to Pictures/ShrinkTests, where it stays after the test
     * app is uninstalled, so CI can collect it with {@code adb pull}.
     */
    static void screenshot(String name) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        if (bitmap == null) {
            return;
        }
        ContentResolver resolver = context().getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name + ".png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ShrinkTests");
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            return;
        }
        try (OutputStream out = resolver.openOutputStream(uri)) {
            if (out != null) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        } catch (IOException ignored) {
            // a missing screenshot must not fail the test
        }
    }

    /** Switches the app's language and theme the way the settings screen does. */
    static void useLanguageAndTheme(Language language, Settings.Theme theme) {
        onMain(() -> {
            ShrinkApp app = ShrinkApp.get();
            app.settings().setLanguage(language);
            app.settings().setTheme(theme);
            app.saveSettings();
            app.applyNightMode();
            AppCompatDelegate.setApplicationLocales(language == Language.SYSTEM
                    ? LocaleListCompat.getEmptyLocaleList()
                    : LocaleListCompat.forLanguageTags(language.id()));
        });
    }

    /** Waits for the running job to finish and returns how it ended. */
    static JobController.State waitForJob() throws InterruptedException {
        waitFor("the job to finish", JOB_TIMEOUT_MS, () -> {
            JobController.State s = ShrinkApp.get().jobs().state();
            return s == JobController.State.DONE || s == JobController.State.FAILED;
        });
        return onMain(() -> ShrinkApp.get().jobs().state());
    }

    /** Runs a job the way the app does (service, saving to Movies or Music) and inspects the result. */
    static Output run(Job job, Uri input, String suffix) throws Exception {
        onMain(() -> ShrinkApp.get().jobs().start(job, input, null, suffix));
        JobController.State state = waitForJob();
        JobController jobs = ShrinkApp.get().jobs();
        String error = onMain(jobs::error);
        assertEquals(job.kind() + " failed: " + error, JobController.State.DONE, state);
        Uri uri = onMain(jobs::resultUri);
        long bytes = onMain(jobs::resultBytes);
        onMain(jobs::dismiss);
        assertNotNull("result uri", uri);
        Output o = Output.inspect(uri);
        o.bytes = bytes;
        o.plan = "estimate " + job.estimatedBytes() + " B"
                + (job.video().isEncode() ? ", video asked " + job.video().bitrate() + " bit/s" : "");
        return o;
    }

    /** What Android's own media framework sees in a result file. */
    static final class Output {
        Uri uri;
        long bytes;
        double durationSeconds;
        int width;
        int height;
        int rotation;
        int frameCount = -1;
        String videoMime;
        String audioMime;
        int audioChannels;
        String plan = "";

        boolean isPortrait() {
            boolean swap = rotation == 90 || rotation == 270;
            return swap ? width > height : height > width;
        }

        static Output inspect(Uri uri) throws IOException {
            Output o = new Output();
            o.uri = uri;
            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.setDataSource(context(), uri, null);
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    MediaFormat f = extractor.getTrackFormat(i);
                    String mime = f.getString(MediaFormat.KEY_MIME);
                    if (mime == null) {
                        continue;
                    }
                    if (mime.startsWith("video/") && o.videoMime == null) {
                        o.videoMime = mime;
                    } else if (mime.startsWith("audio/") && o.audioMime == null) {
                        o.audioMime = mime;
                        o.audioChannels = f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                                ? f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 0;
                    }
                }
            } finally {
                extractor.release();
            }
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            try {
                mmr.setDataSource(context(), uri);
                o.durationSeconds = parse(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) / 1000.0;
                if (o.videoMime != null) {
                    o.width = (int) parse(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                    o.height = (int) parse(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                    o.rotation = (int) parse(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
                    String frames = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);
                    o.frameCount = frames == null ? -1 : (int) parse(frames);
                }
            } finally {
                mmr.release();
            }
            return o;
        }

        private static long parse(String s) {
            try {
                return s == null ? 0 : Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        @Override
        public String toString() {
            return "Output{" + bytes + " B, " + durationSeconds + " s, " + width + "x" + height + " rot " + rotation
                    + ", frames " + frameCount + ", video " + videoMime + ", audio " + audioMime + " x"
                    + audioChannels + ", " + plan + "}";
        }
    }

    /** Deletes a leftover file, e.g. from an earlier run on the same device. */
    static void delete(File f) {
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
