package com.frost.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

/**
 * The floating top panel ("dynamic island", but bigger and frosted). Collapsed it is a small pill with the clock;
 * when focused (press Up from the grid) it springs open into a control centre: live status and quick actions.
 * Everything is drawn by hand on one canvas, and the frosted look is a cached
 * blurred wallpaper, so it is cheap to render.
 */
final class PanelView extends View {
    interface Host {
        void onPanelAction(int id, String arg);
    }

    static final int A_SETTINGS = 0, A_SYSTEM = 1, A_WIFI = 2, A_MEM = 3, A_EXPAND = 99;

    private static final String[] ACTION_LABELS = {"Settings", "System", "Wi-Fi", "Free RAM"};
    private static final int[] ACTION_GLYPHS = {Glyphs.GEAR, Glyphs.SLIDERS, Glyphs.WIFI, Glyphs.BOLT};

    private final Host host;
    private final Glass glass = new Glass();
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF pill = new RectF(), r = new RectF(), r2 = new RectF(), hl = new RectF();

    private String time = "", date = "", ram = "", store = "";
    private int netType, usbCount;
    private float ex, selPos;
    private boolean expanded;
    private int sel;
    private ValueAnimator exAnim, selAnim;

    PanelView(Context c, Host host) {
        super(c);
        this.host = host;
    }

    // ------------------------------------------------------------------ data

    void setGlass(Bitmap b) {
        glass.set(b);
        invalidate();
    }

    void setInfo(String time, String date) {
        this.time = time;
        this.date = date;
        invalidate();
    }

    void setStats(String ram, String store, int netType, int usbCount) {
        this.ram = ram;
        this.store = store;
        this.netType = netType;
        this.usbCount = usbCount;
        invalidate();
    }

    private int count() {
        return ACTION_LABELS.length;
    }

    // ------------------------------------------------------------------ state

    void setExpanded(boolean on, boolean animate) {
        if (on == expanded && animate) return;
        expanded = on;
        if (exAnim != null) exAnim.cancel();
        float to = on ? 1f : 0f;
        if (!animate) {
            ex = to;
            invalidate();
            return;
        }
        Spring sp = on ? Spring.POP : Spring.OUT;
        exAnim = ValueAnimator.ofFloat(ex, to);
        exAnim.setDuration(sp.ms());
        exAnim.setInterpolator(sp);
        exAnim.addUpdateListener(a -> {
            ex = (Float) a.getAnimatedValue();
            invalidate();
        });
        exAnim.start();
    }

    boolean isExpanded() {
        return expanded;
    }

    void move(int d) {
        int j = Math.max(0, Math.min(count() - 1, sel + d));
        if (j == sel) return;
        sel = j;
        if (selAnim != null) selAnim.cancel();
        selAnim = ValueAnimator.ofFloat(selPos, j);
        selAnim.setDuration(Spring.EASE.ms());
        selAnim.setInterpolator(Spring.EASE);
        selAnim.addUpdateListener(a -> {
            selPos = (Float) a.getAnimatedValue();
            invalidate();
        });
        selAnim.start();
    }

    void activate() {
        if (sel >= 0 && sel < count()) host.onPanelAction(sel, null);
    }

    // ------------------------------------------------------------------ geometry

    private int topY() {
        return Ui.px(34);
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        setMeasuredDimension(MeasureSpec.getSize(ws), topY() + Ui.px(350) + Ui.px(40));
    }

    /** Rectangle of item i in the expanded layout. */
    private void cell(int i, RectF out) {
        float gapc = Ui.px(30), aw = Ui.px(250), ch = Ui.px(150);
        int n = count();
        float total = n * aw + (n - 1) * gapc;
        float x = getWidth() / 2f - total / 2f + i * (aw + gapc);
        float y = topY() + Ui.px(170);
        out.set(x, y, x + aw, y + ch);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas c) {
        float vw = getWidth();
        float wc = Ui.px(560), hc = Ui.px(88), we = Math.min(Ui.px(1440), vw - Ui.px(60)), he = Ui.px(350);
        float w = wc + (we - wc) * ex, h = hc + (he - hc) * ex;
        float top = topY();
        pill.set((vw - w) / 2f, top, (vw + w) / 2f, top + h);
        float e = Ui.clamp(ex, 0f, 1f);
        float rad = Ui.px(44) + (Ui.px(58) - Ui.px(44)) * e;

        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        if (glass.ready()) {
            glass.apply(this, p);
        } else {
            p.setColor(0x66222228);
        }
        c.drawRoundRect(pill, rad, rad, p);
        p.setShader(null);
        p.setColor(0x38000000);
        c.drawRoundRect(pill, rad, rad, p);
        p.setColor(0x1AFFFFFF);
        c.drawRoundRect(pill, rad, rad, p);

        float sw = Math.max(1.5f, Ui.px(2));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        p.setColor(0x52FFFFFF);
        r.set(pill);
        r.inset(sw / 2f, sw / 2f);
        c.drawRoundRect(r, rad - sw / 2f, rad - sw / 2f, p);
        p.setStyle(Paint.Style.FILL);

        float ca = 1f - Ui.clamp(e * 2.2f, 0f, 1f);
        float ea = Ui.clamp((e - 0.45f) / 0.55f, 0f, 1f);
        if (ca > 0.02f) drawCollapsed(c, ca);
        if (ea > 0.02f) drawExpanded(c, ea);
    }

    private void drawCollapsed(Canvas c, float a) {
        float cy = pill.centerY();
        int white = Color.argb((int) (a * 255), 255, 255, 255);
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setTypeface(Ui.medium);
        tp.setTextSize(Ui.px(42));
        float tW = tp.measureText(time);
        tp.setTypeface(Ui.regular);
        tp.setTextSize(Ui.px(28));
        float dW = tp.measureText(date);
        float gap = Ui.px(22), ic = Ui.px(34);
        float icons = ic + (usbCount > 0 ? Ui.px(16) + ic : 0f);
        float x = pill.centerX() - (tW + gap + dW + gap + icons) / 2f;

        tp.setTypeface(Ui.medium);
        tp.setTextSize(Ui.px(42));
        tp.setColor(white);
        c.drawText(time, x, cy + tp.getTextSize() * 0.35f, tp);
        x += tW + gap;
        tp.setTypeface(Ui.regular);
        tp.setTextSize(Ui.px(28));
        tp.setColor(Color.argb((int) (a * 190), 255, 255, 255));
        c.drawText(date, x, cy + tp.getTextSize() * 0.35f, tp);
        x += dW + gap;

        int netGlyph = netType == 2 ? Glyphs.GLOBE : Glyphs.WIFI;
        Glyphs.draw(c, netGlyph, x + ic / 2f, cy, ic, p, Color.argb((int) (a * (netType == 0 ? 80 : 255)), 255, 255, 255));
        if (usbCount > 0) Glyphs.draw(c, Glyphs.USB, x + ic + Ui.px(16) + ic / 2f, cy, ic, p, white);
    }

    private String netText() {
        return netType == 1 ? "Wi-Fi" : (netType == 2 ? "Ethernet" : "Offline");
    }

    private float chip(Canvas c, float right, float cy, String text, float a) {
        if (text == null || text.length() == 0) return right;
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setTypeface(Ui.regular);
        tp.setTextSize(Ui.px(26));
        float tw = tp.measureText(text);
        float cw = tw + Ui.px(40), chh = Ui.px(52);
        r.set(right - cw, cy - chh / 2f, right, cy + chh / 2f);
        p.setColor(Color.argb((int) (a * 46), 255, 255, 255));
        c.drawRoundRect(r, chh / 2f, chh / 2f, p);
        tp.setColor(Color.argb((int) (a * 235), 255, 255, 255));
        c.drawText(text, right - cw + Ui.px(20), cy + tp.getTextSize() * 0.35f, tp);
        return right - cw - Ui.px(14);
    }

    private void drawExpanded(Canvas c, float a) {
        float left = pill.left + Ui.px(56), right = pill.right - Ui.px(56), top = pill.top;
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setTypeface(Ui.medium);
        tp.setTextSize(Ui.px(68));
        tp.setColor(Color.argb((int) (a * 255), 255, 255, 255));
        c.drawText(time, left, top + Ui.px(92), tp);
        tp.setTypeface(Ui.regular);
        tp.setTextSize(Ui.px(30));
        tp.setColor(Color.argb((int) (a * 185), 255, 255, 255));
        c.drawText(date, left, top + Ui.px(134), tp);

        float x = right, cy = top + Ui.px(72);
        x = chip(c, x, cy, netText(), a);
        x = chip(c, x, cy, store, a);
        x = chip(c, x, cy, ram, a);
        if (usbCount > 0) chip(c, x, cy, usbCount == 1 ? "USB drive" : usbCount + " USB drives", a);

        int n = count();
        int i0 = Math.max(0, Math.min(n - 1, (int) Math.floor(selPos)));
        int i1 = Math.min(n - 1, i0 + 1);
        float f = Ui.clamp(selPos - i0, 0f, 1f);
        cell(i0, r);
        cell(i1, r2);
        hl.set(r.left + (r2.left - r.left) * f - Ui.px(10), r.top + (r2.top - r.top) * f - Ui.px(8),
                r.right + (r2.right - r.right) * f + Ui.px(10), r.bottom + (r2.bottom - r.bottom) * f + Ui.px(8));
        p.setColor(Color.argb((int) (a * 235), 255, 255, 255));
        c.drawRoundRect(hl, Ui.px(32), Ui.px(32), p);

        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTypeface(Ui.medium);
        tp.setTextSize(Ui.px(24));
        for (int i = 0; i < n; i++) {
            cell(i, r);
            float fa = Ui.clamp(1f - Math.abs(selPos - i), 0f, 1f);
            int col = Ui.lerpColor(0xFFFFFFFF, 0xFF1C1C1E, fa);
            int colA = Color.argb((int) (a * Color.alpha(col)), Color.red(col), Color.green(col), Color.blue(col));
            Glyphs.draw(c, ACTION_GLYPHS[i], r.centerX(), r.top + Ui.px(58), Ui.px(88), p, colA);
            tp.setColor(colA);
            c.drawText(ACTION_LABELS[i], r.centerX(), r.bottom - Ui.px(16), tp);
        }
        tp.setTextAlign(Paint.Align.LEFT);
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        if (!pill.contains(x, y)) return false;           // transparent area: let touches through to the grid
        if (e.getAction() == MotionEvent.ACTION_UP) {
            if (!expanded) {
                host.onPanelAction(A_EXPAND, null);
            } else {
                for (int i = 0; i < count(); i++) {
                    cell(i, r2);
                    if (r2.contains(x, y)) {
                        sel = i;
                        selPos = i;
                        activate();
                        break;
                    }
                }
            }
        }
        return true;
    }
}
