package io.github.m1omg.shrink.desktop.ui.components;

import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/** The empty state: a large dashed area to drop a video on, with a button to choose one. */
public final class DropZone extends JPanel {
    private static final long serialVersionUID = 1L;

    private boolean dragActive;
    private final JLabel title = new JLabel();
    private final JButton choose = new JButton();

    public DropZone(Runnable chooseAction) {
        super(new GridBagLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 32));

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        JLabel icon = new JLabel(new LazyColorIcon("upload", 56, Theme::accent));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setText(I18n.tr("file.drop.title"));
        title.putClientProperty("FlatLaf.style", "font: bold +8");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel subtitle = new JLabel(I18n.tr("file.drop.subtitle"));
        subtitle.setForeground(Theme.secondaryText());
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        choose.setText(I18n.tr("file.choose"));
        choose.putClientProperty("JButton.buttonType", "default");
        choose.setIcon(Theme.icon("folder", 16, java.awt.Color.WHITE));
        choose.setIconTextGap(8);
        choose.addActionListener(e -> chooseAction.run());
        choose.setAlignmentX(Component.CENTER_ALIGNMENT);
        styleDefault(choose);
        JLabel formats = new JLabel(I18n.tr("file.formats"));
        formats.putClientProperty("FlatLaf.styleClass", "small");
        formats.setForeground(Theme.secondaryText());
        formats.setAlignmentX(Component.CENTER_ALIGNMENT);

        column.add(icon);
        column.add(Box.createVerticalStrut(14));
        column.add(title);
        column.add(Box.createVerticalStrut(6));
        column.add(subtitle);
        column.add(Box.createVerticalStrut(18));
        column.add(choose);
        column.add(Box.createVerticalStrut(14));
        column.add(formats);
        add(column);
    }

    /** Gives a button the accent background used for primary actions. */
    public static void styleDefault(JButton button) {
        button.putClientProperty("FlatLaf.style", "background: $Component.accentColor;"
                + " focusedBackground: $Component.accentColor;"
                + " hoverBackground: darken($Component.accentColor,6%);"
                + " pressedBackground: darken($Component.accentColor,12%);"
                + " foreground: #ffffff; focusedForeground: #ffffff; hoverForeground: #ffffff;"
                // Translucent when disabled, so it looks dimmed on light and dark themes alike.
                + " pressedForeground: #ffffff; disabledText: fade(#ffffff,72%);"
                + " disabledBackground: fade($Component.accentColor,38%);"
                + " borderWidth: 0; focusWidth: 2; arc: 10; margin: 6,16,6,16; font: bold");
    }

    public void focusChooseButton() {
        choose.requestFocusInWindow();
    }

    public void setDragActive(boolean active) {
        dragActive = active;
        title.setText(I18n.tr(active ? "file.drop.release" : "file.drop.title"));
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int inset = 12;
        RoundRectangle2D r = new RoundRectangle2D.Float(inset, inset, getWidth() - 2 * inset,
                getHeight() - 2 * inset, 24, 24);
        g2.setColor(dragActive ? Theme.cardSelected() : Theme.card());
        g2.fill(r);
        g2.setColor(dragActive ? Theme.accent() : Theme.cardHover());
        g2.setStroke(new BasicStroke(dragActive ? 2.5f : 1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
                new float[] {8f, 7f}, 0f));
        g2.draw(r);
        g2.dispose();
        super.paintComponent(g);
    }
}
