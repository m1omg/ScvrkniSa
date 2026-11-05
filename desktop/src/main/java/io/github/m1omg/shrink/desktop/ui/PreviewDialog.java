package io.github.m1omg.shrink.desktop.ui;

import io.github.m1omg.shrink.core.i18n.Formats;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.Job;
import io.github.m1omg.shrink.core.model.RateControl;
import io.github.m1omg.shrink.core.model.VideoStream;
import io.github.m1omg.shrink.core.plan.JobPlanner;
import io.github.m1omg.shrink.desktop.AppContext;
import io.github.m1omg.shrink.desktop.engine.Engine;
import io.github.m1omg.shrink.desktop.engine.EngineException;
import io.github.m1omg.shrink.desktop.engine.FfmpegRunner;
import io.github.m1omg.shrink.desktop.ui.components.DropZone;
import io.github.m1omg.shrink.desktop.ui.components.Ui;
import io.github.m1omg.shrink.desktop.ui.player.CompareView;
import io.github.m1omg.shrink.desktop.ui.player.Playback;
import io.github.m1omg.shrink.desktop.ui.theme.Theme;
import io.github.m1omg.shrink.desktop.util.DesktopActions;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * The 5-second quality preview: encodes a short excerpt with the chosen
 * settings and shows it next to the original.
 */
public final class PreviewDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private static final double LENGTH = 5.0;

    private final AppContext app;
    private final Engine engine;
    private final Job job;
    private final Path input;
    private final Runnable compressAll;
    private final CompareView compare = new CompareView();
    private final JProgressBar progress = new JProgressBar();
    private final JLabel sizeLabel = new JLabel(" ");
    private final JLabel estimateLabel = new JLabel(" ");
    private final JButton play = new JButton();
    private final JSlider position;
    private final JButton update = new JButton(I18n.tr("preview.regenerate"));
    private final JButton openExternal = new JButton(I18n.tr("preview.openExternal"));
    private Path previewFile;
    private Job previewJob;
    private Playback playback;
    private FfmpegRunner.CancelToken token;
    private boolean closed;

    public PreviewDialog(Window owner, AppContext app, Engine engine, Job job, Path input, Runnable compressAll) {
        super(owner, I18n.tr("preview.title"), ModalityType.APPLICATION_MODAL);
        this.app = app;
        this.engine = engine;
        this.job = job;
        this.input = input;
        this.compressAll = compressAll;
        VideoStream v = job.source().video();
        compare.setSourceSize(v.displayWidth(), v.displayHeight());
        compare.setMessage(I18n.tr("preview.generating"));
        compare.setPreferredSize(new Dimension(880, 495));
        compare.onPlaybackFinished(this::playbackFinished);

        double available = job.trim() != null ? job.trim().duration() : job.source().durationSeconds();
        int maxStart = (int) Math.max(0, Math.floor(available - LENGTH));
        position = new JSlider(0, Math.max(maxStart, 0), (int) Math.round(JobPlanner.defaultPreviewStart(job, LENGTH)));
        position.setEnabled(maxStart > 0);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 14, 16));
        root.add(compare, BorderLayout.CENTER);
        root.add(controls(), BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e -> togglePlay(), KeyStroke.getKeyStroke("SPACE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                cleanUp();
            }
        });
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setMinimumSize(new Dimension(640, 480));
        setLocationRelativeTo(owner);
        generate();
    }

    private JComponent controls() {
        JPanel south = Ui.clear(new BorderLayout(0, 10));

        play.setIcon(Theme.icon("pause", 16));
        play.setToolTipText(I18n.tr("preview.pause"));
        play.setEnabled(false);
        play.addActionListener(e -> togglePlay());

        JToggleButton fit = new JToggleButton(I18n.tr("preview.zoom.fit"), true);
        JToggleButton actual = new JToggleButton(I18n.tr("preview.zoom.actual"));
        JToggleButton twice = new JToggleButton(I18n.tr("preview.zoom.double"));
        ButtonGroup zoom = new ButtonGroup();
        for (JToggleButton b : Arrays.asList(fit, actual, twice)) {
            zoom.add(b);
            b.putClientProperty("JButton.buttonType", "segmented");
        }
        fit.addActionListener(e -> compare.setZoom(0));
        actual.addActionListener(e -> {
            pauseForInspection();
            compare.setZoom(1);
        });
        twice.addActionListener(e -> {
            pauseForInspection();
            compare.setZoom(2);
        });
        JPanel zoomRow = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        zoomRow.add(new JLabel(Theme.icon("zoom", 16)));
        zoomRow.add(javax.swing.Box.createHorizontalStrut(6));
        zoomRow.add(fit);
        zoomRow.add(actual);
        zoomRow.add(twice);

        progress.setIndeterminate(true);
        JPanel top = Ui.clear(new BorderLayout(10, 0));
        top.add(play, BorderLayout.WEST);
        top.add(progress, BorderLayout.CENTER);
        top.add(zoomRow, BorderLayout.EAST);
        south.add(top, BorderLayout.NORTH);

        JPanel info = Ui.clear(new BorderLayout(0, 2));
        sizeLabel.putClientProperty("FlatLaf.style", "font: bold");
        estimateLabel.setForeground(Theme.secondaryText());
        info.add(sizeLabel, BorderLayout.NORTH);
        info.add(estimateLabel, BorderLayout.CENTER);
        JLabel hint = Ui.secondary(I18n.tr("preview.hint"));
        hint.putClientProperty("FlatLaf.styleClass", "small");
        info.add(hint, BorderLayout.SOUTH);

        JPanel where = Ui.clear(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JLabel positionLabel = new JLabel(I18n.tr("preview.position"));
        JLabel positionValue = new JLabel(I18n.fmt().duration(position.getValue()));
        position.setPreferredSize(new Dimension(180, position.getPreferredSize().height));
        position.addChangeListener(e -> positionValue.setText(I18n.fmt().duration(position.getValue())));
        update.addActionListener(e -> generate());
        where.add(positionLabel);
        where.add(position);
        where.add(positionValue);
        where.add(update);

        JPanel middle = Ui.clear(new BorderLayout(16, 0));
        middle.add(info, BorderLayout.CENTER);
        middle.add(where, BorderLayout.EAST);
        south.add(middle, BorderLayout.CENTER);

        JButton close = new JButton(I18n.tr("common.close"));
        close.addActionListener(e -> dispose());
        openExternal.setEnabled(false);
        openExternal.setIcon(Theme.icon("external", 14));
        openExternal.addActionListener(e -> {
            if (previewFile != null) {
                DesktopActions.open(previewFile);
            }
        });
        JButton compressButton = new JButton(I18n.tr("preview.compressNow"));
        DropZone.styleDefault(compressButton);
        compressButton.addActionListener(e -> {
            dispose();
            compressAll.run();
        });
        JPanel buttons = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(openExternal);
        buttons.add(close);
        buttons.add(compressButton);
        south.add(buttons, BorderLayout.SOUTH);
        return south;
    }

    /** Encodes the excerpt in the background, then starts playback. */
    private void generate() {
        stopPlayback();
        if (token != null) {
            token.cancel();
        }
        progress.setVisible(true);
        progress.setIndeterminate(true);
        play.setEnabled(false);
        update.setEnabled(false);
        openExternal.setEnabled(false);
        compare.setStills(null, null);
        compare.setMessage(I18n.tr("preview.generating"));
        Job excerpt = JobPlanner.preview(job, position.getValue(), LENGTH);
        FfmpegRunner.CancelToken myToken = new FfmpegRunner.CancelToken();
        token = myToken;
        app.background(() -> {
            try {
                Path target = Files.createTempFile("shrink-preview-", "." + excerpt.outputExtension());
                Engine.Result r = engine.renderPreview(excerpt, input, target, p -> SwingUtilities.invokeLater(() -> {
                    progress.setIndeterminate(false);
                    progress.setValue((int) Math.round(p.fraction() * 100));
                }), myToken);
                SwingUtilities.invokeLater(() -> previewReady(excerpt, target, r.sizeBytes()));
            } catch (FfmpegRunner.CancelledException e) {
                // a newer preview replaced this one, or the dialog closed
            } catch (EngineException | IOException e) {
                SwingUtilities.invokeLater(() -> {
                    progress.setVisible(false);
                    update.setEnabled(true);
                    compare.setMessage(I18n.tr("preview.failed"));
                    sizeLabel.setText(e.getMessage());
                });
            }
        });
    }

    private void previewReady(Job excerpt, Path file, long bytes) {
        if (closed) {
            deleteQuietly(file);
            return;
        }
        deleteQuietly(previewFile);
        previewFile = file;
        previewJob = excerpt;
        progress.setVisible(false);
        update.setEnabled(true);
        openExternal.setEnabled(true);
        play.setEnabled(true);
        Formats f = I18n.fmt();
        double length = excerpt.outputDuration();
        sizeLabel.setText(I18n.tr("preview.size", f.size(bytes), f.durationWords(length)));
        long expected = job.estimatedBytes();
        if (job.video().rateControl() == RateControl.CONSTANT_QUALITY && length > 0) {
            expected = Math.round(bytes / length * job.outputDuration());
        }
        estimateLabel.setText(I18n.tr("preview.estimate", f.size(expected)));
        startPlayback();
    }

    private void startPlayback() {
        stopPlayback();
        if (previewFile == null) {
            return;
        }
        VideoStream v = job.source().video();
        int w = Math.min(1280, v.displayWidth());
        int h = (int) Math.round(w * (double) v.displayHeight() / v.displayWidth());
        double fps = Math.min(30, v.frameRate() > 0 ? v.frameRate() : 30);
        double start = previewJob.trim().start();
        playback = new Playback(engine, Arrays.asList(
                new Playback.Source(input, v, start),
                new Playback.Source(previewFile, null, 0)),
                null, previewJob.outputDuration(), fps, w, h, compare);
        playback.start();
        play.setIcon(Theme.icon("pause", 16));
        play.setToolTipText(I18n.tr("preview.pause"));
    }

    private void stopPlayback() {
        if (playback != null) {
            playback.stop();
            playback = null;
        }
        play.setIcon(Theme.icon("play", 16));
        play.setToolTipText(I18n.tr("preview.play"));
    }

    private void togglePlay() {
        if (playback != null) {
            pauseForInspection();
        } else if (previewFile != null) {
            startPlayback();
        }
    }

    /** Pauses and replaces the frames with full-resolution stills for close inspection. */
    private void pauseForInspection() {
        if (previewFile == null) {
            return;
        }
        stopPlayback();
        double start = previewJob.trim().start();
        double middle = Math.min(Math.max(compare.lastTime(), 0), Math.max(0, previewJob.outputDuration() - 0.1));
        VideoStream v = job.source().video();
        Path file = previewFile;
        app.background(() -> {
            try {
                BufferedImage a = engine.frame(input, v, start + middle, v.displayWidth(), v.displayHeight(), true);
                BufferedImage b = engine.frame(file, null, middle, v.displayWidth(), v.displayHeight(), true);
                if (a != null && b != null) {
                    SwingUtilities.invokeLater(() -> {
                        if (playback == null && !closed) {
                            compare.setStills(a, b);
                        }
                    });
                }
            } catch (EngineException ignored) {
                // keep the current frames
            }
        });
    }

    private void playbackFinished() {
        if (!closed && playback != null && previewFile != null) {
            startPlayback(); // loop
        }
    }

    private void cleanUp() {
        closed = true;
        if (token != null) {
            token.cancel();
        }
        stopPlayback();
        deleteQuietly(previewFile);
    }

    private static void deleteQuietly(Path p) {
        if (p != null) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
                // temporary file, best effort
            }
        }
    }
}
