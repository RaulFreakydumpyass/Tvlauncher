package com.frost.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/**
 * One 16:9 tile in the grid, or the "+" tile when {@code tile == null}.
 * Everything is a pre-rendered bitmap, so a frame is just a few bitmap draws.
 * The tile owns ALL of its own motion (position, focus pop, tilt, grid-morph) so animations never fight.
 */
final class TileView extends View {
    static final float FOCUS_SCALE = 0.14f;

    final TileStore.Tile tile;
    Bitmap bg, fg;

    private final int tw, th, namePx, nameMode;
    private final boolean tiltOn;
    private final float rad, ringW, edgeW, labelTop;
    private final String name;
    private String shownName;

    private float focus, press, entry = 1f, morph = 1f, tiltX, tiltY, baseX, baseY;
    private boolean focused;
    private ValueAnimator focusAnim, entryAnim, moveAnim, tiltAnim;

    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint plus = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint np = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF r1 = new RectF(), r2 = new RectF();
    private final Matrix shaderM = new Matrix();
    private final Path shape = new Path(), edgePath = new Path(), ringPath = new Path(), tmp = new Path();

    TileView(Context c, TileStore.Tile tile, int tw, int th, int namePx, int nameMode, boolean tiltOn) {
        super(c);
        this.tile = tile;
        this.tw = tw;
        this.th = th;
        this.namePx = namePx;
        this.nameMode = nameMode;
        this.tiltOn = tiltOn;
        name = tile == null ? "Add App" : tile.label;
        rad = th * TileArt.RADIUS;
        ringW = Math.max(2f, Ui.px(3.5f));
        edgeW = Math.max(1.5f, tw * 3f / TileArt.W);
        Shape.rect(shape, 0, 0, tw, th, rad);                                       // Apple-style corners, built once
        Shape.rect(edgePath, edgeW / 2f, edgeW / 2f, tw - edgeW / 2f, th - edgeW / 2f, rad - edgeW / 2f);
        Shape.rect(ringPath, ringW / 2f, ringW / 2f, tw - ringW / 2f, th - ringW / 2f, rad - ringW / 2f);
        labelTop = Math.round(th * 0.09f) + Ui.px(10);
        ring.setStyle(Paint.Style.STROKE);
        plus.setStyle(Paint.Style.STROKE);
        plus.setStrokeCap(Paint.Cap.ROUND);
        np.setTypeface(Ui.medium);
        np.setTextSize(Math.max(1, namePx));
        np.setColor(Color.WHITE);
        np.setTextAlign(Paint.Align.CENTER);
        setCameraDistance(tw * 14f);
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(tw, th);
    }

    void setArt(Bitmap bg, Bitmap fg) {
        this.bg = bg;
        this.fg = fg;
        if (bg == null) {
            bgPaint.setShader(null);
        } else {                                              // the tile picture is painted through the rounded shape
            BitmapShader sh = new BitmapShader(bg, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            shaderM.setScale(tw / (float) bg.getWidth(), th / (float) bg.getHeight());
            sh.setLocalMatrix(shaderM);
            bgPaint.setShader(sh);
        }
        invalidate();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas c) {
        float fa = Ui.clamp(focus, 0f, 1f);

        r1.set(0, 0, tw, th);
        if (tile == null) {
            drawAdd(c, fa);
        } else {
            if (bg != null) {
                c.drawPath(shape, bgPaint);
            } else {
                fill.setColor(0xFF2A2A2F);
                c.drawPath(shape, fill);
            }
            if (fg != null) {                                // logo grows slightly more than the tile (depth feel)
                float k = tw / (float) TileArt.W / TileArt.FG_SCALE * (1f + 0.06f * fa);
                float fw = fg.getWidth() * k, fh = fg.getHeight() * k;
                r2.set((tw - fw) / 2f, (th - fh) / 2f, (tw + fw) / 2f, (th + fh) / 2f);
                c.drawBitmap(fg, null, r2, bmp);
            }
            ring.setStrokeWidth(edgeW);                      // thin highlighted edge so tiles look separated
            ring.setColor(0x66FFFFFF);
            c.drawPath(edgePath, ring);
            if (fa > 0.01f) {                                // bright focus border
                ring.setStrokeWidth(ringW);
                ring.setColor(Color.argb((int) (fa * 235), 255, 255, 255));
                c.drawPath(ringPath, ring);
            }
        }
        drawLabel(c, fa);
    }

    private void drawLabel(Canvas c, float fa) {
        if (namePx <= 0 || nameMode == 0) return;
        float a = nameMode == 2 ? 0.62f + 0.38f * fa : fa;
        if (a <= 0.02f) return;
        if (shownName == null) {
            shownName = TextUtils.ellipsize(name, np, tw * 1.25f, TextUtils.TruncateAt.END).toString();
        }
        np.setAlpha((int) (Ui.clamp(a, 0f, 1f) * 255));
        c.drawText(shownName, tw / 2f, th + labelTop + namePx, np);
    }

    private void drawAdd(Canvas c, float fa) {
        fill.setColor(Color.argb((int) (255 * (0.13f + 0.12f * fa)), 255, 255, 255));
        c.drawPath(shape, fill);

        float bw = Math.max(2f, ringW * (0.6f + 0.4f * fa));
        ring.setStrokeWidth(bw);
        ring.setColor(Color.argb((int) (255 * (0.30f + 0.65f * fa)), 255, 255, 255));
        float i = bw / 2f;
        Shape.rect(tmp, i, i, tw - i, th - i, rad - i);
        c.drawPath(tmp, ring);

        plus.setStrokeWidth(th * 0.05f);
        plus.setColor(Color.argb(235, 255, 255, 255));
        float cx = tw / 2f, cy = th / 2f, a = th * 0.15f;
        c.drawLine(cx - a, cy, cx + a, cy, plus);
        c.drawLine(cx, cy - a, cx, cy + a, plus);
    }

    // ------------------------------------------------------------------ motion

    private void apply() {
        float s = (1f + FOCUS_SCALE * focus) * (1f - 0.05f * press) * (0.82f + 0.18f * entry) * morph;
        setScaleX(s);
        setScaleY(s);
        setTranslationX(baseX);
        setTranslationY(baseY - Ui.px(10) * focus);
        setRotationX(tiltX);
        setRotationY(tiltY);
        setAlpha(Ui.clamp(entry, 0f, 1f));
        invalidate();
    }

    /** Where the tile sits in the grid (top-left, container coordinates). */
    void setBase(float x, float y, boolean animate) {
        if (moveAnim != null) moveAnim.cancel();
        morph = 1f;
        if (!animate || (x == baseX && y == baseY)) {
            baseX = x;
            baseY = y;
            apply();
            return;
        }
        final float sx = baseX, sy = baseY, ex = x, ey = y;
        moveAnim = ValueAnimator.ofFloat(0f, 1f);
        moveAnim.setDuration(Spring.EASE.ms());
        moveAnim.setInterpolator(Spring.EASE);
        moveAnim.addUpdateListener(a -> {
            float f = (Float) a.getAnimatedValue();
            baseX = sx + (ex - sx) * f;
            baseY = sy + (ey - sy) * f;
            apply();
        });
        moveAnim.start();
    }

    /**
     * Grid morph: the tile is already at its NEW place/size; make it LOOK like it is still at the old
     * place/size (x0,y0 = old top-left in container coords, s0 = old width / new width) and glide into position.
     */
    void morphFrom(final float x0, final float y0, final float s0) {
        final float ex = baseX, ey = baseY;
        if (moveAnim != null) moveAnim.cancel();
        baseX = x0;
        baseY = y0;
        morph = s0;
        apply();
        moveAnim = ValueAnimator.ofFloat(0f, 1f);
        moveAnim.setDuration(Spring.EASE.ms());
        moveAnim.setInterpolator(Spring.EASE);
        moveAnim.addUpdateListener(a -> {
            float f = (Float) a.getAnimatedValue();
            baseX = x0 + (ex - x0) * f;
            baseY = y0 + (ey - y0) * f;
            morph = s0 + (1f - s0) * f;
            apply();
        });
        moveAnim.start();
    }

    void setFocused(boolean on, boolean animate) {
        if (on == focused && animate) return;
        focused = on;
        if (focusAnim != null) focusAnim.cancel();
        float to = on ? 1f : 0f;
        if (!animate) {
            focus = to;
            apply();
            return;
        }
        Spring sp = on ? Spring.POP : Spring.OUT;
        focusAnim = ValueAnimator.ofFloat(focus, to);
        focusAnim.setDuration(sp.ms());
        focusAnim.setInterpolator(sp);
        focusAnim.addUpdateListener(a -> {
            focus = (Float) a.getAnimatedValue();
            apply();
        });
        focusAnim.start();
    }

    /** Little 3D nudge in the direction the focus travelled (Apple TV parallax feel). */
    void tilt(int dx, int dy) {
        if (!tiltOn || (dx == 0 && dy == 0)) return;
        if (tiltAnim != null) tiltAnim.cancel();
        final float ay = dx * 5f, ax = -dy * 4f;
        tiltAnim = ValueAnimator.ofFloat(1f, 0f);
        tiltAnim.setDuration(Spring.POP.ms());
        tiltAnim.setInterpolator(Spring.POP);
        tiltAnim.addUpdateListener(a -> {
            float f = (Float) a.getAnimatedValue();
            tiltY = ay * f;
            tiltX = ax * f;
            apply();
        });
        tiltAnim.start();
    }

    /** Small "press down" squeeze, then run {@code then} (used right before launching an app). */
    void press(final Runnable then) {
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(Spring.dur(90));
        a.addUpdateListener(v -> {
            press = (Float) v.getAnimatedValue();
            apply();
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                then.run();
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        ValueAnimator b = ValueAnimator.ofFloat(press, 0f);
                        b.setDuration(Spring.OUT.ms());
                        b.setInterpolator(Spring.OUT);
                        b.addUpdateListener(v2 -> {
                            press = (Float) v2.getAnimatedValue();
                            apply();
                        });
                        b.start();
                    }
                }, 500);
            }
        });
        a.start();
    }

    /** Pop-in for a freshly added tile. */
    void spawn() {
        entry = 0f;
        apply();
        if (entryAnim != null) entryAnim.cancel();
        entryAnim = ValueAnimator.ofFloat(0f, 1f);
        entryAnim.setDuration(Spring.POP.ms());
        entryAnim.setInterpolator(Spring.POP);
        entryAnim.addUpdateListener(a -> {
            entry = (Float) a.getAnimatedValue();
            apply();
        });
        entryAnim.start();
    }
}
