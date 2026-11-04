package io.github.m1omg.shrink.desktop.ui.tools;

import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.plan.Plan;
import io.github.m1omg.shrink.desktop.AppContext;
import io.github.m1omg.shrink.desktop.ui.components.ScrollablePanel;
import io.github.m1omg.shrink.desktop.ui.components.StackLayout;
import io.github.m1omg.shrink.desktop.ui.components.Ui;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class of the tool screens. A tool shows options for the current video
 * and offers a {@link Plan} (what would happen when the main button is pressed).
 */
public abstract class ToolPanel extends ScrollablePanel {
    private static final long serialVersionUID = 1L;

    protected final AppContext app;
    protected final ToolHost host;
    protected final Tool tool;
    protected MediaInfo source;
    protected Path sourcePath;
    private final List<Runnable> changeListeners = new ArrayList<>();
    /** Holds the tool's own content below the title. */
    protected final JPanel body = Ui.clear(new StackLayout(16));

    protected ToolPanel(AppContext app, ToolHost host, Tool tool) {
        super(new StackLayout(18));
        this.app = app;
        this.host = host;
        this.tool = tool;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(22, 28, 28, 28));
        JPanel header = Ui.clear(new BorderLayout(0, 4));
        header.add(Ui.title(I18n.tr(tool.titleKey())), BorderLayout.NORTH);
        header.add(Ui.secondary(I18n.tr(tool.descriptionKey())), BorderLayout.CENTER);
        add(header);
        add(body);
    }

    public Tool tool() {
        return tool;
    }

    /** Called when a (new) video was opened. */
    public final void setSource(MediaInfo info, Path path) {
        this.source = info;
        this.sourcePath = path;
        body.removeAll();
        if (info != null) {
            buildFor(info);
        }
        revalidate();
        repaint();
        fireChanged();
    }

    /** Builds the tool's controls for this video. */
    protected abstract void buildFor(MediaInfo info);

    /** What pressing the main button would do, or {@code null} if nothing can be done. */
    public abstract Plan plan();

    /** Text of the main button. */
    public abstract String actionText();

    /** Extra text for the output file name, e.g. "-720p". Defaults to the job kind's suffix. */
    public String fileSuffix() {
        return tool.kind() == null ? "" : tool.kind().fileSuffix();
    }

    /** Whether the main button can be pressed. */
    public boolean canRun() {
        Plan p = plan();
        return p != null && p.feasible();
    }

    /** The tool became visible. */
    public void shown() {
    }

    /** The tool was hidden (stop playback, etc.). */
    public void hidden() {
    }

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    /** Tells the window that the plan changed (estimate, output name, button state). */
    protected void fireChanged() {
        for (Runnable r : changeListeners) {
            r.run();
        }
    }
}
