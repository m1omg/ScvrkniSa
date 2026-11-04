package io.github.m1omg.shrink.android.ui.tools;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.m1omg.shrink.android.R;
import io.github.m1omg.shrink.android.ShrinkApp;
import io.github.m1omg.shrink.android.ui.GlassCard;
import io.github.m1omg.shrink.android.ui.Tool;
import io.github.m1omg.shrink.android.ui.Ui;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.model.Container;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.plan.JobPlanner;
import io.github.m1omg.shrink.core.plan.Plan;

import java.util.Arrays;
import java.util.List;

/** Turns the video by 90°, 180° or 270°. */
public final class RotateFragment extends ToolFragment {

    private int rotation;
    private float shownAngle;
    private boolean lossless;
    private Plan plan;
    private ImageView image;
    private TextView result;

    @Override
    protected Tool tool() {
        return Tool.ROTATE;
    }

    @Override
    protected void build(Context c, MediaInfo info) {
        if (!info.hasVideo()) {
            message(c, I18n.tr("file.noVideo"));
            return;
        }
        GlassCard frame = new GlassCard(c);
        frame.setPadding(0, 0, 0, 0);
        image = new ImageView(c);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(Ui.color(c, R.color.video_bg));
        image.setImageBitmap(ShrinkApp.get().state().thumbnail());
        image.setContentDescription(info.fileName());
        frame.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 240)));
        content.addView(frame, Ui.matchWrap(c, 4));

        MaterialButton left = Ui.glassButton(c, I18n.tr("rotate.left"), R.drawable.ic_rotate_left);
        MaterialButton right = Ui.glassButton(c, I18n.tr("rotate.right"), R.drawable.ic_rotate);
        MaterialButton flip = Ui.glassButton(c, I18n.tr("rotate.flip"), R.drawable.ic_flip);
        left.setOnClickListener(v -> rotateBy(270));
        right.setOnClickListener(v -> rotateBy(90));
        flip.setOnClickListener(v -> rotateBy(180));
        content.addView(Ui.row(c, 8, left, right), Ui.matchWrap(c, 14));
        content.addView(flip, Ui.matchWrap(c, 8));

        result = Ui.heading(c, I18n.tr("rotate.none"));
        content.addView(result, Ui.matchWrap(c, 14));

        Container container = JobPlanner.sourceContainer(info);
        boolean losslessPossible = (container == Container.MP4 || container == Container.MOV)
                && container.canCopyVideo(info.video().codecName());
        lossless = losslessPossible;
        content.addView(Ui.heading(c, I18n.tr("rotate.method")), Ui.matchWrap(c, 18));
        GlassCard losslessCard = Ui.option(c, R.drawable.ic_sparkles, I18n.tr("rotate.lossless"),
                I18n.tr("rotate.lossless.hint"));
        GlassCard reencodeCard = Ui.option(c, R.drawable.ic_video, I18n.tr("rotate.reencode"),
                I18n.tr("rotate.reencode.hint"));
        List<GlassCard> methods = Arrays.asList(losslessCard, reencodeCard);
        Ui.singleChoice(methods, lossless ? 0 : 1, index -> {
            lossless = index == 0;
            recompute();
        });
        Ui.setOptionEnabled(losslessCard, losslessPossible);
        content.addView(losslessCard, Ui.matchWrap(c, 8));
        content.addView(reencodeCard, Ui.matchWrap(c, 10));
    }

    private void rotateBy(int degrees) {
        rotation = (rotation + degrees) % 360;
        // Always turn the short way in the animation; the duration is fixed in milliseconds.
        float delta = ((degrees % 360) + 540) % 360 - 180;
        shownAngle += delta == -180 ? 180 : delta;
        // A sideways picture must shrink to stay inside the frame.
        float scale = 1f;
        if (rotation % 180 != 0 && image.getWidth() > 0 && image.getHeight() > 0) {
            float w = image.getWidth();
            float h = image.getHeight();
            scale = Math.min(h / w, w / h);
        }
        image.animate().rotation(shownAngle).scaleX(scale).scaleY(scale).setDuration(280).start();
        recompute();
    }

    private void recompute() {
        if (rotation == 0) {
            plan = null;
            result.setText(I18n.tr("rotate.none"));
        } else {
            plan = planner().rotate(source, rotation, lossless);
            result.setText(I18n.tr("rotate.result", rotation + "°"));
        }
        refresh();
    }

    @Override
    protected Plan plan() {
        return plan;
    }

    @Override
    protected String actionText() {
        return I18n.tr("rotate.action");
    }
}
