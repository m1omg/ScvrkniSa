package io.github.m1omg.shrink.desktop.ui.components;

import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.function.Supplier;

/** A coloured box with an icon and a message: information, warning or error. */
public final class Banner extends RoundedPanel {
    private static final long serialVersionUID = 1L;

    /** Kind of message. */
    public enum Kind { INFO, WARNING, ERROR, SUCCESS }

    private final JLabel icon = new JLabel();
    private final WrapLabel text = new WrapLabel("");
    private Kind kind = Kind.INFO;

    public Banner() {
        super(new BorderLayout(10, 0));
        arc(12);
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        icon.setVerticalAlignment(JLabel.TOP);
        add(icon, BorderLayout.WEST);
        add(text, BorderLayout.CENTER);
        fill(this::background);
        border(() -> Theme.withAlpha(accentOf(kind), 70));
        setKind(Kind.INFO);
    }

    public Banner(Kind kind, String message) {
        this();
        setKind(kind);
        setText(message);
    }

    private Color background() {
        switch (kind) {
            case WARNING:
                return Theme.warningBackground();
            case ERROR:
                return Theme.errorBackground();
            case SUCCESS:
                return Theme.blend(Theme.success(), Theme.card(), Theme.isDark() ? 0.16 : 0.09);
            default:
                return Theme.infoBackground();
        }
    }

    private static Color accentOf(Kind kind) {
        switch (kind) {
            case WARNING:
                return Theme.warning();
            case ERROR:
                return Theme.error();
            case SUCCESS:
                return Theme.success();
            default:
                return Theme.accent();
        }
    }

    public void setKind(Kind kind) {
        this.kind = kind;
        String name;
        switch (kind) {
            case WARNING:
                name = "alert";
                break;
            case ERROR:
                name = "error";
                break;
            case SUCCESS:
                name = "check_circle";
                break;
            default:
                name = "info";
                break;
        }
        Supplier<Color> color = () -> accentOf(this.kind);
        icon.setIcon(new LazyColorIcon(name, 18, color));
        repaint();
    }

    public void setText(String message) {
        text.setText(message);
        revalidate();
    }
}
