package io.github.m1omg.shrink.desktop.ui.components;

import io.github.m1omg.shrink.desktop.ui.player.FrameSurface;
import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Shows a video frame scaled to fit, on a dark rounded background. The frame
 * can be shown rotated; rotation changes are animated over a fixed duration
 * measured with the system clock, so the animation takes the same time on
 * every display regardless of its refresh rate.
 */
public class VideoView extends JComponent implements FrameSurface {
    private static final long serialVersionUID = 1L;
    private static final double ANIMATION_SECONDS = 0.28;

    private BufferedImage image;
    private String message;
    private double rotation;
    private double animationFrom;
    private double animationTo;
    private long animationStart;
    private final Timer timer;
    private double aspect = 16.0 / 9.0;
    private BufferedImage playbackBuffer;
    private transient Runnable onFinished;

    public VideoView() {
        setOpaque(false);
        timer = new Timer(10, e -> {
            double t = (System.nanoTime() - animationStart) / 1e9 / ANIMATION_SECONDS;
            if (t >= 1) {
                rotation = animationTo;
                ((Timer) e.getSource()).stop();
            } else {
                double eased = 1 - Math.pow(1 - t, 3); // ease-out cubic
                rotation = animationFrom + (animationTo - animationFrom) * eased;
            }
            repaint();
        });
    }

    public synchronized void setImage(BufferedImage image) {
        this.image = image;
        if (image != null && image.getHeight() > 0) {
            aspect = (double) image.getWidth() / image.getHeight();
        }
        repaint();
    }

    public synchronized BufferedImage image() {
        return image;
    }

    /** Called when playback into this view ends. */
    public void onPlaybackFinished(Runnable action) {
        this.onFinished = action;
    }

    @Override
    public void showFrames(BufferedImage[] frames, double time) {
        BufferedImage frame = frames[0];
        synchronized (this) {
            if (playbackBuffer == null || playbackBuffer.getWidth() != frame.getWidth()
                    || playbackBuffer.getHeight() != frame.getHeight()) {
                playbackBuffer = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            }
            playbackBuffer.setData(frame.getRaster());
            image = playbackBuffer;
            aspect = (double) frame.getWidth() / frame.getHeight();
        }
        repaint();
    }

    @Override
    public void playbackFinished() {
        Runnable r = onFinished;
        if (r != null) {
            javax.swing.SwingUtilities.invokeLater(r);
        }
    }

    /** Text shown when there is no image (e.g. "Loading…"). */
    public void setMessage(String message) {
        this.message = message;
        repaint();
    }

    /** Shows the frame rotated clockwise by {@code degrees}, animating from the current angle. */
    public void rotateTo(double degrees, boolean animate) {
        if (!animate) {
            timer.stop();
            rotation = degrees;
            animationTo = degrees;
            repaint();
            return;
        }
        animationFrom = rotation;
        animationTo = degrees;
        animationStart = System.nanoTime();
        timer.restart();
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        int w = getWidth() > 0 ? getWidth() : 480;
        return new Dimension(w, (int) Math.round(Math.min(w / Math.max(aspect, 0.5), 420)));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        int w = getWidth();
        int h = getHeight();
        Shape clip = new RoundRectangle2D.Float(0, 0, w, h, 14, 14);
        g2.setColor(Theme.isDark() ? new Color(0x111214) : new Color(0x1C1D21));
        g2.fill(clip);
        g2.clip(clip);
        synchronized (this) {
        if (image != null) {
            double radians = Math.toRadians(rotation);
            double cos = Math.abs(Math.cos(radians));
            double sin = Math.abs(Math.sin(radians));
            double iw = image.getWidth();
            double ih = image.getHeight();
            double boundsW = iw * cos + ih * sin;
            double boundsH = iw * sin + ih * cos;
            double scale = Math.min(w / boundsW, h / boundsH);
            AffineTransform at = new AffineTransform();
            at.translate(w / 2.0, h / 2.0);
            at.rotate(radians);
            at.scale(scale, scale);
            at.translate(-iw / 2.0, -ih / 2.0);
            g2.drawImage(image, at, null);
        } else if (message != null) {
            g2.setColor(new Color(0xB8BAC2));
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(message, (w - fm.stringWidth(message)) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
        }
        }
        g2.dispose();
    }
}
