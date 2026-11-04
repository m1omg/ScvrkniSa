package io.github.m1omg.shrink.android.ui.tools;

import android.content.Context;

import io.github.m1omg.shrink.android.R;
import io.github.m1omg.shrink.android.ui.GlassCard;
import io.github.m1omg.shrink.android.ui.Tool;
import io.github.m1omg.shrink.android.ui.Ui;
import io.github.m1omg.shrink.core.i18n.Formats;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.plan.JobPlanner;
import io.github.m1omg.shrink.core.plan.Plan;

import java.util.ArrayList;
import java.util.List;

/** Lowers the frames per second (e.g. 60 → 30). */
public final class FrameRateFragment extends ToolFragment {

    private static final double[] RATES = {60, 50, 30, 25, 24, 15};

    private double rate;
    private Plan plan;

    @Override
    protected Tool tool() {
        return Tool.FRAME_RATE;
    }

    @Override
    protected void build(Context c, MediaInfo info) {
        if (!info.hasVideo()) {
            message(c, I18n.tr("file.noVideo"));
            return;
        }
        Formats f = I18n.fmt();
        double current = info.video().frameRate() > 0 ? info.video().frameRate() : 30;
        content.addView(Ui.secondary(c, I18n.tr("framerate.current", f.frameRate(current))));
        double preferred = current > 30.5 ? JobPlanner.reducedFrameRate(current) : current / 2;
        int best = -1;
        List<GlassCard> cards = new ArrayList<>();
        for (int i = 0; i < RATES.length; i++) {
            double r = RATES[i];
            GlassCard card = Ui.option(c, 0, f.frameRate(r), null);
            cards.add(card);
            boolean usable = r < current - 0.5;
            if (usable) {
                Ui.setDetail(card, I18n.tr("compress.estimate",
                        f.size(planner().changeFrameRate(info, r).estimatedBytes())), R.color.accent);
                if (best < 0 || Math.abs(r - preferred) < Math.abs(RATES[best] - preferred)) {
                    best = i;
                }
            } else {
                Ui.setDetail(card, Math.abs(r - current) < 0.5 ? I18n.tr("convert.current")
                        : I18n.tr("framerate.higher"), R.color.text_secondary);
            }
        }
        int start = best;
        Ui.singleChoice(cards, start, index -> {
            rate = RATES[index];
            recompute();
        });
        for (int i = 0; i < RATES.length; i++) {
            Ui.setOptionEnabled(cards.get(i), RATES[i] < current - 0.5);
        }
        content.addView(Ui.grid(c, cards, 2, 10), Ui.matchWrap(c, 12));
        content.addView(Ui.secondary(c, I18n.tr("framerate.hint")), Ui.matchWrap(c, 14));
        if (start >= 0) {
            rate = RATES[start];
            recompute();
        }
    }

    private void recompute() {
        plan = rate > 0 ? planner().changeFrameRate(source, rate) : null;
        refresh();
    }

    @Override
    protected String fileSuffix() {
        return "-" + Math.round(rate) + "fps";
    }

    @Override
    protected Plan plan() {
        return plan;
    }

    @Override
    protected String actionText() {
        return I18n.tr("framerate.action");
    }
}
