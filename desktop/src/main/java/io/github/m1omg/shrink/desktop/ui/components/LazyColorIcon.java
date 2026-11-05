package io.github.m1omg.shrink.desktop.ui.components;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.util.function.Supplier;

/** An SVG icon whose colour is chosen when it is painted (follows theme changes). */
public final class LazyColorIcon implements Icon {

    private final FlatSVGIcon icon;
    private final int size;

    public LazyColorIcon(String name, int size, Supplier<Color> color) {
        this.size = size;
        this.icon = Theme.icon(name, size);
        this.icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> color.get()));
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        icon.paintIcon(c, g, x, y);
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }
}
