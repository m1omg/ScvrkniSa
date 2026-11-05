package io.github.m1omg.shrink.desktop.ui.player;

import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Before/after comparison: the original on the left of a draggable divider,
 * the compressed version on the right. Supports zoom (fit, 100 %, 200 %) with
 * panning by dragging, to inspect compression artefacts closely.
 */
public final class CompareView extends JComponent implements FrameSurface {
    private static final long serialVersionUID = 1L;

    private BufferedImage original;
    private BufferedImage compressed;
    private double divider = 0.5;
    private double zoom; // 0 = fit
    private double panX;
    private double panY;
    private boolean draggingDivider;
    private int lastX;
    private int lastY;
    private int sourceWidth;
    private int sourceHeight;
    private transient Runnable onFinished;
    private String message;
    private volatile double lastTime;

    public CompareView() {
        setOpaque(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int dx = (int) Math.round(divider * getWidth());
                draggingDivider = zoom == 0 || Math.abs(e.getX() - dx) < 14;
                lastX = e.getX();
                lastY = e.getY();
                if (draggingDivider) {
                    setDivider(e.getX());
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggingDivider) {
                    setDivider(e.getX());
                } else {
                    panX += e.getX() - lastX;
                    panY += e.getY() - lastY;
                    lastX = e.getX();
                    lastY = e.getY();
                    repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int dx = (int) Math.round(divider * getWidth());
                boolean near = Math.abs(e.getX() - dx) < 14 || zoom == 0;
                setCursor(Cursor.getPredefinedCursor(near ? Cursor.E_RESIZE_CURSOR : Cursor.MOVE_CURSOR));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    private void setDivider(int x) {
        divider = Math.max(0.02, Math.min(0.98, x / (double) Math.max(1, getWidth())));
        repaint();
    }

    /** Size of the original video, so 100 % zoom means one video pixel per screen pixel. */
    public void setSourceSize(int width, int height) {
        sourceWidth = width;
        sourceHeight = height;
    }

    /** 0 to fit the view, otherwise a magnification relative to the original video size. */
    public void setZoom(double zoom) {
        this.zoom = zoom;
        panX = 0;
        panY = 0;
        repaint();
    }

    public void setMessage(String message) {
        this.message = message;
        repaint();
    }

    public void onPlaybackFinished(Runnable action) {
        onFinished = action;
    }

    /** Shows still frames (e.g. full-resolution frames while paused). */
    public synchronized void setStills(BufferedImage originalFrame, BufferedImage compressedFrame) {
        original = originalFrame;
        compressed = compressedFrame;
        message = null;
        repaint();
    }

    /** Time (seconds from the excerpt start) of the frames shown last during playback. */
    public double lastTime() {
        return lastTime;
    }

    @Override
    public void showFrames(BufferedImage[] frames, double time) {
        lastTime = time;
        synchronized (this) {
            original = copyInto(original, frames[0]);
            compressed = copyInto(compressed, frames[1]);
            message = null;
        }
        repaint();
    }

    private static BufferedImage copyInto(BufferedImage target, BufferedImage frame) {
        BufferedImage t = target;
        if (t == null || t.getWidth() != frame.getWidth() || t.getHeight() != frame.getHeight()
                || t.getType() != BufferedImage.TYPE_3BYTE_BGR) {
            t = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        }
        t.setData(frame.getRaster());
        return t;
    }

    @Override
    public void playbackFinished() {
        Runnable r = onFinished;
        if (r != null) {
            SwingUtilities.invokeLater(r);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g2.setColor(new Color(0x0E0F11));
        g2.fillRect(0, 0, w, h);
        BufferedImage a;
        BufferedImage b;
        synchronized (this) {
            a = original;
            b = compressed;
            if (a != null && b != null) {
                Rectangle r = imageRect(a, w, h);
                int split = (int) Math.round(divider * w);
                Object interpolation = zoom >= 1.0 ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                        : RenderingHints.VALUE_INTERPOLATION_BILINEAR;
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
                Graphics2D left = (Graphics2D) g2.create();
                left.clipRect(0, 0, split, h);
                left.drawImage(a, r.x, r.y, r.width, r.height, null);
                left.dispose();
                Graphics2D right = (Graphics2D) g2.create();
                right.clipRect(split, 0, w - split, h);
                right.drawImage(b, r.x, r.y, r.width, r.height, null);
                right.dispose();
            }
        }
        if (a != null && b != null) {
            int split = (int) Math.round(divider * w);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(split, 0, split, h);
            int knob = 30;
            g2.setColor(new Color(255, 255, 255, 235));
            g2.fillOval(split - knob / 2, h / 2 - knob / 2, knob, knob);
            g2.setColor(new Color(0x30333A));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cy = h / 2;
            g2.drawPolyline(new int[] {split - 4, split - 9, split - 4}, new int[] {cy - 5, cy, cy + 5}, 3);
            g2.drawPolyline(new int[] {split + 4, split + 9, split + 4}, new int[] {cy - 5, cy, cy + 5}, 3);
            pill(g2, I18n.tr("preview.original"), 12, 12, false);
            pill(g2, I18n.tr("preview.compressed"), w - 12, 12, true);
        } else if (message != null) {
            g2.setColor(new Color(0xC9CBD2));
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(message, (w - fm.stringWidth(message)) / 2, h / 2);
        }
        g2.dispose();
    }

    private Rectangle imageRect(BufferedImage img, int w, int h) {
        double iw = sourceWidth > 0 ? sourceWidth : img.getWidth();
        double ih = sourceHeight > 0 ? sourceHeight : img.getHeight();
        double scale = zoom == 0 ? Math.min(w / iw, h / ih) : zoom;
        int dw = (int) Math.round(iw * scale);
        int dh = (int) Math.round(ih * scale);
        // Keep the panned image covering the view where possible.
        double maxPanX = Math.max(0, (dw - w) / 2.0);
        double maxPanY = Math.max(0, (dh - h) / 2.0);
        panX = Math.max(-maxPanX, Math.min(maxPanX, panX));
        panY = Math.max(-maxPanY, Math.min(maxPanY, panY));
        int x = (int) Math.round((w - dw) / 2.0 + panX);
        int y = (int) Math.round((h - dh) / 2.0 + panY);
        return new Rectangle(x, y, dw, dh);
    }

    private static void pill(Graphics2D g2, String text, int x, int y, boolean alignRight) {
        g2.setFont(g2.getFont().deriveFont(java.awt.Font.BOLD));
        FontMetrics fm = g2.getFontMetrics();
        int pw = fm.stringWidth(text) + 20;
        int ph = fm.getHeight() + 8;
        int px = alignRight ? x - pw : x;
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fill(new RoundRectangle2D.Float(px, y, pw, ph, ph, ph));
        g2.setColor(Color.WHITE);
        g2.drawString(text, px + 10, y + 4 + fm.getAscent());
    }

    /** Accent colour for the scrubber in the dialog. */
    static Color accent() {
        return Theme.accent();
    }
}
