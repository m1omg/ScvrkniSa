package io.github.m1omg.shrink.desktop.ui.components;

import javax.swing.JTextArea;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

/**
 * A label that wraps long text onto several lines and grows in height.
 * (A plain JLabel never wraps unless given HTML with a fixed width.)
 */
public class WrapLabel extends JTextArea {
    private static final long serialVersionUID = 1L;

    private Color customColor;
    private float sizeDelta;
    private int style = Font.PLAIN;

    public WrapLabel(String text) {
        super(text);
        setEditable(false);
        setFocusable(false);
        setLineWrap(true);
        setWrapStyleWord(true);
        setOpaque(false);
        setBorder(null);
        setHighlighter(null);
        updateUI();
    }

    /** Text colour; {@code null} uses the normal label colour. Re-read on theme changes. */
    public WrapLabel color(Color color) {
        customColor = color;
        updateLook();
        return this;
    }

    public WrapLabel font(int style, float sizeDelta) {
        this.style = style;
        this.sizeDelta = sizeDelta;
        updateLook();
        return this;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        updateLook();
    }

    private void updateLook() {
        Font base = UIManager.getFont("Label.font");
        if (base != null) {
            setFont(base.deriveFont(style, base.getSize2D() + sizeDelta));
        }
        Color c = customColor != null ? customColor : UIManager.getColor("Label.foreground");
        if (c != null) {
            setForeground(c);
            setDisabledTextColor(c);
        }
        setOpaque(false);
        setBorder(null);
    }

    @Override
    public Dimension getMinimumSize() {
        Dimension d = super.getMinimumSize();
        return new Dimension(40, d.height);
    }
}
