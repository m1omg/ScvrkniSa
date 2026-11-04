package io.github.m1omg.shrink.android;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import io.github.m1omg.shrink.android.media.MediaProbe;
import io.github.m1omg.shrink.android.ui.GlassBackgroundView;
import io.github.m1omg.shrink.android.ui.HistoryFragment;
import io.github.m1omg.shrink.android.ui.HomeFragment;
import io.github.m1omg.shrink.android.ui.ProgressFragment;
import io.github.m1omg.shrink.android.ui.SettingsFragment;
import io.github.m1omg.shrink.android.ui.Tool;
import io.github.m1omg.shrink.android.ui.Ui;
import io.github.m1omg.shrink.core.model.Job;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.store.Settings;
import io.github.m1omg.shrink.core.util.OutputNames;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * The only activity of the main app. It hosts the screens (fragments), the
 * animated glass background, the top bar and the floating bottom navigation.
 */
public final class MainActivity extends AppCompatActivity {

    private static volatile boolean foreground;

    private final Map<String, ActivityResultLauncher<String>> createDocument = new HashMap<>();
    private ActivityResultLauncher<PickVisualMediaRequest> pickMedia;
    private ActivityResultLauncher<String[]> openDocument;
    private ActivityResultLauncher<String> notificationPermission;
    private Job pendingJob;
    private String pendingSuffix;
    private View topBar;
    private View bottomNav;
    private View navTools;
    private View navHistory;
    private ViewGroup container;
    private int bottomInset;

    /** Whether a Shrink! screen is visible (then no "done" notification is needed). */
    public static boolean isInForeground() {
        return foreground;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        ShrinkApp.updateLocale(this);
        setContentView(R.layout.activity_main);

        pickMedia = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                loadVideo(uri);
            }
        });
        openDocument = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {
                    // a temporary grant is enough for this session
                }
                loadVideo(uri);
            }
        });
        notificationPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                granted -> { });
        for (String mime : new String[] {"video/mp4", "video/quicktime", "video/x-matroska", "video/webm",
            "audio/mp4", "audio/mpeg"}) {
            createDocument.put(mime, registerForActivityResult(new ActivityResultContracts.CreateDocument(mime),
                    uri -> {
                        if (uri != null && pendingJob != null) {
                            begin(pendingJob, uri, pendingSuffix);
                        }
                        pendingJob = null;
                    }));
        }

        topBar = findViewById(R.id.top_bar);
        bottomNav = findViewById(R.id.bottom_nav);
        navTools = findViewById(R.id.nav_tools);
        navHistory = findViewById(R.id.nav_history);
        container = findViewById(R.id.container);
        GlassBackgroundView background = findViewById(R.id.background);
        background.setAnimated(ShrinkApp.get().settings().isAnimatedBackground());

        View safeArea = findViewById(R.id.safe_area);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            safeArea.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            bottomInset = bars.bottom;
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) bottomNav.getLayoutParams();
            lp.bottomMargin = bars.bottom + Ui.dp(this, 14);
            bottomNav.setLayoutParams(lp);
            return insets;
        });

        findViewById(R.id.settings_button).setOnClickListener(v -> openSettings());
        navTools.setOnClickListener(v -> showHome());
        navHistory.setOnClickListener(v -> showHistory());
        getSupportFragmentManager().addOnBackStackChangedListener(this::updateChrome);

        if (savedInstanceState == null) {
            showHome();
            handleIntent(getIntent());
        } else {
            container.post(this::updateChrome);
        }
        if (ShrinkApp.get().jobs().isBusy() && savedInstanceState == null) {
            openProgress();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        foreground = true;
    }

    @Override
    protected void onPause() {
        foreground = false;
        super.onPause();
    }

    /** Opens videos shared from other apps ("Share → Shrink!") or opened with Shrink!. */
    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        Uri uri = null;
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Parcelable extra = Build.VERSION.SDK_INT >= 33
                    ? intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class)
                    : legacyStream(intent);
            if (extra instanceof Uri) {
                uri = (Uri) extra;
            }
        } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            uri = intent.getData();
        }
        if (uri != null) {
            loadVideo(uri);
            showHome();
        }
    }

    @SuppressWarnings("deprecation")
    private static Parcelable legacyStream(Intent intent) {
        return intent.getParcelableExtra(Intent.EXTRA_STREAM);
    }

    // ------------------------------------------------------------------ choosing videos

    /** Opens the system photo picker showing only videos. */
    public void pickVideo() {
        pickMedia.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.VideoOnly.INSTANCE)
                .build());
    }

    /** Opens the file browser, for videos that are not in the gallery (e.g. Downloads). */
    public void browseFiles() {
        openDocument.launch(new String[] {"video/*", "audio/*"});
    }

    /** Reads the video's information and a thumbnail in the background. */
    public void loadVideo(Uri uri) {
        ShrinkApp app = ShrinkApp.get();
        AppState state = app.state();
        state.setLoading(uri);
        app.background().execute(() -> {
            try {
                MediaInfo info = MediaProbe.probe(app, uri);
                runOnUiThread(() -> state.setSource(uri, info));
                long timeUs = (long) (Math.min(info.durationSeconds() * 0.25, 10) * 1_000_000);
                Bitmap thumb = info.hasVideo() ? MediaProbe.thumbnail(app, uri, timeUs, 960, 960) : null;
                runOnUiThread(() -> {
                    if (uri.equals(state.uri())) {
                        state.setThumbnail(thumb);
                    }
                });
            } catch (IOException e) {
                runOnUiThread(() -> state.setError(getString(R.string.file_error_detail,
                        MediaProbe.displayName(app, uri))));
            }
        });
    }

    // ------------------------------------------------------------------ navigation

    public void showHome() {
        FragmentManager fm = getSupportFragmentManager();
        fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        fm.beginTransaction().setReorderingAllowed(true).replace(R.id.container, new HomeFragment()).commit();
        container.post(this::updateChrome);
    }

    public void showHistory() {
        FragmentManager fm = getSupportFragmentManager();
        fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        fm.beginTransaction().setReorderingAllowed(true).replace(R.id.container, new HistoryFragment()).commit();
        container.post(this::updateChrome);
    }

    public void openTool(Tool tool) {
        push(tool.create());
    }

    public void openSettings() {
        push(new SettingsFragment());
    }

    public void openProgress() {
        push(new ProgressFragment());
    }

    private void push(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(R.anim.slide_in, R.anim.fade_out, R.anim.fade_in, R.anim.slide_out)
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit();
    }

    /** The top bar and the bottom navigation are shown only on the two main tabs. */
    private void updateChrome() {
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.container);
        boolean tab = current instanceof HomeFragment || current instanceof HistoryFragment;
        topBar.setVisibility(tab ? View.VISIBLE : View.GONE);
        bottomNav.setVisibility(tab ? View.VISIBLE : View.GONE);
        navTools.setSelected(current instanceof HomeFragment);
        navHistory.setSelected(current instanceof HistoryFragment);
    }

    /** Space the floating navigation needs at the bottom of scrolling tab content. */
    public int bottomNavSpace() {
        return Ui.dp(this, 96);
    }

    // ------------------------------------------------------------------ jobs

    /** Starts a job, asking where to save first if the settings say so. */
    public void startJob(Job job, String suffix) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
        Settings settings = ShrinkApp.get().settings();
        if (settings.getOutputLocation() == Settings.OutputLocation.ASK) {
            String mime = job.isAudioOnly() ? job.audioFormat().mimeType() : job.container().mimeType();
            ActivityResultLauncher<String> launcher = createDocument.get(mime);
            if (launcher != null) {
                pendingJob = job;
                pendingSuffix = suffix;
                launcher.launch(OutputNames.suggest(job.source().fileName(), suffix, job.outputExtension(), null));
                return;
            }
        }
        begin(job, null, suffix);
    }

    private void begin(Job job, Uri destination, String suffix) {
        AppState state = ShrinkApp.get().state();
        if (state.uri() == null) {
            return;
        }
        ShrinkApp.get().jobs().start(job, state.uri(), destination, suffix);
        openProgress();
    }
}
