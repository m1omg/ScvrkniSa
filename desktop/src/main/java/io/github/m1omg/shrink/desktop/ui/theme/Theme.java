package io.github.m1omg.shrink.desktop.ui.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import io.github.m1omg.shrink.core.store.Settings;
import io.github.m1omg.shrink.desktop.util.Platform;

import javax.swing.Icon;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Look and feel of the desktop app, built on FlatLaf: the macOS variant on Macs
 * (plus the native menu bar and title bar), the standard variant on Linux and
 * Windows. Follows the system's dark mode and accent colour.
 */
public final class Theme {

    /** Shrink!'s own accent, used when the system does not provide one. */
    public static final Color BRAND = new Color(0x5B6CFF);
    public static final Color BRAND_LIGHT = new Color(0x22C7F0);

    private static boolean dark;
    private static Color accent = BRAND;

    private Theme() {
    }

    /** Applies the theme chosen in the settings. Call on the event thread; refreshes open windows. */
    public static void apply(Settings.Theme choice, boolean refreshWindows) {
        boolean wantDark;
        switch (choice) {
            case DARK:
                wantDark = true;
                break;
            case LIGHT:
                wantDark = false;
                break;
            default:
                wantDark = SystemAppearance.isDark();
                break;
        }
        Color systemAccent = SystemAppearance.accentColor();
        Color newAccent = systemAccent != null ? systemAccent : (Platform.isMac() ? new Color(0x007AFF) : BRAND);
        if (refreshWindows && wantDark == dark && newAccent.equals(accent) && UIManager.getLookAndFeel() instanceof FlatLaf) {
            return;
        }
        dark = wantDark;
        accent = newAccent;

        Map<String, String> extra = new HashMap<>();
        extra.put("@accentColor", hex(accent));
        FlatLaf.setGlobalExtraDefaults(extra);

        if (Platform.isMac()) {
            if (dark) {
                FlatMacDarkLaf.setup();
            } else {
                FlatMacLightLaf.setup();
            }
        } else if (dark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        customize();
        if (refreshWindows) {
            FlatLaf.updateUI();
        }
    }

    private static void customize() {
        UIManager.put("Button.arc", 10);
        UIManager.put("Component.arc", 10);
        UIManager.put("ProgressBar.arc", 999);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("CheckBox.arc", 6);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
        UIManager.put("ScrollPane.smoothScrolling", true);
        UIManager.put("TitlePane.unifiedBackground", true);
        UIManager.put("ProgressBar.horizontalSize", new java.awt.Dimension(146, 6));
        UIManager.put("Slider.thumbSize", new java.awt.Dimension(16, 16));
        UIManager.put("TabbedPane.tabHeight", 34);
        UIManager.put("TabbedPane.showTabSeparators", false);
        UIManager.put("ToolTip.border", javax.swing.BorderFactory.createEmptyBorder(6, 8, 6, 8));
    }

    public static boolean isDark() {
        return dark;
    }

    public static Color accent() {
        return accent;
    }

    // ------------------------------------------------------------------ palette

    public static Color background() {
        return UIManager.getColor("Panel.background");
    }

    public static Color foreground() {
        return UIManager.getColor("Label.foreground");
    }

    /** Text for less important information. */
    public static Color secondaryText() {
        return blend(foreground(), background(), dark ? 0.62 : 0.58);
    }

    /** Background of the window's content area (slightly different from the sidebar). */
    public static Color contentBackground() {
        return dark ? new Color(0x1E1F22) : new Color(0xFAFAFB);
    }

    public static Color sidebarBackground() {
        if (Platform.isMac()) {
            return dark ? new Color(0x2A2A2D) : new Color(0xECECEE);
        }
        return dark ? new Color(0x26272B) : new Color(0xF1F1F4);
    }

    public static Color sidebarSelection() {
        return dark ? new Color(0x3A3B40) : new Color(0xDCDDE2);
    }

    public static Color card() {
        return dark ? new Color(0x2B2D31) : Color.WHITE;
    }

    public static Color cardBorder() {
        return dark ? new Color(0x3C3E44) : new Color(0xE1E2E6);
    }

    public static Color cardHover() {
        return dark ? new Color(0x55585F) : new Color(0xC9CBD2);
    }

    /** Background of a selected card: a faint tint of the accent colour. */
    public static Color cardSelected() {
        return blend(accent, card(), dark ? 0.18 : 0.08);
    }

    public static Color divider() {
        return dark ? new Color(0x3A3C41) : new Color(0xE3E4E8);
    }

    public static Color success() {
        return dark ? new Color(0x4CC38A) : new Color(0x1E9E5A);
    }

    public static Color warning() {
        return dark ? new Color(0xE8B339) : new Color(0xB7791F);
    }

    public static Color warningBackground() {
        return dark ? new Color(0x3A3220) : new Color(0xFFF6E0);
    }

    public static Color error() {
        return dark ? new Color(0xFF6B6B) : new Color(0xD93A3A);
    }

    public static Color errorBackground() {
        return dark ? new Color(0x3D2526) : new Color(0xFDECEC);
    }

    public static Color infoBackground() {
        return blend(accent, card(), dark ? 0.16 : 0.07);
    }

    /** Mixes {@code a} into {@code b}: amount 1.0 gives {@code a}, 0.0 gives {@code b}. */
    public static Color blend(Color a, Color b, double amount) {
        double t = Math.max(0, Math.min(1, amount));
        return new Color(
                (int) Math.round(a.getRed() * t + b.getRed() * (1 - t)),
                (int) Math.round(a.getGreen() * t + b.getGreen() * (1 - t)),
                (int) Math.round(a.getBlue() * t + b.getBlue() * (1 - t)));
    }

    public static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    public static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    // ------------------------------------------------------------------ icons

    private static final String ICONS = "io/github/m1omg/shrink/desktop/icons/";

    /** A line icon that follows the text colour of the current theme. */
    public static FlatSVGIcon icon(String name, int size) {
        FlatSVGIcon icon = new FlatSVGIcon(ICONS + name + ".svg", size, size, Theme.class.getClassLoader());
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> foreground()));
        return icon;
    }

    /** A line icon in a fixed colour. */
    public static FlatSVGIcon icon(String name, int size, Color color) {
        FlatSVGIcon icon = new FlatSVGIcon(ICONS + name + ".svg", size, size, Theme.class.getClassLoader());
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> color));
        return icon;
    }

    /** A line icon tinted with the current accent colour. */
    public static FlatSVGIcon accentIcon(String name, int size) {
        FlatSVGIcon icon = new FlatSVGIcon(ICONS + name + ".svg", size, size, Theme.class.getClassLoader());
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> accent));
        return icon;
    }

    public static Icon logo(int size) {
        return new FlatSVGIcon(ICONS + "logo.svg", size, size, Theme.class.getClassLoader());
    }

    /** The app icon in several sizes, for window decorations and the taskbar. */
    public static List<Image> appIcons() {
        List<Image> images = new ArrayList<>();
        for (int size : new int[] {16, 24, 32, 48, 64, 128, 256, 512}) {
            FlatSVGIcon icon = new FlatSVGIcon(ICONS + "logo.svg", size, size, Theme.class.getClassLoader());
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = img.createGraphics();
            icon.paintIcon(null, g, 0, 0);
            g.dispose();
            images.add(img);
        }
        return images;
    }
}
