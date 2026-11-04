package io.github.m1omg.shrink.desktop.ui.tools;

import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.Container;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.plan.JobPlanner;
import io.github.m1omg.shrink.core.plan.Plan;
import io.github.m1omg.shrink.desktop.AppContext;
import io.github.m1omg.shrink.desktop.ui.components.Banner;
import io.github.m1omg.shrink.desktop.ui.components.OptionCard;
import io.github.m1omg.shrink.desktop.ui.components.ResponsiveGrid;
import io.github.m1omg.shrink.desktop.ui.components.SelectableCard;
import io.github.m1omg.shrink.desktop.ui.components.Ui;
import io.github.m1omg.shrink.desktop.ui.components.VideoView;
import io.github.m1omg.shrink.desktop.ui.components.WrapLabel;
import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/** Turns the video by 90°, 180° or 270°, losslessly when the format allows it. */
public final class RotatePanel extends ToolPanel {
    private static final long serialVersionUID = 1L;

    private int rotation;
    private boolean lossless = true;
    private Plan plan;
    private VideoView view;
    private WrapLabel result;
    private OptionCard losslessCard;
    private OptionCard reencodeCard;

    public RotatePanel(AppContext app, ToolHost host) {
        super(app, host, Tool.ROTATE);
    }

    @Override
    protected void buildFor(MediaInfo info) {
        plan = null;
        rotation = 0;
        displayed = 0;
        if (!info.hasVideo()) {
            body.add(new Banner(Banner.Kind.INFO, I18n.tr("file.noVideo")));
            return;
        }
        view = new VideoView();
        view.setPreferredSize(new Dimension(480, 300));
        view.setImage(host.thumbnail());
        body.add(view);

        JButton left = new JButton(I18n.tr("rotate.left"), Theme.icon("rotate_left", 16));
        JButton right = new JButton(I18n.tr("rotate.right"), Theme.icon("rotate", 16));
        JButton flip = new JButton(I18n.tr("rotate.flip"), Theme.icon("flip", 16));
        left.addActionListener(e -> rotateBy(270));
        right.addActionListener(e -> rotateBy(90));
        flip.addActionListener(e -> rotateBy(180));
        JPanel buttons = Ui.clear(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttons.add(left);
        buttons.add(right);
        buttons.add(flip);
        body.add(buttons);

        result = new WrapLabel(I18n.tr("rotate.none")).font(java.awt.Font.BOLD, 0);
        body.add(result);

        Container c = JobPlanner.sourceContainer(info);
        boolean losslessPossible = (c == Container.MP4 || c == Container.MOV) && c.canCopyVideo(info.video().codecName());
        lossless = losslessPossible;
        body.add(Ui.heading(I18n.tr("rotate.method")));
        losslessCard = new OptionCard(Theme.icon("sparkles", 18), I18n.tr("rotate.lossless"),
                I18n.tr("rotate.lossless.hint"));
        reencodeCard = new OptionCard(Theme.icon("video", 18), I18n.tr("rotate.reencode"),
                I18n.tr("rotate.reencode.hint"));
        losslessCard.setEnabled(losslessPossible);
        losslessCard.setSelected(lossless);
        reencodeCard.setSelected(!lossless);
        losslessCard.onSelected(() -> {
            lossless = true;
            recompute();
        });
        reencodeCard.onSelected(() -> {
            lossless = false;
            recompute();
        });
        SelectableCard.group(Arrays.asList(losslessCard, reencodeCard));
        JPanel grid = Ui.clear(new ResponsiveGrid(240, 12, 2, 1));
        grid.add(losslessCard);
        grid.add(reencodeCard);
        body.add(grid);
        recompute();
    }

    /** Called when the thumbnail arrives after the panel was built. */
    public void thumbnailChanged(BufferedImage image) {
        if (view != null) {
            view.setImage(image);
        }
    }

    private void rotateBy(int degrees) {
        rotation = (rotation + degrees) % 360;
        double target = view != null ? closestAngle(rotation) : rotation;
        if (view != null) {
            view.rotateTo(target, true);
        }
        recompute();
    }

    /** The displayed angle may grow beyond 360° so the animation always turns the short way. */
    private double displayed;

    private double closestAngle(int rotationValue) {
        double delta = ((rotationValue - displayed) % 360 + 540) % 360 - 180;
        displayed += delta;
        return displayed;
    }

    private void recompute() {
        if (rotation == 0) {
            plan = null;
            result.setText(I18n.tr("rotate.none"));
        } else {
            plan = app.planner().rotate(source, rotation, lossless);
            result.setText(I18n.tr("rotate.result", rotation + "°"));
        }
        fireChanged();
    }

    @Override
    public Plan plan() {
        return plan;
    }

    @Override
    public String actionText() {
        return I18n.tr("rotate.action");
    }
}
