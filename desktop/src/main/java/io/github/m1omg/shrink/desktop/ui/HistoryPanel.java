package io.github.m1omg.shrink.desktop.ui;

import io.github.m1omg.shrink.core.i18n.Formats;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.JobKind;
import io.github.m1omg.shrink.core.store.HistoryEntry;
import io.github.m1omg.shrink.core.store.HistoryStore;
import io.github.m1omg.shrink.desktop.AppContext;
import io.github.m1omg.shrink.desktop.ui.components.LazyColorIcon;
import io.github.m1omg.shrink.desktop.ui.components.RoundedPanel;
import io.github.m1omg.shrink.desktop.ui.components.ScrollablePanel;
import io.github.m1omg.shrink.desktop.ui.components.StackLayout;
import io.github.m1omg.shrink.desktop.ui.components.Ui;
import io.github.m1omg.shrink.desktop.ui.theme.Theme;
import io.github.m1omg.shrink.desktop.ui.tools.Tool;
import io.github.m1omg.shrink.desktop.util.DesktopActions;
import io.github.m1omg.shrink.desktop.util.Platform;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

/** The list of processed files, newest first. */
public final class HistoryPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final AppContext app;
    private final ScrollablePanel list = new ScrollablePanel(new StackLayout(10));
    private final JLabel count = new JLabel();
    private final JTextField search = new JTextField(16);
    private final JButton clear = new JButton(I18n.tr("history.clear"));
    private final JPanel empty;
    private final JScrollPane scroll;
    private final HistoryStore.Listener listener = () -> SwingUtilities.invokeLater(this::refresh);

    public HistoryPanel(AppContext app) {
        super(new BorderLayout());
        this.app = app;
        setOpaque(false);

        JPanel header = Ui.clear(new BorderLayout(12, 0));
        header.setBorder(BorderFactory.createEmptyBorder(22, 28, 12, 28));
        JPanel titles = Ui.clear(new BorderLayout(0, 4));
        titles.add(Ui.title(I18n.tr("history.title")), BorderLayout.NORTH);
        count.setForeground(Theme.secondaryText());
        titles.add(count, BorderLayout.CENTER);
        header.add(titles, BorderLayout.CENTER);
        search.putClientProperty("JTextField.placeholderText", I18n.tr("history.search"));
        search.putClientProperty("JTextField.leadingIcon", Theme.icon("search", 14));
        search.putClientProperty("JTextField.showClearButton", true);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refresh();
            }
        });
        clear.setIcon(Theme.icon("trash", 14));
        clear.addActionListener(e -> {
            int answer = JOptionPane.showConfirmDialog(this, I18n.tr("history.clearConfirm"), I18n.tr("history.clear"),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer == JOptionPane.OK_OPTION) {
                app.history().clear();
            }
        });
        JPanel actions = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(search);
        actions.add(clear);
        JPanel actionsWrap = Ui.clear(new GridBagLayout());
        actionsWrap.add(actions);
        header.add(actionsWrap, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        list.setOpaque(false);
        list.setBorder(BorderFactory.createEmptyBorder(4, 28, 28, 28));
        scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24);

        empty = Ui.clear(new GridBagLayout());
        JPanel emptyColumn = Ui.clear(null);
        emptyColumn.setLayout(new BoxLayout(emptyColumn, BoxLayout.Y_AXIS));
        JLabel icon = new JLabel(new LazyColorIcon("history", 48, Theme::secondaryText));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel title = new JLabel(I18n.tr("history.empty.title"));
        title.putClientProperty("FlatLaf.style", "font: bold +4");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel subtitle = Ui.secondary(I18n.tr("history.empty.subtitle"));
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        emptyColumn.add(icon);
        emptyColumn.add(Box.createVerticalStrut(12));
        emptyColumn.add(title);
        emptyColumn.add(Box.createVerticalStrut(4));
        emptyColumn.add(subtitle);
        empty.add(emptyColumn);

        app.history().addListener(listener);
        refresh();
    }

    public void dispose() {
        app.history().removeListener(listener);
    }

    public void refresh() {
        List<HistoryEntry> entries = app.history().entries();
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        list.removeAll();
        int shown = 0;
        for (HistoryEntry e : entries) {
            if (!query.isEmpty() && !(e.inputName() + " " + e.outputName()).toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            list.add(row(e));
            shown++;
        }
        count.setText(I18n.tr("history.count", entries.size()));
        clear.setEnabled(!entries.isEmpty());
        remove(scroll);
        remove(empty);
        add(entries.isEmpty() ? empty : scroll, BorderLayout.CENTER);
        if (shown == 0 && !entries.isEmpty()) {
            list.add(Ui.secondary("–"));
        }
        revalidate();
        repaint();
    }

    private JPanel row(HistoryEntry e) {
        Formats f = I18n.fmt();
        RoundedPanel card = new RoundedPanel(new BorderLayout(14, 0));
        card.arc(14);
        card.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        Tool tool = toolOf(e.kind());
        Color iconColor = e.status() == HistoryEntry.Status.DONE ? Theme.accent()
                : (e.status() == HistoryEntry.Status.FAILED ? Theme.error() : Theme.secondaryText());
        JLabel icon = new JLabel(new LazyColorIcon(tool.icon(), 24, () -> iconColor));
        icon.setVerticalAlignment(JLabel.TOP);
        card.add(icon, BorderLayout.WEST);

        JPanel text = Ui.clear(new StackLayout(3));
        String name = e.outputName().isEmpty() ? e.inputName() : e.inputName() + "  →  " + e.outputName();
        JLabel title = new JLabel(name);
        title.putClientProperty("FlatLaf.style", "font: bold");
        title.setToolTipText(e.outputPath().isEmpty() ? e.inputPath() : e.outputPath());
        text.add(title);
        StringBuilder line = new StringBuilder(I18n.tr(tool.titleKey()));
        if (e.status() == HistoryEntry.Status.DONE && e.outputBytes() > 0) {
            line.append("  ·  ").append(f.size(e.inputBytes())).append(" → ").append(f.size(e.outputBytes()));
            if (tool.kind() != JobKind.EXTRACT_AUDIO) {
                line.append(" (").append(f.reduction(e.reductionPercent())).append(')');
            }
        } else {
            line.append("  ·  ").append(I18n.tr("history.status." + e.status().name().toLowerCase(Locale.ROOT)));
        }
        line.append("  ·  ").append(f.dateTime(e.timestamp()));
        JLabel details = Ui.secondary(line.toString());
        text.add(details);
        if (!e.details().isEmpty()) {
            JLabel tech = Ui.secondary(e.details());
            tech.putClientProperty("FlatLaf.styleClass", "small");
            text.add(tech);
        }
        if (e.status() == HistoryEntry.Status.FAILED && !e.message().isEmpty()) {
            JLabel error = new JLabel(e.message());
            error.setForeground(Theme.error());
            error.putClientProperty("FlatLaf.styleClass", "small");
            text.add(error);
        }
        card.add(text, BorderLayout.CENTER);

        Path output = e.outputPath().isEmpty() ? null : Paths.get(e.outputPath());
        boolean exists = output != null && Files.exists(output);
        JPanel buttons = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        if (e.status() == HistoryEntry.Status.DONE) {
            JButton open = new JButton(I18n.tr("history.open"));
            open.setEnabled(exists);
            open.addActionListener(ev -> DesktopActions.open(output));
            JButton reveal = new JButton(Platform.isMac() ? I18n.tr("result.showInFinder")
                    : I18n.tr("history.showInFolder"));
            reveal.setEnabled(exists);
            reveal.addActionListener(ev -> DesktopActions.reveal(output));
            if (!exists) {
                open.setToolTipText(I18n.tr("history.missing"));
                reveal.setToolTipText(I18n.tr("history.missing"));
            }
            buttons.add(open);
            buttons.add(reveal);
        }
        JPanel buttonsWrap = Ui.clear(new GridBagLayout());
        buttonsWrap.add(buttons);
        card.add(buttonsWrap, BorderLayout.EAST);

        JPopupMenu menu = new JPopupMenu();
        JMenuItem remove = new JMenuItem(I18n.tr("history.remove"));
        remove.addActionListener(ev -> app.history().remove(e.id()));
        menu.add(remove);
        card.setComponentPopupMenu(menu);
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent ev) {
                if (ev.getClickCount() == 2 && exists) {
                    DesktopActions.open(output);
                }
            }
        });
        return card;
    }

    private static Tool toolOf(JobKind kind) {
        for (Tool t : Tool.values()) {
            if (t.kind() == kind) {
                return t;
            }
        }
        return Tool.COMPRESS;
    }
}
