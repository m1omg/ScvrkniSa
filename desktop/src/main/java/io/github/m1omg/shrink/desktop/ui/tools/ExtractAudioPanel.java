package io.github.m1omg.shrink.desktop.ui.tools;

import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.AudioFileFormat;
import io.github.m1omg.shrink.core.model.AudioStream;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.plan.Plan;
import io.github.m1omg.shrink.desktop.AppContext;
import io.github.m1omg.shrink.desktop.ui.components.Banner;
import io.github.m1omg.shrink.desktop.ui.components.Combo;
import io.github.m1omg.shrink.desktop.ui.components.OptionCard;
import io.github.m1omg.shrink.desktop.ui.components.ResponsiveGrid;
import io.github.m1omg.shrink.desktop.ui.components.SelectableCard;
import io.github.m1omg.shrink.desktop.ui.components.Ui;
import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.JPanel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Saves the sound as M4A (AAC) or MP3. */
public final class ExtractAudioPanel extends ToolPanel {
    private static final long serialVersionUID = 1L;

    private AudioFileFormat format = AudioFileFormat.M4A;
    private long bitrate;
    private int track;
    private Plan plan;
    private Combo<Long> quality;

    public ExtractAudioPanel(AppContext app, ToolHost host) {
        super(app, host, Tool.EXTRACT_AUDIO);
    }

    @Override
    protected void buildFor(MediaInfo info) {
        plan = null;
        track = 0;
        if (!info.hasAudio()) {
            body.add(new Banner(Banner.Kind.INFO, I18n.tr("extract.noAudio")));
            return;
        }
        body.add(Ui.heading(I18n.tr("extract.format")));
        JPanel grid = Ui.clear(new ResponsiveGrid(220, 12, 2, 1));
        List<SelectableCard> cards = new ArrayList<>();
        for (AudioFileFormat f : AudioFileFormat.values()) {
            OptionCard card = new OptionCard(Theme.icon("audio", 18), f.displayName(),
                    I18n.tr("extract." + f.id() + ".hint"));
            card.setSelected(f == format);
            card.onSelected(() -> {
                format = f;
                updateQualityChoices();
                recompute();
            });
            cards.add(card);
            grid.add(card);
        }
        SelectableCard.group(cards);
        body.add(grid);

        quality = new Combo<>(new ArrayList<>(), v -> v == null ? "" : v == 0
                ? I18n.tr("extract.keepOriginal") : I18n.tr("extract.bitrate.label", v / 1000));
        quality.addActionListener(e -> {
            Long v = quality.selected();
            if (v != null && v != bitrate) {
                bitrate = v;
                recompute();
            }
        });
        JPanel fields = Ui.clear(new ResponsiveGrid(240, 14, 2, 1));
        fields.add(Ui.field(I18n.tr("extract.quality"), quality));
        if (info.audioStreams().size() > 1) {
            List<Integer> indexes = new ArrayList<>();
            for (int i = 0; i < info.audioStreams().size(); i++) {
                indexes.add(i);
            }
            Combo<Integer> tracks = new Combo<>(indexes, i -> i == null ? "" : describe(info.audioStreams().get(i), i));
            tracks.addActionListener(e -> {
                Integer i = tracks.selected();
                if (i != null && i != track) {
                    track = i;
                    updateQualityChoices();
                    recompute();
                }
            });
            fields.add(Ui.field(I18n.tr("extract.track"), tracks));
        }
        body.add(fields);
        updateQualityChoices();
        recompute();
    }

    private static String describe(AudioStream a, int i) {
        StringBuilder sb = new StringBuilder(I18n.tr("info.section.audioN", i + 1));
        sb.append(" – ").append(a.codecName().toUpperCase(Locale.ROOT));
        if (!a.language().isEmpty()) {
            sb.append(", ").append(a.language());
        }
        if (!a.title().isEmpty()) {
            sb.append(", ").append(a.title());
        }
        return sb.toString();
    }

    /** "Keep original quality" is only offered when the codec already matches. */
    private void updateQualityChoices() {
        AudioStream a = source.audioStreams().get(track);
        String codec = a.codecName().toLowerCase(Locale.ROOT);
        boolean canCopy = (format == AudioFileFormat.M4A && codec.equals("aac"))
                || (format == AudioFileFormat.MP3 && codec.equals("mp3"));
        List<Long> values = new ArrayList<>();
        if (canCopy) {
            values.add(0L);
        }
        values.addAll(Arrays.asList(128_000L, 160_000L, 192_000L, 256_000L, 320_000L));
        quality.setItems(values);
        bitrate = canCopy ? 0 : 192_000;
        quality.select(bitrate);
    }

    private void recompute() {
        plan = app.planner().extractAudio(source, format, bitrate, true, track);
        fireChanged();
    }

    @Override
    public Plan plan() {
        return plan;
    }

    @Override
    public String actionText() {
        return I18n.tr("extract.action");
    }
}
