package com.frost.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A menu panel that floats over a blurred snapshot of the home screen (the "glass" look, but the blur
 * is computed once when the sheet opens, so there is no per-frame cost). Used for the app picker,
 * the image browser (with preview), the options menu and the launcher settings.
 * Menus use device units, so they stay the same readable size whatever the main scaler is set to.
 */
final class Sheet {
    static final int K_PLAIN = 0, K_DIR = 1, K_FILE = 2, K_UP = 3, K_OWN = 4, K_HEADER = 5, K_ADJ = 6, K_ACT = 7;

    static final class Item {
        final String title, sub;
        final Bitmap icon;
        final int kind;
        final Object tag;
        String value;
        String shown, shownSub;

        Item(String title, String sub, Bitmap icon, int kind, Object tag) {
            this.title = title;
            this.sub = sub;
            this.icon = icon;
            this.kind = kind;
            this.tag = tag;
        }

        static Item header(String title) {
            return new Item(title, null, null, K_HEADER, null);
        }

        /** A setting you change with Left/Right (or OK to cycle). */
        static Item adj(String title, String value, int id) {
            Item i = new Item(title, null, null, K_ADJ, id);
            i.value = value;
            return i;
        }

        /** A setting row that runs an action when you press OK. */
        static Item act(String title, String value, int id) {
            Item i = new Item(title, null, null, K_ACT, id);
            i.value = value;
            return i;
        }
    }

    interface Listener {
        void onPick(Item it);

        void onBack();

        default void onAdjust(Item it, int dir) {
        }
    }

    final FrameLayout view;
    private final HomeActivity act;
    private final Listener lis;
    private final boolean withPreview;
    private final FrameLayout panel;
    private final TextView subV;
    private final RowList list;
    private ImageView previewV;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean armed, closing;
    private long lastNav, lastAdj;
    private int previewToken;

    Sheet(HomeActivity act, String title, String sub, int widthUnits, boolean withPreview, Bitmap blur,
          final Listener lis) {
        this.act = act;
        this.lis = lis;
        this.withPreview = withPreview;

        view = new FrameLayout(act);
        view.setClickable(true);
        view.setOnClickListener(v -> lis.onBack());

        ImageView bgv = new ImageView(act);
        bgv.setScaleType(ImageView.ScaleType.FIT_XY);
        if (blur != null) {
            bgv.setImageBitmap(blur);
            bgv.setColorFilter(0xFF8E8E96, PorterDuff.Mode.MULTIPLY);
        } else {
            bgv.setBackgroundColor(0xCC000000);
        }
        view.addView(bgv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        int pw = Math.min(Ui.pd(withPreview ? 1380 : widthUnits), Ui.W - Ui.pd(60));
        int ph = Math.min(Ui.pd(840), Ui.H - Ui.pd(60));
        panel = new FrameLayout(act);
        panel.setClickable(true);
        GradientDrawable pg = new GradientDrawable();
        pg.setColor(0x8C26262B);
        pg.setCornerRadius(Ui.pd(46));
        pg.setStroke(Math.max(1, Ui.pd(1.5f)), 0x33FFFFFF);
        panel.setBackground(pg);
        view.addView(panel, new FrameLayout.LayoutParams(pw, ph, Gravity.CENTER));

        int pad = Ui.pd(56);
        int listW = withPreview ? Ui.pd(800) : pw - Ui.pd(56);

        TextView titleV = Ui.textd(act, 44, Ui.medium, 0xFFFFFFFF);
        titleV.setText(title);
        titleV.setSingleLine(true);
        titleV.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(listW - Ui.pd(28), ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.leftMargin = pad;
        tl.topMargin = Ui.pd(46);
        panel.addView(titleV, tl);

        subV = Ui.textd(act, 25, Ui.regular, 0x99FFFFFF);
        subV.setSingleLine(true);
        subV.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        subV.setText(sub == null ? "" : sub);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(listW - Ui.pd(28), ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.leftMargin = pad;
        sl.topMargin = Ui.pd(106);
        panel.addView(subV, sl);

        list = new RowList(act);
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(listW, ph - Ui.pd(160) - Ui.pd(30));
        ll.leftMargin = Ui.pd(28);
        ll.topMargin = Ui.pd(160);
        panel.addView(list, ll);

        if (withPreview) {
            FrameLayout box = new FrameLayout(act);
            GradientDrawable bd = new GradientDrawable();
            bd.setColor(0x40000000);
            bd.setCornerRadius(Ui.pd(30));
            box.setBackground(bd);
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Ui.pd(468), Ui.pd(468));
            bl.leftMargin = Ui.pd(28 + 800 + 28);
            bl.topMargin = Ui.pd(170);
            panel.addView(box, bl);

            previewV = new ImageView(act);
            previewV.setScaleType(ImageView.ScaleType.FIT_CENTER);
            previewV.setPadding(Ui.pd(28), Ui.pd(28), Ui.pd(28), Ui.pd(28));
            previewV.setAlpha(0f);
            box.addView(previewV, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            TextView hint = Ui.textd(act, 24, Ui.regular, 0x80FFFFFF);
            hint.setText("Icons: a transparent PNG is trimmed and centred; a photo or banner (JPG or PNG) is cropped to fill the tile. "
                    + "Backgrounds and fonts are copied into the launcher, so you can unplug the USB drive.");
            FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(Ui.pd(468), ViewGroup.LayoutParams.WRAP_CONTENT);
            hl.leftMargin = bl.leftMargin;
            hl.topMargin = Ui.pd(170 + 468 + 24);
            panel.addView(hint, hl);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    void show(ViewGroup parent) {
        parent.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(Spring.dur(240)).start();
        panel.setScaleX(0.9f);
        panel.setScaleY(0.9f);
        panel.animate().scaleX(1f).scaleY(1f).setDuration(Spring.POP.ms()).setInterpolator(Spring.POP).start();
    }

    void dismiss() {
        if (closing) return;
        closing = true;
        h.removeCallbacksAndMessages(null);
        panel.animate().scaleX(0.95f).scaleY(0.95f).setDuration(Spring.dur(170)).start();
        view.animate().alpha(0f).setDuration(Spring.dur(190)).withEndAction(new Runnable() {
            @Override
            public void run() {
                ViewParent p = view.getParent();
                if (p instanceof ViewGroup) ((ViewGroup) p).removeView(view);
            }
        }).start();
    }

    void setItems(List<Item> items, int sel) {
        list.setItems(items, sel);
    }

    void setSubtitle(String s) {
        subV.setText(s == null ? "" : s);
    }

    /** Redraw after item values changed. */
    void refresh() {
        list.invalidate();
    }

    // ------------------------------------------------------------------ input

    boolean onKey(KeyEvent e) {
        if (closing) return true;
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) {
                    long now = SystemClock.uptimeMillis();
                    if (e.getRepeatCount() == 0 || now - lastNav > 90) {
                        lastNav = now;
                        list.move(code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                    }
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) {
                    Item it = list.current();
                    long now = SystemClock.uptimeMillis();
                    if (it != null && it.kind == K_ADJ && (e.getRepeatCount() == 0 || now - lastAdj > 130)) {
                        lastAdj = now;
                        lis.onAdjust(it, code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                        list.invalidate();
                    }
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (down) {
                    if (e.getRepeatCount() == 0) armed = true;
                } else if (armed) {
                    armed = false;
                    list.confirm();
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BUTTON_B:
                if (!down) lis.onBack();
                return true;
            default:
                return false;
        }
    }

    // ------------------------------------------------------------------ preview (image browser)

    private void loadPreview(Item it) {
        if (!withPreview || previewV == null) return;
        previewToken++;
        final int tok = previewToken;
        h.removeCallbacksAndMessages(null);
        if (it == null || it.kind != K_FILE || !(it.tag instanceof File)) {
            previewV.animate().alpha(0f).setDuration(120).start();
            return;
        }
        final String path = ((File) it.tag).getPath();
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                act.exec.execute(new Runnable() {
                    @Override
                    public void run() {
                        final Bitmap b = FontTools.isFontName(path) ? FontTools.sample(path, 640)
                                : TileArt.decode(path, 640);
                        act.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (closing || tok != previewToken || b == null) return;
                                previewV.setImageBitmap(b);
                                previewV.setAlpha(0f);
                                previewV.animate().alpha(1f).setDuration(220).start();
                            }
                        });
                    }
                });
            }
        }, 160);
    }

    // ------------------------------------------------------------------ the list itself

    private final class RowList extends View {
        final ArrayList<Item> items = new ArrayList<>();
        float pos;
        int sel;
        ValueAnimator anim;
        final float rowH = Ui.pd(96);
        final float inset = Ui.pd(8);
        final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint sp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint vp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint hp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint ip = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF rf = new RectF(), rg = new RectF();
        final Path path = new Path();

        RowList(Context c) {
            super(c);
            tp.setTypeface(Ui.medium);
            tp.setTextSize(Ui.pd(34));
            sp.setTypeface(Ui.regular);
            sp.setTextSize(Ui.pd(24));
            vp.setTypeface(Ui.regular);
            vp.setTextSize(Ui.pd(32));
            hp.setTypeface(Ui.medium);
            hp.setTextSize(Ui.pd(23));
        }

        Item current() {
            return items.isEmpty() ? null : items.get(sel);
        }

        void setItems(List<Item> src, int selIndex) {
            items.clear();
            items.addAll(src);
            sel = Math.max(0, Math.min(items.size() - 1, selIndex));
            while (sel < items.size() - 1 && items.get(sel).kind == K_HEADER) sel++;
            pos = sel;
            if (anim != null) anim.cancel();
            invalidate();
            loadPreview(items.isEmpty() ? null : items.get(sel));
        }

        void move(int d) {
            int j = sel + d;
            while (j >= 0 && j < items.size() && items.get(j).kind == K_HEADER) j += d;
            if (j < 0 || j >= items.size()) return;
            select(j);
        }

        void select(int i) {
            if (items.isEmpty()) return;
            i = Math.max(0, Math.min(items.size() - 1, i));
            if (i == sel || items.get(i).kind == K_HEADER) return;
            sel = i;
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(pos, i);
            anim.setDuration(Spring.EASE.ms());
            anim.setInterpolator(Spring.EASE);
            anim.addUpdateListener(a -> {
                pos = (Float) a.getAnimatedValue();
                invalidate();
            });
            anim.start();
            loadPreview(items.get(sel));
        }

        void confirm() {
            if (!items.isEmpty()) lis.onPick(items.get(sel));
        }

        float topRows() {
            float vis = getHeight() / rowH;
            return Ui.clamp(pos - (vis - 1f) / 2f, 0f, Math.max(0f, items.size() - vis));
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                int i = (int) Math.floor(topRows() + e.getY() / rowH);
                if (i >= 0 && i < items.size() && items.get(i).kind != K_HEADER) {
                    if (i == sel) confirm();
                    else select(i);
                }
            }
            return true;
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            for (Item it : items) {
                it.shown = null;
                it.shownSub = null;
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            int n = items.size();
            if (n == 0) return;
            float top = topRows();
            float vis = getHeight() / rowH;
            int first = Math.max(0, (int) Math.floor(top));
            int last = Math.min(n - 1, (int) Math.ceil(top + vis));
            int w = getWidth();

            for (int i = first; i <= last; i++) {
                Item it = items.get(i);
                float y = (i - top) * rowH;

                if (it.kind == K_HEADER) {
                    hp.setColor(0x99FFFFFF);
                    c.drawText(it.title, inset + Ui.pd(28), y + rowH * 0.78f, hp);
                    continue;
                }

                float a = Ui.clamp(1f - Math.abs(pos - i), 0f, 1f);
                if (a > 0.01f) {                                   // the white selection pill, cross-fading between rows
                    float in = (1f - a) * Ui.pd(10);
                    pill.setColor(Color.argb((int) (a * 238), 255, 255, 255));
                    rf.set(inset + in, y + Ui.pd(6) + in * 0.5f, w - inset - in, y + rowH - Ui.pd(6) - in * 0.5f);
                    c.drawRoundRect(rf, Ui.pd(26), Ui.pd(26), pill);
                }
                int titleC = Ui.lerpColor(0xF2FFFFFF, 0xFF141416, a);
                int subC = Ui.lerpColor(0x8CFFFFFF, 0x99141416, a);

                float x = inset + Ui.pd(28);
                float isz = Ui.pd(60);
                if (it.icon != null || it.kind == K_DIR || it.kind == K_FILE || it.kind == K_UP || it.kind == K_OWN) {
                    float iy = y + (rowH - isz) / 2f;
                    if (it.icon != null) {
                        rg.set(x, iy, x + isz, iy + isz);
                        c.drawBitmap(it.icon, null, rg, ip);
                    } else {
                        glyph(c, it.kind, x, iy, isz, titleC);
                    }
                    x += isz + Ui.pd(24);
                }

                float reserve = 0f;
                if (it.kind == K_ADJ || it.kind == K_ACT) {
                    String v = it.value == null ? "" : it.value;
                    vp.setColor(Ui.lerpColor(0xB3FFFFFF, 0xCC141416, a));
                    float vw = vp.measureText(v);
                    float chev = it.kind == K_ADJ ? Ui.pd(34) : 0f;
                    float vr = w - inset - Ui.pd(30) - chev;              // right edge of the value text
                    c.drawText(v, vr - vw, y + rowH / 2f + vp.getTextSize() * 0.34f, vp);
                    if (it.kind == K_ADJ && a > 0.2f) {
                        int cc = Color.argb((int) (a * 255), 20, 20, 22);
                        chevron(c, vr - vw - Ui.pd(22), y + rowH / 2f, Ui.pd(9), -1, cc);
                        chevron(c, vr + Ui.pd(20), y + rowH / 2f, Ui.pd(9), 1, cc);
                    }
                    reserve = vw + chev + Ui.pd(50);
                }

                float avail = Math.max(10f, w - x - Ui.pd(34) - reserve);
                if (it.shown == null) {
                    it.shown = TextUtils.ellipsize(it.title, tp, avail, TextUtils.TruncateAt.END).toString();
                    it.shownSub = it.sub == null ? null
                            : TextUtils.ellipsize(it.sub, sp, avail, TextUtils.TruncateAt.MIDDLE).toString();
                }
                tp.setColor(titleC);
                if (it.shownSub != null && it.shownSub.length() > 0) {
                    c.drawText(it.shown, x, y + rowH * 0.45f, tp);
                    sp.setColor(subC);
                    c.drawText(it.shownSub, x, y + rowH * 0.45f + Ui.pd(31), sp);
                } else {
                    c.drawText(it.shown, x, y + rowH / 2f + tp.getTextSize() * 0.34f, tp);
                }
            }
        }

        private void chevron(Canvas c, float x, float cy, float s, int dir, int color) {
            gp.setStyle(Paint.Style.STROKE);
            gp.setStrokeWidth(Ui.pd(4));
            gp.setStrokeCap(Paint.Cap.ROUND);
            gp.setStrokeJoin(Paint.Join.ROUND);
            gp.setColor(color);
            path.reset();
            path.moveTo(x - dir * s / 2f, cy - s);
            path.lineTo(x + dir * s / 2f, cy);
            path.lineTo(x - dir * s / 2f, cy + s);
            c.drawPath(path, gp);
        }

        private void glyph(Canvas c, int kind, float x, float y, float s, int color) {
            gp.setColor(color);
            if (kind == K_DIR) {
                gp.setStyle(Paint.Style.FILL);
                gp.setColor(0xFF5AA9F5);
                rg.set(x, y + s * 0.22f, x + s, y + s * 0.88f);
                c.drawRoundRect(rg, s * 0.12f, s * 0.12f, gp);
                rg.set(x, y + s * 0.12f, x + s * 0.42f, y + s * 0.42f);
                c.drawRoundRect(rg, s * 0.1f, s * 0.1f, gp);
            } else if (kind == K_UP) {
                gp.setStyle(Paint.Style.STROKE);
                gp.setStrokeWidth(s * 0.1f);
                gp.setStrokeCap(Paint.Cap.ROUND);
                gp.setStrokeJoin(Paint.Join.ROUND);
                path.reset();
                path.moveTo(x + s * 0.24f, y + s * 0.52f);
                path.lineTo(x + s * 0.5f, y + s * 0.26f);
                path.lineTo(x + s * 0.76f, y + s * 0.52f);
                c.drawPath(path, gp);
                c.drawLine(x + s * 0.5f, y + s * 0.28f, x + s * 0.5f, y + s * 0.78f, gp);
            } else {                                               // image file / "own icon"
                gp.setStyle(Paint.Style.STROKE);
                gp.setStrokeWidth(s * 0.07f);
                rg.set(x + s * 0.08f, y + s * 0.14f, x + s * 0.92f, y + s * 0.86f);
                c.drawRoundRect(rg, s * 0.14f, s * 0.14f, gp);
                gp.setStyle(Paint.Style.FILL);
                c.drawCircle(x + s * 0.34f, y + s * 0.38f, s * 0.07f, gp);
                path.reset();
                path.moveTo(x + s * 0.14f, y + s * 0.78f);
                path.lineTo(x + s * 0.42f, y + s * 0.52f);
                path.lineTo(x + s * 0.60f, y + s * 0.68f);
                path.lineTo(x + s * 0.72f, y + s * 0.58f);
                path.lineTo(x + s * 0.88f, y + s * 0.78f);
                path.close();
                c.drawPath(path, gp);
            }
        }
    }
}
