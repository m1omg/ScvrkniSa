package io.github.m1omg.shrink.desktop;

import io.github.m1omg.shrink.core.AppInfo;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.desktop.ui.MainWindow;
import io.github.m1omg.shrink.desktop.ui.SettingsDialog;
import io.github.m1omg.shrink.desktop.ui.WindowRegistry;
import io.github.m1omg.shrink.desktop.ui.theme.Theme;
import io.github.m1omg.shrink.desktop.util.Platform;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Desktop;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

/** Starts the desktop app. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        // These must be set before any AWT class is used.
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", AppInfo.NAME);
        System.setProperty("apple.awt.application.appearance", "system");
        System.setProperty("awt.useSystemAAFontSettings", "on");
        if (Platform.isLinux()) {
            setLinuxWindowClass();
        }

        AppContext app = new AppContext();
        I18n.setLocale(app.settings().getLanguage().toLocale());

        SwingUtilities.invokeLater(() -> {
            Theme.apply(app.settings().getTheme(), false);
            installDesktopHandlers(app);
            setTaskbarIcon();
            MainWindow window = new MainWindow(app, null);
            WindowRegistry.set(window);
            window.setVisible(true);
            app.reloadEngine();
            for (String arg : args) {
                File f = new File(arg);
                if (f.isFile()) {
                    window.openFile(f.toPath());
                    break;
                }
            }
        });
    }

    /** About, Settings, Quit and "Open with" in the macOS application menu and Dock. */
    private static void installDesktopHandlers(AppContext app) {
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        Desktop desktop = Desktop.getDesktop();
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler(e -> JOptionPane.showMessageDialog(WindowRegistry.current(),
                    AppInfo.NAME + " " + AppInfo.version() + "\n\n" + I18n.tr("about.text") + "\n"
                            + I18n.tr("settings.about.license"),
                    I18n.tr("menu.help.about"), JOptionPane.INFORMATION_MESSAGE, Theme.logo(64)));
        }
        if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
            desktop.setPreferencesHandler(e -> {
                MainWindow w = WindowRegistry.current();
                if (w != null) {
                    SettingsDialog.show(w, app, w::rebuild, () -> { });
                }
            });
        }
        if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            desktop.setQuitHandler((e, response) -> {
                MainWindow w = WindowRegistry.current();
                if (w == null || w.confirmClose()) {
                    response.performQuit();
                } else {
                    response.cancelQuit();
                }
            });
        }
        if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
            desktop.setOpenFileHandler(e -> {
                List<File> files = e.getFiles();
                if (!files.isEmpty()) {
                    Path p = files.get(0).toPath();
                    SwingUtilities.invokeLater(() -> {
                        MainWindow w = WindowRegistry.current();
                        if (w != null) {
                            w.openFile(p);
                        }
                    });
                }
            });
        }
    }

    private static void setTaskbarIcon() {
        try {
            if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)
                    && !isPackagedMacApp()) {
                Taskbar.getTaskbar().setIconImage(Theme.appIcons().get(Theme.appIcons().size() - 1));
            }
        } catch (UnsupportedOperationException | SecurityException ignored) {
            // the packaged app has its own icon
        }
    }

    private static boolean isPackagedMacApp() {
        String path = System.getProperty("java.home", "");
        return Platform.isMac() && path.contains(".app/Contents");
    }

    /**
     * Sets the X11 window class to "shrink" so Linux desktops match the window
     * with Shrink!'s .desktop file and show the right name and icon.
     */
    private static void setLinuxWindowClass() {
        try {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            Field field = toolkit.getClass().getDeclaredField("awtAppClassName");
            field.setAccessible(true);
            field.set(toolkit, "shrink");
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Not an X11 toolkit or not allowed: only affects taskbar grouping.
        }
    }
}
