package io.github.m1omg.shrink.desktop.ui.tools;

import io.github.m1omg.shrink.core.i18n.Formats;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.CompressionPreset;
import io.github.m1omg.shrink.core.model.Container;
import io.github.m1omg.shrink.core.model.EncoderSpeed;
import io.github.m1omg.shrink.core.model.Job;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.RateControl;
import io.github.m1omg.shrink.core.model.VideoCodec;
import io.github.m1omg.shrink.core.model.VideoStream;
import io.github.m1omg.shrink.core.plan.AudioChoice;
import io.github.m1omg.shrink.core.plan.CompressionRequest;
import io.github.m1omg.shrink.core.plan.Dimensions;
import io.github.m1omg.shrink.core.plan.JobPlanner;
import io.github.m1omg.shrink.core.plan.Plan;
import io.github.m1omg.shrink.core.plan.PlanText;
import io.github.m1omg.shrink.core.plan.QualityModel;
import io.github.m1omg.shrink.desktop.AppContext;
import io.github.m1omg.shrink.desktop.engine.Engine;
import io.github.m1omg.shrink.desktop.ui.components.Banner;
import io.github.m1omg.shrink.desktop.ui.components.Combo;
import io.github.m1omg.shrink.desktop.ui.components.ResponsiveGrid;
import io.github.m1omg.shrink.desktop.ui.components.RoundedPanel;
import io.github.m1omg.shrink.desktop.ui.components.SelectableCard;
import io.github.m1omg.shrink.desktop.ui.components.StackLayout;
import io.github.m1omg.shrink.desktop.ui.components.Ui;
import io.github.m1omg.shrink.desktop.ui.components.WrapLabel;
import io.github.m1omg.shrink.desktop.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.text.DefaultFormatter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The main tool: four compression modes with size estimates, a preview and optional advanced settings. */
public final class CompressPanel extends ToolPanel {
    private static final long serialVersionUID = 1L;

    private CompressionPreset selected = CompressionPreset.BALANCED;
    private long targetBytes;
    private List<Plan> plans;
    private final Map<CompressionPreset, PresetCard> cards = new EnumMap<>(CompressionPreset.class);
    private Banner warning;
    private WrapLabel summary;
    private JLabel summaryTitle;
    private JPanel advanced;
    private JButton advancedToggle;
    private boolean updating;

    // Advanced overrides (null = automatic)
    private VideoCodec codec;
    private Container container;
    private RateControl rateControl;
    private Long videoBitrate;
    private Integer crf;
    private EncoderSpeed speed;
    private Integer shortSide;
    private Double frameRate;
    private AudioChoice audio;
    private Long audioBitrate;
    private Boolean hardware;

    private Combo<VideoCodec> codecBox;
    private Combo<Container> containerBox;
    private Combo<RateControl> rateBox;
    private JSpinner bitrateSpinner;
    private JSlider crfSlider;
    private JLabel crfLabel;
    private JPanel bitrateField;
    private JPanel crfField;
    private Combo<EncoderSpeed> speedBox;
    private Combo<Integer> resolutionBox;
    private Combo<Double> fpsBox;
    private Combo<AudioChoice> audioBox;
    private Combo<Long> audioBitrateBox;
    private JCheckBox hardwareBox;

    public CompressPanel(AppContext app, ToolHost host) {
        super(app, host, Tool.COMPRESS);
    }

    @Override
    protected void buildFor(MediaInfo info) {
        cards.clear();
        plans = null;
        if (!info.hasVideo()) {
            body.add(new Banner(Banner.Kind.INFO, I18n.tr("file.noVideo")));
            return;
        }
        resetOverrides();
        targetBytes = JobPlanner.suggestedTargetBytes(info);

        JPanel grid = Ui.clear(new ResponsiveGrid(176, 12, 4, 2, 1));
        List<SelectableCard> group = new ArrayList<>();
        for (CompressionPreset preset : CompressionPreset.values()) {
            PresetCard card = new PresetCard(preset);
            cards.put(preset, card);
            group.add(card);
            grid.add(card);
            card.onSelected(() -> {
                if (selected != preset) {
                    selected = preset;
                    videoBitrate = null;
                    crf = null;
                    recompute();
                }
            });
        }
        SelectableCard.group(group);
        cards.get(selected).setSelected(true);
        body.add(grid);

        warning = new Banner();
        warning.setVisible(false);
        body.add(warning);

        JPanel summaryPanel = Ui.clear(new BorderLayout(0, 4));
        summaryTitle = new JLabel(I18n.tr("compress.auto.title"));
        summaryTitle.setIcon(Theme.accentIcon("sparkles", 16));
        summaryTitle.setIconTextGap(6);
        summaryTitle.putClientProperty("FlatLaf.style", "font: bold");
        summary = Ui.secondaryWrap("");
        summaryPanel.add(summaryTitle, BorderLayout.NORTH);
        summaryPanel.add(summary, BorderLayout.CENTER);
        body.add(summaryPanel);

        JButton preview = new JButton(I18n.tr("compress.preview"));
        preview.setIcon(Theme.icon("eye", 16));
        preview.setIconTextGap(8);
        preview.setToolTipText(I18n.tr("compress.preview.tooltip"));
        preview.addActionListener(e -> {
            Plan p = plan();
            if (p != null) {
                host.showPreview(p.job());
            }
        });
        advancedToggle = new JButton(I18n.tr("compress.advanced"));
        advancedToggle.putClientProperty("JButton.buttonType", "borderless");
        advancedToggle.addActionListener(e -> setAdvancedOpen(!advanced.isVisible()));
        JPanel buttons = Ui.clear(new BorderLayout());
        buttons.add(Ui.row(0, preview), BorderLayout.WEST);
        buttons.add(Ui.row(0, advancedToggle), BorderLayout.EAST);
        body.add(buttons);

        advanced = buildAdvanced(info);
        body.add(advanced);
        setAdvancedOpen(app.settings().isShowAdvanced());
        recompute();
    }

    private void resetOverrides() {
        codec = null;
        container = null;
        rateControl = null;
        videoBitrate = null;
        crf = null;
        speed = null;
        shortSide = null;
        frameRate = null;
        audio = null;
        audioBitrate = null;
        hardware = null;
    }

    private void setAdvancedOpen(boolean open) {
        advanced.setVisible(open);
        advancedToggle.setIcon(Theme.icon(open ? "chevron_down" : "chevron_right", 14));
        advancedToggle.setHorizontalTextPosition(JButton.LEADING);
        revalidate();
        repaint();
    }

    // ------------------------------------------------------------------ planning

    private CompressionRequest request(CompressionPreset preset) {
        CompressionRequest.Builder b = CompressionRequest.builder(preset)
                .targetBytes(targetBytes)
                .codec(codec)
                .container(container)
                .rateControl(rateControl)
                .speed(speed)
                .shortSide(shortSide)
                .frameRate(frameRate)
                .audio(audio)
                .audioBitrate(audioBitrate)
                .hardware(hardware != null ? hardware
                        : codec != null ? app.settings().isHardwareEncoding() && app.hasHardwareEncoder(codec) : null);
        if (preset == selected) {
            b.videoBitrate(videoBitrate).crf(crf);
        }
        return b.build();
    }

    private void recompute() {
        if (source == null || !source.hasVideo()) {
            return;
        }
        JobPlanner planner = app.planner();
        List<Plan> list = new ArrayList<>();
        for (CompressionPreset preset : CompressionPreset.values()) {
            list.add(planner.compress(source, request(preset)));
        }
        plans = list;
        for (CompressionPreset preset : CompressionPreset.values()) {
            cards.get(preset).show(plans.get(preset.ordinal()));
        }
        Plan current = plan();
        String warnings = PlanText.warnings(current);
        warning.setVisible(warnings != null);
        if (warnings != null) {
            warning.setKind(current.feasible() ? Banner.Kind.WARNING : Banner.Kind.ERROR);
            warning.setText(warnings);
        }
        boolean custom = request(selected).isCustomized();
        summaryTitle.setText(I18n.tr(custom ? "compress.customized" : "compress.auto.title"));
        summary.setText(PlanText.summary(current.job()));
        syncAdvanced(current.job());
        revalidate();
        repaint();
        fireChanged();
    }

    @Override
    public Plan plan() {
        return plans == null ? null : plans.get(selected.ordinal());
    }

    @Override
    public String actionText() {
        return I18n.tr("compress.action");
    }

    // ------------------------------------------------------------------ preset cards

    private final class PresetCard extends SelectableCard {
        private static final long serialVersionUID = 1L;

        private final CompressionPreset preset;
        private final JLabel size = new JLabel();
        private final JLabel reduction = new JLabel();
        private JSpinner spinner;

        PresetCard(CompressionPreset preset) {
            super(new StackLayout(6));
            this.preset = preset;
            arc(16);
            setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
            getAccessibleContext().setAccessibleName(I18n.tr(preset.titleKey()));

            JPanel titleRow = Ui.clear(new BorderLayout(6, 0));
            JLabel title = new JLabel(I18n.tr(preset.titleKey()));
            title.putClientProperty("FlatLaf.style", "font: bold");
            titleRow.add(title, BorderLayout.CENTER);
            if (preset == CompressionPreset.BALANCED) {
                JLabel badge = new JLabel(I18n.tr("preset.recommended"));
                badge.putClientProperty("FlatLaf.styleClass", "mini");
                badge.setForeground(Theme.accent());
                titleRow.add(badge, BorderLayout.SOUTH);
            }
            add(titleRow);

            if (preset == CompressionPreset.TARGET_SIZE) {
                spinner = new JSpinner(new SpinnerNumberModel(targetBytes / 1_000_000.0, 0.1, 1_000_000.0, 1.0));
                JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "0.##");
                DecimalFormat format = editor.getFormat();
                format.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(I18n.current().locale()));
                spinner.setEditor(editor);
                JFormattedTextField field = editor.getTextField();
                field.setColumns(5);
                if (field.getFormatter() instanceof DefaultFormatter) {
                    ((DefaultFormatter) field.getFormatter()).setCommitsOnValidEdit(true);
                }
                spinner.addChangeListener(e -> {
                    long bytes = Math.round(((Number) spinner.getValue()).doubleValue() * 1_000_000.0);
                    if (bytes != targetBytes) {
                        targetBytes = bytes;
                        if (!isSelected()) {
                            select();
                        } else {
                            recompute();
                        }
                    }
                });
                field.addFocusListener(new java.awt.event.FocusAdapter() {
                    @Override
                    public void focusGained(java.awt.event.FocusEvent e) {
                        select();
                    }
                });
                JLabel unit = new JLabel(I18n.tr("compress.targetSize.unit"));
                JPanel row = Ui.clear(new FlowLayout(FlowLayout.LEFT, 6, 0));
                row.add(spinner);
                row.add(unit);
                row.setToolTipText(I18n.tr("compress.targetSize.hint"));
                add(row);
                size.putClientProperty("FlatLaf.styleClass", "small");
                size.setForeground(Theme.secondaryText());
            } else {
                size.putClientProperty("FlatLaf.style", "font: bold +8");
            }
            add(size);
            reduction.putClientProperty("FlatLaf.style", "font: bold");
            add(reduction);
            WrapLabel description = new WrapLabel(I18n.tr(preset.descriptionKey())).color(Theme.secondaryText())
                    .font(java.awt.Font.PLAIN, -1);
            add(description);
            forwardClicks(description);
            forwardClicks(title);
            forwardClicks(size);
            forwardClicks(reduction);
        }

        void show(Plan plan) {
            Formats f = I18n.fmt();
            if (preset == CompressionPreset.TARGET_SIZE) {
                size.setText(plan.feasible() ? I18n.tr("compress.estimate", f.size(plan.estimatedBytes()))
                        : I18n.tr("compress.targetSize.minimum", f.size(plan.minimumBytes())));
            } else {
                size.setText(I18n.tr("compress.estimate", f.size(plan.estimatedBytes())));
            }
            double r = plan.reductionPercent();
            reduction.setText(I18n.tr(r >= 0 ? "result.smaller" : "result.larger", f.percent(Math.abs(Math.round(r)))));
            reduction.setForeground(r > 0.5 && plan.feasible() ? Theme.success() : Theme.warning());
        }
    }

    // ------------------------------------------------------------------ advanced settings

    private JPanel buildAdvanced(MediaInfo info) {
        RoundedPanel panel = new RoundedPanel(new StackLayout(14));
        panel.arc(16);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        panel.add(Ui.secondaryWrap(I18n.tr("compress.advanced.hint")));

        Engine engine = host.engine();
        List<VideoCodec> codecs = new ArrayList<>();
        for (VideoCodec c : VideoCodec.values()) {
            if (engine == null || engine.tools().canEncode(c)) {
                codecs.add(c);
            }
        }
        codecBox = new Combo<>(codecs, c -> c == null ? "" : c.displayName() + "  –  "
                + I18n.tr("codec." + c.id() + ".hint"));
        containerBox = new Combo<>(Arrays.asList(Container.values()), c -> c == null ? "" : c.displayName());
        rateBox = new Combo<>(Arrays.asList(RateControl.values()), r -> r == null ? "" : I18n.tr(r.titleKey()));
        bitrateSpinner = new JSpinner(new SpinnerNumberModel(1000, 30, 200_000, 50));
        crfSlider = new JSlider(0, 51, 23);
        crfLabel = new JLabel("23");
        speedBox = new Combo<>(Arrays.asList(EncoderSpeed.values()), s -> s == null ? "" : I18n.tr(s.titleKey()));

        VideoStream v = info.video();
        List<Integer> sides = new ArrayList<>();
        sides.add(0);
        for (int s : Dimensions.standardShortSides()) {
            if (s < v.shortSide()) {
                sides.add(s);
            }
        }
        resolutionBox = new Combo<>(sides, s -> s == null || s == 0
                ? I18n.tr("advanced.auto") + " (" + I18n.fmt().resolution(v.displayWidth(), v.displayHeight()) + ")"
                : Dimensions.nameOf(s) + "  (" + Dimensions.scaleToShortSide(v.displayWidth(), v.displayHeight(), s)
                .label() + ")");
        List<Double> rates = new ArrayList<>();
        rates.add(0.0);
        for (double r : new double[] {60, 50, 30, 25, 24, 15}) {
            if (r < v.frameRate() - 0.5) {
                rates.add(r);
            }
        }
        fpsBox = new Combo<>(rates, r -> r == null || r == 0 ? I18n.tr("advanced.auto") + " ("
                + I18n.fmt().frameRate(v.frameRate()) + ")" : I18n.fmt().frameRate(r));
        audioBox = new Combo<>(Arrays.asList(AudioChoice.values()), a -> a == null ? "" : I18n.tr(a.titleKey()));
        List<Long> audioRates = new ArrayList<>();
        audioRates.add(0L);
        for (long r : JobPlanner.audioBitrates()) {
            if (r >= 48_000) {
                audioRates.add(r);
            }
        }
        audioBitrateBox = new Combo<>(audioRates, r -> r == null || r == 0 ? I18n.tr("advanced.auto")
                : I18n.fmt().bitrate(r));
        hardwareBox = new JCheckBox(I18n.tr("advanced.hardware"));
        hardwareBox.setToolTipText(I18n.tr("advanced.hardware.hint"));

        JPanel grid = Ui.clear(new ResponsiveGrid(230, 14, 2, 1));
        grid.add(Ui.field(I18n.tr("advanced.codec"), codecBox));
        grid.add(Ui.field(I18n.tr("advanced.container"), containerBox));
        grid.add(Ui.field(I18n.tr("advanced.rateControl"), rateBox));
        JPanel bitrateRow = Ui.clear(new BorderLayout(6, 0));
        bitrateRow.add(bitrateSpinner, BorderLayout.CENTER);
        bitrateRow.add(new JLabel(I18n.tr("advanced.bitrate.unit")), BorderLayout.EAST);
        bitrateField = Ui.field(I18n.tr("advanced.bitrate"), bitrateRow);
        grid.add(bitrateField);
        JPanel crfRow = Ui.clear(new BorderLayout(8, 0));
        crfRow.add(crfSlider, BorderLayout.CENTER);
        crfRow.add(crfLabel, BorderLayout.EAST);
        crfRow.setToolTipText(I18n.tr("advanced.crf.hint"));
        crfField = Ui.field(I18n.tr("advanced.crf"), crfRow);
        grid.add(crfField);
        grid.add(Ui.field(I18n.tr("advanced.speed"), speedBox));
        grid.add(Ui.field(I18n.tr("advanced.resolution"), resolutionBox));
        grid.add(Ui.field(I18n.tr("advanced.frameRate"), fpsBox));
        grid.add(Ui.field(I18n.tr("advanced.audio"), audioBox));
        grid.add(Ui.field(I18n.tr("advanced.audioBitrate"), audioBitrateBox));
        panel.add(grid);

        JButton reset = new JButton(I18n.tr("compress.advanced.reset"));
        reset.addActionListener(e -> {
            resetOverrides();
            recompute();
        });
        JPanel bottom = Ui.clear(new BorderLayout());
        bottom.add(hardwareBox, BorderLayout.WEST);
        bottom.add(reset, BorderLayout.EAST);
        panel.add(bottom);

        codecBox.addActionListener(e -> changed(() -> codec = codecBox.selected()));
        containerBox.addActionListener(e -> changed(() -> {
            container = containerBox.selected();
            if (codec != null && container != null && !container.supports(codec)) {
                codec = null;
            }
        }));
        rateBox.addActionListener(e -> changed(() -> {
            rateControl = rateBox.selected();
            videoBitrate = null;
            crf = null;
        }));
        bitrateSpinner.addChangeListener(e -> changed(() ->
                videoBitrate = ((Number) bitrateSpinner.getValue()).longValue() * 1000L));
        crfSlider.addChangeListener(e -> {
            crfLabel.setText(Integer.toString(crfSlider.getValue()));
            if (!crfSlider.getValueIsAdjusting()) {
                changed(() -> crf = crfSlider.getValue());
            }
        });
        speedBox.addActionListener(e -> changed(() -> speed = speedBox.selected()));
        resolutionBox.addActionListener(e -> changed(() -> {
            Integer s = resolutionBox.selected();
            shortSide = s == null || s == 0 ? null : s;
        }));
        fpsBox.addActionListener(e -> changed(() -> {
            Double r = fpsBox.selected();
            frameRate = r == null || r == 0 ? null : r;
        }));
        audioBox.addActionListener(e -> changed(() -> audio = audioBox.selected()));
        audioBitrateBox.addActionListener(e -> changed(() -> {
            Long r = audioBitrateBox.selected();
            audioBitrate = r == null || r == 0 ? null : r;
        }));
        hardwareBox.addActionListener(e -> changed(() -> hardware = hardwareBox.isSelected()));
        return panel;
    }

    /** Applies a user change from an advanced control (ignored while the panel itself updates controls). */
    private void changed(Runnable apply) {
        if (updating) {
            return;
        }
        apply.run();
        recompute();
    }

    /** Shows the values the plan actually uses in the advanced controls. */
    private void syncAdvanced(Job job) {
        updating = true;
        try {
            codecBox.select(job.video().codec());
            Container c = job.container();
            containerBox.select(c);
            List<VideoCodec> allowed = new ArrayList<>();
            Engine engine = host.engine();
            for (VideoCodec vc : VideoCodec.values()) {
                if (c.supports(vc) && (engine == null || engine.tools().canEncode(vc))) {
                    allowed.add(vc);
                }
            }
            codecBox.setItems(allowed);
            codecBox.select(job.video().codec());
            rateBox.select(job.video().rateControl());
            boolean quality = job.video().rateControl() == RateControl.CONSTANT_QUALITY;
            bitrateField.setVisible(!quality);
            crfField.setVisible(quality);
            bitrateSpinner.setValue((int) Math.max(30, Math.round(job.video().bitrate() / 1000.0)));
            crfSlider.setMaximum(QualityModel.maxCrf(job.video().codec()));
            crfSlider.setValue(job.video().crf() > 0 ? job.video().crf()
                    : QualityModel.referenceCrf(job.video().codec()));
            crfLabel.setText(Integer.toString(crfSlider.getValue()));
            speedBox.select(job.video().speed());
            resolutionBox.select(shortSide == null ? 0 : shortSide);
            fpsBox.select(frameRate == null ? 0.0 : frameRate);
            audioBox.select(audio == null ? AudioChoice.AUTO : audio);
            audioBitrateBox.select(audioBitrate == null ? 0L : audioBitrate);
            boolean hwAvailable = engine != null && engine.tools().hardwareEncoder(job.video().codec()) != null;
            hardwareBox.setEnabled(hwAvailable);
            hardwareBox.setSelected(hwAvailable && job.video().hardware());
            hardwareBox.setToolTipText(I18n.tr(hwAvailable ? "advanced.hardware.hint"
                    : "advanced.hardware.unavailable"));
        } finally {
            updating = false;
        }
    }

    /** Makes the target-size field reachable from outside (keyboard shortcut friendliness). */
    JComponent targetField() {
        PresetCard card = cards.get(CompressionPreset.TARGET_SIZE);
        return card == null ? null : card.spinner;
    }
}
