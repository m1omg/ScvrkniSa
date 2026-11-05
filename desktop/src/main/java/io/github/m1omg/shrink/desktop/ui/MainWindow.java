package io.github.m1omg.shrink.desktop.ui;

import io.github.m1omg.shrink.core.AppInfo;
import io.github.m1omg.shrink.core.i18n.Formats;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.Job;
import io.github.m1omg.shrink.core.model.JobKind;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.plan.Plan;
import io.github.m1omg.shrink.core.store.HistoryEntry;
import io.github.m1omg.shrink.core.store.Settings;
import io.github.m1omg.shrink.core.util.OutputNames;
import io.github.m1omg.shrink.desktop.AppContext;
import io.github.m1omg.shrink.desktop.engine.Engine;
import io.github.m1omg.shrink.desktop.engine.EngineException;
import io.github.m1omg.shrink.desktop.engine.FfmpegRunner;
import io.github.m1omg.shrink.desktop.ui.components.ActionBar;
import io.github.m1omg.shrink.desktop.ui.components.Banner;
import io.github.m1omg.shrink.desktop.ui.components.DropZone;
import io.github.m1omg.shrink.desktop.ui.components.FileHeader;
import io.github.m1omg.shrink.desktop.ui.components.JobOverlay;
import io.github.m1omg.shrink.desktop.ui.components.Sidebar;
import io.github.m1omg.shrink.desktop.ui.components.Ui;
import io.github.m1omg.shrink.desktop.ui.theme.Theme;
import io.github.m1omg.shrink.desktop.ui.tools.CompressPanel;
import io.github.m1omg.shrink.desktop.ui.tools.ConvertPanel;
import io.github.m1omg.shrink.desktop.ui.tools.ExtractAudioPanel;
import io.github.m1omg.shrink.desktop.ui.tools.FrameRatePanel;
import io.github.m1omg.shrink.desktop.ui.tools.InfoPanel;
import io.github.m1omg.shrink.desktop.ui.tools.MutePanel;
import io.github.m1omg.shrink.core.plan.PlanText;
import io.github.m1omg.shrink.desktop.ui.tools.ResolutionPanel;
import io.github.m1omg.shrink.desktop.ui.tools.RotatePanel;
import io.github.m1omg.shrink.desktop.ui.tools.Tool;
import io.github.m1omg.shrink.desktop.ui.tools.ToolHost;
import io.github.m1omg.shrink.desktop.ui.tools.ToolPanel;
import io.github.m1omg.shrink.desktop.ui.tools.TrimPanel;
import io.github.m1omg.shrink.desktop.util.DesktopActions;
import io.github.m1omg.shrink.desktop.util.FileDialogs;
import io.github.m1omg.shrink.desktop.util.Platform;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The main window: sidebar on the left, the selected tool (or the history) on
 * the right, and the selected video at the top.
 */
public final class MainWindow extends JFrame implements ToolHost {
    private static final long serialVersionUID = 1L;

    private static final String CARD_EMPTY = "empty";
    private static final String CARD_LOADING = "loading";
    private static final String CARD_TOOL = "tool";
    private static final String CARD_HISTORY = "history";

    private final AppContext app;
    private final Sidebar sidebar;
    private final CardLayout cards = new CardLayout();
    private final JPanel main = new JPanel(cards) {
        private static final long serialVersionUID = 1L;

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(Theme.contentBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    };
    private final DropZone dropZone;
    private final JPanel toolArea = Ui.clear(new BorderLayout());
    private final JScrollPane toolScroll = new JScrollPane();
    private final FileHeader header;
    private final ActionBar actionBar;
    private final HistoryPanel history;
    private final JobOverlay overlay = new JobOverlay();
    private final Banner ffmpegBanner = new Banner();
    private final Map<Tool, ToolPanel> panels = new EnumMap<>(Tool.class);
    private final Runnable engineListener = this::engineChanged;
    private final AtomicLong loadCounter = new AtomicLong();

    private Tool currentTool = Tool.COMPRESS;
    private boolean showingHistory;
    private MediaInfo source;
    private Path sourcePath;
    private BufferedImage thumbnail;
    private Path chosenOutput;
    private boolean busy;
    private FfmpegRunner.CancelToken runningToken;

    public MainWindow(AppContext app, Rectangle bounds) {
        super(AppInfo.NAME);
        this.app = app;
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setIconImages(Theme.appIcons());
        setMinimumSize(new Dimension(760, 560));

        boolean mac = Platform.isMac();
        if (mac) {
            getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
            getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
            getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        }

        sidebar = new Sidebar(mac ? 44 : 16, this::showTool, this::showHistory, this::openSettings);
        dropZone = new DropZone(this::chooseFile);
        header = new FileHeader(this::chooseFile);
        actionBar = new ActionBar(this::changeOutput, this::runCurrentTool);
        history = new HistoryPanel(app);

        // Tool area: header with the video, the tool's scrollable content, the action bar.
        JPanel top = Ui.clear(new BorderLayout(0, 10));
        top.setBorder(BorderFactory.createEmptyBorder(mac ? 34 : 18, 24, 0, 24));
        top.add(header, BorderLayout.CENTER);
        ffmpegBanner.setVisible(false);
        top.add(ffmpegBanner, BorderLayout.SOUTH);
        toolScroll.setBorder(null);
        toolScroll.setOpaque(false);
        toolScroll.getViewport().setOpaque(false);
        toolScroll.getVerticalScrollBar().setUnitIncrement(24);
        toolArea.add(top, BorderLayout.NORTH);
        toolArea.add(toolScroll, BorderLayout.CENTER);
        toolArea.add(actionBar, BorderLayout.SOUTH);

        JPanel emptyCard = Ui.clear(new BorderLayout());
        emptyCard.setBorder(BorderFactory.createEmptyBorder(mac ? 30 : 12, 12, 12, 12));
        emptyCard.add(dropZone, BorderLayout.CENTER);
        main.add(emptyCard, CARD_EMPTY);
        main.add(loadingCard(), CARD_LOADING);
        main.add(toolArea, CARD_TOOL);
        JPanel historyCard = Ui.clear(new BorderLayout());
        historyCard.setBorder(BorderFactory.createEmptyBorder(mac ? 16 : 0, 0, 0, 0));
        historyCard.add(history, BorderLayout.CENTER);
        main.add(historyCard, CARD_HISTORY);

        JPanel root = new JPanel(new BorderLayout());
        root.add(sidebar, BorderLayout.WEST);
        root.add(main, BorderLayout.CENTER);
        setContentPane(root);
        setGlassPane(overlay);

        createPanels();
        installDragAndDrop(root);
        installShortcuts(root);
        if (mac) {
            setJMenuBar(menuBar());
        }

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                boolean compact = getWidth() < 900;
                if (compact != sidebar.isCompact()) {
                    sidebar.setCompact(compact);
                }
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                if (app.settings().getTheme() == Settings.Theme.SYSTEM && !busy) {
                    boolean wasDark = Theme.isDark();
                    java.awt.Color accent = Theme.accent();
                    Theme.apply(Settings.Theme.SYSTEM, true);
                    if (wasDark != Theme.isDark() || !accent.equals(Theme.accent())) {
                        rebuild();
                    }
                }
            }

            @Override
            public void windowClosing(WindowEvent e) {
                if (confirmClose()) {
                    dispose();
                }
            }

            @Override
            public void windowClosed(WindowEvent e) {
                app.removeEngineListener(engineListener);
                history.dispose();
                for (ToolPanel p : panels.values()) {
                    p.hidden();
                }
            }
        });
        app.onEngineChanged(engineListener);

        if (bounds != null) {
            setBounds(bounds);
        } else {
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            setSize(Math.min(1180, screen.width - 80), Math.min(800, screen.height - 80));
            setLocationRelativeTo(null);
        }
        sidebar.setCompact(getWidth() < 900);
        showTool(Tool.COMPRESS);
        engineChanged();
        addWindowFocusListener(new java.awt.event.WindowAdapter() {
            private boolean first = true;

            @Override
            public void windowGainedFocus(WindowEvent e) {
                if (first) {
                    first = false;
                    if (source == null) {
                        dropZone.focusChooseButton();
                    }
                }
            }
        });
    }

    private JComponent loadingCard() {
        JPanel p = Ui.clear(new GridBagLayout());
        JPanel column = Ui.clear(new BorderLayout(0, 10));
        JLabel label = new JLabel(I18n.tr("file.loading"));
        label.putClientProperty("FlatLaf.style", "font: bold +4");
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setPreferredSize(new Dimension(220, 6));
        column.add(label, BorderLayout.NORTH);
        column.add(bar, BorderLayout.CENTER);
        p.add(column);
        return p;
    }

    private void createPanels() {
        List<ToolPanel> list = Arrays.asList(new CompressPanel(app, this), new TrimPanel(app, this),
                new RotatePanel(app, this), new MutePanel(app, this), new ExtractAudioPanel(app, this),
                new ConvertPanel(app, this), new ResolutionPanel(app, this), new FrameRatePanel(app, this),
                new InfoPanel(app, this));
        for (ToolPanel p : list) {
            panels.put(p.tool(), p);
            p.addChangeListener(() -> {
                if (!showingHistory && p.tool() == currentTool) {
                    updateActionBar();
                }
            });
        }
    }

    // ------------------------------------------------------------------ navigation

    private void showTool(Tool tool) {
        ToolPanel previous = panels.get(currentTool);
        if (previous != null && (tool != currentTool || showingHistory)) {
            previous.hidden();
        }
        currentTool = tool;
        showingHistory = false;
        chosenOutput = null;
        sidebar.select(Sidebar.Page.TOOL, tool);
        if (source == null) {
            cards.show(main, loadCounter.get() > 0 && sourcePath != null ? CARD_LOADING : CARD_EMPTY);
            return;
        }
        ToolPanel panel = panels.get(tool);
        toolScroll.setViewportView(panel);
        toolScroll.getVerticalScrollBar().setValue(0);
        cards.show(main, CARD_TOOL);
        panel.shown();
        updateActionBar();
        if (actionBar.isVisible() && actionBar.primaryButton().isEnabled()) {
            actionBar.primaryButton().requestFocusInWindow();
        }
    }

    private void showHistory() {
        ToolPanel previous = panels.get(currentTool);
        if (previous != null) {
            previous.hidden();
        }
        showingHistory = true;
        sidebar.select(Sidebar.Page.HISTORY, null);
        history.refresh();
        cards.show(main, CARD_HISTORY);
    }

    private void openSettings() {
        SettingsDialog.show(this, app, this::rebuild, this::encodingSettingsChanged);
    }

    private void encodingSettingsChanged() {
        if (source != null) {
            for (ToolPanel p : panels.values()) {
                p.setSource(source, sourcePath);
            }
            showTool(currentTool);
        }
    }

    /**
     * Re-creates the window, e.g. after the language or appearance changed. The
     * open video is loaded again.
     */
    public void rebuild() {
        I18n.setLocale(app.settings().getLanguage().toLocale());
        Theme.apply(app.settings().getTheme(), true);
        MainWindow fresh = new MainWindow(app, getBounds());
        WindowRegistry.set(fresh);
        fresh.setVisible(true);
        if (showingHistory) {
            fresh.showHistory();
        } else {
            fresh.currentTool = currentTool;
        }
        if (sourcePath != null) {
            fresh.openFile(sourcePath);
        } else if (!showingHistory) {
            fresh.showTool(currentTool);
        }
        dispose();
    }

    // ------------------------------------------------------------------ files

    private void chooseFile() {
        if (busy) {
            return;
        }
        Path start = sourcePath != null ? sourcePath.getParent() : Platform.videosDir();
        Path chosen = FileDialogs.chooseVideo(this, start);
        if (chosen != null) {
            openFile(chosen);
        }
    }

    /** Opens a video: reads its information and a thumbnail in the background. */
    public void openFile(Path file) {
        if (busy) {
            return;
        }
        Engine engine = app.engine();
        long id = loadCounter.incrementAndGet();
        sourcePath = file;
        source = null;
        if (engine == null) {
            if (app.isEngineLoading()) {
                cards.show(main, CARD_LOADING);
                return; // engineChanged() opens it once FFmpeg is ready
            }
            showLoadError(file, I18n.tr("ffmpeg.missing.body"));
            return;
        }
        if (showingHistory) {
            showingHistory = false;
            sidebar.select(Sidebar.Page.TOOL, currentTool);
        }
        cards.show(main, CARD_LOADING);
        app.background(() -> {
            try {
                MediaInfo info = engine.probe(file);
                SwingUtilities.invokeLater(() -> {
                    if (loadCounter.get() == id) {
                        sourceLoaded(info, file);
                    }
                });
                if (info.hasVideo()) {
                    double t = Math.min(info.durationSeconds() * 0.25, 10);
                    BufferedImage image = engine.frame(file, info.video(), t, 960, 540, false);
                    SwingUtilities.invokeLater(() -> {
                        if (loadCounter.get() == id) {
                            thumbnailLoaded(image);
                        }
                    });
                }
            } catch (EngineException e) {
                SwingUtilities.invokeLater(() -> {
                    if (loadCounter.get() == id) {
                        showLoadError(file, I18n.tr("file.error.detail", file.getFileName()));
                    }
                });
            }
        });
    }

    private void showLoadError(Path file, String message) {
        source = null;
        sourcePath = null;
        cards.show(main, CARD_EMPTY);
        overlay.showError(I18n.tr("file.error.title"), message, null, List.of(
                new JobOverlay.Action(I18n.tr("common.ok"), overlay::close, true)));
    }

    private void sourceLoaded(MediaInfo info, Path file) {
        source = info;
        sourcePath = file;
        thumbnail = null;
        chosenOutput = null;
        setTitle(AppInfo.NAME + " – " + info.fileName());
        header.setSource(info, null);
        for (Tool tool : Tool.values()) {
            boolean usable = !tool.needsVideo() || info.hasVideo();
            if (tool == Tool.EXTRACT_AUDIO) {
                usable = info.hasAudio();
            }
            sidebar.setToolEnabled(tool, usable);
        }
        for (ToolPanel p : panels.values()) {
            p.setSource(info, file);
        }
        Tool tool = currentTool;
        if (!info.hasVideo()) {
            tool = info.hasAudio() ? Tool.EXTRACT_AUDIO : Tool.INFO;
        }
        showTool(tool);
    }

    private void thumbnailLoaded(BufferedImage image) {
        thumbnail = image;
        header.setThumbnail(image);
        ((RotatePanel) panels.get(Tool.ROTATE)).thumbnailChanged(image);
        ((TrimPanel) panels.get(Tool.TRIM)).thumbnailChanged(image);
    }

    private void installDragAndDrop(JComponent target) {
        TransferHandler handler = new TransferHandler() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean canImport(TransferSupport support) {
                boolean ok = !busy && (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                        || support.isDataFlavorSupported(uriListFlavor()));
                dropZone.setDragActive(ok);
                return ok;
            }

            @Override
            public boolean importData(TransferSupport support) {
                dropZone.setDragActive(false);
                Path file = droppedFile(support.getTransferable());
                if (file == null) {
                    return false;
                }
                openFile(file);
                return true;
            }
        };
        target.setTransferHandler(handler);
        dropZone.setTransferHandler(handler);
        toolArea.setTransferHandler(handler);
        dropZone.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                dropZone.setDragActive(false);
            }
        });
        new java.awt.dnd.DropTarget(dropZone, java.awt.dnd.DnDConstants.ACTION_COPY,
                new java.awt.dnd.DropTargetAdapter() {
                    @Override
                    public void dragEnter(java.awt.dnd.DropTargetDragEvent e) {
                        dropZone.setDragActive(true);
                    }

                    @Override
                    public void dragExit(java.awt.dnd.DropTargetEvent e) {
                        dropZone.setDragActive(false);
                    }

                    @Override
                    public void drop(java.awt.dnd.DropTargetDropEvent e) {
                        dropZone.setDragActive(false);
                        e.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY);
                        Path file = droppedFile(e.getTransferable());
                        e.dropComplete(file != null);
                        if (file != null) {
                            openFile(file);
                        }
                    }
                }, true);
    }

    private static DataFlavor uriListFlavor() {
        try {
            return new DataFlavor("text/uri-list;class=java.lang.String");
        } catch (ClassNotFoundException e) {
            return DataFlavor.stringFlavor;
        }
    }

    /** Reads a dropped file: a file list on macOS/Windows, a "text/uri-list" on many Linux desktops. */
    @SuppressWarnings("unchecked")
    private static Path droppedFile(Transferable t) {
        try {
            if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                if (!files.isEmpty()) {
                    return files.get(0).toPath();
                }
            }
            DataFlavor uris = uriListFlavor();
            if (t.isDataFlavorSupported(uris)) {
                String data = (String) t.getTransferData(uris);
                for (String line : data.split("\r?\n")) {
                    line = line.trim();
                    if (line.startsWith("file:")) {
                        return Paths.get(new URI(line));
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    // ------------------------------------------------------------------ output

    private ToolPanel currentPanel() {
        return panels.get(currentTool);
    }

    /** Where the result of the current tool goes, or {@code null} to ask when starting. */
    private Path defaultOutput(Plan plan) {
        if (plan == null || sourcePath == null) {
            return null;
        }
        String extension = plan.job().outputExtension();
        if (chosenOutput != null && chosenOutput.getFileName().toString().endsWith("." + extension)) {
            return chosenOutput;
        }
        Settings s = app.settings();
        Path dir;
        switch (s.getOutputLocation()) {
            case ASK:
                return null;
            case CUSTOM_FOLDER:
                dir = s.getCustomOutputFolder().isEmpty() ? sourcePath.getParent()
                        : Paths.get(s.getCustomOutputFolder());
                break;
            default:
                dir = sourcePath.toAbsolutePath().getParent();
                break;
        }
        if (dir == null || !Files.isDirectory(dir) || !Files.isWritable(dir)) {
            dir = Platform.videosDir();
        }
        Path folder = dir;
        String name = OutputNames.suggest(sourcePath.getFileName().toString(), currentPanel().fileSuffix(),
                extension, n -> Files.exists(folder.resolve(n)));
        return folder.resolve(name);
    }

    private void changeOutput() {
        Plan plan = currentPanel().plan();
        if (plan == null) {
            return;
        }
        Path suggested = defaultOutput(plan);
        if (suggested == null) {
            suggested = sourcePath.resolveSibling(OutputNames.suggest(sourcePath.getFileName().toString(),
                    currentPanel().fileSuffix(), plan.job().outputExtension(), null));
        }
        Path chosen = FileDialogs.chooseSaveFile(this, suggested);
        if (chosen != null) {
            chosenOutput = chosen;
            updateActionBar();
        }
    }

    private void updateActionBar() {
        ToolPanel panel = currentPanel();
        Plan plan = panel.plan();
        boolean hasAction = panel.actionText() != null;
        actionBar.setVisible(hasAction);
        if (!hasAction) {
            return;
        }
        actionBar.setAction(panel.actionText(), panel.canRun() && app.engine() != null && !busy);
        actionBar.setDestinationVisible(plan != null);
        if (plan != null) {
            actionBar.setOutput(defaultOutput(plan));
            actionBar.setEstimate(PlanText.estimate(plan));
        } else {
            actionBar.setEstimate("");
        }
    }

    // ------------------------------------------------------------------ running jobs

    @Override
    public void runCurrentTool() {
        ToolPanel panel = currentPanel();
        Plan plan = panel.plan();
        Engine engine = app.engine();
        if (busy || plan == null || !plan.feasible() || engine == null) {
            return;
        }
        Path output = defaultOutput(plan);
        if (output == null) {
            output = FileDialogs.chooseSaveFile(this, sourcePath.resolveSibling(OutputNames.suggest(
                    sourcePath.getFileName().toString(), panel.fileSuffix(), plan.job().outputExtension(),
                    n -> Files.exists(sourcePath.resolveSibling(n)))));
            if (output == null) {
                return;
            }
        }
        run(engine, plan.job().withOutput(output.toString()), output);
    }

    private void run(Engine engine, Job job, Path output) {
        busy = true;
        updateActionBar();
        Tool tool = currentTool;
        FfmpegRunner.CancelToken token = new FfmpegRunner.CancelToken();
        runningToken = token;
        overlay.showProgress(tool.icon(), I18n.tr(tool.titleKey()), output.getFileName().toString(),
                () -> {
                    if (javax.swing.JOptionPane.showConfirmDialog(this, I18n.tr("progress.cancelConfirm"),
                            I18n.tr("progress.cancel"), javax.swing.JOptionPane.YES_NO_OPTION)
                            == javax.swing.JOptionPane.YES_OPTION) {
                        token.cancel();
                    }
                });
        setDockProgress(0);
        Path input = sourcePath;
        MediaInfo info = source;
        app.background(() -> {
            try {
                Engine.Result result = engine.run(job, input, output, p -> SwingUtilities.invokeLater(() -> {
                    overlay.updateProgress(p.fraction(), p.remainingSeconds(), p.speed(), p.pass(), p.passCount(),
                            p.correcting());
                    setDockProgress((int) Math.round(p.fraction() * 100));
                }), token);
                SwingUtilities.invokeLater(() -> finished(job, info, input, result));
            } catch (FfmpegRunner.CancelledException e) {
                SwingUtilities.invokeLater(this::cancelled);
            } catch (EngineException e) {
                SwingUtilities.invokeLater(() -> failed(job, info, input, output, e));
            }
        });
    }

    private void finished(Job job, MediaInfo info, Path input, Engine.Result result) {
        busy = false;
        runningToken = null;
        setDockProgress(-1);
        Formats f = I18n.fmt();
        if (app.settings().isHistoryEnabled()) {
            app.history().add(new HistoryEntry(HistoryEntry.newId(), System.currentTimeMillis(), job.kind(),
                    HistoryEntry.Status.DONE, info.fileName(), input.toString(), info.sizeBytes(),
                    result.output().getFileName().toString(), result.output().toString(), result.sizeBytes(),
                    job.outputDuration(), PlanText.summary(job), ""));
        }
        double reduction = info.sizeBytes() > 0 ? (1.0 - (double) result.sizeBytes() / info.sizeBytes()) * 100 : 0;
        String chip = null;
        boolean good = reduction > 0.5;
        if (job.kind() != JobKind.EXTRACT_AUDIO && Math.abs(reduction) >= 0.5) {
            chip = I18n.tr(reduction >= 0 ? "result.smaller" : "result.larger", f.percent(Math.abs(Math.round(reduction))));
        }
        String subtitle = I18n.tr("result.original") + " " + f.size(info.sizeBytes()) + "   →   "
                + I18n.tr("result.result") + " " + f.size(result.sizeBytes());
        if (reduction > 0.5 && job.kind() != JobKind.EXTRACT_AUDIO) {
            subtitle += "   ·   " + I18n.tr("result.saved", f.size(info.sizeBytes() - result.sizeBytes()));
        }
        List<JobOverlay.Action> actions = new ArrayList<>();
        Path out = result.output();
        actions.add(new JobOverlay.Action(Platform.isMac() ? I18n.tr("result.showInFinder")
                : I18n.tr("result.showInFolder"), () -> DesktopActions.reveal(out), false));
        actions.add(new JobOverlay.Action(I18n.tr("result.open"), () -> DesktopActions.open(out), false));
        actions.add(new JobOverlay.Action(I18n.tr("common.done"), this::closeOverlay, true));
        overlay.showSuccess(I18n.tr("result.done"), f.size(result.sizeBytes()), chip, good,
                subtitle, actions);
        if (app.settings().isRevealWhenDone()) {
            DesktopActions.reveal(out);
        }
        requestAttention();
    }

    private void failed(Job job, MediaInfo info, Path input, Path output, EngineException e) {
        busy = false;
        runningToken = null;
        setDockProgress(-1);
        if (app.settings().isHistoryEnabled()) {
            app.history().add(new HistoryEntry(HistoryEntry.newId(), System.currentTimeMillis(), job.kind(),
                    HistoryEntry.Status.FAILED, info.fileName(), input.toString(), info.sizeBytes(),
                    output.getFileName().toString(), output.toString(), 0, job.outputDuration(),
                    PlanText.summary(job), e.getMessage()));
        }
        overlay.showError(I18n.tr("result.failed"), I18n.tr("result.error", e.getMessage()), e.details(), List.of(
                new JobOverlay.Action(I18n.tr("common.close"), this::closeOverlay, false),
                new JobOverlay.Action(I18n.tr("result.retry"), () -> {
                    closeOverlay();
                    runCurrentTool();
                }, true)));
        requestAttention();
    }

    private void cancelled() {
        busy = false;
        runningToken = null;
        setDockProgress(-1);
        closeOverlay();
    }

    private void closeOverlay() {
        overlay.close();
        chosenOutput = null;
        updateActionBar();
    }

    /** Stops a running job (e.g. when the window is closed). */
    public void cancelRunningJob() {
        FfmpegRunner.CancelToken t = runningToken;
        if (t != null) {
            t.cancel();
        }
    }

    public boolean isBusy() {
        return busy;
    }

    /** Asks before closing while a job runs; cancels it when confirmed. */
    public boolean confirmClose() {
        if (!busy) {
            return true;
        }
        int answer = javax.swing.JOptionPane.showConfirmDialog(this, I18n.tr("progress.cancelConfirm"),
                AppInfo.NAME, javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE);
        if (answer != javax.swing.JOptionPane.YES_OPTION) {
            return false;
        }
        cancelRunningJob();
        return true;
    }

    private void setDockProgress(int percent) {
        try {
            if (Taskbar.isTaskbarSupported()
                    && Taskbar.getTaskbar().isSupported(Taskbar.Feature.PROGRESS_VALUE)) {
                Taskbar.getTaskbar().setProgressValue(percent);
            }
        } catch (UnsupportedOperationException | SecurityException ignored) {
            // not available on this desktop
        }
    }

    private void requestAttention() {
        if (isFocused()) {
            return;
        }
        try {
            if (Taskbar.isTaskbarSupported()
                    && Taskbar.getTaskbar().isSupported(Taskbar.Feature.USER_ATTENTION)) {
                Taskbar.getTaskbar().requestUserAttention(true, false);
            }
        } catch (UnsupportedOperationException | SecurityException ignored) {
            // not available on this desktop
        }
    }

    // ------------------------------------------------------------------ engine

    private void engineChanged() {
        Engine engine = app.engine();
        if (engine == null && !app.isEngineLoading()) {
            ffmpegBanner.setKind(Banner.Kind.ERROR);
            String install = Platform.isMac() ? I18n.tr("ffmpeg.missing.mac")
                    : Platform.isWindows() ? I18n.tr("ffmpeg.missing.windows") : I18n.tr("ffmpeg.missing.linux");
            ffmpegBanner.setText(I18n.tr("ffmpeg.missing.title") + ". " + I18n.tr("ffmpeg.missing.body") + "\n"
                    + install);
            ffmpegBanner.setVisible(true);
        } else {
            ffmpegBanner.setVisible(false);
        }
        if (engine != null && sourcePath != null && source == null) {
            openFile(sourcePath);
        }
        if (source != null) {
            updateActionBar();
        }
    }

    // ------------------------------------------------------------------ ToolHost

    @Override
    public Engine engine() {
        return app.engine();
    }

    @Override
    public BufferedImage thumbnail() {
        return thumbnail;
    }

    @Override
    public void showPreview(Job job) {
        Engine engine = app.engine();
        if (engine == null || busy) {
            return;
        }
        new PreviewDialog(this, app, engine, job, sourcePath, this::runCurrentTool).setVisible(true);
    }

    @Override
    public Window window() {
        return this;
    }

    // ------------------------------------------------------------------ menus and keys

    private JMenuBar menuBar() {
        int menuKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu(I18n.tr("menu.file"));
        JMenuItem open = new JMenuItem(I18n.tr("menu.file.open"));
        open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, menuKey));
        open.addActionListener(e -> chooseFile());
        file.add(open);
        JMenuItem close = new JMenuItem(I18n.tr("menu.file.close"));
        close.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, menuKey));
        close.addActionListener(e -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));
        file.add(close);
        bar.add(file);

        JMenu view = new JMenu(I18n.tr("menu.view"));
        JMenuItem tools = new JMenuItem(I18n.tr("menu.view.tools"));
        tools.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1, menuKey));
        tools.addActionListener(e -> showTool(currentTool));
        JMenuItem hist = new JMenuItem(I18n.tr("menu.view.history"));
        hist.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_2, menuKey));
        hist.addActionListener(e -> showHistory());
        view.add(tools);
        view.add(hist);
        bar.add(view);

        JMenu window = new JMenu(I18n.tr("menu.window"));
        JMenuItem minimize = new JMenuItem(I18n.tr("menu.window.minimize"));
        minimize.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, menuKey));
        minimize.addActionListener(e -> setState(JFrame.ICONIFIED));
        window.add(minimize);
        bar.add(window);

        JMenu help = new JMenu(I18n.tr("menu.help"));
        JMenuItem site = new JMenuItem(I18n.tr("menu.help.website"));
        site.addActionListener(e -> DesktopActions.browse(AppInfo.WEBSITE));
        help.add(site);
        bar.add(help);
        return bar;
    }

    private void installShortcuts(JComponent root) {
        int menuKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_O, menuKey), "open", this::chooseFile);
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, menuKey), "settings", this::openSettings);
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, menuKey), "run", this::runCurrentTool);
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_2, menuKey), "history", this::showHistory);
        Tool[] tools = Tool.values();
        for (int i = 0; i < tools.length; i++) {
            Tool t = tools[i];
            bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_1 + Math.min(i, 8), menuKey | InputEvent.ALT_DOWN_MASK),
                    "tool" + i, () -> showTool(t));
        }
        if (!Platform.isMac()) {
            bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_Q, menuKey), "quit",
                    () -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));
        }
    }

    private static void bind(JComponent c, KeyStroke key, String name, Runnable action) {
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, name);
        c.getActionMap().put(name, new javax.swing.AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }
}
