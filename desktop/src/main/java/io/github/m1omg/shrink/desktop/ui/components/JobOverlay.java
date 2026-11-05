package io.github.m1omg.shrink.desktop.ui.components;

import io.github.m1omg.shrink.core.i18n.Formats;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.util.ArrayList;
import java.util.List;

/**
 * Covers the window while a job runs: shows progress, then the result (or an
 * error). Installed as the frame's glass pane, so it blocks clicks on the
 * window underneath.
 */
public final class JobOverlay extends JPanel {
    private static final long serialVersionUID = 1L;

    /** A button shown on the result card. */
    public record Action(String text, Runnable run, boolean primary) {
    }

    private final RoundedPanel card = new RoundedPanel(null);
    private final JPanel content = new JPanel();

    public JobOverlay() {
        super(new GridBagLayout());
        setOpaque(false);
        // Swallow mouse events so nothing underneath can be clicked.
        addMouseListener(new MouseAdapter() {
        });
        addMouseMotionListener(new MouseAdapter() {
        });
        setCursor(Cursor.getDefaultCursor());
        card.setLayout(new java.awt.BorderLayout());
        card.arc(20);
        card.setBorder(BorderFactory.createEmptyBorder(26, 30, 24, 30));
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        card.add(content);
        add(card);
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(Theme.isDark() ? new Color(0, 0, 0, 140) : new Color(20, 22, 30, 90));
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    private JProgressBar bar;
    private JLabel status;
    private JLabel detail;

    /** Shows the progress card. */
    public void showProgress(String iconName, String title, String fileName, Runnable cancel) {
        content.removeAll();
        content.add(centered(new JLabel(new LazyColorIcon(iconName, 34, Theme::accent))));
        content.add(Box.createVerticalStrut(10));
        JLabel heading = new JLabel(title);
        heading.putClientProperty("FlatLaf.style", "font: bold +4");
        content.add(centered(heading));
        content.add(Box.createVerticalStrut(4));
        JLabel name = new JLabel(fileName);
        name.setForeground(Theme.secondaryText());
        content.add(centered(name));
        content.add(Box.createVerticalStrut(18));
        bar = new JProgressBar(0, 1000);
        bar.setIndeterminate(true);
        bar.setPreferredSize(new Dimension(380, 8));
        bar.setMaximumSize(new Dimension(380, 8));
        content.add(centered(bar));
        content.add(Box.createVerticalStrut(10));
        status = new JLabel(I18n.tr("progress.preparing"));
        status.putClientProperty("FlatLaf.style", "font: bold");
        content.add(centered(status));
        content.add(Box.createVerticalStrut(2));
        detail = new JLabel(" ");
        detail.setForeground(Theme.secondaryText());
        detail.putClientProperty("FlatLaf.styleClass", "small");
        content.add(centered(detail));
        content.add(Box.createVerticalStrut(18));
        JButton cancelButton = new JButton(I18n.tr("progress.cancel"));
        cancelButton.addActionListener(e -> cancel.run());
        content.add(centered(cancelButton));
        card.setPreferredSize(null);
        setVisible(true);
        revalidate();
        repaint();
        cancelButton.requestFocusInWindow();
    }

    /**
     * Updates the progress card.
     *
     * @param fraction  0..1
     * @param remaining seconds left, or a negative value when unknown
     * @param speed     speed relative to real time, 0 when unknown
     * @param pass      current pass (1-based)
     * @param passes    number of passes
     * @param correcting true during an extra pass that brings the size closer to the estimate
     */
    public void updateProgress(double fraction, double remaining, double speed, int pass, int passes,
                               boolean correcting) {
        if (bar == null) {
            return;
        }
        Formats f = I18n.fmt();
        bar.setIndeterminate(false);
        bar.setValue((int) Math.round(Math.max(0, Math.min(1, fraction)) * 1000));
        StringBuilder s = new StringBuilder(f.percent(Math.floor(fraction * 100)));
        if (remaining >= 0) {
            s.append("  ·  ").append(I18n.tr("progress.remaining", f.durationWords(remaining)));
        }
        status.setText(s.toString());
        List<String> parts = new ArrayList<>();
        if (correcting) {
            parts.add(I18n.tr("progress.secondPass"));
        } else if (passes > 1) {
            parts.add(I18n.tr("progress.pass", pass, passes));
        }
        if (speed > 0) {
            parts.add(I18n.tr("progress.speed", f.compactNumber(speed, 1)));
        }
        detail.setText(parts.isEmpty() ? " " : String.join("  ·  ", parts));
    }

    public void showFinishing() {
        if (status != null) {
            bar.setValue(1000);
            status.setText(I18n.tr("progress.finishing"));
        }
    }

    /** Shows a success card. */
    public void showSuccess(String title, String big, String chip, boolean chipGood, String subtitle,
                            List<Action> actions) {
        content.removeAll();
        content.add(centered(new JLabel(new LazyColorIcon("check_circle", 44, Theme::success))));
        content.add(Box.createVerticalStrut(10));
        JLabel heading = new JLabel(title);
        heading.putClientProperty("FlatLaf.style", "font: bold +8");
        content.add(centered(heading));
        content.add(Box.createVerticalStrut(12));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        row.setOpaque(false);
        JLabel size = new JLabel(big);
        size.putClientProperty("FlatLaf.style", "font: bold +14");
        row.add(size);
        if (chip != null) {
            row.add(new Chip(chip, chipGood ? Theme::success : Theme::warning));
        }
        content.add(centered(row));
        content.add(Box.createVerticalStrut(8));
        if (subtitle != null) {
            JLabel sub = new JLabel(subtitle);
            sub.setForeground(Theme.secondaryText());
            content.add(centered(sub));
        }
        content.add(Box.createVerticalStrut(22));
        content.add(centered(buttons(actions)));
        setVisible(true);
        revalidate();
        repaint();
    }

    /** Shows an error card with optional technical details. */
    public void showError(String title, String message, String details, List<Action> actions) {
        content.removeAll();
        content.add(centered(new JLabel(new LazyColorIcon("error", 44, Theme::error))));
        content.add(Box.createVerticalStrut(10));
        JLabel heading = new JLabel(title);
        heading.putClientProperty("FlatLaf.style", "font: bold +8");
        content.add(centered(heading));
        content.add(Box.createVerticalStrut(10));
        WrapLabel text = new WrapLabel(message);
        text.setAlignmentX(Component.CENTER_ALIGNMENT);
        text.setMaximumSize(new Dimension(420, 200));
        content.add(text);
        if (details != null && !details.trim().isEmpty()) {
            content.add(Box.createVerticalStrut(10));
            JTextArea log = new JTextArea(details, 6, 50);
            log.setEditable(false);
            log.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN,
                    Math.max(11, log.getFont().getSize() - 1)));
            JScrollPane scroll = new JScrollPane(log);
            scroll.setPreferredSize(new Dimension(440, 110));
            scroll.setMaximumSize(new Dimension(440, 110));
            scroll.setAlignmentX(Component.CENTER_ALIGNMENT);
            content.add(scroll);
        }
        content.add(Box.createVerticalStrut(20));
        content.add(centered(buttons(actions)));
        setVisible(true);
        revalidate();
        repaint();
    }

    public void close() {
        setVisible(false);
        content.removeAll();
        bar = null;
        status = null;
        detail = null;
    }

    private JComponent buttons(List<Action> actions) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        row.setOpaque(false);
        JButton focus = null;
        for (Action a : actions) {
            JButton b = new JButton(a.text());
            b.addActionListener(e -> a.run().run());
            if (a.primary()) {
                DropZone.styleDefault(b);
                focus = b;
            }
            row.add(b);
        }
        if (focus != null) {
            JButton f = focus;
            javax.swing.SwingUtilities.invokeLater(f::requestFocusInWindow);
        }
        return row;
    }

    private static JComponent centered(JComponent c) {
        c.setAlignmentX(Component.CENTER_ALIGNMENT);
        return c;
    }

    /** A small coloured pill such as "−62 %". */
    static final class Chip extends JLabel {
        private static final long serialVersionUID = 1L;
        private final java.util.function.Supplier<Color> color;

        Chip(String text, java.util.function.Supplier<Color> color) {
            super(text);
            this.color = color;
            setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
            putClientProperty("FlatLaf.style", "font: bold");
        }

        @Override
        protected void paintComponent(Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = color.get();
            g2.setColor(Theme.blend(c, Theme.card(), Theme.isDark() ? 0.25 : 0.14));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            setForeground(c);
            super.paintComponent(g);
        }
    }
}
