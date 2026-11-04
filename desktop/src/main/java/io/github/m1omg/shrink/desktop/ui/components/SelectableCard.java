package io.github.m1omg.shrink.desktop.ui.components;

import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * A card that works like a radio button: click (or press Space) to select it.
 * The selected card gets an accent-coloured border and a faint accent tint.
 */
public class SelectableCard extends RoundedPanel {
    private static final long serialVersionUID = 1L;

    private boolean selected;
    private boolean hover;
    private boolean focused;
    private final List<Runnable> selectionListeners = new ArrayList<>();

    public SelectableCard(LayoutManager layout) {
        super(layout);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (isEnabled()) {
                    requestFocusInWindow();
                    select();
                }
            }
        };
        addMouseListener(mouse);
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
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("SPACE"), "select");
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "select");
        getActionMap().put("select", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                if (isEnabled()) {
                    select();
                }
            }
        });
        fill(() -> selected ? Theme.cardSelected() : Theme.card());
    }

    /** Child components also select the card when clicked. */
    public void forwardClicks(JComponent child) {
        child.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (isEnabled()) {
                    select();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        });
    }

    public void select() {
        if (!selected) {
            setSelected(true);
            for (Runnable r : selectionListeners) {
                r.run();
            }
        }
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    public boolean isSelected() {
        return selected;
    }

    public void onSelected(Runnable listener) {
        selectionListeners.add(listener);
    }

    @Override
    protected Color borderColor() {
        if (!isEnabled()) {
            return Theme.cardBorder();
        }
        if (selected) {
            return Theme.accent();
        }
        return hover || focused ? Theme.cardHover() : Theme.cardBorder();
    }

    @Override
    protected float currentBorderWidth() {
        return selected ? 2f : 1f;
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        if (!isEnabled()) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.withAlpha(Theme.background(), 120));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            g2.dispose();
        }
    }

    /** Makes a set of cards mutually exclusive. */
    public static void group(List<? extends SelectableCard> cards) {
        for (SelectableCard card : cards) {
            card.onSelected(() -> {
                for (SelectableCard other : cards) {
                    if (other != card) {
                        other.setSelected(false);
                    }
                }
            });
        }
    }
}
