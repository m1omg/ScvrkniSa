package io.github.m1omg.shrink.android.ui.tools;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;

import io.github.m1omg.shrink.android.R;
import io.github.m1omg.shrink.android.ui.GlassCard;
import io.github.m1omg.shrink.android.ui.Tool;
import io.github.m1omg.shrink.android.ui.Ui;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.plan.Plan;

/** Removes the sound; the picture is copied without loss. */
public final class MuteFragment extends ToolFragment {

    private Plan plan;

    @Override
    protected Tool tool() {
        return Tool.MUTE;
    }

    @Override
    protected void build(Context c, MediaInfo info) {
        if (!info.hasVideo()) {
            message(c, I18n.tr("file.noVideo"));
            return;
        }
        if (!info.hasAudio()) {
            message(c, I18n.tr("mute.noAudio"));
            return;
        }
        plan = planner().mute(info);
        GlassCard card = new GlassCard(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(Ui.iconBadge(c, R.drawable.ic_mute, 52));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.dp(c, 14);
        card.addView(Ui.body(c, I18n.tr("mute.description")), lp);
        content.addView(card, Ui.matchWrap(c, 8));
        if (info.audioStreams().size() > 1) {
            content.addView(Ui.secondary(c, I18n.tr("mute.tracks", info.audioStreams().size())), Ui.matchWrap(c, 12));
        }
    }

    @Override
    protected Plan plan() {
        return plan;
    }

    @Override
    protected String actionText() {
        return I18n.tr("mute.action");
    }
}
