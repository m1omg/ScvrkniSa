package io.github.m1omg.shrink.desktop.ui.tools;

import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.Container;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.plan.JobPlanner;
import io.github.m1omg.shrink.core.plan.Plan;
import io.github.m1omg.shrink.core.plan.PlanText;
import io.github.m1omg.shrink.desktop.AppContext;
import io.github.m1omg.shrink.desktop.ui.components.Banner;
import io.github.m1omg.shrink.desktop.ui.components.OptionCard;
import io.github.m1omg.shrink.desktop.ui.components.ResponsiveGrid;
import io.github.m1omg.shrink.desktop.ui.components.SelectableCard;
import io.github.m1omg.shrink.desktop.ui.components.Ui;
import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.JPanel;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Changes the container format (MP4, MOV, MKV, WEBM), copying streams when possible. */
public final class ConvertPanel extends ToolPanel {
    private static final long serialVersionUID = 1L;

    private Container target;
    private Plan plan;
    private Banner status;
    private final Map<Container, OptionCard> cards = new EnumMap<>(Container.class);

    public ConvertPanel(AppContext app, ToolHost host) {
        super(app, host, Tool.CONVERT);
    }

    @Override
    protected void buildFor(MediaInfo info) {
        plan = null;
        cards.clear();
        if (!info.hasVideo()) {
            body.add(new Banner(Banner.Kind.INFO, I18n.tr("file.noVideo")));
            return;
        }
        Container current = Container.fromFileName(info.fileName());
        target = current == Container.MP4 ? Container.MKV : Container.MP4;
        body.add(Ui.heading(I18n.tr("convert.target")));
        JPanel grid = Ui.clear(new ResponsiveGrid(200, 12, 4, 2, 1));
        List<SelectableCard> group = new ArrayList<>();
        for (Container c : Container.values()) {
            OptionCard card = new OptionCard(Theme.icon("video", 18), c.displayName(),
                    I18n.tr("convert." + c.id() + ".hint"));
            if (c == current) {
                card.setBadge(I18n.tr("convert.current"));
                card.setEnabled(false);
            }
            card.setSelected(c == target);
            card.onSelected(() -> {
                target = c;
                recompute();
            });
            cards.put(c, card);
            group.add(card);
            grid.add(card);
        }
        SelectableCard.group(group);
        body.add(grid);
        status = new Banner();
        body.add(status);
        recompute();
    }

    private void recompute() {
        plan = app.planner().convert(source, target);
        for (Map.Entry<Container, OptionCard> e : cards.entrySet()) {
            if (e.getValue().isEnabled()) {
                Plan p = app.planner().convert(source, e.getKey());
                e.getValue().setDetail(I18n.tr("compress.estimate", I18n.fmt().size(p.estimatedBytes())));
            }
        }
        if (plan.job().video().isCopy() && !plan.job().audio().isEncode()) {
            status.setKind(Banner.Kind.SUCCESS);
            status.setText(I18n.tr("convert.fast"));
        } else if (plan.job().video().isCopy()) {
            status.setKind(Banner.Kind.INFO);
            status.setText(I18n.tr("convert.audioReencode", plan.job().audio().codec().displayName()));
        } else {
            status.setKind(Banner.Kind.INFO);
            status.setText(I18n.tr("convert.reencode", plan.job().video().codec().displayName()));
        }
        String warnings = PlanText.warnings(plan);
        if (warnings != null) {
            status.setKind(Banner.Kind.WARNING);
            status.setText(warnings);
        }
        fireChanged();
    }

    @Override
    public String fileSuffix() {
        return Container.fromFileName(source == null ? null : source.fileName()) == target ? "-converted" : "";
    }

    @Override
    public Plan plan() {
        return plan;
    }

    @Override
    public String actionText() {
        return I18n.tr("convert.action");
    }

    /** The format chosen by default when the source has no recognised container. */
    static Container fallback(MediaInfo info) {
        return JobPlanner.sourceContainer(info);
    }
}
