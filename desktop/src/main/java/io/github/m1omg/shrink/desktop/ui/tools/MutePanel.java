package io.github.m1omg.shrink.desktop.ui.tools;

import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.plan.Plan;
import io.github.m1omg.shrink.desktop.AppContext;
import io.github.m1omg.shrink.desktop.ui.components.Banner;
import io.github.m1omg.shrink.desktop.ui.components.LazyColorIcon;
import io.github.m1omg.shrink.desktop.ui.components.RoundedPanel;
import io.github.m1omg.shrink.desktop.ui.components.Ui;
import io.github.m1omg.shrink.desktop.ui.components.WrapLabel;
import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import java.awt.BorderLayout;

/** Removes the sound track; the picture is copied unchanged. */
public final class MutePanel extends ToolPanel {
    private static final long serialVersionUID = 1L;

    private Plan plan;

    public MutePanel(AppContext app, ToolHost host) {
        super(app, host, Tool.MUTE);
    }

    @Override
    protected void buildFor(MediaInfo info) {
        plan = null;
        if (!info.hasVideo()) {
            body.add(new Banner(Banner.Kind.INFO, I18n.tr("file.noVideo")));
            return;
        }
        if (!info.hasAudio()) {
            body.add(new Banner(Banner.Kind.INFO, I18n.tr("mute.noAudio")));
            return;
        }
        plan = app.planner().mute(info);
        RoundedPanel card = new RoundedPanel(new BorderLayout(16, 0));
        card.arc(16);
        card.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        card.add(new JLabel(new LazyColorIcon("mute", 36, Theme::accent)), BorderLayout.WEST);
        WrapLabel text = new WrapLabel(I18n.tr("mute.description"));
        card.add(text, BorderLayout.CENTER);
        body.add(card);
        if (info.audioStreams().size() > 1) {
            body.add(Ui.secondary(I18n.tr("mute.tracks", info.audioStreams().size())));
        }
        if (plan.job().video().isEncode()) {
            body.add(new Banner(Banner.Kind.INFO, I18n.tr("convert.reencode",
                    plan.job().video().codec().displayName())));
        }
    }

    @Override
    public Plan plan() {
        return plan;
    }

    @Override
    public String actionText() {
        return I18n.tr("mute.action");
    }
}
