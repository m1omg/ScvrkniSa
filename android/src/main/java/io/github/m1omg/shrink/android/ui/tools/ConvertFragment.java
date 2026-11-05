package io.github.m1omg.shrink.android.ui.tools;

import android.content.Context;
import android.widget.TextView;

import io.github.m1omg.shrink.android.R;
import io.github.m1omg.shrink.android.media.EncoderSupport;
import io.github.m1omg.shrink.android.media.Media3Runner;
import io.github.m1omg.shrink.android.ui.GlassCard;
import io.github.m1omg.shrink.android.ui.Tool;
import io.github.m1omg.shrink.android.ui.Ui;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.Container;
import io.github.m1omg.shrink.core.model.Job;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.plan.Plan;

import java.util.ArrayList;
import java.util.List;

/** Changes the container format, copying the streams when the new format allows it. */
public final class ConvertFragment extends ToolFragment {

    private Container target;
    private Plan plan;
    private TextView status;

    @Override
    protected Tool tool() {
        return Tool.CONVERT;
    }

    @Override
    protected void build(Context c, MediaInfo info) {
        if (!info.hasVideo()) {
            message(c, I18n.tr("file.noVideo"));
            return;
        }
        Container current = Container.fromFileName(info.fileName());
        target = current == Container.MP4 ? Container.MKV : Container.MP4;
        content.addView(Ui.heading(c, I18n.tr("convert.target")));
        List<GlassCard> cards = new ArrayList<>();
        for (Container ct : Container.values()) {
            GlassCard card = Ui.option(c, R.drawable.ic_video, ct.displayName(), I18n.tr("convert." + ct.id() + ".hint"));
            cards.add(card);
        }
        Ui.singleChoice(cards, target.ordinal(), index -> {
            target = Container.values()[index];
            recompute();
        });
        for (Container ct : Container.values()) {
            GlassCard card = cards.get(ct.ordinal());
            boolean usable = ct != current && EncoderSupport.canWrite(ct);
            Ui.setOptionEnabled(card, usable);
            if (ct == current) {
                Ui.setDetail(card, I18n.tr("convert.current"), R.color.text_secondary);
            } else if (!EncoderSupport.canWrite(ct)) {
                Ui.setDetail(card, I18n.tr("android.webmAudio"), R.color.warning);
            } else {
                Ui.setDetail(card, I18n.tr("compress.estimate",
                        I18n.fmt().size(planner().convert(info, ct).estimatedBytes())), R.color.accent);
            }
        }
        content.addView(Ui.grid(c, cards, 2, 10), Ui.matchWrap(c, 8));
        GlassCard statusCard = new GlassCard(c);
        status = Ui.body(c, "");
        statusCard.addView(status);
        content.addView(statusCard, Ui.matchWrap(c, 14));
        recompute();
    }

    private void recompute() {
        plan = planner().convert(source, target);
        Job job = Media3Runner.adapt(plan.job());
        if (job.video().isCopy() && !job.audio().isEncode()) {
            status.setText(I18n.tr("convert.fast"));
        } else if (job.video().isCopy()) {
            status.setText(I18n.tr("convert.audioReencode", job.audio().codec().displayName()));
        } else {
            status.setText(I18n.tr("convert.reencode", job.video().codec().displayName()));
        }
        refresh();
    }

    @Override
    protected String fileSuffix() {
        return Container.fromFileName(source.fileName()) == target ? "-converted" : "";
    }

    @Override
    protected Plan plan() {
        return plan;
    }

    @Override
    protected String actionText() {
        return I18n.tr("convert.action");
    }
}
