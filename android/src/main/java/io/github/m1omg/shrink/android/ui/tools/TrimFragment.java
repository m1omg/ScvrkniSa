package io.github.m1omg.shrink.android.ui.tools;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.RangeSlider;

import io.github.m1omg.shrink.android.R;
import io.github.m1omg.shrink.android.ShrinkApp;
import io.github.m1omg.shrink.android.ui.GlassCard;
import io.github.m1omg.shrink.android.ui.Tool;
import io.github.m1omg.shrink.android.ui.Ui;
import io.github.m1omg.shrink.core.i18n.Formats;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.TimeRange;
import io.github.m1omg.shrink.core.plan.Plan;

import java.util.Arrays;
import java.util.List;

/** Keeps a part of the video, chosen with a range slider while watching. */
@OptIn(markerClass = UnstableApi.class)
public final class TrimFragment extends ToolFragment {

    private ExoPlayer player;
    private RangeSlider slider;
    private TextView times;
    private MaterialButton play;
    private boolean accurate = true;
    private Plan plan;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable stopAtEnd = new Runnable() {
        @Override
        public void run() {
            if (player == null || slider == null) {
                return;
            }
            if (player.getCurrentPosition() >= Math.round(end() * 1000)) {
                player.pause();
                updatePlayButton();
            } else if (player.isPlaying()) {
                handler.postDelayed(this, 40);
            }
        }
    };

    @Override
    protected Tool tool() {
        return Tool.TRIM;
    }

    @Override
    protected void build(Context c, MediaInfo info) {
        if (!info.hasVideo()) {
            message(c, I18n.tr("file.noVideo"));
            return;
        }
        GlassCard videoCard = new GlassCard(c);
        videoCard.setPadding(0, 0, 0, 0);
        PlayerView view = new PlayerView(c);
        view.setUseController(false);
        view.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        view.setShutterBackgroundColor(Ui.color(c, R.color.video_bg));
        player = new ExoPlayer.Builder(c).build();
        player.setMediaItem(MediaItem.fromUri(ShrinkApp.get().state().uri()));
        player.prepare();
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayButton();
            }
        });
        view.setPlayer(player);
        videoCard.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 220)));
        content.addView(videoCard, Ui.matchWrap(c, 4));

        float duration = (float) Math.max(0.2, info.durationSeconds());
        slider = new RangeSlider(c);
        slider.setValueFrom(0f);
        slider.setValueTo(duration);
        slider.setValues(0f, duration);
        slider.setMinSeparation(0.1f);
        slider.setLabelFormatter(value -> I18n.fmt().durationPrecise(value));
        slider.addOnChangeListener((s, value, fromUser) -> {
            if (fromUser && player != null) {
                player.pause();
                player.seekTo(Math.round(value * 1000));
            }
            rangeChanged();
        });
        content.addView(slider, Ui.matchWrap(c, 16));

        times = Ui.heading(c, "");
        times.setGravity(Gravity.CENTER);
        content.addView(times, Ui.matchWrap(c, 2));

        play = Ui.glassButton(c, I18n.tr("trim.play"), R.drawable.ic_play);
        play.setOnClickListener(v -> togglePlay());
        content.addView(play, Ui.matchWrap(c, 12));

        content.addView(Ui.heading(c, I18n.tr("trim.mode")), Ui.matchWrap(c, 18));
        GlassCard precise = Ui.option(c, R.drawable.ic_trim, I18n.tr("trim.mode.accurate"),
                I18n.tr("trim.mode.accurate.hint"));
        GlassCard fast = Ui.option(c, R.drawable.ic_sparkles, I18n.tr("trim.mode.fast"),
                I18n.tr("trim.mode.fast.hint"));
        List<GlassCard> modes = Arrays.asList(precise, fast);
        Ui.singleChoice(modes, accurate ? 0 : 1, index -> {
            accurate = index == 0;
            recompute();
        });
        content.addView(precise, Ui.matchWrap(c, 8));
        content.addView(fast, Ui.matchWrap(c, 10));
        rangeChanged();
    }

    private float start() {
        return slider.getValues().get(0);
    }

    private float end() {
        return slider.getValues().get(1);
    }

    private void rangeChanged() {
        Formats f = I18n.fmt();
        times.setText(f.durationPrecise(start()) + "  –  " + f.durationPrecise(end()) + "\n"
                + I18n.tr("trim.keeps", f.durationPrecise(end() - start()), f.durationPrecise(source.durationSeconds())));
        recompute();
    }

    private void recompute() {
        TimeRange range = new TimeRange(start(), end());
        plan = range.coversWhole(source.durationSeconds()) ? null : planner().trim(source, range, accurate);
        refresh();
    }

    private void togglePlay() {
        if (player == null) {
            return;
        }
        if (player.isPlaying()) {
            player.pause();
            return;
        }
        long startMs = Math.round(start() * 1000);
        if (player.getCurrentPosition() < startMs || player.getCurrentPosition() >= Math.round(end() * 1000) - 50) {
            player.seekTo(startMs);
        }
        player.play();
        handler.removeCallbacks(stopAtEnd);
        handler.postDelayed(stopAtEnd, 40);
    }

    private void updatePlayButton() {
        if (play == null || player == null) {
            return;
        }
        boolean playing = player.isPlaying();
        play.setText(I18n.tr(playing ? "trim.stop" : "trim.play"));
        play.setIconResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    @Override
    public void onStop() {
        if (player != null) {
            player.pause();
        }
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroyView();
    }

    @Override
    protected Plan plan() {
        return plan;
    }

    @Override
    protected String actionText() {
        return I18n.tr("trim.action");
    }
}
