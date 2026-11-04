package io.github.m1omg.shrink.desktop.ui;

import io.github.m1omg.shrink.core.AppInfo;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.i18n.Language;
import io.github.m1omg.shrink.core.model.Container;
import io.github.m1omg.shrink.core.model.EncoderSpeed;
import io.github.m1omg.shrink.core.model.VideoCodec;
import io.github.m1omg.shrink.core.store.Settings;
import io.github.m1omg.shrink.desktop.AppContext;
import io.github.m1omg.shrink.desktop.engine.Engine;
import io.github.m1omg.shrink.desktop.engine.FfmpegLocator;
import io.github.m1omg.shrink.desktop.ui.components.Combo;
import io.github.m1omg.shrink.desktop.ui.components.ScrollablePanel;
import io.github.m1omg.shrink.desktop.ui.components.StackLayout;
import io.github.m1omg.shrink.desktop.ui.components.Ui;
import io.github.m1omg.shrink.desktop.ui.components.WrapLabel;
import io.github.m1omg.shrink.desktop.ui.theme.Theme;
import io.github.m1omg.shrink.desktop.util.DesktopActions;
import io.github.m1omg.shrink.desktop.util.FileDialogs;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Settings, applied immediately (like macOS and GNOME settings windows).
 * Language and appearance changes rebuild the main window.
 */
public final class SettingsDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    private final AppContext app;
    private final Settings settings;
    private final Runnable onLookChanged;
    private final Runnable onEncodingChanged;
    private final JLabel ffmpegStatus = new JLabel();
    private final Runnable engineListener = this::updateFfmpegStatus;

    public SettingsDialog(Window owner, AppContext app, Runnable onLookChanged, Runnable onEncodingChanged) {
        super(owner, I18n.tr("settings.title"), ModalityType.APPLICATION_MODAL);
        this.app = app;
        this.settings = app.settings();
        this.onLookChanged = onLookChanged;
        this.onEncodingChanged = onEncodingChanged;

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(I18n.tr("settings.section.general"), Theme.icon("settings", 16), scroll(general()));
        tabs.addTab(I18n.tr("settings.section.encoding"), Theme.icon("video", 16), scroll(encoding()));
        tabs.addTab(I18n.tr("settings.section.ffmpeg"), Theme.icon("tools", 16), scroll(ffmpeg()));
        tabs.addTab(I18n.tr("settings.section.about"), Theme.icon("info", 16), scroll(about()));

        JButton close = new JButton(I18n.tr("common.close"));
        close.addActionListener(e -> dispose());
        JButton reset = new JButton(I18n.tr("settings.reset"));
        reset.addActionListener(e -> {
            Settings defaults = new Settings();
            settings.fromMap(defaults.toMap());
            app.saveSettings();
            dispose();
            onLookChanged.run();
            onEncodingChanged.run();
            app.reloadEngine();
        });
        JPanel buttons = Ui.clear(new BorderLayout());
        buttons.setBorder(BorderFactory.createEmptyBorder(10, 14, 12, 14));
        buttons.add(reset, BorderLayout.WEST);
        buttons.add(close, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout());
        root.add(tabs, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(close);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        app.onEngineChanged(engineListener);
        setSize(new Dimension(620, 560));
        setMinimumSize(new Dimension(480, 420));
        setLocationRelativeTo(owner);
        updateFfmpegStatus();
    }

    private static JScrollPane scroll(JComponent content) {
        JScrollPane s = new JScrollPane(content);
        s.setBorder(null);
        s.getVerticalScrollBar().setUnitIncrement(24);
        return s;
    }

    private static ScrollablePanel page() {
        ScrollablePanel p = new ScrollablePanel(new StackLayout(18));
        p.setBorder(BorderFactory.createEmptyBorder(18, 20, 20, 20));
        return p;
    }

    private void saveAnd(Runnable after) {
        app.saveSettings();
        if (after != null) {
            after.run();
        }
    }

    // ------------------------------------------------------------------ General

    private JComponent general() {
        ScrollablePanel p = page();

        Combo<Language> language = new Combo<>(Arrays.asList(Language.values()), l -> l == null ? ""
                : l == Language.SYSTEM ? I18n.tr("settings.language.system") : l.nativeName());
        language.select(settings.getLanguage());
        language.addActionListener(e -> {
            if (language.selected() != settings.getLanguage()) {
                settings.setLanguage(language.selected());
                saveAnd(null);
                dispose();
                onLookChanged.run();
            }
        });
        p.add(Ui.field(I18n.tr("settings.language"), language));

        Combo<Settings.Theme> theme = new Combo<>(Arrays.asList(Settings.Theme.values()), t -> t == null ? ""
                : I18n.tr("settings.theme." + t.name().toLowerCase(java.util.Locale.ROOT)));
        theme.select(settings.getTheme());
        theme.addActionListener(e -> {
            if (theme.selected() != settings.getTheme()) {
                settings.setTheme(theme.selected());
                saveAnd(onLookChanged);
            }
        });
        p.add(Ui.field(I18n.tr("settings.theme"), theme));

        JPanel output = Ui.clear(new StackLayout(6));
        output.add(Ui.heading(I18n.tr("settings.output.where")));
        JRadioButton source = new JRadioButton(I18n.tr("settings.output.source"));
        JRadioButton custom = new JRadioButton(I18n.tr("settings.output.custom"));
        JRadioButton ask = new JRadioButton(I18n.tr("settings.output.ask"));
        ButtonGroup group = new ButtonGroup();
        group.add(source);
        group.add(custom);
        group.add(ask);
        switch (settings.getOutputLocation()) {
            case CUSTOM_FOLDER:
                custom.setSelected(true);
                break;
            case ASK:
                ask.setSelected(true);
                break;
            default:
                source.setSelected(true);
                break;
        }
        JLabel folder = Ui.secondary(settings.getCustomOutputFolder().isEmpty() ? I18n.tr("settings.output.none")
                : settings.getCustomOutputFolder());
        JButton chooseFolder = new JButton(I18n.tr("settings.output.choose"));
        chooseFolder.addActionListener(e -> {
            Path current = settings.getCustomOutputFolder().isEmpty() ? null
                    : Path.of(settings.getCustomOutputFolder());
            Path chosen = FileDialogs.chooseFolder(this, current);
            if (chosen != null) {
                settings.setCustomOutputFolder(chosen.toString());
                settings.setOutputLocation(Settings.OutputLocation.CUSTOM_FOLDER);
                custom.setSelected(true);
                folder.setText(chosen.toString());
                saveAnd(onEncodingChanged);
            }
        });
        source.addActionListener(e -> {
            settings.setOutputLocation(Settings.OutputLocation.NEXT_TO_SOURCE);
            saveAnd(onEncodingChanged);
        });
        custom.addActionListener(e -> {
            if (settings.getCustomOutputFolder().isEmpty()) {
                chooseFolder.doClick();
            }
            if (!settings.getCustomOutputFolder().isEmpty()) {
                settings.setOutputLocation(Settings.OutputLocation.CUSTOM_FOLDER);
                saveAnd(onEncodingChanged);
            } else {
                source.setSelected(true);
            }
        });
        ask.addActionListener(e -> {
            settings.setOutputLocation(Settings.OutputLocation.ASK);
            saveAnd(onEncodingChanged);
        });
        output.add(source);
        output.add(custom);
        JPanel folderRow = Ui.clear(new BorderLayout(8, 0));
        folderRow.setBorder(BorderFactory.createEmptyBorder(0, 26, 0, 0));
        folderRow.add(folder, BorderLayout.CENTER);
        folderRow.add(chooseFolder, BorderLayout.EAST);
        output.add(folderRow);
        output.add(ask);
        p.add(output);

        p.add(checkbox(I18n.tr("settings.revealWhenDone"), settings.isRevealWhenDone(), v -> {
            settings.setRevealWhenDone(v);
            saveAnd(null);
        }));

        JPanel history = Ui.clear(new StackLayout(8));
        history.add(Ui.heading(I18n.tr("settings.section.history")));
        history.add(checkbox(I18n.tr("settings.history.keep"), settings.isHistoryEnabled(), v -> {
            settings.setHistoryEnabled(v);
            saveAnd(null);
        }));
        JButton clear = new JButton(I18n.tr("settings.history.clear"));
        clear.addActionListener(e -> {
            int answer = JOptionPane.showConfirmDialog(this, I18n.tr("history.clearConfirm"),
                    I18n.tr("history.clear"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer == JOptionPane.OK_OPTION) {
                app.history().clear();
            }
        });
        history.add(Ui.row(0, clear));
        p.add(history);
        return p;
    }

    // ------------------------------------------------------------------ Encoding

    private JComponent encoding() {
        ScrollablePanel p = page();
        Engine engine = app.engine();
        List<VideoCodec> codecs = new ArrayList<>();
        for (VideoCodec c : VideoCodec.values()) {
            if (engine == null || engine.tools().canEncode(c)) {
                codecs.add(c);
            }
        }
        Combo<VideoCodec> codec = new Combo<>(codecs, c -> c == null ? "" : c.displayName() + "  –  "
                + I18n.tr("codec." + c.id() + ".hint"));
        codec.select(settings.getVideoCodec());
        codec.addActionListener(e -> {
            settings.setVideoCodec(codec.selected());
            saveAnd(onEncodingChanged);
        });
        p.add(Ui.field(I18n.tr("settings.codec"), codec));

        Combo<Container> container = new Combo<>(Arrays.asList(Container.values()),
                c -> c == null ? "" : c.displayName());
        container.select(settings.getContainer());
        container.addActionListener(e -> {
            settings.setContainer(container.selected());
            saveAnd(onEncodingChanged);
        });
        p.add(Ui.field(I18n.tr("settings.container"), container));

        Combo<EncoderSpeed> speed = new Combo<>(Arrays.asList(EncoderSpeed.values()),
                s -> s == null ? "" : I18n.tr(s.titleKey()));
        speed.select(settings.getSpeed());
        speed.addActionListener(e -> {
            settings.setSpeed(speed.selected());
            saveAnd(onEncodingChanged);
        });
        p.add(Ui.field(I18n.tr("settings.speed"), speed));

        p.add(checkbox(I18n.tr("settings.twoPass"), settings.isTwoPass(), v -> {
            settings.setTwoPass(v);
            saveAnd(onEncodingChanged);
        }));
        JCheckBox hardware = checkbox(I18n.tr("settings.hardware"), settings.isHardwareEncoding(), v -> {
            settings.setHardwareEncoding(v);
            saveAnd(onEncodingChanged);
        });
        hardware.setToolTipText(I18n.tr("advanced.hardware.hint"));
        p.add(hardware);
        p.add(checkbox(I18n.tr("settings.keepMetadata"), settings.isKeepMetadata(), v -> {
            settings.setKeepMetadata(v);
            saveAnd(onEncodingChanged);
        }));
        p.add(checkbox(I18n.tr("settings.showAdvanced"), settings.isShowAdvanced(), v -> {
            settings.setShowAdvanced(v);
            saveAnd(null);
        }));
        return p;
    }

    // ------------------------------------------------------------------ FFmpeg

    private JComponent ffmpeg() {
        ScrollablePanel p = page();
        p.add(Ui.heading(I18n.tr("settings.ffmpeg.mode")));
        ButtonGroup group = new ButtonGroup();
        JTextField path = new JTextField(settings.getFfmpegPath());
        JButton browse = new JButton(I18n.tr("common.browse"));
        for (Settings.FfmpegMode mode : Settings.FfmpegMode.values()) {
            if (mode == Settings.FfmpegMode.BUNDLED && !FfmpegLocator.hasBundled()) {
                continue;
            }
            JRadioButton radio = new JRadioButton(I18n.tr("settings.ffmpeg." + mode.name().toLowerCase(
                    java.util.Locale.ROOT)));
            radio.setSelected(settings.getFfmpegMode() == mode);
            radio.addActionListener(e -> {
                settings.setFfmpegMode(mode);
                saveAnd(app::reloadEngine);
                ffmpegStatus.setText(I18n.tr("ffmpeg.preparing"));
            });
            group.add(radio);
            p.add(radio);
        }
        browse.addActionListener(e -> {
            Path chosen = FileDialogs.chooseProgram(this);
            if (chosen != null) {
                path.setText(chosen.toString());
                settings.setFfmpegPath(chosen.toString());
                settings.setFfmpegMode(Settings.FfmpegMode.CUSTOM);
                saveAnd(app::reloadEngine);
            }
        });
        path.addActionListener(e -> {
            settings.setFfmpegPath(path.getText().trim());
            saveAnd(app::reloadEngine);
        });
        JPanel pathRow = Ui.clear(new BorderLayout(8, 0));
        pathRow.add(path, BorderLayout.CENTER);
        pathRow.add(browse, BorderLayout.EAST);
        p.add(Ui.field(I18n.tr("settings.ffmpeg.path"), pathRow));
        JButton check = new JButton(I18n.tr("settings.ffmpeg.test"));
        check.addActionListener(e -> {
            ffmpegStatus.setText(I18n.tr("ffmpeg.preparing"));
            app.reloadEngine();
        });
        JPanel statusRow = Ui.clear(new BorderLayout(8, 0));
        statusRow.add(ffmpegStatus, BorderLayout.CENTER);
        statusRow.add(check, BorderLayout.EAST);
        p.add(statusRow);
        return p;
    }

    private void updateFfmpegStatus() {
        Engine engine = app.engine();
        if (app.isEngineLoading()) {
            ffmpegStatus.setText(I18n.tr("ffmpeg.preparing"));
        } else if (engine != null) {
            ffmpegStatus.setText(I18n.tr("settings.ffmpeg.using", engine.tools().toString()));
            ffmpegStatus.setForeground(Theme.success());
        } else {
            ffmpegStatus.setText(I18n.tr("settings.ffmpeg.notFound"));
            ffmpegStatus.setForeground(Theme.error());
        }
    }

    // ------------------------------------------------------------------ About

    private JComponent about() {
        ScrollablePanel p = page();
        JLabel logo = new JLabel(Theme.logo(88));
        p.add(Ui.row(0, logo));
        JLabel name = new JLabel(AppInfo.NAME);
        name.putClientProperty("FlatLaf.style", "font: bold +14");
        p.add(name);
        p.add(Ui.secondary(I18n.tr("settings.about.version", AppInfo.version())));
        p.add(new WrapLabel(I18n.tr("about.text")));
        p.add(Ui.secondaryWrap(I18n.tr("settings.about.license")));
        p.add(Ui.secondaryWrap(I18n.tr("settings.about.ffmpeg")));
        JButton website = new JButton(I18n.tr("settings.about.website"), Theme.icon("external", 14));
        website.addActionListener(e -> DesktopActions.browse(AppInfo.WEBSITE));
        p.add(Ui.row(0, website));
        return p;
    }

    // ------------------------------------------------------------------ helpers

    private static JCheckBox checkbox(String text, boolean value, java.util.function.Consumer<Boolean> onChange) {
        JCheckBox box = new JCheckBox(text, value);
        box.addActionListener(e -> onChange.accept(box.isSelected()));
        return box;
    }

    @Override
    public void dispose() {
        app.removeEngineListener(engineListener);
        super.dispose();
    }

    /** Shows the settings window. */
    public static void show(Window owner, AppContext app, Runnable onLookChanged, Runnable onEncodingChanged) {
        SettingsDialog d = new SettingsDialog(owner, app, onLookChanged, onEncodingChanged);
        d.setVisible(true);
    }

    /** Places a component in a left-aligned row (used for wide buttons). */
    static JPanel left(JComponent c) {
        JPanel p = Ui.clear(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.add(c);
        return p;
    }
}
