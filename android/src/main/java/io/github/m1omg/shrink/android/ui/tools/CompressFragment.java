package io.github.m1omg.shrink.android.ui.tools;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import io.github.m1omg.shrink.android.MainActivity;
import io.github.m1omg.shrink.android.R;
import io.github.m1omg.shrink.android.ShrinkApp;
import io.github.m1omg.shrink.android.media.EncoderSupport;
import io.github.m1omg.shrink.android.ui.GlassCard;
import io.github.m1omg.shrink.android.ui.PreviewActivity;
import io.github.m1omg.shrink.android.ui.Tool;
import io.github.m1omg.shrink.android.ui.Ui;
import io.github.m1omg.shrink.core.i18n.Formats;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.CompressionPreset;
import io.github.m1omg.shrink.core.model.Container;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.VideoCodec;
import io.github.m1omg.shrink.core.model.VideoStream;
import io.github.m1omg.shrink.core.plan.AudioChoice;
import io.github.m1omg.shrink.core.plan.CompressionRequest;
import io.github.m1omg.shrink.core.plan.Dimensions;
import io.github.m1omg.shrink.core.plan.JobPlanner;
import io.github.m1omg.shrink.core.plan.Plan;
import io.github.m1omg.shrink.core.plan.PlanText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Compression with four modes, size estimates, a 5-second preview and optional advanced settings. */
public final class CompressFragment extends ToolFragment {

    private CompressionPreset preset = CompressionPreset.BALANCED;
    private long targetBytes;
    private final List<GlassCard> cards = new ArrayList<>();
    private Plan[] plans;
    private TextInputLayout targetLayout;
    private TextView warning;
    private GlassCard warningCard;
    private LinearLayout advanced;
    private Slider bitrateSlider;
    private MaterialSwitch autoBitrate;
    private TextView bitrateLabel;

    // Advanced overrides; null = automatic.
    private VideoCodec codec;
    private Container container;
    private Integer shortSide;
    private Double frameRate;
    private Long videoBitrate;
    private AudioChoice audio;

    private ActivityResultLauncher<Intent> preview;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preview = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                Plan plan = plan();
                if (plan != null && plan.feasible()) {
                    ((MainActivity) requireActivity()).startJob(plan.job(), fileSuffix());
                }
            }
        });
    }

    @Override
    protected Tool tool() {
        return Tool.COMPRESS;
    }

    @Override
    protected void build(Context c, MediaInfo info) {
        if (!info.hasVideo()) {
            message(c, I18n.tr("file.noVideo"));
            return;
        }
        targetBytes = JobPlanner.suggestedTargetBytes(info);
        content.addView(Ui.secondary(c, I18n.tr("compress.original", I18n.fmt().size(info.sizeBytes()))));

        cards.clear();
        for (CompressionPreset p : CompressionPreset.values()) {
            GlassCard card = new GlassCard(c);
            card.setClickableCard(true);
            int pad = Ui.dp(c, 14);
            card.setPadding(pad, pad, pad, pad);
            card.addView(Ui.text(c, I18n.tr(p.titleKey()), 15.5f, true, R.color.text_primary));
            if (p == CompressionPreset.BALANCED) {
                card.addView(Ui.text(c, I18n.tr("preset.recommended"), 12, true, R.color.accent), Ui.matchWrap(c, 2));
            }
            TextView size = Ui.text(c, "", 22, true, R.color.text_primary);
            size.setTag("size");
            card.addView(size, Ui.matchWrap(c, 8));
            TextView reductionText = Ui.text(c, "", 14, true, R.color.success);
            reductionText.setTag("reduction");
            card.addView(reductionText, Ui.matchWrap(c, 2));
            TextView description = Ui.secondary(c, I18n.tr(p.descriptionKey()));
            description.setMaxLines(4);
            card.addView(description, Ui.matchWrap(c, 6));
            cards.add(card);
        }
        Ui.singleChoice(cards, preset.ordinal(), index -> {
            preset = CompressionPreset.values()[index];
            videoBitrate = null;
            recompute();
        });
        content.addView(Ui.grid(c, cards, 2, 10), Ui.matchWrap(c, 12));

        targetLayout = new TextInputLayout(c);
        targetLayout.setHint(I18n.tr("preset.target_size.title"));
        targetLayout.setSuffixText(I18n.tr("compress.targetSize.unit"));
        targetLayout.setHelperText(I18n.tr("compress.targetSize.hint"));
        targetLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText targetField = new TextInputEditText(targetLayout.getContext());
        targetField.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        targetField.setText(I18n.fmt().megabytes(targetBytes));
        targetLayout.addView(targetField);
        targetField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                double mb = Formats.parseDecimal(s.toString());
                if (!Double.isNaN(mb) && mb > 0) {
                    targetBytes = Math.round(mb * 1_000_000.0);
                    recompute();
                }
            }
        });
        content.addView(targetLayout, Ui.matchWrap(c, 12));

        warningCard = new GlassCard(c);
        warning = Ui.text(c, "", 14, false, R.color.warning);
        warningCard.addView(warning);
        content.addView(warningCard, Ui.matchWrap(c, 12));

        MaterialButton previewButton = Ui.glassButton(c, I18n.tr("compress.preview"), R.drawable.ic_eye);
        previewButton.setOnClickListener(v -> openPreview());
        content.addView(previewButton, Ui.matchWrap(c, 14));

        content.addView(advancedSection(c, info), Ui.matchWrap(c, 12));
        recompute();
    }

    private View advancedSection(Context c, MediaInfo info) {
        GlassCard card = new GlassCard(c);
        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.heading(c, I18n.tr("compress.advanced"));
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        View chevron = Ui.icon(c, R.drawable.ic_chevron_down, 22, R.color.text_secondary);
        header.addView(chevron);
        card.addView(header);

        advanced = new LinearLayout(c);
        advanced.setOrientation(LinearLayout.VERTICAL);
        advanced.setVisibility(ShrinkApp.get().settings().isShowAdvanced() ? View.VISIBLE : View.GONE);
        header.setOnClickListener(v -> {
            boolean open = advanced.getVisibility() != View.VISIBLE;
            advanced.setVisibility(open ? View.VISIBLE : View.GONE);
            chevron.animate().rotation(open ? 180 : 0).setDuration(200).start();
        });
        advanced.addView(Ui.secondary(c, I18n.tr("compress.advanced.hint")), Ui.matchWrap(c, 8));

        List<VideoCodec> codecs = new ArrayList<>();
        codecs.add(null);
        codecs.addAll(EncoderSupport.videoCodecs());
        addChips(c, I18n.tr("advanced.codec"), codecs, null,
                v -> v == null ? I18n.tr("advanced.auto") : v.displayName(), v -> {
                    codec = v;
                    recompute();
                });

        List<Container> containers = new ArrayList<>();
        containers.add(null);
        for (Container ct : Container.values()) {
            if (EncoderSupport.canWrite(ct)) {
                containers.add(ct);
            }
        }
        addChips(c, I18n.tr("advanced.container"), containers, null,
                v -> v == null ? I18n.tr("advanced.auto") : v.displayName(), v -> {
                    container = v;
                    recompute();
                });

        VideoStream video = info.video();
        List<Integer> sides = new ArrayList<>();
        sides.add(null);
        for (int s : Dimensions.standardShortSides()) {
            if (s < video.shortSide()) {
                sides.add(s);
            }
        }
        addChips(c, I18n.tr("advanced.resolution"), sides, null,
                v -> v == null ? I18n.tr("advanced.original") : Dimensions.nameOf(v), v -> {
                    shortSide = v;
                    recompute();
                });

        List<Double> rates = new ArrayList<>();
        rates.add(null);
        for (double r : new double[] {60, 50, 30, 25, 24, 15}) {
            if (r < video.frameRate() - 0.5) {
                rates.add(r);
            }
        }
        addChips(c, I18n.tr("advanced.frameRate"), rates, null,
                v -> v == null ? I18n.tr("advanced.original") : I18n.fmt().frameRate(v), v -> {
                    frameRate = v;
                    recompute();
                });

        addChips(c, I18n.tr("advanced.audio"), Arrays.asList(AudioChoice.AUTO, AudioChoice.COPY, AudioChoice.AAC,
                AudioChoice.NONE), AudioChoice.AUTO, v -> I18n.tr(v.titleKey()), v -> {
                    audio = v;
                    recompute();
                });

        LinearLayout bitrateRow = new LinearLayout(c);
        bitrateRow.setOrientation(LinearLayout.HORIZONTAL);
        bitrateRow.setGravity(Gravity.CENTER_VERTICAL);
        bitrateLabel = Ui.heading(c, I18n.tr("advanced.bitrate"));
        bitrateRow.addView(bitrateLabel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        autoBitrate = new MaterialSwitch(c);
        autoBitrate.setText(I18n.tr("advanced.auto"));
        autoBitrate.setChecked(true);
        bitrateRow.addView(autoBitrate);
        advanced.addView(bitrateRow, Ui.matchWrap(c, 14));
        bitrateSlider = new Slider(c);
        bitrateSlider.setValueFrom(0.2f);
        bitrateSlider.setValueTo(Math.max(1f, Math.round(info.videoBitrate() / 100_000.0) / 10f));
        bitrateSlider.setValue(Math.min(bitrateSlider.getValueTo(), 1f));
        bitrateSlider.setLabelFormatter(value -> I18n.fmt().bitrate(Math.round(value * 1_000_000)));
        bitrateSlider.setEnabled(false);
        bitrateSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                videoBitrate = Math.round(value * 1_000_000.0);
                recompute();
            }
        });
        autoBitrate.setOnCheckedChangeListener((button, checked) -> {
            bitrateSlider.setEnabled(!checked);
            videoBitrate = checked ? null : Math.round(bitrateSlider.getValue() * 1_000_000.0);
            recompute();
        });
        advanced.addView(bitrateSlider, Ui.matchWrap(c, 4));
        card.addView(advanced);
        return card;
    }

    private <T> void addChips(Context c, String label, List<T> values, T selected,
                              java.util.function.Function<T, String> text, java.util.function.Consumer<T> onSelect) {
        advanced.addView(Ui.heading(c, label), Ui.matchWrap(c, 14));
        advanced.addView(Ui.chips(c, values, selected, text, onSelect), Ui.matchWrap(c, 6));
    }

    private CompressionRequest request(CompressionPreset p) {
        CompressionRequest.Builder b = CompressionRequest.builder(p)
                .targetBytes(targetBytes)
                .codec(codec)
                .container(container)
                .shortSide(shortSide)
                .frameRate(frameRate)
                .audio(audio);
        if (p == preset) {
            b.videoBitrate(videoBitrate);
        }
        return b.build();
    }

    private void recompute() {
        if (source == null || !source.hasVideo() || cards.isEmpty()) {
            return;
        }
        JobPlanner planner = planner();
        Formats f = I18n.fmt();
        plans = new Plan[CompressionPreset.values().length];
        for (CompressionPreset p : CompressionPreset.values()) {
            Plan plan = planner.compress(source, request(p));
            plans[p.ordinal()] = plan;
            GlassCard card = cards.get(p.ordinal());
            TextView size = card.findViewWithTag("size");
            size.setText(plan.feasible() || p != CompressionPreset.TARGET_SIZE
                    ? I18n.tr("compress.estimate", f.size(plan.estimatedBytes()))
                    : I18n.tr("compress.targetSize.minimum", f.size(plan.minimumBytes())));
            double r = plan.reductionPercent();
            TextView reductionText = card.findViewWithTag("reduction");
            reductionText.setText(I18n.tr(r >= 0 ? "result.smaller" : "result.larger",
                    f.percent(Math.abs(Math.round(r)))));
            reductionText.setTextColor(Ui.color(requireContext(),
                    r > 0.5 && plan.feasible() ? R.color.success : R.color.warning));
        }
        targetLayout.setVisibility(preset == CompressionPreset.TARGET_SIZE ? View.VISIBLE : View.GONE);
        Plan current = plan();
        String warnings = PlanText.warnings(current);
        warningCard.setVisibility(warnings == null ? View.GONE : View.VISIBLE);
        warning.setText(warnings == null ? "" : warnings);
        if (videoBitrate == null && bitrateSlider != null) {
            float mbps = current.job().video().bitrate() / 1_000_000f;
            bitrateSlider.setValue(Math.max(bitrateSlider.getValueFrom(), Math.min(bitrateSlider.getValueTo(),
                    Math.round(mbps * 10) / 10f)));
        }
        refresh();
    }

    private void openPreview() {
        Plan plan = plan();
        if (plan == null) {
            return;
        }
        ShrinkApp.get().state().setPreviewJob(plan.job());
        preview.launch(new Intent(requireContext(), PreviewActivity.class));
    }

    @Override
    protected Plan plan() {
        return plans == null ? null : plans[preset.ordinal()];
    }

    @Override
    protected String actionText() {
        return I18n.tr("compress.action");
    }
}
