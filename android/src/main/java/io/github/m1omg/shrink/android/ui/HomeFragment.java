package io.github.m1omg.shrink.android.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import io.github.m1omg.shrink.android.AppState;
import io.github.m1omg.shrink.android.MainActivity;
import io.github.m1omg.shrink.android.R;
import io.github.m1omg.shrink.android.ShrinkApp;
import io.github.m1omg.shrink.core.model.MediaInfo;

import java.util.ArrayList;
import java.util.List;

/** The "Tools" tab: choose a video, then pick what to do with it. */
public final class HomeFragment extends Fragment implements AppState.Listener {

    private LinearLayout content;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        NestedScrollView scroll = new NestedScrollView(c);
        scroll.setClipToPadding(false);
        content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = getResources().getDimensionPixelSize(R.dimen.screen_padding);
        content.setPadding(pad, Ui.dp(c, 4), pad, ((MainActivity) requireActivity()).bottomNavSpace());
        scroll.addView(content);
        return scroll;
    }

    @Override
    public void onStart() {
        super.onStart();
        ShrinkApp.get().state().addListener(this);
        render();
    }

    @Override
    public void onStop() {
        ShrinkApp.get().state().removeListener(this);
        super.onStop();
    }

    @Override
    public void sourceChanged() {
        if (isAdded()) {
            render();
        }
    }

    private MainActivity activity() {
        return (MainActivity) requireActivity();
    }

    private void render() {
        Context c = requireContext();
        AppState state = ShrinkApp.get().state();
        content.removeAllViews();
        if (state.isLoading()) {
            GlassCard card = new GlassCard(c);
            card.setGravity(Gravity.CENTER);
            card.setPadding(Ui.dp(c, 24), Ui.dp(c, 40), Ui.dp(c, 24), Ui.dp(c, 40));
            CircularProgressIndicator spinner = new CircularProgressIndicator(c);
            spinner.setIndeterminate(true);
            card.addView(spinner);
            TextView label = Ui.heading(c, getString(R.string.file_loading));
            label.setGravity(Gravity.CENTER);
            card.addView(label, Ui.matchWrap(c, 16));
            content.addView(card, Ui.matchWrap(c, 8));
            return;
        }
        if (!state.hasSource()) {
            if (state.error() != null) {
                GlassCard error = Ui.section(c, getString(R.string.file_error_title), Ui.secondary(c, state.error()));
                content.addView(error, Ui.matchWrap(c, 8));
            }
            content.addView(hero(c), Ui.matchWrap(c, 8));
            return;
        }
        content.addView(videoCard(c, state), Ui.matchWrap(c, 8));
        content.addView(tools(c, state.info()), Ui.matchWrap(c, 18));
    }

    private View hero(Context c) {
        GlassCard card = new GlassCard(c);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(Ui.dp(c, 24), Ui.dp(c, 36), Ui.dp(c, 24), Ui.dp(c, 28));
        card.addView(Ui.iconBadge(c, R.drawable.ic_upload, 84));
        TextView title = Ui.title(c, getString(R.string.app_tagline));
        title.setGravity(Gravity.CENTER);
        card.addView(title, Ui.matchWrap(c, 20));
        TextView subtitle = Ui.secondary(c, getString(R.string.file_drop_subtitle_mobile));
        subtitle.setGravity(Gravity.CENTER);
        card.addView(subtitle, Ui.matchWrap(c, 8));
        MaterialButton choose = Ui.primaryButton(c, getString(R.string.android_pick));
        choose.setIconResource(R.drawable.ic_video);
        choose.setOnClickListener(v -> activity().pickVideo());
        card.addView(choose, Ui.matchWrap(c, 24));
        MaterialButton browse = Ui.glassButton(c, getString(R.string.common_browse), R.drawable.ic_folder);
        browse.setOnClickListener(v -> activity().browseFiles());
        card.addView(browse, Ui.matchWrap(c, 10));
        TextView formats = Ui.secondary(c, getString(R.string.file_formats));
        formats.setGravity(Gravity.CENTER);
        card.addView(formats, Ui.matchWrap(c, 16));
        return card;
    }

    private View videoCard(Context c, AppState state) {
        MediaInfo info = state.info();
        GlassCard card = new GlassCard(c);
        card.setPadding(0, 0, 0, 0);
        if (info.hasVideo()) {
            ImageView thumb = new ImageView(c);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackgroundColor(Ui.color(c, R.color.video_bg));
            thumb.setImageBitmap(state.thumbnail());
            thumb.setContentDescription(info.fileName());
            card.addView(thumb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 190)));
        }
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 16);
        text.setPadding(pad, pad, pad, pad);
        TextView name = Ui.heading(c, info.fileName());
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        text.addView(name);
        text.addView(Ui.secondary(c, Texts.facts(info)), Ui.matchWrap(c, 4));
        MaterialButton replace = Ui.glassButton(c, getString(R.string.file_replace_short), R.drawable.ic_folder);
        replace.setOnClickListener(v -> activity().pickVideo());
        replace.setOnLongClickListener(v -> {
            activity().browseFiles();
            return true;
        });
        text.addView(replace, Ui.matchWrap(c, 12));
        card.addView(text);
        return card;
    }

    private View tools(Context c, MediaInfo info) {
        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(Ui.heading(c, getString(R.string.nav_tools)));

        // Compression is the main feature: a wide tile on its own row.
        GlassCard featured = new GlassCard(c);
        featured.setOrientation(LinearLayout.HORIZONTAL);
        featured.setGravity(Gravity.CENTER_VERTICAL);
        featured.setClickableCard(true);
        featured.setChecked(true);
        featured.addView(Ui.iconBadge(c, Tool.COMPRESS.icon, 52));
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(c, 14), 0, 0, 0);
        texts.addView(Ui.text(c, getString(Tool.COMPRESS.title), 17, true, R.color.text_primary));
        texts.addView(Ui.secondary(c, getString(Tool.COMPRESS.description)));
        featured.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        featured.addView(Ui.icon(c, R.drawable.ic_chevron_right, 22, R.color.text_secondary));
        featured.setOnClickListener(v -> activity().openTool(Tool.COMPRESS));
        Ui.setOptionEnabled(featured, info.hasVideo());
        column.addView(featured, Ui.matchWrap(c, 10));

        List<View> tiles = new ArrayList<>();
        for (Tool tool : Tool.values()) {
            if (tool == Tool.COMPRESS) {
                continue;
            }
            GlassCard tile = new GlassCard(c);
            tile.setClickableCard(true);
            int pad = Ui.dp(c, 14);
            tile.setPadding(pad, pad, pad, pad);
            tile.addView(Ui.iconBadge(c, tool.icon, 40));
            tile.addView(Ui.text(c, getString(tool.title), 15, true, R.color.text_primary), Ui.matchWrap(c, 10));
            TextView desc = Ui.secondary(c, getString(tool.description));
            desc.setMaxLines(2);
            tile.addView(desc, Ui.matchWrap(c, 2));
            tile.setOnClickListener(v -> activity().openTool(tool));
            boolean usable = tool == Tool.EXTRACT_AUDIO ? info.hasAudio() : (!tool.needsVideo() || info.hasVideo());
            Ui.setOptionEnabled(tile, usable);
            tiles.add(tile);
        }
        column.addView(Ui.grid(c, tiles, 2, 10), Ui.matchWrap(c, 10));
        return column;
    }
}
