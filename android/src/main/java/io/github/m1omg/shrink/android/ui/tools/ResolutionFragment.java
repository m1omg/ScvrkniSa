package io.github.m1omg.shrink.android.ui.tools;

import android.content.Context;

import io.github.m1omg.shrink.android.R;
import io.github.m1omg.shrink.android.ui.GlassCard;
import io.github.m1omg.shrink.android.ui.Tool;
import io.github.m1omg.shrink.android.ui.Ui;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.VideoStream;
import io.github.m1omg.shrink.core.plan.Dimensions;
import io.github.m1omg.shrink.core.plan.Plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Scales the video down to a standard size such as 1080p or 720p. */
public final class ResolutionFragment extends ToolFragment {

    private int shortSide;
    private Plan plan;

    @Override
    protected Tool tool() {
        return Tool.RESOLUTION;
    }

    @Override
    protected void build(Context c, MediaInfo info) {
        if (!info.hasVideo()) {
            message(c, I18n.tr("file.noVideo"));
            return;
        }
        VideoStream v = info.video();
        content.addView(Ui.secondary(c, I18n.tr("resolution.current",
                I18n.fmt().resolution(v.displayWidth(), v.displayHeight()))));
        int[] sides = Dimensions.standardShortSides();
        List<GlassCard> cards = new ArrayList<>();
        int initial = -1;
        for (int i = 0; i < sides.length; i++) {
            int side = sides[i];
            Dimensions d = Dimensions.scaleToShortSide(v.displayWidth(), v.displayHeight(), side);
            GlassCard card = Ui.option(c, 0, Dimensions.nameOf(side), d.label());
            cards.add(card);
            if (side >= v.shortSide()) {
                Ui.setDetail(card, side == v.shortSide() ? I18n.tr("convert.current") : I18n.tr("resolution.larger"),
                        R.color.text_secondary);
            } else {
                Ui.setDetail(card, I18n.tr("compress.estimate",
                        I18n.fmt().size(planner().resize(info, side).estimatedBytes())), R.color.accent);
                if (initial < 0) {
                    initial = i;
                }
            }
        }
        int start = initial;
        Ui.singleChoice(cards, start, index -> {
            shortSide = sides[index];
            recompute();
        });
        for (int i = 0; i < sides.length; i++) {
            Ui.setOptionEnabled(cards.get(i), sides[i] < v.shortSide());
        }
        content.addView(Ui.grid(c, cards, 2, 10), Ui.matchWrap(c, 12));
        content.addView(Ui.secondary(c, I18n.tr("resolution.hint")), Ui.matchWrap(c, 14));
        if (start >= 0) {
            shortSide = sides[start];
            recompute();
        }
    }

    private void recompute() {
        plan = shortSide > 0 ? planner().resize(source, shortSide) : null;
        refresh();
    }

    @Override
    protected String fileSuffix() {
        return shortSide > 0 ? "-" + Dimensions.nameOf(shortSide).toLowerCase(Locale.ROOT) : "-resized";
    }

    @Override
    protected Plan plan() {
        return plan;
    }

    @Override
    protected String actionText() {
        return I18n.tr("resolution.action");
    }
}
