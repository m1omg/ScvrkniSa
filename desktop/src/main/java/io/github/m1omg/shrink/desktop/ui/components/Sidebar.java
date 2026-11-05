package io.github.m1omg.shrink.desktop.ui.components;

import io.github.m1omg.shrink.core.AppInfo;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.desktop.ui.theme.Theme;
import io.github.m1omg.shrink.desktop.ui.tools.Tool;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.AbstractAction;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/** The navigation column: tools, history and settings. Collapses to icons when the window is narrow. */
public final class Sidebar extends JPanel {
    private static final long serialVersionUID = 1L;

    /** What can be selected in the sidebar. */
    public enum Page { TOOL, HISTORY }

    private final Map<Tool, Item> toolItems = new EnumMap<>(Tool.class);
    private final Item historyItem;
    private final Item settingsItem;
    private final JLabel appName = new JLabel(AppInfo.NAME);
    private final JLabel toolsHeader = new JLabel();
    private boolean compact;

    public Sidebar(int topInset, Consumer<Tool> onTool, Runnable onHistory, Runnable onSettings) {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(topInset, 10, 12, 10));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        JPanel brand = new JPanel(new BorderLayout(10, 0));
        brand.setOpaque(false);
        brand.setBorder(BorderFactory.createEmptyBorder(0, 6, 14, 0));
        brand.add(new JLabel(Theme.logo(30)), BorderLayout.WEST);
        appName.putClientProperty("FlatLaf.style", "font: bold +4");
        brand.add(appName, BorderLayout.CENTER);
        brand.setAlignmentX(LEFT_ALIGNMENT);
        brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        top.add(brand);

        toolsHeader.setText(I18n.tr("nav.tools").toUpperCase(I18n.current().locale()));
        toolsHeader.putClientProperty("FlatLaf.styleClass", "mini");
        toolsHeader.setForeground(Theme.secondaryText());
        toolsHeader.setBorder(BorderFactory.createEmptyBorder(4, 10, 6, 0));
        toolsHeader.setAlignmentX(LEFT_ALIGNMENT);
        top.add(toolsHeader);

        for (Tool tool : Tool.values()) {
            Item item = new Item(tool.icon(), I18n.tr(tool.titleKey()), () -> onTool.accept(tool));
            toolItems.put(tool, item);
            top.add(item);
        }
        top.add(Box.createVerticalStrut(10));
        JComponent divider = new JComponent() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.divider());
                g.fillRect(8, getHeight() / 2, getWidth() - 16, 1);
            }
        };
        divider.setPreferredSize(new Dimension(10, 11));
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 11));
        divider.setAlignmentX(LEFT_ALIGNMENT);
        top.add(divider);
        top.add(Box.createVerticalStrut(6));
        historyItem = new Item("history", I18n.tr("nav.history"), onHistory);
        top.add(historyItem);

        settingsItem = new Item("settings", I18n.tr("nav.settings"), onSettings);
        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.add(settingsItem);

        add(top, BorderLayout.NORTH);
        add(bottom, BorderLayout.SOUTH);
        setCompact(false);
    }

    public void select(Page page, Tool tool) {
        for (Map.Entry<Tool, Item> e : toolItems.entrySet()) {
            e.getValue().setSelected(page == Page.TOOL && e.getKey() == tool);
        }
        historyItem.setSelected(page == Page.HISTORY);
    }

    public void setToolEnabled(Tool tool, boolean enabled) {
        toolItems.get(tool).setEnabled(enabled);
    }

    /** Icons only (for narrow windows). */
    public void setCompact(boolean compact) {
        this.compact = compact;
        appName.setVisible(!compact);
        toolsHeader.setVisible(!compact);
        for (Item item : toolItems.values()) {
            item.setCompact(compact);
        }
        historyItem.setCompact(compact);
        settingsItem.setCompact(compact);
        setPreferredSize(new Dimension(compact ? 68 : 246, 10));
        revalidate();
    }

    public boolean isCompact() {
        return compact;
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(Theme.sidebarBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Theme.divider());
        g.fillRect(getWidth() - 1, 0, 1, getHeight());
    }

    /** One row of the sidebar. */
    private static final class Item extends JComponent {
        private static final long serialVersionUID = 1L;

        private final String iconName;
        private final String text;
        private final Runnable action;
        private boolean selected;
        private boolean hover;
        private boolean focused;
        private boolean compactMode;

        Item(String iconName, String text, Runnable action) {
            this.iconName = iconName;
            this.text = text;
            this.action = action;
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(text);
            getAccessibleContext().setAccessibleName(text);
            addMouseListener(new MouseAdapter() {
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
                        action.run();
                    }
                }
            });
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
            getInputMap().put(KeyStroke.getKeyStroke("SPACE"), "go");
            getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "go");
            getActionMap().put("go", new AbstractAction() {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent e) {
                    if (isEnabled()) {
                        action.run();
                    }
                }
            });
        }

        @Override
        public javax.accessibility.AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleJComponent() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public javax.accessibility.AccessibleRole getAccessibleRole() {
                        return javax.accessibility.AccessibleRole.PUSH_BUTTON;
                    }
                };
            }
            return accessibleContext;
        }

        void setSelected(boolean selected) {
            this.selected = selected;
            repaint();
        }

        void setCompact(boolean compact) {
            this.compactMode = compact;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(compactMode ? 44 : 200, 34);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, 34);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (selected || hover) {
                Color bg = selected ? Theme.sidebarSelection() : Theme.withAlpha(Theme.sidebarSelection(), 120);
                g2.setColor(bg);
                g2.fillRoundRect(0, 1, getWidth(), getHeight() - 2, 10, 10);
            }
            if (focused) {
                // Keyboard focus is a ring, so it is never mistaken for the selection.
                g2.setColor(Theme.withAlpha(Theme.accent(), 170));
                g2.setStroke(new java.awt.BasicStroke(1.5f));
                g2.drawRoundRect(1, 2, getWidth() - 3, getHeight() - 5, 10, 10);
            }
            Color fg = !isEnabled() ? Theme.blend(Theme.foreground(), Theme.sidebarBackground(), 0.35)
                    : Theme.foreground();
            Color iconColor = !isEnabled() ? fg : (selected ? Theme.accent() : Theme.foreground());
            Icon icon = Theme.icon(iconName, 18, iconColor);
            int iconX = compactMode ? (getWidth() - 18) / 2 : 10;
            icon.paintIcon(this, g2, iconX, (getHeight() - 18) / 2);
            if (!compactMode) {
                g2.setFont(getFont().deriveFont(selected ? java.awt.Font.BOLD : java.awt.Font.PLAIN));
                g2.setColor(fg);
                FontMetrics fm = g2.getFontMetrics();
                String label = text;
                int available = getWidth() - 40;
                while (fm.stringWidth(label) > available && label.length() > 2) {
                    label = label.substring(0, label.length() - 2) + "…";
                }
                g2.drawString(label, 38, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            }
            g2.dispose();
        }

        @Override
        public void updateUI() {
            super.updateUI();
            setFont(javax.swing.UIManager.getFont("Label.font"));
        }
    }
}
