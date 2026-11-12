package com.frost.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/** Full-screen wallpaper (centre-cropped) under an adjustable dim, cross-fading when it changes. */
final class WallView extends View {
    private Bitmap prev, cur;
    private float mix = 1f;
    private int dim = 35;
    private ValueAnimator anim;
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint shade = new Paint();
    private final Paint grad = new Paint();
    private final Rect src = new Rect(), dst = new Rect();

    WallView(Context c) {
        super(c);
    }

    void setDim(int pct) {
        dim = pct;
        invalidate();
    }

    void setBitmap(Bitmap b, boolean fade) {
        if (b == cur) return;
        if (anim != null) anim.cancel();
        if (!fade || cur == null) {
            prev = null;
            cur = b;
            mix = 1f;
            invalidate();
            return;
        }
        prev = cur;
        cur = b;
        mix = 0f;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(Spring.dur(700));
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.addUpdateListener(a -> {
            mix = (Float) a.getAnimatedValue();
            invalidate();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                prev = null;
                invalidate();
            }
        });
        anim.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        dst.set(0, 0, w, h);
        grad.setShader(new LinearGradient(0, 0, 0, h, 0x00000000, 0x47000000, Shader.TileMode.CLAMP));
    }

    private void drawBmp(Canvas c, Bitmap b, int a) {
        int w = getWidth(), h = getHeight();
        float k = Math.max(w / (float) b.getWidth(), h / (float) b.getHeight());
        float sw = w / k, sh = h / k;
        src.set((int) ((b.getWidth() - sw) / 2f), (int) ((b.getHeight() - sh) / 2f),
                (int) ((b.getWidth() + sw) / 2f), (int) ((b.getHeight() + sh) / 2f));
        bmp.setAlpha(a);
        c.drawBitmap(b, src, dst, bmp);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawColor(Color.BLACK);
        if (prev != null) drawBmp(c, prev, 255);
        if (cur != null) drawBmp(c, cur, (int) (Ui.clamp(mix, 0f, 1f) * 255));
        if (dim > 0) {
            shade.setColor(Color.argb((int) (dim * 2.55f), 0, 0, 0));
            c.drawRect(0, 0, w, h, shade);
        }
        c.drawRect(0, 0, w, h, grad);
    }
}
