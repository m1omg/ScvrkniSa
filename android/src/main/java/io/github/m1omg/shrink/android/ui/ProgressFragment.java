package io.github.m1omg.shrink.android.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.m1omg.shrink.android.JobController;
import io.github.m1omg.shrink.android.MainActivity;
import io.github.m1omg.shrink.android.R;
import io.github.m1omg.shrink.android.ShrinkApp;
import io.github.m1omg.shrink.android.media.OutputStore;
import io.github.m1omg.shrink.core.i18n.Formats;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.Job;
import io.github.m1omg.shrink.core.model.JobKind;

/** Shows the running job and then its result (or what went wrong). */
public final class ProgressFragment extends Fragment implements JobController.Listener {

    private ProgressRing ring;
    private TextView percent;
    private ImageView resultIcon;
    private TextView title;
    private TextView big;
    private TextView subtitle;
    private TextView detail;
    private LinearLayout buttons;
    private JobController.State shownState;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        NestedScrollView scroll = new NestedScrollView(c);
        scroll.setFillViewport(true);
        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        int pad = getResources().getDimensionPixelSize(R.dimen.screen_padding);
        column.setPadding(pad, pad, pad, pad);

        GlassCard card = new GlassCard(c);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(Ui.dp(c, 24), Ui.dp(c, 32), Ui.dp(c, 24), Ui.dp(c, 24));
        FrameLayout ringFrame = new FrameLayout(c);
        ring = new ProgressRing(c);
        ringFrame.addView(ring, new FrameLayout.LayoutParams(Ui.dp(c, 200), Ui.dp(c, 200), Gravity.CENTER));
        percent = Ui.text(c, "", 40, true, R.color.text_primary);
        ringFrame.addView(percent, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        resultIcon = new ImageView(c);
        resultIcon.setVisibility(View.GONE);
        ringFrame.addView(resultIcon, new FrameLayout.LayoutParams(Ui.dp(c, 84), Ui.dp(c, 84), Gravity.CENTER));
        card.addView(ringFrame, new LinearLayout.LayoutParams(Ui.dp(c, 200), Ui.dp(c, 200)));

        title = Ui.title(c, "");
        title.setGravity(Gravity.CENTER);
        card.addView(title, Ui.matchWrap(c, 20));
        big = Ui.text(c, "", 32, true, R.color.text_primary);
        big.setGravity(Gravity.CENTER);
        card.addView(big, Ui.matchWrap(c, 8));
        subtitle = Ui.body(c, "");
        subtitle.setGravity(Gravity.CENTER);
        card.addView(subtitle, Ui.matchWrap(c, 8));
        detail = Ui.secondary(c, "");
        detail.setGravity(Gravity.CENTER);
        card.addView(detail, Ui.matchWrap(c, 6));
        buttons = new LinearLayout(c);
        buttons.setOrientation(LinearLayout.VERTICAL);
        card.addView(buttons, Ui.matchWrap(c, 22));
        column.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.addView(column);
        return scroll;
    }

    @Override
    public void onStart() {
        super.onStart();
        ShrinkApp.get().jobs().addListener(this);
        shownState = null;
        jobChanged();
    }

    @Override
    public void onStop() {
        ShrinkApp.get().jobs().removeListener(this);
        super.onStop();
    }

    @Override
    public void jobChanged() {
        if (!isAdded()) {
            return;
        }
        JobController jobs = ShrinkApp.get().jobs();
        Job job = jobs.job();
        switch (jobs.state()) {
            case RUNNING:
            case SAVING:
                showRunning(jobs, job);
                break;
            case DONE:
                if (shownState != JobController.State.DONE) {
                    showDone(jobs, job);
                }
                break;
            case FAILED:
                if (shownState != JobController.State.FAILED) {
                    showFailed(jobs);
                }
                break;
            default:
                ((MainActivity) requireActivity()).showHome();
                return;
        }
        shownState = jobs.state();
    }

    private void showRunning(JobController jobs, Job job) {
        Context c = requireContext();
        Formats f = I18n.fmt();
        resultIcon.setVisibility(View.GONE);
        percent.setVisibility(View.VISIBLE);
        int p = jobs.percent();
        if (jobs.state() == JobController.State.SAVING || p <= 0) {
            ring.setIndeterminate(true);
        } else {
            ring.setProgress(p / 100f);
        }
        percent.setText(p > 0 ? f.percent(p) : "");
        title.setText(job == null ? "" : I18n.tr(job.kind().titleKey()));
        big.setVisibility(View.GONE);
        subtitle.setText(job == null ? "" : job.source().fileName());
        String eta = jobs.remainingSeconds() >= 0
                ? I18n.tr("progress.remaining", f.durationWords(jobs.remainingSeconds())) : null;
        if (jobs.state() == JobController.State.SAVING) {
            detail.setText(I18n.tr("progress.finishing"));
        } else if (jobs.isCorrecting()) {
            detail.setText(I18n.tr("progress.secondPass") + (eta == null ? "" : "\n" + eta));
        } else if (eta != null) {
            detail.setText(eta);
        } else {
            detail.setText(I18n.tr("progress.preparing"));
        }
        if (shownState != JobController.State.RUNNING && shownState != JobController.State.SAVING) {
            buttons.removeAllViews();
            MaterialButton cancel = Ui.glassButton(c, I18n.tr("progress.cancel"), R.drawable.ic_close);
            cancel.setOnClickListener(v -> new MaterialAlertDialogBuilder(c)
                    .setMessage(I18n.tr("progress.cancelConfirm"))
                    .setPositiveButton(I18n.tr("common.yes"), (d, w) -> ShrinkApp.get().jobs().cancel())
                    .setNegativeButton(I18n.tr("common.no"), null)
                    .show());
            buttons.addView(cancel, Ui.matchWrap(c, 0));
            buttons.addView(Ui.secondary(c, I18n.tr("android.notification.channel.description")), Ui.matchWrap(c, 10));
        }
    }

    private void showDone(JobController jobs, Job job) {
        Context c = requireContext();
        Formats f = I18n.fmt();
        ring.setProgress(1f);
        percent.setVisibility(View.GONE);
        resultIcon.setVisibility(View.VISIBLE);
        resultIcon.setImageResource(R.drawable.ic_check_circle);
        resultIcon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(c, R.color.success)));
        title.setText(I18n.tr("result.done"));
        big.setVisibility(View.VISIBLE);
        big.setText(f.size(jobs.resultBytes()));
        long original = job.source().sizeBytes();
        double reduction = original > 0 ? (1.0 - (double) jobs.resultBytes() / original) * 100 : 0;
        StringBuilder line = new StringBuilder();
        line.append(I18n.tr("result.original")).append(' ').append(f.size(original)).append("  →  ")
                .append(I18n.tr("result.result")).append(' ').append(f.size(jobs.resultBytes()));
        if (job.kind() != JobKind.EXTRACT_AUDIO && Math.abs(reduction) >= 0.5) {
            line.append("\n").append(I18n.tr(reduction >= 0 ? "result.smaller" : "result.larger",
                    f.percent(Math.abs(Math.round(reduction)))));
        }
        subtitle.setText(line.toString());
        detail.setText(I18n.tr("android.saved", OutputStore.folderLabel(job.isAudioOnly())) + "\n"
                + jobs.resultName());

        buttons.removeAllViews();
        Uri uri = jobs.resultUri();
        String mime = job.isAudioOnly() ? job.audioFormat().mimeType() : job.container().mimeType();
        MaterialButton open = Ui.glassButton(c, I18n.tr("result.open"), R.drawable.ic_play);
        open.setOnClickListener(v -> openResult(uri, mime));
        MaterialButton share = Ui.glassButton(c, I18n.tr("result.share"), R.drawable.ic_share);
        share.setOnClickListener(v -> shareResult(uri, mime));
        buttons.addView(Ui.row(c, 8, open, share), Ui.matchWrap(c, 0));
        MaterialButton done = Ui.primaryButton(c, I18n.tr("common.done"));
        done.setOnClickListener(v -> {
            ShrinkApp.get().jobs().dismiss();
            ((MainActivity) requireActivity()).showHome();
        });
        buttons.addView(done, Ui.matchWrap(c, 10));
    }

    private void showFailed(JobController jobs) {
        Context c = requireContext();
        ring.setProgress(0f);
        percent.setVisibility(View.GONE);
        resultIcon.setVisibility(View.VISIBLE);
        resultIcon.setImageResource(R.drawable.ic_error);
        resultIcon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(c, R.color.error)));
        title.setText(I18n.tr("result.failed"));
        big.setVisibility(View.GONE);
        subtitle.setText(I18n.tr("result.error", jobs.error()));
        detail.setText("");
        buttons.removeAllViews();
        MaterialButton close = Ui.primaryButton(c, I18n.tr("common.close"));
        close.setOnClickListener(v -> {
            ShrinkApp.get().jobs().dismiss();
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        });
        buttons.addView(close, Ui.matchWrap(c, 0));
    }

    private void openResult(Uri uri, String mime) {
        Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), I18n.tr("history.missing"), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareResult(Uri uri, String mime) {
        Intent send = new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, I18n.tr("android.share.title")));
    }
}
