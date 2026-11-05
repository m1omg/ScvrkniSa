package io.github.m1omg.shrink.desktop.ui.tools;

import io.github.m1omg.shrink.core.i18n.Formats;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.AudioStream;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.SubtitleStream;
import io.github.m1omg.shrink.core.model.VideoStream;
import io.github.m1omg.shrink.core.plan.Plan;
import io.github.m1omg.shrink.desktop.AppContext;
import io.github.m1omg.shrink.desktop.ui.components.ResponsiveGrid;
import io.github.m1omg.shrink.desktop.ui.components.RoundedPanel;
import io.github.m1omg.shrink.desktop.ui.components.StackLayout;
import io.github.m1omg.shrink.desktop.ui.components.Ui;
import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Technical details of the video: format, codecs, bitrates. */
public final class InfoPanel extends ToolPanel {
    private static final long serialVersionUID = 1L;

    public InfoPanel(AppContext app, ToolHost host) {
        super(app, host, Tool.INFO);
    }

    @Override
    protected void buildFor(MediaInfo info) {
        List<Section> sections = sections(info);
        JPanel grid = Ui.clear(new ResponsiveGrid(320, 14, 2, 1));
        for (Section s : sections) {
            RoundedPanel card = new RoundedPanel(new StackLayout(12));
            card.arc(16);
            card.setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
            JLabel title = Ui.heading(s.title);
            title.setIcon(Theme.accentIcon(s.icon, 18));
            title.setIconTextGap(8);
            card.add(title);
            card.add(Ui.keyValues(s.rows.toArray(new String[0][])));
            grid.add(card);
        }
        body.add(grid);
        JButton copy = new JButton(I18n.tr("info.copy"));
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(asText(sections)), null);
            copy.setText(I18n.tr("info.copied"));
            Timer t = new Timer(1600, ev -> copy.setText(I18n.tr("info.copy")));
            t.setRepeats(false);
            t.start();
        });
        body.add(Ui.row(0, copy));
    }

    private static final class Section {
        final String title;
        final String icon;
        final List<String[]> rows = new ArrayList<>();

        Section(String title, String icon) {
            this.title = title;
            this.icon = icon;
        }

        void add(String key, String value) {
            if (value != null && !value.trim().isEmpty()) {
                rows.add(new String[] {I18n.tr(key), value});
            }
        }
    }

    static List<Section> sections(MediaInfo info) {
        Formats f = I18n.fmt();
        List<Section> list = new ArrayList<>();
        Section file = new Section(I18n.tr("info.section.file"), "film");
        file.add("info.name", info.fileName());
        String parent = new File(info.location()).getParent();
        file.add("info.location", parent);
        file.add("info.size", f.size(info.sizeBytes()) + "  (" + f.number(info.sizeBytes(), 0) + " B)");
        file.add("info.format", info.formatLongName().isEmpty() ? info.formatName() : info.formatLongName());
        file.add("info.duration", f.durationPrecise(info.durationSeconds()));
        file.add("info.overallBitrate", f.bitrate(info.overallBitrate()));
        String created = info.tags().get("creation_time");
        if (created != null) {
            file.add("info.created", created.replace('T', ' ').replaceAll("\\.\\d+Z$", " UTC").replace("Z", " UTC"));
        }
        String make = info.tags().getOrDefault("com.apple.quicktime.make", "");
        String model = info.tags().getOrDefault("com.apple.quicktime.model", "");
        if (!(make + model).isEmpty()) {
            file.add("info.device", (make + " " + model).trim());
        }
        file.add("info.encoder", info.tags().getOrDefault("encoder",
                info.tags().getOrDefault("com.apple.quicktime.software", "")));
        list.add(file);

        VideoStream v = info.video();
        if (v != null) {
            Section video = new Section(I18n.tr("info.section.video"), "video");
            video.add("info.codec", v.codecName().toUpperCase(Locale.ROOT) + (v.codecLongName().isEmpty() ? ""
                    : "  (" + v.codecLongName() + ")"));
            video.add("info.profile", v.profile());
            video.add("info.resolution", f.resolution(v.displayWidth(), v.displayHeight()));
            video.add("info.aspect", aspect(v.displayWidth(), v.displayHeight()));
            video.add("info.frameRate", f.frameRate(v.frameRate()));
            video.add("info.videoBitrate", f.bitrate(info.videoBitrate()));
            video.add("info.pixelFormat", v.pixelFormat());
            video.add("info.bitDepth", I18n.tr("info.bitDepth.value", v.bitDepth()));
            video.add("info.dynamicRange", v.isHdr() ? v.hdrLabel() : I18n.tr("info.dynamicRange.sdr"));
            video.add("info.rotation", v.rotation() == 0 ? I18n.tr("info.rotation.none") : v.rotation() + "°");
            if (v.frameCount() > 0) {
                video.add("info.frames", f.number(v.frameCount(), 0));
            }
            list.add(video);
        }
        int n = 1;
        for (AudioStream a : info.audioStreams()) {
            Section audio = new Section(info.audioStreams().size() > 1 ? I18n.tr("info.section.audioN", n)
                    : I18n.tr("info.section.audio"), "audio");
            audio.add("info.codec", a.codecName().toUpperCase(Locale.ROOT) + (a.profile().isEmpty() ? ""
                    : " " + a.profile()));
            audio.add("info.channels", channels(a));
            audio.add("info.sampleRate", f.sampleRate(a.sampleRate()));
            audio.add("info.audioBitrate", a.bitrate() > 0 ? f.bitrate(a.bitrate()) : I18n.tr("info.unknown"));
            audio.add("info.language", a.language());
            list.add(audio);
            n++;
        }
        if (!info.subtitleStreams().isEmpty()) {
            Section subs = new Section(I18n.tr("info.section.subtitles"), "menu");
            List<String> names = new ArrayList<>();
            for (SubtitleStream s : info.subtitleStreams()) {
                names.add(s.codecName() + (s.language().isEmpty() ? "" : " (" + s.language() + ")"));
            }
            subs.add("info.tracks", String.join(", ", names));
            list.add(subs);
        }
        return list;
    }

    private static String channels(AudioStream a) {
        switch (a.channels()) {
            case 1:
                return I18n.tr("info.channels.mono");
            case 2:
                return I18n.tr("info.channels.stereo");
            default:
                return I18n.tr("info.channels.n", a.channels()) + (a.channelLayout().isEmpty() ? ""
                        : " (" + a.channelLayout() + ")");
        }
    }

    private static String aspect(int w, int h) {
        if (w <= 0 || h <= 0) {
            return "";
        }
        int g = gcd(w, h);
        int aw = w / g;
        int ah = h / g;
        if (aw > 50 || ah > 50) {
            return I18n.fmt().compactNumber((double) w / h, 2) + ":1";
        }
        return aw + ":" + ah;
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    private static String asText(List<Section> sections) {
        StringBuilder sb = new StringBuilder();
        for (Section s : sections) {
            sb.append(s.title).append('\n');
            for (String[] row : s.rows) {
                sb.append("  ").append(row[0]).append(": ").append(row[1].replace(' ', ' ')).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString().trim() + "\n";
    }

    @Override
    public Plan plan() {
        return null;
    }

    @Override
    public String actionText() {
        return null;
    }

    @Override
    public boolean canRun() {
        return false;
    }
}
