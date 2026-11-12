package com.frost.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * Screen-size helpers. 1 "unit" = 1/1080 of the short screen side, so the layout scales to any TV.
 * {@code u} also includes the user's main scaler; {@code u0} ignores it (used by menus so they stay readable).
 */
final class Ui {
    static float u0 = 1f, u = 1f, scale = 1f;
    static int W = 1920, H = 1080;
    static Typeface regular = Typeface.DEFAULT;
    static Typeface medium = Typeface.DEFAULT;
    static Typeface light = Typeface.DEFAULT;
    static Typeface bold = Typeface.DEFAULT_BOLD;

    private Ui() { }

    static void init(Activity a, int uiScalePct, int fontMode) {
        DisplayMetrics m = new DisplayMetrics();
        a.getWindowManager().getDefaultDisplay().getRealMetrics(m);
        W = Math.max(m.widthPixels, m.heightPixels);
        H = Math.min(m.widthPixels, m.heightPixels);
        u0 = H / 1080f;
        scale = uiScalePct / 100f;
        u = u0 * scale;
        regular = Typeface.DEFAULT;
        medium = Typeface.DEFAULT;
        light = Typeface.DEFAULT;
        bold = Typeface.DEFAULT_BOLD;
        try {
            if (fontMode == Prefs.FONT_CUSTOM) {
                Typeface t = FontTools.load(a);                  // the font copied from USB / storage
                if (t != null) {
                    regular = t;                                 // a custom font is one weight; bold is synthesised
                    medium = t;
                    light = t;
                    bold = Typeface.create(t, Typeface.BOLD);
                    return;
                }
                fontMode = 0;                                    // file missing or unreadable: back to the system font
            }
            String fam = fontMode == 1 ? "sans-serif-condensed" : fontMode == 2 ? "serif"
                    : fontMode == 3 ? "monospace" : "sans-serif";
            regular = Typeface.create(fam, Typeface.NORMAL);
            bold = Typeface.create(fam, Typeface.BOLD);
            if (fontMode == 0) {
                medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
                light = Typeface.create("sans-serif-light", Typeface.NORMAL);
            } else {
                medium = regular;
                light = regular;
            }
        } catch (Throwable ignored) {
            // keep defaults
        }
    }

    /** Scaled units (follows the main scaler). */
    static int px(float units) {
        return Math.round(units * u);
    }

    /** Device units (ignores the main scaler). */
    static int pd(float units) {
        return Math.round(units * u0);
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static int lerpColor(int a, int b, float t) {
        t = clamp(t, 0f, 1f);
        return Color.argb(
                mix(a >>> 24, b >>> 24, t),
                mix((a >> 16) & 255, (b >> 16) & 255, t),
                mix((a >> 8) & 255, (b >> 8) & 255, t),
                mix(a & 255, b & 255, t));
    }

    private static int mix(int x, int y, float t) {
        return Math.round(x + (y - x) * t);
    }

    static TextView text(Context c, float units, Typeface tf, int color) {
        return make(c, units * u, tf, color);
    }

    static TextView textd(Context c, float units, Typeface tf, int color) {
        return make(c, units * u0, tf, color);
    }

    private static TextView make(Context c, float px, Typeface tf, int color) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }
}
