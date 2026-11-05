package io.github.m1omg.shrink.desktop.ui.components;

import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * A trimming timeline: a strip of thumbnails with two handles for the start and
 * end of the selection, and a playhead. Arrow keys move the active handle by a
 * tenth of a second (a whole second with Shift).
 */
public final class Timeline extends JComponent {
    private static final long serialVersionUID = 1L;

    /** Which part is being changed. */
    public enum Part { START, END, PLAYHEAD }

    /** Receives changes made by the user. */
    public interface Listener {
        /**
         * @param part      what changed
         * @param time      the new time of that part, in seconds
         * @param adjusting true while the mouse button is still held
         */
        void changed(Part part, double time, boolean adjusting);
    }

    private static final int HANDLE = 12;
    private static final double MIN_LENGTH = 0.1;

    private double duration = 1;
    private double start;
    private double end = 1;
    private double playhead = -1;
    private BufferedImage[] strip;
    private Part dragging;
    private Part active = Part.START;
    private boolean focused;
    private transient Listener listener;

    public Timeline() {
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                double t = timeAt(e.getX());
                int startX = xOf(start);
                int endX = xOf(end);
                if (Math.abs(e.getX() - startX) <= HANDLE && Math.abs(e.getX() - startX) <= Math.abs(e.getX() - endX)) {
                    dragging = Part.START;
                } else if (Math.abs(e.getX() - endX) <= HANDLE) {
                    dragging = Part.END;
                } else {
                    dragging = Part.PLAYHEAD;
                }
                if (dragging != Part.PLAYHEAD) {
                    active = dragging;
                }
                move(dragging, t, true);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragging != null) {
                    move(dragging, timeAt(e.getX()), true);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragging != null) {
                    Part p = dragging;
                    dragging = null;
                    move(p, timeAt(e.getX()), false);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                boolean nearHandle = Math.abs(e.getX() - xOf(start)) <= HANDLE
                        || Math.abs(e.getX() - xOf(end)) <= HANDLE;
                setCursor(Cursor.getPredefinedCursor(nearHandle ? Cursor.E_RESIZE_CURSOR : Cursor.HAND_CURSOR));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                focused = true;
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                focused = false;
                repaint();
            }
        });
        bindKey(KeyEvent.VK_LEFT, 0, -0.1);
        bindKey(KeyEvent.VK_RIGHT, 0, 0.1);
        bindKey(KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK, -1);
        bindKey(KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK, 1);
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.ALT_DOWN_MASK), "switch");
        getActionMap().put("switch", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                active = active == Part.START ? Part.END : Part.START;
                repaint();
            }
        });
    }

    private void bindKey(int key, int modifiers, double delta) {
        String name = "move" + key + "_" + modifiers;
        getInputMap().put(KeyStroke.getKeyStroke(key, modifiers), name);
        getActionMap().put(name, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                double value = active == Part.START ? start : end;
                move(active, value + delta, false);
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setDuration(double duration) {
        this.duration = Math.max(0.1, duration);
        start = 0;
        end = this.duration;
        playhead = -1;
        repaint();
    }

    public void setRange(double newStart, double newEnd) {
        start = clamp(newStart, 0, duration - MIN_LENGTH);
        end = clamp(newEnd, start + MIN_LENGTH, duration);
        repaint();
    }

    public void setPlayhead(double time) {
        playhead = time;
        repaint();
    }

    public void setStrip(BufferedImage[] images) {
        this.strip = images;
        repaint();
    }

    public double start() {
        return start;
    }

    public double end() {
        return end;
    }

    public double playhead() {
        return playhead;
    }

    private void move(Part part, double time, boolean adjusting) {
        switch (part) {
            case START:
                start = clamp(time, 0, end - MIN_LENGTH);
                time = start;
                break;
            case END:
                end = clamp(time, start + MIN_LENGTH, duration);
                time = end;
                break;
            default:
                playhead = clamp(time, 0, duration);
                time = playhead;
                break;
        }
        repaint();
        if (listener != null) {
            listener.changed(part, time, adjusting);
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private int trackLeft() {
        return HANDLE;
    }

    private int trackWidth() {
        return Math.max(1, getWidth() - 2 * HANDLE);
    }

    private int xOf(double time) {
        return trackLeft() + (int) Math.round(time / duration * trackWidth());
    }

    private double timeAt(int x) {
        return clamp((x - trackLeft()) / (double) trackWidth() * duration, 0, duration);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(400, 64);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int top = 6;
        int h = getHeight() - 12;
        int left = trackLeft();
        int width = trackWidth();
        Shape track = new RoundRectangle2D.Float(left, top, width, h, 10, 10);
        g2.setColor(Theme.isDark() ? new Color(0x16171A) : new Color(0x2B2C31));
        g2.fill(track);

        // Thumbnail strip, each image filling an equal slot (cropped to fill).
        Shape oldClip = g2.getClip();
        g2.clip(track);
        if (strip != null && strip.length > 0) {
            double slot = width / (double) strip.length;
            for (int i = 0; i < strip.length; i++) {
                BufferedImage img = strip[i];
                if (img == null) {
                    continue;
                }
                int x = left + (int) Math.floor(i * slot);
                int w = (int) Math.ceil(slot) + 1;
                double scale = Math.max((double) w / img.getWidth(), (double) h / img.getHeight());
                int dw = (int) Math.round(img.getWidth() * scale);
                int dh = (int) Math.round(img.getHeight() * scale);
                Shape c = g2.getClip();
                g2.clipRect(x, top, w, h);
                g2.drawImage(img, x + (w - dw) / 2, top + (h - dh) / 2, dw, dh, null);
                g2.setClip(c);
            }
        }
        // Dim what is cut away.
        int sx = xOf(start);
        int ex = xOf(end);
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(left, top, sx - left, h);
        g2.fillRect(ex, top, left + width - ex, h);
        g2.setClip(oldClip);

        // Selection frame and handles.
        Color accent = Theme.accent();
        g2.setColor(accent);
        g2.setStroke(new BasicStroke(3f));
        g2.drawLine(sx, top + 1, ex, top + 1);
        g2.drawLine(sx, top + h - 2, ex, top + h - 2);
        paintHandle(g2, sx - HANDLE, top, h, accent, focused && active == Part.START);
        paintHandle(g2, ex, top, h, accent, focused && active == Part.END);

        if (playhead >= 0) {
            int px = xOf(playhead);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(px, top - 3, px, top + h + 3);
            g2.fillOval(px - 4, top - 6, 8, 8);
        }
        g2.dispose();
    }

    private static void paintHandle(Graphics2D g2, int x, int top, int h, Color accent, boolean active) {
        g2.setColor(active ? accent.brighter() : accent);
        g2.fill(new RoundRectangle2D.Float(x, top, HANDLE, h, 6, 6));
        g2.setColor(new Color(255, 255, 255, 220));
        g2.setStroke(new BasicStroke(1.5f));
        int cx = x + HANDLE / 2;
        g2.drawLine(cx - 2, top + h / 2 - 6, cx - 2, top + h / 2 + 6);
        g2.drawLine(cx + 2, top + h / 2 - 6, cx + 2, top + h / 2 + 6);
    }
}
