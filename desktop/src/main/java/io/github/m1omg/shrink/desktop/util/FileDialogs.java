package io.github.m1omg.shrink.desktop.util;

import io.github.m1omg.shrink.core.i18n.I18n;

import javax.swing.JFileChooser;
import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Window;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Native file dialogs: AWT's FileDialog is the real system dialog on macOS,
 * Windows and (via GTK) Linux, so it is used wherever possible.
 */
public final class FileDialogs {

    /** Extensions offered in the open dialog. */
    public static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp4", "m4v", "mov", "qt", "mkv", "webm", "avi", "wmv", "flv", "mpg", "mpeg", "m2ts", "mts", "ts",
            "3gp", "3g2", "ogv", "vob", "mxf", "dv", "asf", "f4v", "divx", "rm", "rmvb", "hevc", "264", "h264",
            "m4a", "mp3", "wav", "aac", "flac", "ogg", "opus"));

    private FileDialogs() {
    }

    private static FileDialog create(Window owner, String title, int mode) {
        if (owner instanceof Frame) {
            return new FileDialog((Frame) owner, title, mode);
        }
        if (owner instanceof Dialog) {
            return new FileDialog((Dialog) owner, title, mode);
        }
        return new FileDialog((Frame) null, title, mode);
    }

    public static boolean isVideoFile(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 && VIDEO_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** Lets the user pick a video; returns {@code null} when cancelled. */
    public static Path chooseVideo(Window owner, Path startDir) {
        FileDialog d = create(owner, I18n.tr("file.chooser.title"), FileDialog.LOAD);
        if (startDir != null) {
            d.setDirectory(startDir.toString());
        }
        d.setFilenameFilter((dir, name) -> isVideoFile(new File(dir, name)));
        if (Platform.isWindows()) {
            d.setFile("*.mp4;*.mov;*.mkv;*.webm;*.avi;*.wmv;*.m4v;*.mpg;*.ts;*.3gp;*.flv");
        }
        d.setVisible(true);
        if (d.getFile() == null) {
            return null;
        }
        return new File(d.getDirectory(), d.getFile()).toPath();
    }

    /** Asks where to save a file; returns {@code null} when cancelled. */
    public static Path chooseSaveFile(Window owner, Path suggested) {
        FileDialog d = create(owner, I18n.tr("output.chooseTitle"), FileDialog.SAVE);
        if (suggested != null) {
            if (suggested.getParent() != null) {
                d.setDirectory(suggested.getParent().toString());
            }
            d.setFile(suggested.getFileName().toString());
        }
        d.setVisible(true);
        if (d.getFile() == null) {
            return null;
        }
        Path chosen = new File(d.getDirectory(), d.getFile()).toPath();
        // Keep the extension the format needs.
        if (suggested != null) {
            String wanted = extension(suggested.getFileName().toString());
            if (!wanted.isEmpty() && !extension(chosen.getFileName().toString()).equals(wanted)) {
                chosen = chosen.resolveSibling(chosen.getFileName() + "." + wanted);
            }
        }
        return chosen;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Lets the user pick a folder; returns {@code null} when cancelled. */
    public static Path chooseFolder(Window owner, Path current) {
        if (Platform.isMac()) {
            System.setProperty("apple.awt.fileDialogForDirectories", "true");
            try {
                FileDialog d = create(owner, I18n.tr("settings.output.choose"), FileDialog.LOAD);
                if (current != null) {
                    d.setDirectory(current.toString());
                }
                d.setVisible(true);
                return d.getFile() == null ? null : new File(d.getDirectory(), d.getFile()).toPath();
            } finally {
                System.setProperty("apple.awt.fileDialogForDirectories", "false");
            }
        }
        JFileChooser chooser = new JFileChooser(current == null ? null : current.toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(I18n.tr("settings.output.choose"));
        return chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION
                ? chooser.getSelectedFile().toPath() : null;
    }

    /** Lets the user pick the ffmpeg program. */
    public static Path chooseProgram(Window owner) {
        FileDialog d = create(owner, I18n.tr("settings.ffmpeg.path"), FileDialog.LOAD);
        d.setVisible(true);
        return d.getFile() == null ? null : new File(d.getDirectory(), d.getFile()).toPath();
    }
}
