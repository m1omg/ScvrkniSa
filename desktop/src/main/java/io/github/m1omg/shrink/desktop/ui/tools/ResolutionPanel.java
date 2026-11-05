package io.github.m1omg.shrink.desktop.ui.tools;

import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.VideoStream;
import io.github.m1omg.shrink.core.plan.Dimensions;
import io.github.m1omg.shrink.core.plan.Plan;
import io.github.m1omg.shrink.desktop.AppContext;
import io.github.m1omg.shrink.desktop.ui.components.Banner;
import io.github.m1omg.shrink.desktop.ui.components.OptionCard;
import io.github.m1omg.shrink.desktop.ui.components.ResponsiveGrid;
import io.github.m1omg.shrink.desktop.ui.components.SelectableCard;
import io.github.m1omg.shrink.desktop.ui.components.Ui;
import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.util.ArrayList;
import java.util.List;

/** Scales the video to a standard or custom size. */
public final class ResolutionPanel extends ToolPanel {
    private static final long serialVersionUID = 1L;

    private int chosenShortSide;
    private boolean custom;
    private Plan plan;
    private JSpinner width;
    private JSpinner height;
    private JCheckBox keepAspect;
    private boolean updating;

    public ResolutionPanel(AppContext app, ToolHost host) {
        super(app, host, Tool.RESOLUTION);
    }

    @Override
    protected void buildFor(MediaInfo info) {
        plan = null;
        custom = false;
        if (!info.hasVideo()) {
            body.add(new Banner(Banner.Kind.INFO, I18n.tr("file.noVideo")));
            return;
        }
        VideoStream v = info.video();
        body.add(Ui.secondary(I18n.tr("resolution.current", I18n.fmt().resolution(v.displayWidth(),
                v.displayHeight()))));
        JPanel grid = Ui.clear(new ResponsiveGrid(150, 10, 4, 3, 2, 1));
        List<SelectableCard> group = new ArrayList<>();
        chosenShortSide = 0;
        for (int side : Dimensions.standardShortSides()) {
            Dimensions d = Dimensions.scaleToShortSide(v.displayWidth(), v.displayHeight(), side);
            OptionCard card = new OptionCard(null, Dimensions.nameOf(side), d.label());
            boolean larger = side >= v.shortSide();
            if (larger) {
                card.setEnabled(false);
                card.setDetail(side == v.shortSide() ? I18n.tr("convert.current") : I18n.tr("resolution.larger"));
                card.setDetailColor(Theme.secondaryText());
            } else {
                Plan p = app.planner().resize(info, side);
                card.setDetail(I18n.tr("compress.estimate", I18n.fmt().size(p.estimatedBytes())));
                if (chosenShortSide == 0) {
                    chosenShortSide = side;
                    card.setSelected(true);
                }
            }
            card.onSelected(() -> {
                custom = false;
                chosenShortSide = side;
                recompute();
            });
            group.add(card);
            grid.add(card);
        }
        OptionCard customCard = new OptionCard(null, I18n.tr("resolution.custom"), null);
        width = new JSpinner(new SpinnerNumberModel(Dimensions.even(v.displayWidth() / 2.0), 16, 16384, 2));
        height = new JSpinner(new SpinnerNumberModel(Dimensions.even(v.displayHeight() / 2.0), 16, 16384, 2));
        keepAspect = new JCheckBox(I18n.tr("resolution.keepAspect"), true);
        ((JSpinner.DefaultEditor) width.getEditor()).getTextField().setColumns(4);
        ((JSpinner.DefaultEditor) height.getEditor()).getTextField().setColumns(4);
        JPanel sizes = Ui.clear(new java.awt.GridLayout(2, 2, 6, 6));
        sizes.add(new JLabel(I18n.tr("resolution.width")));
        sizes.add(width);
        sizes.add(new JLabel(I18n.tr("resolution.height")));
        sizes.add(height);
        customCard.add(sizes);
        customCard.add(keepAspect);
        customCard.onSelected(() -> {
            custom = true;
            recompute();
        });
        width.addChangeListener(e -> sizeEdited(true));
        height.addChangeListener(e -> sizeEdited(false));
        group.add(customCard);
        grid.add(customCard);
        if (chosenShortSide == 0) {
            custom = true;
            customCard.setSelected(true);
        }
        SelectableCard.group(group);
        body.add(grid);
        body.add(Ui.secondaryWrap(I18n.tr("resolution.hint")));
        recompute();
    }

    private void sizeEdited(boolean widthChanged) {
        if (updating) {
            return;
        }
        VideoStream v = source.video();
        updating = true;
        try {
            if (keepAspect.isSelected()) {
                double aspect = (double) v.displayWidth() / v.displayHeight();
                if (widthChanged) {
                    height.setValue(Dimensions.even(((Number) width.getValue()).intValue() / aspect));
                } else {
                    width.setValue(Dimensions.even(((Number) height.getValue()).intValue() * aspect));
                }
            }
        } finally {
            updating = false;
        }
        custom = true;
        recompute();
    }

    private void recompute() {
        if (custom) {
            plan = app.planner().resize(source, ((Number) width.getValue()).intValue(),
                    ((Number) height.getValue()).intValue());
        } else {
            plan = app.planner().resize(source, chosenShortSide);
        }
        fireChanged();
    }

    @Override
    public String fileSuffix() {
        if (plan == null) {
            return "-resized";
        }
        int shortSide = Math.min(plan.job().outputWidth(), plan.job().outputHeight());
        return custom ? "-" + plan.job().outputWidth() + "x" + plan.job().outputHeight() : "-" + Dimensions.nameOf(
                shortSide).toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public Plan plan() {
        return plan;
    }

    @Override
    public String actionText() {
        return I18n.tr("resolution.action");
    }
}
