package io.github.m1omg.shrink.android.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import io.github.m1omg.shrink.android.R;

/**
 * A large circular progress indicator with a gradient arc. Changes in progress
 * glide over a fixed time measured with the system clock (not per frame), so
 * the motion looks the same on every screen refresh rate.
 */
public class ProgressRing extends View {

    private static final double GLIDE_SECONDS = 0.35;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final int accent;
    private final int accent2;
    private float shown;
    private float from;
    private float target;
    private long changedAt;
    private boolean indeterminate = true;
    private final long created = System.nanoTime();

    public ProgressRing(Context context) {
        this(context, null);
    }

    public ProgressRing(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(12 * density);
        track.setColor(ContextCompat.getColor(context, R.color.glass_fill_strong));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(12 * density);
        arc.setStrokeCap(Paint.Cap.ROUND);
        accent = ContextCompat.getColor(context, R.color.accent);
        accent2 = ContextCompat.getColor(context, R.color.accent_2);
    }

    /** @param fraction 0..1 */
    public void setProgress(float fraction) {
        indeterminate = false;
        from = shown;
        target = Math.max(0, Math.min(1, fraction));
        changedAt = System.nanoTime();
        invalidate();
    }

    public void setIndeterminate(boolean value) {
        indeterminate = value;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float inset = arc.getStrokeWidth() / 2 + 2;
        oval.set(inset, inset, w - inset, h - inset);
        SweepGradient gradient = new SweepGradient(w / 2f, h / 2f, new int[] {accent, accent2, accent},
                new float[] {0f, 0.5f, 1f});
        arc.setShader(gradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawArc(oval, 0, 360, false, track);
        if (indeterminate) {
            double seconds = (System.nanoTime() - created) / 1e9;
            float start = (float) ((seconds * 270) % 360);
            canvas.drawArc(oval, start, 80, false, arc);
            postInvalidateOnAnimation();
            return;
        }
        double t = Math.min(1.0, (System.nanoTime() - changedAt) / 1e9 / GLIDE_SECONDS);
        double eased = 1 - Math.pow(1 - t, 3);
        shown = (float) (from + (target - from) * eased);
        canvas.drawArc(oval, -90, 360 * shown, false, arc);
        if (t < 1) {
            postInvalidateOnAnimation();
        }
    }
}
