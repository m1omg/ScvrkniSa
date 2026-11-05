package io.github.m1omg.shrink.android.ui;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import io.github.m1omg.shrink.android.R;
import io.github.m1omg.shrink.android.ShrinkApp;
import io.github.m1omg.shrink.android.media.Media3Runner;
import io.github.m1omg.shrink.core.i18n.Formats;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.Job;
import io.github.m1omg.shrink.core.plan.JobPlanner;

import java.io.File;

/**
 * The 5-second quality preview: encodes an excerpt with the chosen settings
 * and plays it in sync with the original. Drag the divider to compare.
 */
@OptIn(markerClass = UnstableApi.class)
public final class PreviewActivity extends AppCompatActivity {

    private static final double LENGTH = 5.0;
    private static final long SYNC_MS = 500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Job job;
    private Job excerpt;
    private Media3Runner runner;
    private File previewFile;
    private ExoPlayer original;
    private ExoPlayer compressed;
    private PlayerView compressedView;
    private View divider;
    private View knob;
    private View compare;
    private float dividerFraction = 0.5f;

    private final Runnable sync = new Runnable() {
        @Override
        public void run() {
            if (original != null && compressed != null) {
                long a = original.getCurrentPosition();
                long b = compressed.getCurrentPosition();
                if (Math.abs(a - b) > 120) {
                    compressed.seekTo(a);
                }
                handler.postDelayed(this, SYNC_MS);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        ShrinkApp.updateLocale(this);
        job = ShrinkApp.get().state().previewJob();
        Uri input = ShrinkApp.get().state().uri();
        if (job == null || input == null) {
            finish();
            return;
        }
        setContentView(R.layout.activity_preview);
        View safe = findViewById(R.id.safe_area);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            safe.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ((GlassBackgroundView) findViewById(R.id.background))
                .setAnimated(ShrinkApp.get().settings().isAnimatedBackground());
        findViewById(R.id.close).setOnClickListener(v -> finish());
        findViewById(R.id.compress_all).setOnClickListener(v -> {
            setResult(RESULT_OK);
            finish();
        });
        compressedView = findViewById(R.id.compressed_view);
        divider = findViewById(R.id.divider);
        knob = findViewById(R.id.divider_knob);
        compare = findViewById(R.id.compare);
        setDividerVisible(false);
        installDivider();
        generate(input);
    }

    private void generate(Uri input) {
        double start = JobPlanner.defaultPreviewStart(job, LENGTH);
        excerpt = JobPlanner.preview(job, start, LENGTH);
        File dir = new File(getCacheDir(), "preview");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        previewFile = new File(dir, "preview." + excerpt.outputExtension());
        //noinspection ResultOfMethodCallIgnored
        previewFile.delete();
        runner = new Media3Runner(this);
        runner.start(excerpt, input, previewFile, new Media3Runner.Listener() {
            @Override
            public void progress(int percent) {
            }

            @Override
            public void completed(File output) {
                ready(input, output);
            }

            @Override
            public void failed(String message) {
                findViewById(R.id.generating).setVisibility(View.GONE);
                ((TextView) findViewById(R.id.preview_size)).setText(I18n.tr("preview.failed"));
                ((TextView) findViewById(R.id.preview_estimate)).setText(message);
            }
        });
    }

    private void ready(Uri input, File output) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        findViewById(R.id.generating).setVisibility(View.GONE);
        findViewById(R.id.compress_all).setEnabled(true);
        Formats f = I18n.fmt();
        long bytes = output.length();
        double length = excerpt.outputDuration();
        ((TextView) findViewById(R.id.preview_size)).setText(I18n.tr("preview.size", f.size(bytes),
                f.durationWords(length)));
        ((TextView) findViewById(R.id.preview_estimate)).setText(I18n.tr("preview.estimate",
                f.size(job.estimatedBytes())));

        long startMs = Math.round(excerpt.trim().start() * 1000);
        long endMs = Math.round(excerpt.trim().end() * 1000);
        original = new ExoPlayer.Builder(this).build();
        original.setMediaItem(new MediaItem.Builder().setUri(input)
                .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs).setEndPositionMs(endMs).build())
                .build());
        original.setVolume(0f);
        original.setRepeatMode(Player.REPEAT_MODE_ONE);
        compressed = new ExoPlayer.Builder(this).build();
        compressed.setMediaItem(MediaItem.fromUri(Uri.fromFile(output)));
        compressed.setRepeatMode(Player.REPEAT_MODE_ONE);
        ((PlayerView) findViewById(R.id.original_view)).setPlayer(original);
        compressedView.setPlayer(compressed);
        original.prepare();
        compressed.prepare();
        original.play();
        compressed.play();
        setDividerVisible(true);
        compare.post(this::applyDivider);
        handler.postDelayed(sync, SYNC_MS);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void installDivider() {
        compare.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                dividerFraction = Math.max(0.02f, Math.min(0.98f, event.getX() / Math.max(1, v.getWidth())));
                applyDivider();
                return true;
            }
            return event.getActionMasked() == MotionEvent.ACTION_UP;
        });
        compare.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> applyDivider());
    }

    /** The compressed video is drawn on top, clipped to the right of the divider. */
    private void applyDivider() {
        int w = compare.getWidth();
        int h = compare.getHeight();
        if (w == 0) {
            return;
        }
        int x = Math.round(w * dividerFraction);
        compressedView.setClipBounds(new Rect(x, 0, w, h));
        divider.setTranslationX(x - divider.getWidth() / 2f);
        knob.setTranslationX(x - knob.getWidth() / 2f);
    }

    private void setDividerVisible(boolean visible) {
        int v = visible ? View.VISIBLE : View.INVISIBLE;
        divider.setVisibility(v);
        knob.setVisibility(v);
        findViewById(R.id.label_original).setVisibility(v);
        findViewById(R.id.label_compressed).setVisibility(v);
    }

    @Override
    protected void onStop() {
        if (original != null) {
            original.pause();
        }
        if (compressed != null) {
            compressed.pause();
        }
        super.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (original != null && compressed != null) {
            compressed.seekTo(original.getCurrentPosition());
            original.play();
            compressed.play();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (runner != null) {
            runner.cancel();
        }
        if (original != null) {
            original.release();
        }
        if (compressed != null) {
            compressed.release();
        }
        if (previewFile != null) {
            //noinspection ResultOfMethodCallIgnored
            previewFile.delete();
        }
        super.onDestroy();
    }
}
