package io.github.m1omg.shrink.desktop.ui.components;

import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** A small rounded thumbnail; the image is cropped to fill the box. */
public final class Thumbnail extends JComponent {
    private static final long serialVersionUID = 1L;

    private BufferedImage image;
    private final int arc;

    public Thumbnail(int width, int height, int arc) {
        this.arc = arc;
        setPreferredSize(new Dimension(width, height));
        setMinimumSize(new Dimension(width, height));
        setMaximumSize(new Dimension(width, height));
    }

    public void setImage(BufferedImage image) {
        this.image = image;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc);
        g2.setColor(Theme.isDark() ? new Color(0x111214) : new Color(0x2A2B30));
        g2.fill(shape);
        if (image != null) {
            g2.clip(shape);
            double scale = Math.max((double) getWidth() / image.getWidth(), (double) getHeight() / image.getHeight());
            int w = (int) Math.round(image.getWidth() * scale);
            int h = (int) Math.round(image.getHeight() * scale);
            g2.drawImage(image, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
        }
        g2.dispose();
    }
}
