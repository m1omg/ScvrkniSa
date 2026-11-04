package io.github.m1omg.shrink.android.ui.tools;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import io.github.m1omg.shrink.android.MainActivity;
import io.github.m1omg.shrink.android.R;
import io.github.m1omg.shrink.android.ShrinkApp;
import io.github.m1omg.shrink.android.media.EncoderSupport;
import io.github.m1omg.shrink.android.media.Media3Runner;
import io.github.m1omg.shrink.android.ui.Tool;
import io.github.m1omg.shrink.android.ui.Ui;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.Container;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.model.VideoCodec;
import io.github.m1omg.shrink.core.plan.EncodingDefaults;
import io.github.m1omg.shrink.core.plan.JobPlanner;
import io.github.m1omg.shrink.core.plan.Plan;
import io.github.m1omg.shrink.core.plan.PlanText;
import io.github.m1omg.shrink.core.store.Settings;

/**
 * Base of every tool screen: a header with a back button, scrolling content
 * built by the subclass, and a glass panel at the bottom with the estimated
 * size and the main button.
 */
public abstract class ToolFragment extends Fragment {

    protected LinearLayout content;
    protected MediaInfo source;
    private TextView estimate;
    private TextView reduction;
    private TextView summary;
    private MaterialButton action;
    private View actionPanel;

    protected abstract Tool tool();

    /** Builds the screen's controls for the video. */
    protected abstract void build(Context c, MediaInfo info);

    /** What the main button would do, or {@code null} when nothing can be done yet. */
    protected abstract Plan plan();

    /** Text of the main button, or {@code null} to hide the bottom panel. */
    protected abstract String actionText();

    /** Text added to the output file name. */
    protected String fileSuffix() {
        return tool() == Tool.INFO || plan() == null ? "" : plan().job().kind().fileSuffix();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_tool, parent, false);
        content = root.findViewById(R.id.content);
        estimate = root.findViewById(R.id.estimate);
        reduction = root.findViewById(R.id.reduction);
        summary = root.findViewById(R.id.summary);
        action = root.findViewById(R.id.action_button);
        actionPanel = root.findViewById(R.id.action_panel);
        ((TextView) root.findViewById(R.id.tool_title)).setText(tool().title);
        root.findViewById(R.id.back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        action.setOnClickListener(v -> run());
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        source = ShrinkApp.get().state().info();
        if (source == null) {
            view.post(() -> ((MainActivity) requireActivity()).showHome());
            return;
        }
        build(requireContext(), source);
        refresh();
    }

    /** Updates the bottom panel after the plan changed. */
    protected void refresh() {
        if (action == null) {
            return;
        }
        String text = actionText();
        actionPanel.setVisibility(text == null ? View.GONE : View.VISIBLE);
        if (text == null) {
            return;
        }
        Plan plan = plan();
        action.setText(text);
        action.setEnabled(plan != null && plan.feasible() && !ShrinkApp.get().jobs().isBusy());
        if (plan == null) {
            estimate.setText("");
            reduction.setVisibility(View.GONE);
            summary.setText("");
            return;
        }
        estimate.setText(I18n.tr("compress.estimate", I18n.fmt().size(plan.estimatedBytes())));
        double r = plan.reductionPercent();
        boolean showReduction = plan.job().source().sizeBytes() > 0 && !plan.job().isAudioOnly();
        reduction.setVisibility(showReduction ? View.VISIBLE : View.GONE);
        reduction.setText(I18n.fmt().reduction(r));
        reduction.setTextColor(Ui.color(requireContext(), r > 0.5 ? R.color.success : R.color.warning));
        summary.setText(PlanText.summary(Media3Runner.adapt(plan.job())));
    }

    private void run() {
        Plan plan = plan();
        if (plan == null || !plan.feasible()) {
            return;
        }
        ((MainActivity) requireActivity()).startJob(plan.job(), fileSuffix());
    }

    /**
     * Planner with Android's encoding defaults: phones encode in hardware, in
     * one pass, and only with codecs this phone supports.
     */
    protected static JobPlanner planner() {
        Settings s = ShrinkApp.get().settings();
        VideoCodec codec = EncoderSupport.canEncode(s.getVideoCodec()) ? s.getVideoCodec() : VideoCodec.H264;
        Container container = EncoderSupport.canWrite(s.getContainer()) ? s.getContainer() : Container.MP4;
        if (!container.supports(codec)) {
            codec = container.defaultVideoCodec();
        }
        return new JobPlanner(new EncodingDefaults(codec, container, s.getSpeed(), false, true, s.isKeepMetadata()));
    }

    /** A message card for screens that cannot be used with this file. */
    protected void message(Context c, String text) {
        content.addView(Ui.section(c, null, Ui.body(c, text)), Ui.matchWrap(c, 8));
    }
}
