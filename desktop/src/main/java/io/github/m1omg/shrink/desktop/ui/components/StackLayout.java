package io.github.m1omg.shrink.desktop.ui.components;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * Stacks children vertically, each as wide as the container. Heights are
 * measured after the width is known, so wrapping text gets the right height.
 */
public final class StackLayout implements LayoutManager {

    private final int gap;

    public StackLayout(int gap) {
        this.gap = gap;
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
    }

    /** Preferred height of {@code c} when it is {@code width} pixels wide. */
    static int heightFor(Component c, int width) {
        if (c.getWidth() != width) {
            c.setSize(width, Math.max(c.getHeight(), 1));
        }
        if (c instanceof Container) {
            ((Container) c).doLayout();
            for (Component child : ((Container) c).getComponents()) {
                if (child instanceof Container) {
                    ((Container) child).doLayout();
                }
            }
        }
        return c.getPreferredSize().height;
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        Insets in = parent.getInsets();
        int width = parent.getWidth() > 0 ? parent.getWidth() - in.left - in.right : 0;
        int height = 0;
        int maxWidth = 0;
        int visible = 0;
        for (Component c : parent.getComponents()) {
            if (!c.isVisible()) {
                continue;
            }
            Dimension pref = c.getPreferredSize();
            maxWidth = Math.max(maxWidth, pref.width);
            height += width > 0 ? heightFor(c, width) : pref.height;
            visible++;
        }
        height += Math.max(0, visible - 1) * gap;
        return new Dimension(Math.max(maxWidth, 1) + in.left + in.right, height + in.top + in.bottom);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        Insets in = parent.getInsets();
        return new Dimension(in.left + in.right + 120, in.top + in.bottom);
    }

    @Override
    public void layoutContainer(Container parent) {
        Insets in = parent.getInsets();
        int width = parent.getWidth() - in.left - in.right;
        int y = in.top;
        for (Component c : parent.getComponents()) {
            if (!c.isVisible()) {
                continue;
            }
            int h = heightFor(c, width);
            c.setBounds(in.left, y, width, h);
            y += h + gap;
        }
    }
}
