package io.github.m1omg.shrink.desktop.ui.tools;

import io.github.m1omg.shrink.core.i18n.Formats;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.plan.JobPlanner;
import io.github.m1omg.shrink.core.plan.Plan;
import io.github.m1omg.shrink.desktop.AppContext;
import io.github.m1omg.shrink.desktop.ui.components.Banner;
import io.github.m1omg.shrink.desktop.ui.components.OptionCard;
import io.github.m1omg.shrink.desktop.ui.components.ResponsiveGrid;
import io.github.m1omg.shrink.desktop.ui.components.SelectableCard;
import io.github.m1omg.shrink.desktop.ui.components.Ui;
import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lowers the number of frames per second. */
public final class FrameRatePanel extends ToolPanel {
    private static final long serialVersionUID = 1L;

    private static final double[] RATES = {60, 50, 30, 25, 24, 15};

    private double chosen;
    private Plan plan;
    private JSpinner customValue;

    public FrameRatePanel(AppContext app, ToolHost host) {
        super(app, host, Tool.FRAME_RATE);
    }

    @Override
    protected void buildFor(MediaInfo info) {
        plan = null;
        if (!info.hasVideo()) {
            body.add(new Banner(Banner.Kind.INFO, I18n.tr("file.noVideo")));
            return;
        }
        Formats f = I18n.fmt();
        double current = info.video().frameRate() > 0 ? info.video().frameRate() : 30;
        body.add(Ui.secondary(I18n.tr("framerate.current", f.frameRate(current))));
        JPanel grid = Ui.clear(new ResponsiveGrid(130, 10, 4, 3, 2, 1));
        List<SelectableCard> group = new ArrayList<>();
        // Preselect the rate closest to half of the original (60 -> 30, 30 -> 15, 50 -> 25).
        double preferred = current > 30.5 ? JobPlanner.reducedFrameRate(current) : current / 2;
        double best = 0;
        for (double rate : RATES) {
            if (rate < current - 0.5 && (best == 0 || Math.abs(rate - preferred) < Math.abs(best - preferred))) {
                best = rate;
            }
        }
        chosen = 0;
        for (double rate : RATES) {
            OptionCard card = new OptionCard(null, f.frameRate(rate), null);
            if (rate >= current - 0.5) {
                card.setEnabled(false);
                card.setDetail(Math.abs(rate - current) < 0.5 ? I18n.tr("convert.current")
                        : I18n.tr("framerate.higher"));
                card.setDetailColor(Theme.secondaryText());
            } else {
                card.setDetail(I18n.tr("compress.estimate",
                        f.size(app.planner().changeFrameRate(info, rate).estimatedBytes())));
                if (rate == best) {
                    chosen = rate;
                    card.setSelected(true);
                }
            }
            card.onSelected(() -> {
                chosen = rate;
                recompute();
            });
            group.add(card);
            grid.add(card);
        }
        OptionCard customCard = new OptionCard(null, I18n.tr("framerate.custom"), null);
        customValue = new JSpinner(new SpinnerNumberModel(Math.max(1, Math.min(12.0, current - 1)), 1.0,
                Math.max(1.0, current - 0.01), 1.0));
        customValue.setEditor(new JSpinner.NumberEditor(customValue, "0.##"));
        customCard.add(customValue);
        customCard.onSelected(() -> {
            chosen = -1;
            recompute();
        });
        customValue.addChangeListener(e -> {
            if (!customCard.isSelected()) {
                customCard.select();
            } else {
                recompute();
            }
        });
        group.add(customCard);
        grid.add(customCard);
        if (chosen == 0) {
            chosen = -1;
            customCard.setSelected(true);
        }
        SelectableCard.group(group);
        body.add(grid);
        body.add(Ui.secondaryWrap(I18n.tr("framerate.hint")));
        recompute();
    }

    private double rate() {
        return chosen > 0 ? chosen : ((Number) customValue.getValue()).doubleValue();
    }

    private void recompute() {
        plan = app.planner().changeFrameRate(source, rate());
        fireChanged();
    }

    @Override
    public String fileSuffix() {
        String r = I18n.fmt().compactNumber(rate(), 2).replace(',', '.').replace(" ", "");
        return "-" + r.toLowerCase(Locale.ROOT) + "fps";
    }

    @Override
    public Plan plan() {
        return plan;
    }

    @Override
    public String actionText() {
        return I18n.tr("framerate.action");
    }
}
