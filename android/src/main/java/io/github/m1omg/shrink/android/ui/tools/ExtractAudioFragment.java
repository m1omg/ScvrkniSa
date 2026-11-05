package io.github.m1omg.shrink.android.ui.tools;

import android.content.Context;

import io.github.m1omg.shrink.android.R;
import io.github.m1omg.shrink.android.ui.GlassCard;
import io.github.m1omg.shrink.android.ui.Tool;
import io.github.m1omg.shrink.android.ui.Ui;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.AudioFileFormat;
import io.github.m1omg.shrink.core.model.AudioStream;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.plan.Plan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Saves the sound as M4A (AAC) or MP3. */
public final class ExtractAudioFragment extends ToolFragment {

    private AudioFileFormat format = AudioFileFormat.M4A;
    private long bitrate;
    private Plan plan;
    private android.widget.LinearLayout qualityHolder;

    @Override
    protected Tool tool() {
        return Tool.EXTRACT_AUDIO;
    }

    @Override
    protected void build(Context c, MediaInfo info) {
        if (!info.hasAudio()) {
            message(c, I18n.tr("extract.noAudio"));
            return;
        }
        content.addView(Ui.heading(c, I18n.tr("extract.format")));
        List<GlassCard> cards = new ArrayList<>();
        for (AudioFileFormat f : AudioFileFormat.values()) {
            cards.add(Ui.option(c, R.drawable.ic_audio, f.displayName(), I18n.tr("extract." + f.id() + ".hint")));
        }
        Ui.singleChoice(cards, format.ordinal(), index -> {
            format = AudioFileFormat.values()[index];
            showQuality(c);
            recompute();
        });
        content.addView(Ui.grid(c, cards, 2, 10), Ui.matchWrap(c, 8));
        content.addView(Ui.heading(c, I18n.tr("extract.quality")), Ui.matchWrap(c, 18));
        qualityHolder = new android.widget.LinearLayout(c);
        content.addView(qualityHolder, Ui.matchWrap(c, 6));
        showQuality(c);
        recompute();
    }

    /** "Keep original" is offered only when the sound already has the right codec. */
    private void showQuality(Context c) {
        AudioStream a = source.audio();
        String codec = a.codecName().toLowerCase(Locale.ROOT);
        boolean canCopy = (format == AudioFileFormat.M4A && codec.equals("aac"))
                || (format == AudioFileFormat.MP3 && codec.equals("mp3"));
        List<Long> values = new ArrayList<>();
        if (canCopy) {
            values.add(0L);
        }
        values.addAll(Arrays.asList(128_000L, 192_000L, 256_000L, 320_000L));
        bitrate = canCopy ? 0 : 192_000;
        qualityHolder.removeAllViews();
        qualityHolder.addView(Ui.chips(c, values, bitrate, v -> v == 0 ? I18n.tr("extract.keepOriginal")
                : I18n.tr("extract.bitrate.label", v / 1000), v -> {
                    bitrate = v;
                    recompute();
                }));
    }

    private void recompute() {
        plan = planner().extractAudio(source, format, bitrate, true, 0);
        refresh();
    }

    @Override
    protected Plan plan() {
        return plan;
    }

    @Override
    protected String actionText() {
        return I18n.tr("extract.action");
    }
}
