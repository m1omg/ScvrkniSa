package io.github.m1omg.shrink.desktop.ui.components;

import io.github.m1omg.shrink.core.i18n.Formats;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.VideoStream;
import io.github.m1omg.shrink.core.util.OutputNames;
import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The selected video: thumbnail, name, key facts and a button to choose another file. */
public final class FileHeader extends RoundedPanel {
    private static final long serialVersionUID = 1L;

    private final Thumbnail thumbnail = new Thumbnail(112, 63, 10);
    private final JLabel name = new JLabel();
    private final JLabel facts = new JLabel();
    private final JButton replace = new JButton();

    public FileHeader(Runnable replaceAction) {
        super(new BorderLayout(14, 0));
        arc(16);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 14));

        name.putClientProperty("FlatLaf.style", "font: bold +4");
        facts.setForeground(Theme.secondaryText());
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(Box.createVerticalGlue());
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        facts.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(name);
        text.add(Box.createVerticalStrut(4));
        text.add(facts);
        text.add(Box.createVerticalGlue());

        replace.setText(I18n.tr("file.replace"));
        replace.setIcon(Theme.icon("folder", 16));
        replace.setIconTextGap(6);
        replace.addActionListener(e -> replaceAction.run());
        JPanel right = new JPanel(new java.awt.GridBagLayout());
        right.setOpaque(false);
        right.add(replace);

        add(thumbnail, BorderLayout.WEST);
        add(text, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
    }

    public void setSource(MediaInfo info, BufferedImage image) {
        name.setText(info.fileName());
        name.setToolTipText(info.location());
        facts.setText(summary(info));
        thumbnail.setImage(image);
    }

    public void setThumbnail(BufferedImage image) {
        thumbnail.setImage(image);
    }

    /** "0:23 · 1280 × 720 · 30 fps · 3.9 MB · MP4". */
    public static String summary(MediaInfo info) {
        Formats f = I18n.fmt();
        List<String> parts = new ArrayList<>();
        parts.add(f.duration(info.durationSeconds()));
        VideoStream v = info.video();
        if (v != null) {
            parts.add(f.resolution(v.displayWidth(), v.displayHeight()));
            if (v.frameRate() > 0) {
                parts.add(f.frameRate(v.frameRate()));
            }
        }
        parts.add(f.size(info.sizeBytes()));
        String ext = OutputNames.extension(info.fileName()).toUpperCase(Locale.ROOT);
        if (!ext.isEmpty()) {
            parts.add(ext);
        }
        return String.join("  ·  ", parts);
    }
}
