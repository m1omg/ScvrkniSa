package io.github.m1omg.shrink.android.ui;

import android.content.Context;
import android.graphics.Outline;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;

import io.github.m1omg.shrink.android.R;

/**
 * A vertical container drawn as frosted glass. Can act like a radio button
 * ({@link #setChecked}) for option lists.
 */
public class GlassCard extends LinearLayout {

    private final GlassDrawable glass;
    private final float radius;
    private boolean checked;

    /** A vertical glass card for building screens in code. */
    public GlassCard(Context context) {
        this(context, null);
        setOrientation(VERTICAL);
    }

    /** From XML: the orientation comes from the layout (horizontal unless specified). */
    public GlassCard(Context context, AttributeSet attrs) {
        super(context, attrs);
        radius = getResources().getDimension(R.dimen.glass_radius);
        glass = new GlassDrawable(context, radius);
        setBackground(glass);
        int pad = getResources().getDimensionPixelSize(R.dimen.card_padding);
        if (getPaddingLeft() == 0 && getPaddingTop() == 0) {
            setPadding(pad, pad, pad, pad);
        }
        // Clip children (e.g. images and video) to the rounded shape.
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);
    }

    /** Makes the card react to touches with a ripple. */
    public void setClickableCard(boolean clickable) {
        setClickable(clickable);
        setFocusable(clickable);
        if (clickable) {
            TypedValue value = new TypedValue();
            getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
            setForeground(androidx.core.content.ContextCompat.getDrawable(getContext(), value.resourceId));
        } else {
            setForeground(null);
        }
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
        glass.setSelected(checked);
        setActivated(checked);
        setSelected(checked);
    }

    public boolean isChecked() {
        return checked;
    }

    /** More opaque glass for panels with lots of text. */
    public void setStrong(boolean strong) {
        glass.setStrong(strong);
    }
}
