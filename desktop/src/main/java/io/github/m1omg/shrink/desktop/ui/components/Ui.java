package io.github.m1omg.shrink.desktop.ui.components;

import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/** Small helpers for building consistent layouts. */
public final class Ui {

    private Ui() {
    }

    public static JLabel title(String text) {
        JLabel l = new JLabel(text);
        l.putClientProperty("FlatLaf.style", "font: bold +8");
        return l;
    }

    public static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.putClientProperty("FlatLaf.style", "font: bold +1");
        return l;
    }

    public static JLabel secondary(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Theme.secondaryText());
        return l;
    }

    public static WrapLabel secondaryWrap(String text) {
        return new WrapLabel(text).color(Theme.secondaryText());
    }

    /** A transparent panel. */
    public static JPanel clear(java.awt.LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        return p;
    }

    /** Components in a row, left-aligned. */
    public static JPanel row(int gap, Component... items) {
        JPanel p = clear(new FlowLayout(FlowLayout.LEFT, gap, 0));
        for (Component c : items) {
            p.add(c);
        }
        return p;
    }

    /** Label above control, used in forms. */
    public static JPanel field(String label, JComponent control) {
        JPanel p = clear(new BorderLayout(0, 6));
        JLabel l = new JLabel(label);
        l.putClientProperty("FlatLaf.style", "font: bold");
        p.add(l, BorderLayout.NORTH);
        p.add(control, BorderLayout.CENTER);
        return p;
    }

    /** A two-column table of label/value rows. */
    public static JPanel keyValues(String[][] rows) {
        JPanel p = clear(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.NORTHWEST;
        for (int i = 0; i < rows.length; i++) {
            c.gridy = i;
            c.gridx = 0;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            c.insets = new Insets(i == 0 ? 0 : 7, 0, 0, 18);
            JLabel key = new JLabel(rows[i][0]);
            key.setForeground(Theme.secondaryText());
            p.add(key, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(i == 0 ? 0 : 7, 0, 0, 0);
            WrapLabel value = new WrapLabel(rows[i][1]);
            p.add(value, c);
        }
        return p;
    }

    public static <T extends JComponent> T padded(T c, int top, int left, int bottom, int right) {
        c.setBorder(BorderFactory.createEmptyBorder(top, left, bottom, right));
        return c;
    }
}
