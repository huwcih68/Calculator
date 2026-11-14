package vn.edu.course.calculator;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.SystemClock;
import android.text.Layout;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Short, non-interactive vector effects shared by both screen orientations. */
final class PetalEffects {
    private static final int MAX_PETALS = 100;
    private final ViewGroup root;
    private final float density;
    private final Random random = new Random();
    private final List<Petal> petals = new ArrayList<>();
    private final List<Glyph> glyphs = new ArrayList<>();
    private final List<KeyFeedback> keys = new ArrayList<>();
    private final Path petalShape = new Path();
    private final Path crease = new Path();
    private final Paint petalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Shader[] petalColors;
    private final FrameDrawable foreground = new FrameDrawable();
    private final ValueAnimator ticker = ValueAnimator.ofFloat(0f, 1f);
    private TextView dissolvingText;
    private CharSequence expressionHint;
    private boolean disposed;

    PetalEffects(ViewGroup root) {
        this.root = root;
        density = root.getResources().getDisplayMetrics().density;
        // A slightly folded cherry petal, with a shallow notch at its tip.
        petalShape.moveTo(0f, 0.12f);
        petalShape.cubicTo(-0.6f, -0.12f, -0.66f, -0.8f, -0.26f, -1f);
        petalShape.quadTo(-0.12f, -1.07f, 0f, -0.88f);
        petalShape.quadTo(0.22f, -1.1f, 0.4f, -0.88f);
        petalShape.cubicTo(0.75f, -0.47f, 0.33f, -0.02f, 0f, 0.12f);
        petalShape.close();
        crease.moveTo(0f, 0.08f);
        crease.quadTo(-0.2f, -0.38f, -0.03f, -0.78f);
        petalColors = new Shader[] {
                petalGradient(0xFFFFE5F2, 0xFFF299CF, 0xFFC28AF0),
                petalGradient(0xFFFFD8ED, 0xFFEF80C2, 0xFFA781E8),
                petalGradient(0xFFF9E5FF, 0xFFDCA5F2, 0xFFB28CE9)
        };
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(0.025f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        foreground.setBounds(0, 0, root.getWidth(), root.getHeight());
        root.getOverlay().add(foreground);
        ticker.setDuration(1000);
        ticker.setRepeatCount(ValueAnimator.INFINITE);
        ticker.setInterpolator(new LinearInterpolator());
        ticker.addUpdateListener(animation -> {
            if (!motionEnabled()) {
                stop();
                return;
            }
            long now = SystemClock.uptimeMillis();
            petals.removeIf(petal -> now - petal.born >= petal.life);
            glyphs.removeIf(glyph -> now - glyph.born >= 430);
            if (glyphs.isEmpty()) restoreHint();
            foreground.setBounds(0, 0, root.getWidth(), root.getHeight());
            foreground.invalidateSelf();
            if (petals.isEmpty() && glyphs.isEmpty()) ticker.cancel();
        });
    }

    private Shader petalGradient(int top, int middle, int bottom) {
        return new LinearGradient(-0.3f, -1f, 0.35f, 0.12f,
                new int[] {top, middle, bottom}, null, Shader.TileMode.CLAMP);
    }

    boolean motionEnabled() {
        return !disposed && ValueAnimator.areAnimatorsEnabled();
    }

    @SuppressLint("ClickableViewAccessibility") // Button handles click/long-click normally.
    void bind(Button button, Runnable action) {
        KeyFeedback key = new KeyFeedback(button);
        keys.add(key);
        button.setStateListAnimator(null);
        button.setOnTouchListener((view, event) -> {
            if (!motionEnabled()) {
                key.reset();
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    key.x = event.getX();
                    key.y = event.getY();
                    key.press();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (event.getX() < 0 || event.getX() > view.getWidth()
                            || event.getY() < 0 || event.getY() > view.getHeight()) {
                        key.release(false);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    key.releasedAt = SystemClock.uptimeMillis();
                    key.release(true);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    key.release(false);
                    break;
                default:
                    break;
            }
            return false;
        });
        button.setOnClickListener(view -> {
            if (motionEnabled()) {
                boolean fromTouch = SystemClock.uptimeMillis() - key.releasedAt < 200;
                if (!fromTouch) {
                    key.x = button.getWidth() / 2f;
                    key.y = button.getHeight() / 2f;
                    button.setScaleX(0.94f);
                    button.setScaleY(0.94f);
                    key.release(true);
                }
                PointF point = pointInRoot(button, key.x, key.y);
                burst(point.x, point.y, 3 + random.nextInt(3));
            }
            action.run();
        });
    }

    /** Capture only the visible glyph outlines before C updates the real expression. */
    void dissolve(TextView text) {
        if (!motionEnabled() || text.length() == 0 || text.getLayout() == null) return;
        restoreHint();
        glyphs.clear();
        Layout layout = text.getLayout();
        String visibleText = layout.getText().toString();
        PointF origin = pointInRoot(text, 0f, 0f);
        Matrix transform = new Matrix();
        transform.setScale(text.getScaleX(), text.getScaleY());
        transform.postTranslate(origin.x, origin.y);
        long now = SystemClock.uptimeMillis();
        for (int line = 0; line < layout.getLineCount() && glyphs.size() < 32; line++) {
            for (int i = layout.getLineStart(line);
                 i < layout.getLineVisibleEnd(line) && glyphs.size() < 32;) {
                int end = i + Character.charCount(visibleText.codePointAt(i));
                if (!Character.isWhitespace(visibleText.charAt(i)) && visibleText.charAt(i) != '\uFEFF') {
                    Path path = new Path();
                    text.getPaint().getTextPath(visibleText, i, end,
                            layout.getPrimaryHorizontal(i) + text.getTotalPaddingLeft() - text.getScrollX(),
                            layout.getLineBaseline(line) + text.getTotalPaddingTop() - text.getScrollY(), path);
                    path.transform(transform);
                    RectF bounds = new RectF();
                    path.computeBounds(bounds, true);
                    RectF visible = new RectF(origin.x, origin.y,
                            origin.x + text.getWidth() * text.getScaleX(),
                            origin.y + text.getHeight() * text.getScaleY());
                    if (!bounds.isEmpty() && RectF.intersects(bounds, visible)) {
                        glyphs.add(new Glyph(path, bounds.centerX(), bounds.centerY(),
                                text.getCurrentTextColor(), text.getAlpha(), now));
                        addPetal(bounds.centerX(), bounds.centerY(),
                                between(-30, 30) * density, between(-85, -40) * density,
                                between(9, 14) * density, 760, now + random.nextInt(70));
                    }
                }
                i = end;
            }
        }
        if (!glyphs.isEmpty()) {
            dissolvingText = text;
            expressionHint = text.getHint();
            text.setHint(null);
        }
        startFrames();
    }

    private void restoreHint() {
        if (dissolvingText != null) {
            dissolvingText.setHint(expressionHint);
            dissolvingText = null;
            expressionHint = null;
        }
    }

    private PointF pointInRoot(View view, float x, float y) {
        int[] location = new int[2];
        int[] parentLocation = new int[2];
        view.getLocationInWindow(location);
        root.getLocationInWindow(parentLocation);
        return new PointF(location[0] - parentLocation[0] + x * view.getScaleX(),
                location[1] - parentLocation[1] + y * view.getScaleY());
    }

    private void burst(float x, float y, int count) {
        long now = SystemClock.uptimeMillis();
        for (int i = 0; i < count; i++) {
            double angle = -Math.PI + (i + random.nextFloat()) / count * Math.PI;
            float speed = between(35, 75) * density;
            addPetal(x, y, (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed - 12 * density,
                    between(7, 11) * density, 580 + random.nextInt(180), now);
        }
        startFrames();
    }

    private void addPetal(float x, float y, float vx, float vy, float size, long life, long born) {
        if (petals.size() >= MAX_PETALS) petals.remove(0);
        petals.add(new Petal(x, y, vx, vy, size, life, born, random.nextInt(3),
                between(-180, 180), between(-110, 110)));
    }

    private float between(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    private void startFrames() {
        if (motionEnabled() && !ticker.isStarted()) ticker.start();
    }

    void stop() {
        ticker.cancel();
        petals.clear();
        glyphs.clear();
        restoreHint();
        foreground.invalidateSelf();
        for (KeyFeedback key : keys) key.reset();
    }

    @SuppressLint("ClickableViewAccessibility") // Removing listeners; native Button still owns clicks.
    void dispose() {
        stop();
        disposed = true;
        for (KeyFeedback key : keys) {
            key.button.setOnTouchListener(null);
            key.button.setForeground(key.originalForeground);
        }
        ticker.removeAllUpdateListeners();
        root.getOverlay().remove(foreground);
    }

    private void drawPetal(Canvas canvas, float x, float y, float size,
                           float rotation, float fold, float alpha, int color) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotation);
        canvas.scale(size * fold, size);
        petalPaint.setShader(petalColors[color]);
        petalPaint.setAlpha((int) (255 * alpha));
        canvas.drawPath(petalShape, petalPaint);
        linePaint.setColor(0xFF885695);
        linePaint.setAlpha((int) (110 * alpha));
        canvas.drawPath(petalShape, linePaint);
        linePaint.setColor(0xFFFFF0FA);
        linePaint.setAlpha((int) (160 * alpha));
        canvas.drawPath(crease, linePaint);
        canvas.restore();
    }

    private final class FrameDrawable extends Drawable {
        @Override public void draw(Canvas canvas) {
            long now = SystemClock.uptimeMillis();
            for (Glyph glyph : glyphs) {
                float t = Math.min(1f, (now - glyph.born) / 430f);
                canvas.save();
                canvas.translate(12 * density * t, -28 * density * t);
                canvas.rotate(12 * t, glyph.x, glyph.y);
                canvas.scale(1 - t * 0.35f, 1 - t * 0.35f, glyph.x, glyph.y);
                glyphPaint.setColor(glyph.color);
                glyphPaint.setAlpha((int) (Color.alpha(glyph.color) * glyph.alpha
                        * Math.pow(1 - t, 2)));
                canvas.drawPath(glyph.path, glyphPaint);
                canvas.restore();
            }
            for (Petal petal : petals) {
                if (now < petal.born) continue;
                float t = Math.min(1f, (now - petal.born) / (float) petal.life);
                float seconds = (now - petal.born) / 1000f;
                float alpha = Math.min(1f, t / 0.09f) * (1 - t * t);
                drawPetal(canvas,
                        petal.x + petal.vx * seconds + (float) Math.sin(t * 6) * 5 * density,
                        petal.y + petal.vy * seconds + 48 * density * seconds * seconds,
                        petal.size * (1f - 0.28f * t), petal.rotation + petal.spin * t,
                        0.7f + 0.3f * (float) Math.cos(t * 9 + petal.rotation), alpha, petal.color);
            }
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private final class KeyFeedback {
        final Button button;
        final Drawable originalForeground;
        final TouchGlow glow = new TouchGlow();
        float x;
        float y;
        long releasedAt = -1000;

        KeyFeedback(Button button) {
            this.button = button;
            originalForeground = button.getForeground();
            button.setForeground(originalForeground == null ? glow
                    : new LayerDrawable(new Drawable[] {originalForeground, glow}));
        }

        void press() {
            button.animate().cancel();
            button.animate().scaleX(0.94f).scaleY(0.94f).translationY(density)
                    .setDuration(90).setInterpolator(new DecelerateInterpolator()).start();
            glow.animateTo(1f, x, y, 220);
        }

        void release(boolean bounce) {
            button.animate().cancel();
            button.animate().scaleX(1f).scaleY(1f).translationY(0f)
                    .setDuration(bounce ? 380 : 140)
                    .setInterpolator(bounce ? new OvershootInterpolator(1.25f)
                            : new DecelerateInterpolator()).start();
            glow.animateTo(0f, x, y, 240);
        }

        void reset() {
            button.animate().cancel();
            button.setScaleX(1f);
            button.setScaleY(1f);
            button.setTranslationY(0f);
            glow.reset();
        }
    }

    private final class TouchGlow extends Drawable {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path clip = new Path();
        final Path waveClip = new Path();
        final RectF rect = new RectF();
        ValueAnimator animator;
        float amount;
        float x;
        float y;

        void animateTo(float target, float x, float y, long duration) {
            if (animator != null) animator.cancel();
            this.x = x;
            this.y = y;
            float radius = Math.max(density, (float) Math.hypot(getBounds().width(), getBounds().height()));
            paint.setShader(new RadialGradient(x, y, radius,
                    new int[] {0xFFF1CFFF, 0xFFD29AFF, 0x007E51C5},
                    new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            animator = ValueAnimator.ofFloat(amount, target);
            animator.setDuration(duration);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(value -> {
                amount = (float) value.getAnimatedValue();
                invalidateSelf();
            });
            animator.start();
        }

        void reset() {
            if (animator != null) animator.cancel();
            amount = 0;
            invalidateSelf();
        }

        @Override public void draw(Canvas canvas) {
            if (amount <= 0) return;
            float inset = 1.5f * density;
            rect.set(getBounds());
            rect.inset(inset, inset);
            float radius = rect.height() / 2f;
            clip.reset();
            clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            float wave = (float) Math.hypot(rect.width(), rect.height()) * amount;
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha((int) (35 * amount));
            canvas.drawCircle(x, y, wave, paint);
            // The outline is revealed by the wave expanding from the touch point.
            canvas.save();
            waveClip.reset();
            waveClip.addCircle(x, y, wave, Path.Direction.CW);
            canvas.clipPath(waveClip);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2 * density);
            paint.setAlpha((int) (235 * amount));
            canvas.drawRoundRect(rect, radius, radius, paint);
            canvas.restore();
            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static final class Petal {
        final float x, y, vx, vy, size, rotation, spin;
        final long life, born;
        final int color;

        Petal(float x, float y, float vx, float vy, float size, long life, long born,
              int color, float rotation, float spin) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.size = size;
            this.life = life; this.born = born; this.color = color;
            this.rotation = rotation; this.spin = spin;
        }
    }

    private static final class Glyph {
        final Path path;
        final float x, y, alpha;
        final int color;
        final long born;

        Glyph(Path path, float x, float y, int color, float alpha, long born) {
            this.path = path; this.x = x; this.y = y;
            this.color = color; this.alpha = alpha; this.born = born;
        }
    }

}
