package io.github.m1omg.shrink.android.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import io.github.m1omg.shrink.android.R;

/**
 * A frosted-glass panel: a translucent fill, a thin border that is brighter at
 * the top left (as if lit from above) and a faint highlight along the top edge.
 * When selected, the border uses the accent gradient.
 */
public final class GlassDrawable extends Drawable {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float radius;
    private final float strokeWidth;
    private final int fillColor;
    private final int strongFillColor;
    private final int strokeLight;
    private final int strokeDark;
    private final int highlightColor;
    private final int accent;
    private final int accent2;
    private boolean selected;
    private boolean strong;

    public GlassDrawable(Context context, float radiusPx) {
        this.radius = radiusPx;
        this.strokeWidth = context.getResources().getDisplayMetrics().density * 1.1f;
        fillColor = ContextCompat.getColor(context, R.color.glass_fill);
        strongFillColor = ContextCompat.getColor(context, R.color.glass_fill_strong);
        strokeLight = ContextCompat.getColor(context, R.color.glass_stroke_light);
        strokeDark = ContextCompat.getColor(context, R.color.glass_stroke_dark);
        highlightColor = ContextCompat.getColor(context, R.color.glass_highlight);
        accent = ContextCompat.getColor(context, R.color.accent);
        accent2 = ContextCompat.getColor(context, R.color.accent_2);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(strokeWidth);
    }

    /** Selected glass gets an accent border and slightly stronger fill. */
    public void setSelected(boolean selected) {
        if (this.selected != selected) {
            this.selected = selected;
            updateShaders(getBounds());
            invalidateSelf();
        }
    }

    /** A more opaque variant for panels that hold dense content (e.g. bottom bars). */
    public void setStrong(boolean strong) {
        this.strong = strong;
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        updateShaders(bounds);
    }

    private void updateShaders(Rect b) {
        if (b.isEmpty()) {
            return;
        }
        if (selected) {
            stroke.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom, accent, accent2,
                    Shader.TileMode.CLAMP));
            stroke.setStrokeWidth(strokeWidth * 2f);
        } else {
            stroke.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom, strokeLight, strokeDark,
                    Shader.TileMode.CLAMP));
            stroke.setStrokeWidth(strokeWidth);
        }
        highlight.setShader(new LinearGradient(0, b.top, 0, b.top + Math.min(b.height(), radius * 3),
                highlightColor, highlightColor & 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float inset = stroke.getStrokeWidth() / 2f;
        rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
        int color = strong || selected ? strongFillColor : fillColor;
        if (selected) {
            // A faint accent tint on selected cards.
            color = blend(color, accent, 0.12f);
        }
        fill.setColor(color);
        canvas.drawRoundRect(rect, radius, radius, fill);
        canvas.drawRoundRect(rect, radius, radius, highlight);
        canvas.drawRoundRect(rect, radius, radius, stroke);
    }

    private static int blend(int base, int tint, float amount) {
        int a = base >>> 24;
        int r = Math.round(((base >> 16) & 0xFF) * (1 - amount) + ((tint >> 16) & 0xFF) * amount);
        int g = Math.round(((base >> 8) & 0xFF) * (1 - amount) + ((tint >> 8) & 0xFF) * amount);
        int bl = Math.round((base & 0xFF) * (1 - amount) + (tint & 0xFF) * amount);
        return Math.max(a, 0x40) << 24 | r << 16 | g << 8 | bl;
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        stroke.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        fill.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
