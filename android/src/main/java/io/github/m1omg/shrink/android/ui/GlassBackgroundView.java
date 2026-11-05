package io.github.m1omg.shrink.android.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import io.github.m1omg.shrink.android.R;

/**
 * The colourful backdrop behind the glass cards: a soft gradient with large
 * blurred colour orbs that drift slowly.
 *
 * <p>The orbs' positions are computed from the elapsed time (System.nanoTime),
 * never from a frame count, so the motion is equally fast on 60, 90 or 120 Hz
 * screens. Redraws are limited to about 30 per second to save battery, and the
 * animation stops when animations are disabled in the system settings.
 */
public class GlassBackgroundView extends View {

    private static final long FRAME_INTERVAL_MS = 33;

    private final Paint basePaint = new Paint();
    private final Paint orbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final int[] orbColors = new int[4];
    private final RadialGradient[] orbShaders = new RadialGradient[4];
    // Each orb: base x, base y (fractions), radius (fraction of the larger side),
    // drift amplitude x, y (fractions), period in seconds, phase.
    private static final float[][] ORBS = {
        {0.15f, 0.12f, 0.62f, 0.10f, 0.06f, 23f, 0.0f},
        {0.92f, 0.30f, 0.55f, 0.08f, 0.09f, 29f, 1.7f},
        {0.25f, 0.78f, 0.58f, 0.09f, 0.07f, 31f, 3.1f},
        {0.85f, 0.92f, 0.50f, 0.07f, 0.08f, 37f, 4.4f},
    };
    private final long start = System.nanoTime();
    private boolean animated = true;
    private final Runnable tick = this::invalidate;

    public GlassBackgroundView(Context context) {
        this(context, null);
    }

    public GlassBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        orbColors[0] = ContextCompat.getColor(context, R.color.orb_1);
        orbColors[1] = ContextCompat.getColor(context, R.color.orb_2);
        orbColors[2] = ContextCompat.getColor(context, R.color.orb_3);
        orbColors[3] = ContextCompat.getColor(context, R.color.orb_4);
    }

    public void setAnimated(boolean animated) {
        this.animated = animated;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int top = ContextCompat.getColor(getContext(), R.color.bg_top);
        int bottom = ContextCompat.getColor(getContext(), R.color.bg_bottom);
        basePaint.setShader(new LinearGradient(0, 0, w * 0.3f, h, top, bottom, Shader.TileMode.CLAMP));
        float size = Math.max(w, h);
        for (int i = 0; i < ORBS.length; i++) {
            float radius = ORBS[i][2] * size;
            int color = orbColors[i];
            int transparent = color & 0x00FFFFFF;
            orbShaders[i] = new RadialGradient(0, 0, radius, new int[] {color, mix(color), transparent},
                    new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        }
    }

    /** Half-transparent version of a colour, for a soft fall-off. */
    private static int mix(int color) {
        int alpha = (color >>> 24) / 3;
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, h, basePaint);
        boolean animate = animated && ValueAnimator.areAnimatorsEnabled() && isShown();
        double seconds = animate ? (System.nanoTime() - start) / 1e9 : 0;
        float size = Math.max(w, h);
        for (int i = 0; i < ORBS.length; i++) {
            if (orbShaders[i] == null) {
                continue;
            }
            float[] o = ORBS[i];
            double angle = 2 * Math.PI * seconds / o[5] + o[6];
            float x = (float) ((o[0] + o[3] * Math.sin(angle)) * w);
            float y = (float) ((o[1] + o[4] * Math.cos(angle * 0.8)) * h);
            matrix.setTranslate(x, y);
            orbShaders[i].setLocalMatrix(matrix);
            orbPaint.setShader(orbShaders[i]);
            canvas.drawCircle(x, y, o[2] * size, orbPaint);
        }
        if (animate) {
            removeCallbacks(tick);
            postDelayed(tick, FRAME_INTERVAL_MS);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }
}
