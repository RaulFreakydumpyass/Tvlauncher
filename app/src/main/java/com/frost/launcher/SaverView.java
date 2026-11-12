package com.frost.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Apple-TV-style screensaver: slow zoom/pan over your wallpaper with a big drifting clock (also protects TV panels). */
final class SaverView extends View {
    private static final float[][] SPOTS = {{0.07f, 0.84f}, {0.50f, 0.84f}, {0.50f, 0.30f}, {0.07f, 0.30f}};

    private Bitmap bmp;
    private String time = "", date = "";
    private float k, curX = SPOTS[0][0], curY = SPOTS[0][1];
    private int spot;
    private boolean active;
    private ValueAnimator burns, mover;
    private final Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint shade = new Paint();
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable hop = new Runnable() {
        @Override
        public void run() {
            hopClock();
        }
    };

    SaverView(Context c) {
        super(c);
        shade.setColor(0x80000000);
    }

    void setInfo(String time, String date) {
        this.time = time;
        this.date = date;
        if (active) invalidate();
    }

    void start(Bitmap wall, String time, String date) {
        this.bmp = wall;
        this.time = time;
        this.date = date;
        active = true;
        spot = 0;
        curX = SPOTS[0][0];
        curY = SPOTS[0][1];
        setVisibility(VISIBLE);
        animate().cancel();
        setAlpha(0f);
        animate().alpha(1f).setDuration(Spring.dur(1400)).setListener(null).start();
        if (burns != null) burns.cancel();
        burns = ValueAnimator.ofFloat(0f, 1f);
        burns.setDuration(50000);
        burns.setInterpolator(new LinearInterpolator());
        burns.setRepeatCount(ValueAnimator.INFINITE);
        burns.setRepeatMode(ValueAnimator.REVERSE);
        burns.addUpdateListener(a -> {
            k = (Float) a.getAnimatedValue();
            invalidate();
        });
        burns.start();
        h.removeCallbacks(hop);
        h.postDelayed(hop, 45000);
    }

    void stop() {
        active = false;
        h.removeCallbacks(hop);
        if (burns != null) burns.cancel();
        if (mover != null) mover.cancel();
        animate().cancel();
        animate().alpha(0f).setDuration(Spring.dur(350)).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!active) setVisibility(GONE);
            }
        }).start();
    }

    private void hopClock() {
        if (!active) return;
        spot = (spot + 1) % SPOTS.length;
        final float sx = curX, sy = curY, ex = SPOTS[spot][0], ey = SPOTS[spot][1];
        if (mover != null) mover.cancel();
        mover = ValueAnimator.ofFloat(0f, 1f);
        mover.setDuration(Spring.dur(2600));
        mover.setInterpolator(Spring.EASE);
        mover.addUpdateListener(a -> {
            float f = (Float) a.getAnimatedValue();
            curX = sx + (ex - sx) * f;
            curY = sy + (ey - sy) * f;
            invalidate();
        });
        mover.start();
        h.postDelayed(hop, 45000);
    }

    @Override
    protected void onDraw(Canvas c) {
        float vw = getWidth(), vh = getHeight();
        c.drawColor(Color.BLACK);
        if (bmp != null) {
            float s0 = Math.max(vw / bmp.getWidth(), vh / bmp.getHeight());
            float s = s0 * (1f + 0.18f * k);
            float dx = (vw - bmp.getWidth() * s) / 2f + (k - 0.5f) * vw * 0.04f;
            float dy = (vh - bmp.getHeight() * s) / 2f;
            m.setScale(s, s);
            m.postTranslate(dx, dy);
            c.drawBitmap(bmp, m, bp);
        }
        c.drawRect(0, 0, vw, vh, shade);
        tp.setTypeface(Ui.light);
        tp.setTextSize(Ui.px(190));
        tp.setColor(0xF2FFFFFF);
        c.drawText(time, curX * vw, curY * vh, tp);
        tp.setTypeface(Ui.regular);
        tp.setTextSize(Ui.px(46));
        tp.setColor(0xB8FFFFFF);
        c.drawText(date, curX * vw + Ui.px(8), curY * vh + Ui.px(66), tp);
    }
}
