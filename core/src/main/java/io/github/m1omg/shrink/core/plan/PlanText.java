package io.github.m1omg.shrink.core.plan;

import io.github.m1omg.shrink.core.i18n.Formats;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.AudioOutput;
import io.github.m1omg.shrink.core.model.Job;
import io.github.m1omg.shrink.core.model.RateControl;
import io.github.m1omg.shrink.core.model.VideoOutput;
import io.github.m1omg.shrink.core.model.VideoStream;

import java.util.ArrayList;
import java.util.List;

/** Human readable descriptions of plans and jobs. */
public final class PlanText {

    private PlanText() {
    }

    /** E.g. "H.264 · 1280 × 720 · 30 fps · 678 kbps · AAC 128 kbps · 2 passes". */
    public static String summary(Job job) {
        Formats f = I18n.fmt();
        List<String> parts = new ArrayList<>();
        VideoOutput v = job.video();
        VideoStream src = job.source().video();
        if (!job.isAudioOnly() && src != null) {
            if (v.isCopy()) {
                parts.add(I18n.tr("summary.videoCopy"));
            } else if (v.isEncode()) {
                parts.add(v.codec().displayName());
                int w = job.isScaled() ? job.outputWidth() : src.displayWidth();
                int h = job.isScaled() ? job.outputHeight() : src.displayHeight();
                if (job.rotation() == 90 || job.rotation() == 270) {
                    int t = w;
                    w = h;
                    h = t;
                }
                parts.add(f.resolution(w, h));
                double fps = job.outputFrameRate() > 0 ? job.outputFrameRate() : src.frameRate();
                if (fps > 0) {
                    parts.add(f.frameRate(fps));
                }
                if (v.rateControl() == RateControl.CONSTANT_QUALITY && !v.hardware()) {
                    parts.add(I18n.tr("summary.crf", Integer.toString(v.crf())));
                } else {
                    parts.add(f.bitrate(Math.round(SizeEstimator.videoBitrate(job))));
                }
                if (v.rateControl() == RateControl.TWO_PASS && !v.hardware()) {
                    parts.add(I18n.tr("summary.twoPass"));
                }
                if (v.hardware()) {
                    parts.add(I18n.tr("summary.hardware"));
                }
                if (job.toneMapHdr()) {
                    parts.add("HDR → SDR");
                }
            }
        }
        AudioOutput a = job.audio();
        if (!job.source().hasAudio() || a.isNone()) {
            parts.add(I18n.tr("summary.noAudio"));
        } else if (a.isCopy()) {
            parts.add(I18n.tr("summary.audioCopy"));
        } else {
            parts.add(a.codec().displayName() + " " + f.bitrate(a.bitrate()));
        }
        if (job.container() != null) {
            parts.add(job.container().displayName());
        } else if (job.audioFormat() != null) {
            parts.add(job.audioFormat().displayName());
        }
        return String.join("  ·  ", parts);
    }

    /** "≈ 1.5 MB  −62 %" for the action bar. */
    public static String estimate(Plan plan) {
        Formats f = I18n.fmt();
        String size = I18n.tr("compress.estimate", f.size(plan.estimatedBytes()));
        if (plan.job().source().sizeBytes() <= 0) {
            return size;
        }
        return size + "   " + f.reduction(plan.reductionPercent());
    }

    /** All warnings of a plan as one message, or {@code null}. */
    public static String warnings(Plan plan) {
        List<String> messages = new ArrayList<>();
        for (PlanWarning w : plan.warnings()) {
            if (w == PlanWarning.TARGET_TOO_SMALL) {
                messages.add(I18n.tr(w.messageKey(), I18n.fmt().size(plan.minimumBytes())));
            } else {
                messages.add(I18n.tr(w.messageKey()));
            }
        }
        return messages.isEmpty() ? null : String.join("\n", messages);
    }
}
