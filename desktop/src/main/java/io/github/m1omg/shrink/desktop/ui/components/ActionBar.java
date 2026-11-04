package io.github.m1omg.shrink.desktop.ui.components;

import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The bar at the bottom of a tool: where the result is saved, the estimated
 * size, and the main button ("Compress", "Trim", ...).
 */
public final class ActionBar extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JLabel saveTo = new JLabel();
    private final JLabel path = new JLabel();
    private final JButton change = new JButton();
    private final JLabel estimate = new JLabel();
    private final JButton primary = new JButton();
    private final JPanel destination;

    public ActionBar(Runnable changeOutput, Runnable primaryAction) {
        super(new BorderLayout(16, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));

        saveTo.setText(I18n.tr("output.saveTo"));
        saveTo.setForeground(Theme.secondaryText());
        saveTo.putClientProperty("FlatLaf.styleClass", "small");
        path.setIcon(Theme.icon("folder", 14));
        path.setIconTextGap(6);
        change.setText(I18n.tr("output.change"));
        change.putClientProperty("JButton.buttonType", "borderless");
        change.setForeground(Theme.accent());
        change.addActionListener(e -> changeOutput.run());

        destination = new JPanel();
        destination.setOpaque(false);
        destination.setLayout(new BoxLayout(destination, BoxLayout.Y_AXIS));
        JPanel pathRow = new JPanel();
        pathRow.setOpaque(false);
        pathRow.setLayout(new BoxLayout(pathRow, BoxLayout.X_AXIS));
        pathRow.add(path);
        pathRow.add(Box.createHorizontalStrut(4));
        pathRow.add(change);
        saveTo.setAlignmentX(LEFT_ALIGNMENT);
        pathRow.setAlignmentX(LEFT_ALIGNMENT);
        destination.add(saveTo);
        destination.add(pathRow);

        estimate.putClientProperty("FlatLaf.style", "font: bold");
        primary.addActionListener(e -> primaryAction.run());
        DropZone.styleDefault(primary);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        right.add(estimate);
        right.add(Box.createHorizontalStrut(14));
        right.add(primary);

        add(destination, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
    }

    public void setOutput(Path output) {
        if (output == null) {
            path.setText(I18n.tr("settings.output.ask"));
            path.setToolTipText(null);
            return;
        }
        path.setText(abbreviate(output));
        path.setToolTipText(output.toString());
    }

    public void setDestinationVisible(boolean visible) {
        destination.setVisible(visible);
    }

    public void setEstimate(String text) {
        estimate.setText(text == null ? "" : text);
    }

    public void setAction(String text, boolean enabled) {
        primary.setText(text);
        primary.setEnabled(enabled);
        primary.setVisible(text != null);
    }

    public JButton primaryButton() {
        return primary;
    }

    /** Shortens the home folder to "~" and long folders in the middle. */
    static String abbreviate(Path p) {
        String home = System.getProperty("user.home", "");
        String s = p.toString();
        if (!home.isEmpty() && s.startsWith(home)) {
            s = "~" + s.substring(home.length());
        }
        if (s.length() > 64) {
            Path name = p.getFileName();
            String tail = name == null ? "" : name.toString();
            String sep = Paths.get("").getFileSystem().getSeparator();
            s = s.substring(0, Math.max(12, 60 - tail.length())) + "…" + sep + tail;
        }
        return s;
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(Theme.card());
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Theme.divider());
        g.fillRect(0, 0, getWidth(), 1);
    }
}
