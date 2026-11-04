package io.github.m1omg.shrink.android.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import io.github.m1omg.shrink.android.R;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Small builders for the glass-styled views, so screens stay short and consistent. */
public final class Ui {

    private Ui() {
    }

    public static int dp(Context c, float dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                c.getResources().getDisplayMetrics()));
    }

    public static int color(Context c, int res) {
        return ContextCompat.getColor(c, res);
    }

    public static LinearLayout.LayoutParams matchWrap(Context c, int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topMarginDp);
        return lp;
    }

    public static TextView text(Context c, String s, float sp, boolean bold, int colorRes) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color(c, colorRes));
        if (bold) {
            t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        }
        return t;
    }

    public static TextView title(Context c, String s) {
        return text(c, s, 24, true, R.color.text_primary);
    }

    public static TextView heading(Context c, String s) {
        return text(c, s, 17, true, R.color.text_primary);
    }

    public static TextView body(Context c, String s) {
        return text(c, s, 15, false, R.color.text_primary);
    }

    public static TextView secondary(Context c, String s) {
        return text(c, s, 13.5f, false, R.color.text_secondary);
    }

    /** An icon tinted with a colour resource. */
    public static ImageView icon(Context c, int drawable, int sizeDp, int colorRes) {
        ImageView v = new ImageView(c);
        v.setImageResource(drawable);
        v.setImageTintList(ColorStateList.valueOf(color(c, colorRes)));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    /** A white icon on a round accent-gradient badge, used on tool tiles. */
    public static View iconBadge(Context c, int drawable, int sizeDp) {
        FrameLayout frame = new FrameLayout(c);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {color(c, R.color.accent), color(c, R.color.accent_2)});
        bg.setShape(GradientDrawable.OVAL);
        frame.setBackground(bg);
        ImageView icon = new ImageView(c);
        icon.setImageResource(drawable);
        icon.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
        int inner = Math.round(sizeDp * 0.5f);
        frame.addView(icon, new FrameLayout.LayoutParams(dp(c, inner), dp(c, inner), Gravity.CENTER));
        frame.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return frame;
    }

    public static MaterialButton primaryButton(Context c, String text) {
        MaterialButton b = new MaterialButton(c);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    public static MaterialButton glassButton(Context c, String text, int iconRes) {
        MaterialButton b = new MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(text);
        b.setAllCaps(false);
        if (iconRes != 0) {
            b.setIconResource(iconRes);
        }
        return b;
    }

    /** A row that splits its children evenly. */
    public static LinearLayout row(Context c, int gapDp, View... children) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < children.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) {
                lp.leftMargin = dp(c, gapDp);
            }
            row.addView(children[i], lp);
        }
        return row;
    }

    /** Lays out views in a grid with {@code columns} equal columns. */
    public static LinearLayout grid(Context c, List<? extends View> views, int columns, int gapDp) {
        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < views.size(); i += columns) {
            View[] cells = new View[columns];
            for (int j = 0; j < columns; j++) {
                cells[j] = i + j < views.size() ? views.get(i + j) : new View(c);
            }
            LinearLayout r = row(c, gapDp, cells);
            column.addView(r, matchWrap(c, i == 0 ? 0 : gapDp));
        }
        return column;
    }

    /** A selectable option card: title, optional description and detail line. */
    public static GlassCard option(Context c, int iconRes, String title, String description) {
        GlassCard card = new GlassCard(c);
        card.setClickableCard(true);
        int pad = dp(c, 14);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        if (iconRes != 0) {
            ImageView icon = icon(c, iconRes, 20, R.color.text_primary);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) icon.getLayoutParams();
            lp.rightMargin = dp(c, 8);
            top.addView(icon, lp);
        }
        TextView t = text(c, title, 15.5f, true, R.color.text_primary);
        t.setTag("title");
        top.addView(t);
        card.addView(top);
        if (description != null && !description.isEmpty()) {
            TextView d = secondary(c, description);
            d.setTag("description");
            card.addView(d, matchWrap(c, 4));
        }
        TextView detail = text(c, "", 14, true, R.color.accent);
        detail.setTag("detail");
        detail.setVisibility(View.GONE);
        card.addView(detail, matchWrap(c, 6));
        return card;
    }

    /** Sets the detail line of an {@link #option} card (empty hides it). */
    public static void setDetail(GlassCard card, String text, int colorRes) {
        TextView detail = card.findViewWithTag("detail");
        if (detail != null) {
            detail.setText(text);
            detail.setTextColor(color(card.getContext(), colorRes));
            detail.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    /** Makes a set of option cards behave like radio buttons. */
    public static void singleChoice(List<GlassCard> cards, int initial, Consumer<Integer> onSelect) {
        for (int i = 0; i < cards.size(); i++) {
            int index = i;
            GlassCard card = cards.get(i);
            card.setChecked(i == initial);
            card.setOnClickListener(v -> {
                if (!card.isEnabled()) {
                    return;
                }
                for (GlassCard other : cards) {
                    other.setChecked(other == card);
                }
                onSelect.accept(index);
            });
        }
    }

    /** A disabled option card is dimmed and cannot be chosen. */
    public static void setOptionEnabled(GlassCard card, boolean enabled) {
        card.setEnabled(enabled);
        card.setClickable(enabled);
        card.setAlpha(enabled ? 1f : 0.45f);
    }

    /** Single-selection chips for a list of values. */
    public static <T> ChipGroup chips(Context c, List<T> values, T selected, Function<T, String> label,
                                      Consumer<T> onSelect) {
        ChipGroup group = new ChipGroup(c);
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        for (T value : values) {
            Chip chip = new Chip(c);
            chip.setText(label.apply(value));
            chip.setCheckable(true);
            chip.setChecked(value == null ? selected == null : value.equals(selected));
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setOnCheckedChangeListener((button, checked) -> {
                if (checked) {
                    onSelect.accept(value);
                }
            });
            group.addView(chip);
        }
        return group;
    }

    /** A glass card with a heading and content below. */
    public static GlassCard section(Context c, String heading, View... content) {
        GlassCard card = new GlassCard(c);
        if (heading != null) {
            card.addView(heading(c, heading));
        }
        for (int i = 0; i < content.length; i++) {
            card.addView(content[i], matchWrap(c, heading == null && i == 0 ? 0 : 10));
        }
        return card;
    }

    /** "Label   value" row for information lists. */
    public static View keyValue(Context c, String key, String value) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView k = secondary(c, key);
        TextView v = body(c, value);
        v.setGravity(Gravity.END);
        row.addView(k, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.42f));
        row.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.58f));
        return row;
    }
}
