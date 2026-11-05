package io.github.m1omg.shrink.android;

import android.net.Uri;
import android.view.View;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;

import io.github.m1omg.shrink.android.ui.Tool;
import io.github.m1omg.shrink.core.i18n.Language;
import io.github.m1omg.shrink.core.store.Settings;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Goes through the app like a person would: picks a video, compresses it with
 * the main button, then visits the other screens. Takes screenshots on the way.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class AppFlowTest {

    @Rule
    public final GrantPermissionRule notifications = TestSupport.notificationPermission();

    @Rule
    public final ActivityScenarioRule<MainActivity> activity = new ActivityScenarioRule<>(MainActivity.class);

    @After
    public void restoreSettings() {
        TestSupport.useLanguageAndTheme(Language.SYSTEM, Settings.Theme.SYSTEM);
    }

    @Test
    public void compressAVideoAndVisitEveryScreen() throws Exception {
        File video = TestSupport.copyAsset("sample.mp4", "Dovolenka v Tatrách.mp4");
        TestSupport.useLanguageAndTheme(Language.ENGLISH, Settings.Theme.LIGHT);
        TestSupport.settle(2500);
        activity.getScenario().onActivity(MainActivity::showHome);
        TestSupport.settle(1000);
        TestSupport.screenshot("01-home-empty");

        activity.getScenario().onActivity(a -> a.loadVideo(Uri.fromFile(video)));
        TestSupport.waitFor("the video to load", 30_000, () -> {
            AppState s = ShrinkApp.get().state();
            return s.hasSource() && s.thumbnail() != null;
        });
        assertNotNull(TestSupport.onMain(() -> ShrinkApp.get().state().info()));
        TestSupport.settle(1000);
        TestSupport.screenshot("02-home-video");

        activity.getScenario().onActivity(a -> a.openTool(Tool.COMPRESS));
        TestSupport.settle(2000);
        TestSupport.screenshot("03-compress");

        // Press the main button, as a person would.
        activity.getScenario().onActivity(a -> {
            View button = a.findViewById(R.id.action_button);
            assertNotNull("main button", button);
            assertTrue("the Compress button is enabled", button.isEnabled());
            button.performClick();
        });
        TestSupport.waitFor("the job to start", 15_000,
                () -> ShrinkApp.get().jobs().state() != JobController.State.IDLE);
        TestSupport.settle(600);
        TestSupport.screenshot("04-progress");
        JobController.State end = TestSupport.waitForJob();
        TestSupport.settle(1200);
        TestSupport.screenshot("05-result");
        assertEquals(TestSupport.onMain(() -> ShrinkApp.get().jobs().error()), JobController.State.DONE, end);
        long bytes = TestSupport.onMain(() -> ShrinkApp.get().jobs().resultBytes());
        assertTrue("the result is not empty", bytes > 0);
        TestSupport.assertSize("the result is smaller than the original: " + bytes, bytes < video.length());

        activity.getScenario().onActivity(a -> {
            ShrinkApp.get().jobs().dismiss();
            a.showHistory();
        });
        TestSupport.settle(1500);
        TestSupport.screenshot("06-history");

        activity.getScenario().onActivity(MainActivity::openSettings);
        TestSupport.settle(1500);
        TestSupport.screenshot("07-settings");

        int n = 8;
        for (Tool tool : new Tool[] {Tool.TRIM, Tool.ROTATE, Tool.CONVERT, Tool.EXTRACT_AUDIO, Tool.RESOLUTION,
            Tool.FRAME_RATE, Tool.MUTE, Tool.INFO}) {
            activity.getScenario().onActivity(a -> {
                a.showHome();
                a.openTool(tool);
            });
            TestSupport.settle(tool == Tool.TRIM ? 4000 : 1800);
            TestSupport.screenshot(String.format("%02d-%s", n++, tool.name().toLowerCase(java.util.Locale.ROOT)));
        }

        // The same screens in Slovak with the dark theme.
        activity.getScenario().onActivity(MainActivity::showHome);
        TestSupport.useLanguageAndTheme(Language.SLOVAK, Settings.Theme.DARK);
        TestSupport.settle(3000);
        activity.getScenario().onActivity(MainActivity::showHome);
        TestSupport.settle(1500);
        TestSupport.screenshot("20-sk-dark-home");
        activity.getScenario().onActivity(a -> a.openTool(Tool.COMPRESS));
        TestSupport.settle(2000);
        TestSupport.screenshot("21-sk-dark-compress");
        activity.getScenario().onActivity(MainActivity::showHistory);
        TestSupport.settle(1500);
        TestSupport.screenshot("22-sk-dark-history");
    }
}
