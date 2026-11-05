package io.github.m1omg.shrink.desktop.ui.tools;

import io.github.m1omg.shrink.core.i18n.Formats;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.TimeRange;
import io.github.m1omg.shrink.core.plan.Plan;
import io.github.m1omg.shrink.desktop.AppContext;
import io.github.m1omg.shrink.desktop.engine.Engine;
import io.github.m1omg.shrink.desktop.engine.EngineException;
import io.github.m1omg.shrink.desktop.ui.components.Banner;
import io.github.m1omg.shrink.desktop.ui.components.OptionCard;
import io.github.m1omg.shrink.desktop.ui.components.ResponsiveGrid;
import io.github.m1omg.shrink.desktop.ui.components.SelectableCard;
import io.github.m1omg.shrink.desktop.ui.components.Timeline;
import io.github.m1omg.shrink.desktop.ui.components.Ui;
import io.github.m1omg.shrink.desktop.ui.components.VideoView;
import io.github.m1omg.shrink.desktop.ui.player.FrameSurface;
import io.github.m1omg.shrink.desktop.ui.player.Playback;
import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Cuts the video to a selected range, precisely (re-encode) or instantly (keyframe cut). */
public final class TrimPanel extends ToolPanel {
    private static final long serialVersionUID = 1L;
    private static final int STRIP_FRAMES = 12;

    private final ExecutorService frameWorker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "shrink-trim-frames");
        t.setDaemon(true);
        return t;
    });
    private final AtomicLong frameRequest = new AtomicLong();
    private final AtomicLong stripRequest = new AtomicLong();

    private boolean accurate = true;
    private Plan plan;
    private VideoView view;
    private Timeline timeline;
    private JTextField startField;
    private JTextField endField;
    private JLabel selection;
    private JButton play;
    private Playback playback;

    public TrimPanel(AppContext app, ToolHost host) {
        super(app, host, Tool.TRIM);
    }

    @Override
    protected void buildFor(MediaInfo info) {
        stopPlayback();
        plan = null;
        if (!info.hasVideo()) {
            body.add(new Banner(Banner.Kind.INFO, I18n.tr("file.noVideo")));
            return;
        }
        view = new VideoView();
        view.setPreferredSize(new Dimension(560, 300));
        view.setImage(host.thumbnail());
        view.onPlaybackFinished(this::playbackEnded);
        body.add(view);

        play = new JButton(I18n.tr("trim.play"), Theme.icon("play", 14));
        play.addActionListener(e -> togglePlayback());
        selection = new JLabel();
        selection.putClientProperty("FlatLaf.style", "font: bold");
        JPanel controls = Ui.clear(new BorderLayout());
        controls.add(play, BorderLayout.WEST);
        controls.add(selection, BorderLayout.EAST);
        body.add(controls);

        timeline = new Timeline();
        timeline.setDuration(info.durationSeconds());
        timeline.setListener(this::timelineChanged);
        body.add(timeline);

        startField = timeField(true);
        endField = timeField(false);
        JButton setStart = new JButton(I18n.tr("trim.setStart"));
        setStart.addActionListener(e -> {
            if (timeline.playhead() >= 0) {
                timeline.setRange(timeline.playhead(), timeline.end());
                rangeChanged();
            }
        });
        JButton setEnd = new JButton(I18n.tr("trim.setEnd"));
        setEnd.addActionListener(e -> {
            if (timeline.playhead() >= 0) {
                timeline.setRange(timeline.start(), timeline.playhead());
                rangeChanged();
            }
        });
        JPanel fields = Ui.clear(new ResponsiveGrid(260, 14, 2, 1));
        fields.add(Ui.field(I18n.tr("trim.start"), Ui.row(6, startField, setStart)));
        fields.add(Ui.field(I18n.tr("trim.end"), Ui.row(6, endField, setEnd)));
        body.add(fields);

        body.add(Ui.heading(I18n.tr("trim.mode")));
        OptionCard precise = new OptionCard(Theme.icon("trim", 18), I18n.tr("trim.mode.accurate"),
                I18n.tr("trim.mode.accurate.hint"));
        OptionCard fast = new OptionCard(Theme.icon("sparkles", 18), I18n.tr("trim.mode.fast"),
                I18n.tr("trim.mode.fast.hint"));
        precise.setSelected(accurate);
        fast.setSelected(!accurate);
        precise.onSelected(() -> {
            accurate = true;
            recompute();
        });
        fast.onSelected(() -> {
            accurate = false;
            recompute();
        });
        SelectableCard.group(Arrays.asList(precise, fast));
        JPanel modes = Ui.clear(new ResponsiveGrid(240, 12, 2, 1));
        modes.add(precise);
        modes.add(fast);
        body.add(modes);

        rangeChanged();
        loadStrip(info);
    }

    /** Called when the thumbnail arrives after the panel was built. */
    public void thumbnailChanged(BufferedImage image) {
        if (view != null && playback == null && view.image() == null) {
            view.setImage(image);
        }
    }

    private JTextField timeField(boolean start) {
        JTextField field = new JTextField(8);
        field.addActionListener(e -> applyField(field, start));
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                applyField(field, start);
            }
        });
        return field;
    }

    private void applyField(JTextField field, boolean start) {
        double t = Formats.parseDuration(field.getText());
        if (!Double.isNaN(t)) {
            if (start) {
                timeline.setRange(t, timeline.end());
            } else {
                timeline.setRange(timeline.start(), t);
            }
            showFrameAt(start ? timeline.start() : timeline.end(), true);
        }
        rangeChanged();
    }

    private void timelineChanged(Timeline.Part part, double time, boolean adjusting) {
        stopPlayback();
        showFrameAt(time, !adjusting);
        if (part != Timeline.Part.PLAYHEAD) {
            rangeChanged();
        }
    }

    private void rangeChanged() {
        Formats f = I18n.fmt();
        startField.setText(f.durationPrecise(timeline.start()));
        endField.setText(f.durationPrecise(timeline.end()));
        double length = timeline.end() - timeline.start();
        selection.setText(I18n.tr("trim.keeps", f.durationPrecise(length), f.durationPrecise(source.durationSeconds())));
        recompute();
    }

    private void recompute() {
        TimeRange range = new TimeRange(timeline.start(), timeline.end());
        plan = range.coversWhole(source.durationSeconds()) ? null : app.planner().trim(source, range, accurate);
        fireChanged();
    }

    // ------------------------------------------------------------------ frames

    /** Shows the frame at {@code time}; only the latest request is fulfilled while dragging. */
    private void showFrameAt(double time, boolean exact) {
        Engine engine = host.engine();
        if (engine == null || view == null) {
            return;
        }
        long id = frameRequest.incrementAndGet();
        MediaInfo info = source;
        frameWorker.execute(() -> {
            if (frameRequest.get() != id) {
                return; // a newer request is waiting
            }
            try {
                double t = Math.min(time, Math.max(0, info.durationSeconds() - 0.05));
                BufferedImage img = engine.frame(sourcePath, info.video(), t, 960, 540, exact);
                SwingUtilities.invokeLater(() -> {
                    if (frameRequest.get() == id && img != null && playback == null) {
                        view.setImage(img);
                    }
                });
            } catch (EngineException e) {
                // keep the previous frame
            }
        });
    }

    private void loadStrip(MediaInfo info) {
        Engine engine = host.engine();
        if (engine == null) {
            return;
        }
        long id = stripRequest.incrementAndGet();
        Timeline target = timeline;
        app.background(() -> {
            BufferedImage[] images = new BufferedImage[STRIP_FRAMES];
            for (int i = 0; i < STRIP_FRAMES && stripRequest.get() == id; i++) {
                double t = (i + 0.5) * info.durationSeconds() / STRIP_FRAMES;
                try {
                    images[i] = engine.frame(sourcePath, info.video(), t, 160, 90, false);
                } catch (EngineException e) {
                    images[i] = null;
                }
                BufferedImage[] copy = images.clone();
                SwingUtilities.invokeLater(() -> {
                    if (stripRequest.get() == id) {
                        target.setStrip(copy);
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------------ playback

    private void togglePlayback() {
        if (playback != null) {
            stopPlayback();
            return;
        }
        Engine engine = host.engine();
        if (engine == null) {
            return;
        }
        double start = timeline.start();
        double length = timeline.end() - start;
        int w = Math.max(2, Math.min(960, source.video().displayWidth()));
        int h = (int) Math.round(w * (double) source.video().displayHeight() / source.video().displayWidth());
        Playback.Sound sound = source.hasAudio()
                ? new Playback.Sound(sourcePath, source.audio().index(), start) : null;
        FrameSurface surface = new FrameSurface() {
            @Override
            public void showFrames(BufferedImage[] frames, double time) {
                view.showFrames(frames, time);
                SwingUtilities.invokeLater(() -> timeline.setPlayhead(start + time));
            }

            @Override
            public void playbackFinished() {
                view.playbackFinished();
            }
        };
        double fps = Math.min(30, source.video().frameRate() > 0 ? source.video().frameRate() : 30);
        playback = new Playback(engine, Collections.singletonList(new Playback.Source(sourcePath, source.video(), start)),
                sound, length, fps, w, h, surface);
        play.setText(I18n.tr("trim.stop"));
        play.setIcon(Theme.icon("pause", 14));
        playback.start();
    }

    private void playbackEnded() {
        playback = null;
        if (play != null) {
            play.setText(I18n.tr("trim.play"));
            play.setIcon(Theme.icon("play", 14));
        }
    }

    private void stopPlayback() {
        if (playback != null) {
            playback.stop();
            playbackEnded();
        }
    }

    @Override
    public void hidden() {
        stopPlayback();
    }

    @Override
    public Plan plan() {
        return plan;
    }

    @Override
    public String actionText() {
        return I18n.tr("trim.action");
    }
}
