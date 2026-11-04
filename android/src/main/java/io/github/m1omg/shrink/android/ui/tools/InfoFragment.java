package io.github.m1omg.shrink.android.ui.tools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import io.github.m1omg.shrink.android.R;
import io.github.m1omg.shrink.android.ui.GlassCard;
import io.github.m1omg.shrink.android.ui.Tool;
import io.github.m1omg.shrink.android.ui.Ui;
import io.github.m1omg.shrink.core.i18n.Formats;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.AudioStream;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.VideoStream;
import io.github.m1omg.shrink.core.plan.Plan;

import java.util.Locale;

/** Technical details: format, codecs, bitrates. */
public final class InfoFragment extends ToolFragment {

    private final StringBuilder text = new StringBuilder();

    @Override
    protected Tool tool() {
        return Tool.INFO;
    }

    @Override
    protected void build(Context c, MediaInfo info) {
        Formats f = I18n.fmt();
        GlassCard file = section(c, I18n.tr("info.section.file"));
        add(c, file, "info.name", info.fileName());
        add(c, file, "info.size", f.size(info.sizeBytes()));
        add(c, file, "info.format", info.formatLongName().isEmpty() ? info.formatName() : info.formatLongName());
        add(c, file, "info.duration", f.durationPrecise(info.durationSeconds()));
        add(c, file, "info.overallBitrate", f.bitrate(info.overallBitrate()));
        add(c, file, "info.created", info.tags().get("creation_time"));
        content.addView(file, Ui.matchWrap(c, 8));

        VideoStream v = info.video();
        if (v != null) {
            GlassCard video = section(c, I18n.tr("info.section.video"));
            add(c, video, "info.codec", v.codecName().toUpperCase(Locale.ROOT));
            add(c, video, "info.resolution", f.resolution(v.displayWidth(), v.displayHeight()));
            add(c, video, "info.frameRate", f.frameRate(v.frameRate()));
            add(c, video, "info.videoBitrate", f.bitrate(info.videoBitrate()));
            add(c, video, "info.dynamicRange", v.isHdr() ? v.hdrLabel() : I18n.tr("info.dynamicRange.sdr"));
            add(c, video, "info.rotation", v.rotation() == 0 ? I18n.tr("info.rotation.none") : v.rotation() + "°");
            if (v.frameCount() > 0) {
                add(c, video, "info.frames", f.number(v.frameCount(), 0));
            }
            content.addView(video, Ui.matchWrap(c, 12));
        }
        int n = 1;
        for (AudioStream a : info.audioStreams()) {
            GlassCard audio = section(c, info.audioStreams().size() > 1 ? I18n.tr("info.section.audioN", n)
                    : I18n.tr("info.section.audio"));
            add(c, audio, "info.codec", a.codecName().toUpperCase(Locale.ROOT));
            add(c, audio, "info.channels", a.channels() == 1 ? I18n.tr("info.channels.mono")
                    : a.channels() == 2 ? I18n.tr("info.channels.stereo") : I18n.tr("info.channels.n", a.channels()));
            add(c, audio, "info.sampleRate", f.sampleRate(a.sampleRate()));
            add(c, audio, "info.audioBitrate", a.bitrate() > 0 ? f.bitrate(a.bitrate()) : I18n.tr("info.unknown"));
            add(c, audio, "info.language", a.language());
            content.addView(audio, Ui.matchWrap(c, 12));
            n++;
        }
        MaterialButton copy = Ui.glassButton(c, I18n.tr("info.copy"), R.drawable.ic_share);
        copy.setOnClickListener(view -> {
            ClipboardManager cm = c.getSystemService(ClipboardManager.class);
            cm.setPrimaryClip(ClipData.newPlainText(info.fileName(), text.toString()));
            Toast.makeText(c, I18n.tr("info.copied"), Toast.LENGTH_SHORT).show();
        });
        content.addView(copy, Ui.matchWrap(c, 16));
    }

    private GlassCard section(Context c, String title) {
        GlassCard card = new GlassCard(c);
        card.addView(Ui.heading(c, title));
        text.append(title).append('\n');
        return card;
    }

    private void add(Context c, GlassCard card, String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        card.addView(Ui.keyValue(c, I18n.tr(key), value), Ui.matchWrap(c, 8));
        text.append("  ").append(I18n.tr(key)).append(": ").append(value.replace(' ', ' ')).append('\n');
    }

    @Override
    protected Plan plan() {
        return null;
    }

    @Override
    protected String actionText() {
        return null;
    }
}
