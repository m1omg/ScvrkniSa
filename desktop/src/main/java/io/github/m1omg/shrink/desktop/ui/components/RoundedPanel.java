package io.github.m1omg.shrink.desktop.ui.components;

import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Supplier;

/**
 * A panel with rounded corners and a thin border, used for cards. Colours are
 * looked up while painting, so cards follow light/dark theme changes.
 */
public class RoundedPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private int arc = 14;
    private Supplier<Color> fill = Theme::card;
    private Supplier<Color> border = Theme::cardBorder;
    private float borderWidth = 1f;

    public RoundedPanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    public RoundedPanel arc(int arc) {
        this.arc = arc;
        repaint();
        return this;
    }

    public RoundedPanel fill(Supplier<Color> fill) {
        this.fill = fill;
        repaint();
        return this;
    }

    public RoundedPanel border(Supplier<Color> border) {
        this.border = border;
        repaint();
        return this;
    }

    public RoundedPanel borderWidth(float width) {
        this.borderWidth = width;
        repaint();
        return this;
    }

    protected Color fillColor() {
        return fill == null ? null : fill.get();
    }

    protected Color borderColor() {
        return border == null ? null : border.get();
    }

    protected float currentBorderWidth() {
        return borderWidth;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        float bw = currentBorderWidth();
        float inset = bw / 2f;
        RoundRectangle2D shape = new RoundRectangle2D.Float(inset, inset, getWidth() - bw, getHeight() - bw, arc, arc);
        Color f = fillColor();
        if (f != null) {
            g2.setColor(f);
            g2.fill(shape);
        }
        Color b = borderColor();
        if (b != null && bw > 0) {
            g2.setColor(b);
            g2.setStroke(new BasicStroke(bw));
            g2.draw(shape);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
