package io.github.m1omg.shrink.desktop.ui.components;

import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Font;

/** A selectable card with a title, an optional badge, a description and a detail line (e.g. a size estimate). */
public final class OptionCard extends SelectableCard {
    private static final long serialVersionUID = 1L;

    private final JLabel title = new JLabel();
    private final JLabel badge = new JLabel();
    private final WrapLabel description = new WrapLabel("");
    private final JLabel detail = new JLabel();

    public OptionCard(Icon icon, String titleText, String descriptionText) {
        super(new StackLayout(4));
        arc(14);
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        title.setText(titleText);
        title.setIcon(icon);
        title.setIconTextGap(8);
        title.putClientProperty("FlatLaf.style", "font: bold +1");
        badge.putClientProperty("FlatLaf.styleClass", "mini");
        badge.setForeground(Theme.accent());
        JPanel top = Ui.clear(new BorderLayout(8, 0));
        top.add(title, BorderLayout.CENTER);
        top.add(badge, BorderLayout.EAST);
        add(top);
        description.color(Theme.secondaryText()).font(Font.PLAIN, -1);
        description.setText(descriptionText == null ? "" : descriptionText);
        description.setVisible(descriptionText != null && !descriptionText.isEmpty());
        add(description);
        detail.putClientProperty("FlatLaf.style", "font: bold");
        detail.setVisible(false);
        add(detail);
        forwardClicks(title);
        forwardClicks(description);
        forwardClicks(detail);
        getAccessibleContext().setAccessibleName(titleText);
    }

    public void setBadge(String text) {
        badge.setText(text == null ? "" : text);
    }

    public void setDetail(String text) {
        detail.setText(text == null ? "" : text);
        detail.setVisible(text != null && !text.isEmpty());
    }

    public void setDescription(String text) {
        description.setText(text == null ? "" : text);
        description.setVisible(text != null && !text.isEmpty());
    }

    public void setDetailColor(java.awt.Color c) {
        detail.setForeground(c);
    }
}
