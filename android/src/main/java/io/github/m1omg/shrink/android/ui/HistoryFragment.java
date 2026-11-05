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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.m1omg.shrink.android.MainActivity;
import io.github.m1omg.shrink.android.R;
import io.github.m1omg.shrink.android.ShrinkApp;
import io.github.m1omg.shrink.core.i18n.Formats;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.JobKind;
import io.github.m1omg.shrink.core.store.HistoryEntry;
import io.github.m1omg.shrink.core.store.HistoryStore;
import io.github.m1omg.shrink.core.util.OutputNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The "History" tab: previous jobs, newest first. */
public final class HistoryFragment extends Fragment implements HistoryStore.Listener {

    private final List<HistoryEntry> entries = new ArrayList<>();
    private Adapter adapter;
    private View empty;
    private RecyclerView list;
    private MaterialButton clear;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = getResources().getDimensionPixelSize(R.dimen.screen_padding);

        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(pad, Ui.dp(c, 4), pad, Ui.dp(c, 8));
        header.addView(Ui.title(c, I18n.tr("history.title")),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        clear = Ui.glassButton(c, I18n.tr("history.clear"), R.drawable.ic_trash);
        clear.setOnClickListener(v -> new MaterialAlertDialogBuilder(c)
                .setMessage(I18n.tr("history.clearConfirm"))
                .setPositiveButton(I18n.tr("common.ok"), (d, w) -> ShrinkApp.get().history().clear())
                .setNegativeButton(I18n.tr("common.cancel"), null)
                .show());
        header.addView(clear);
        root.addView(header);

        FrameLayout frame = new FrameLayout(c);
        list = new RecyclerView(c);
        list.setLayoutManager(new LinearLayoutManager(c));
        list.setClipToPadding(false);
        list.setPadding(pad, 0, pad, ((MainActivity) requireActivity()).bottomNavSpace());
        adapter = new Adapter();
        list.setAdapter(adapter);
        frame.addView(list);

        LinearLayout emptyColumn = new LinearLayout(c);
        emptyColumn.setOrientation(LinearLayout.VERTICAL);
        emptyColumn.setGravity(Gravity.CENTER);
        emptyColumn.addView(Ui.icon(c, R.drawable.ic_history, 56, R.color.text_secondary));
        TextView title = Ui.heading(c, I18n.tr("history.empty.title"));
        title.setGravity(Gravity.CENTER);
        emptyColumn.addView(title, Ui.matchWrap(c, 12));
        TextView sub = Ui.secondary(c, I18n.tr("history.empty.subtitle"));
        sub.setGravity(Gravity.CENTER);
        emptyColumn.addView(sub, Ui.matchWrap(c, 4));
        empty = emptyColumn;
        frame.addView(emptyColumn, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(frame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        ShrinkApp.get().history().addListener(this);
        reload();
    }

    @Override
    public void onStop() {
        ShrinkApp.get().history().removeListener(this);
        super.onStop();
    }

    @Override
    public void historyChanged() {
        if (isAdded()) {
            requireActivity().runOnUiThread(this::reload);
        }
    }

    @SuppressWarnings("NotifyDataSetChanged")
    private void reload() {
        entries.clear();
        entries.addAll(ShrinkApp.get().history().entries());
        adapter.notifyDataSetChanged();
        boolean none = entries.isEmpty();
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        list.setVisibility(none ? View.GONE : View.VISIBLE);
        clear.setEnabled(!none);
    }

    private static Tool toolOf(JobKind kind) {
        for (Tool t : Tool.values()) {
            if (t.name().equals(kind.name())) {
                return t;
            }
        }
        return Tool.COMPRESS;
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            GlassCard card = new GlassCard(parent.getContext());
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(parent.getContext(), 10);
            card.setLayoutParams(lp);
            return new Holder(card);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(entries.get(position));
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }
    }

    private final class Holder extends RecyclerView.ViewHolder {
        private final GlassCard card;

        Holder(GlassCard card) {
            super(card);
            this.card = card;
        }

        void bind(HistoryEntry e) {
            Context c = card.getContext();
            Formats f = I18n.fmt();
            card.removeAllViews();
            Tool tool = toolOf(e.kind());

            LinearLayout top = new LinearLayout(c);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(Ui.iconBadge(c, tool.icon, 40));
            LinearLayout texts = new LinearLayout(c);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(Ui.dp(c, 12), 0, 0, 0);
            String name = e.outputName().isEmpty() ? e.inputName() : e.inputName() + "  →  " + e.outputName();
            TextView title = Ui.text(c, name, 14.5f, true, R.color.text_primary);
            title.setMaxLines(2);
            texts.addView(title);
            StringBuilder line = new StringBuilder(getString(tool.title));
            if (e.status() == HistoryEntry.Status.DONE && e.outputBytes() > 0) {
                line.append("  ·  ").append(f.size(e.inputBytes())).append(" → ").append(f.size(e.outputBytes()));
                if (e.kind() != JobKind.EXTRACT_AUDIO) {
                    line.append(" (").append(f.reduction(e.reductionPercent())).append(')');
                }
            } else {
                line.append("  ·  ").append(I18n.tr("history.status." + e.status().name().toLowerCase(Locale.ROOT)));
            }
            texts.addView(Ui.secondary(c, line.toString()), Ui.matchWrap(c, 2));
            texts.addView(Ui.secondary(c, f.dateTime(e.timestamp())), Ui.matchWrap(c, 2));
            if (e.status() == HistoryEntry.Status.FAILED && !e.message().isEmpty()) {
                texts.addView(Ui.text(c, e.message(), 12.5f, false, R.color.error), Ui.matchWrap(c, 2));
            }
            top.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            card.addView(top);

            if (e.status() == HistoryEntry.Status.DONE && !e.outputPath().isEmpty()) {
                Uri uri = Uri.parse(e.outputPath());
                String mime = mimeOf(e.outputName());
                MaterialButton open = Ui.glassButton(c, I18n.tr("history.open"), R.drawable.ic_play);
                open.setOnClickListener(v -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                    } catch (ActivityNotFoundException | SecurityException ex) {
                        Toast.makeText(c, I18n.tr("history.missing"), Toast.LENGTH_SHORT).show();
                    }
                });
                MaterialButton share = Ui.glassButton(c, I18n.tr("result.share"), R.drawable.ic_share);
                share.setOnClickListener(v -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                        .setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), I18n.tr("android.share.title"))));
                card.addView(Ui.row(c, 8, open, share), Ui.matchWrap(c, 12));
            }
            card.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(c)
                        .setMessage(I18n.tr("history.remove") + "?")
                        .setPositiveButton(I18n.tr("common.yes"), (d, w) -> ShrinkApp.get().history().remove(e.id()))
                        .setNegativeButton(I18n.tr("common.no"), null)
                        .show();
                return true;
            });
        }
    }

    private static String mimeOf(String fileName) {
        switch (OutputNames.extension(fileName)) {
            case "mov":
                return "video/quicktime";
            case "mkv":
                return "video/x-matroska";
            case "webm":
                return "video/webm";
            case "m4a":
                return "audio/mp4";
            case "mp3":
                return "audio/mpeg";
            default:
                return "video/mp4";
        }
    }
}
