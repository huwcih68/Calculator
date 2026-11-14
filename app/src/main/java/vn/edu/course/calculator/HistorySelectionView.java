package vn.edu.course.calculator;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.google.android.material.color.MaterialColors;

/** A circular check mark with a liquid fill and a damped droplet motion. */
final class HistorySelectionView extends View {
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path circle = new Path();
    private final Path water = new Path();
    private final Path tickSegment = new Path();
    private final PathMeasure tickMeasure;
    private final float radius;
    private boolean checked;
    private float progress;
    private float deformation;
    private ValueAnimator animator;

    HistorySelectionView(Context context) {
        super(context);
        radius = dp(10);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(1.5f));
        ringPaint.setColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
        fillPaint.setColor(0xFFAD8EF8);
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(dp(2));
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        tickPaint.setStrokeJoin(Paint.Join.ROUND);
        tickPaint.setColor(Color.WHITE);
        Path tick = new Path();
        tick.moveTo(dp(-4.5f), 0);
        tick.lineTo(dp(-1.5f), dp(3));
        tick.lineTo(dp(4.5f), dp(-3));
        tickMeasure = new PathMeasure(tick, false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    boolean isChecked() {
        return checked;
    }

    void bindChecked(boolean value) {
        stopAnimation();
        checked = value;
        progress = value ? 1f : 0f;
        deformation = 0;
        invalidate();
    }

    void setChecked(boolean value, boolean animate) {
        if (checked == value) return;
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            bindChecked(value);
            return;
        }
        stopAnimation();
        checked = value;
        float start = progress;
        float target = value ? 1f : 0f;
        ValueAnimator motion = ValueAnimator.ofFloat(0f, 1f);
        animator = motion;
        motion.setDuration(380);
        motion.setInterpolator(new LinearInterpolator());
        motion.addUpdateListener(animation -> {
            float time = (float) animation.getAnimatedValue();
            float remaining = 1f - time;
            float eased = 1f - remaining * remaining * remaining;
            progress = start + (target - start) * eased;
            deformation = (float) Math.sin(time * Math.PI * 3) * 0.16f * remaining * remaining;
            invalidate();
        });
        motion.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (animator != animation) return;
                animator = null;
                progress = target;
                deformation = 0;
                invalidate();
            }
        });
        motion.start();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int saved = canvas.save();
        canvas.translate(getWidth() / 2f, getHeight() / 2f);
        canvas.scale(1f + deformation, 1f - deformation);
        ringPaint.setAlpha(Math.round(255 * (1f - progress)));
        canvas.drawCircle(0, 0, radius, ringPaint);

        if (progress > 0) {
            int clipped = canvas.save();
            circle.reset();
            circle.addCircle(0, 0, radius, Path.Direction.CW);
            canvas.clipPath(circle);
            float level = radius - 2f * radius * progress;
            float wave = dp(2.2f) * (float) Math.sin(Math.PI * progress);
            water.reset();
            water.moveTo(-radius, radius + 1);
            water.lineTo(-radius, level);
            water.cubicTo(-radius / 3, level - wave, radius / 3, level + wave, radius, level);
            water.lineTo(radius, radius + 1);
            water.close();
            canvas.drawPath(water, fillPaint);
            canvas.restoreToCount(clipped);

            float tickProgress = Math.min(1f, Math.max(0f, (progress - 0.55f) / 0.45f));
            tickSegment.reset();
            tickMeasure.getSegment(0, tickMeasure.getLength() * tickProgress, tickSegment, true);
            canvas.drawPath(tickSegment, tickPaint);
        }
        canvas.restoreToCount(saved);
    }

    private void stopAnimation() {
        ValueAnimator running = animator;
        animator = null;
        if (running != null) running.cancel();
    }

    @Override protected void onDetachedFromWindow() {
        bindChecked(checked);
        super.onDetachedFromWindow();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
