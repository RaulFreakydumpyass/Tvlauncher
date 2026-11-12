package com.frost.launcher;

import android.content.Context;
import android.content.SharedPreferences;

/** All launcher settings, stored in SharedPreferences. */
final class Prefs {
    static final float[] SPEEDS = {0.05f, 0.65f, 1f, 1.5f};      // Off, Fast, Normal, Relaxed
    static final int[] SAVER_MIN = {0, 2, 5, 10, 20, 30};
    static final int FONT_CUSTOM = 4;

    int cols = 4;            // apps per row (3..6)
    int iconScale = 100;     // tile size %, 60..150
    int nameScale = 100;     // app name size %, 70..150
    int names = 1;           // 0 off, 1 on focus, 2 always
    int uiScale = 100;       // main scaler %, 70..130
    int font = 0;            // 0 system, 1 condensed, 2 serif, 3 monospace, 4 custom (copied from USB / storage)
    int dim = 35;            // background dim %, 0..80
    int blur = 1;            // background blur: 0 off, 1 soft, 2 strong
    int timeFmt = 0;         // 0 system, 1 12-hour, 2 24-hour
    int speed = 2;           // index into SPEEDS
    int saver = 3;           // index into SAVER_MIN (3 = 10 minutes)
    boolean panel = true;    // floating top panel
    boolean band = true;     // frosted selection bar
    boolean tilt = true;     // tile tilt when focus moves

    static Prefs load(Context c) {
        SharedPreferences sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        Prefs p = new Prefs();
        p.cols = sp.getInt("cols", p.cols);
        p.iconScale = sp.getInt("iconScale", p.iconScale);
        p.nameScale = sp.getInt("nameScale", p.nameScale);
        p.names = sp.getInt("names", p.names);
        p.uiScale = sp.getInt("uiScale", p.uiScale);
        p.font = sp.getInt("font", p.font);
        p.dim = sp.getInt("dim", p.dim);
        p.blur = sp.getInt("blur", p.blur);
        p.timeFmt = sp.getInt("timeFmt", p.timeFmt);
        p.speed = sp.getInt("speed", p.speed);
        p.saver = sp.getInt("saver", p.saver);
        p.panel = sp.getBoolean("panel", p.panel);
        p.band = sp.getBoolean("band", p.band);
        p.tilt = sp.getBoolean("tilt", p.tilt);
        p.clampAll();
        return p;
    }

    void save(Context c) {
        c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putInt("cols", cols).putInt("iconScale", iconScale).putInt("nameScale", nameScale)
                .putInt("names", names).putInt("uiScale", uiScale).putInt("font", font).putInt("dim", dim).putInt("blur", blur)
                .putInt("timeFmt", timeFmt).putInt("speed", speed).putInt("saver", saver)
                .putBoolean("panel", panel).putBoolean("band", band).putBoolean("tilt", tilt)
                .apply();
    }

    Prefs copy() {
        Prefs p = new Prefs();
        p.cols = cols;
        p.iconScale = iconScale;
        p.nameScale = nameScale;
        p.names = names;
        p.uiScale = uiScale;
        p.font = font;
        p.dim = dim;
        p.blur = blur;
        p.timeFmt = timeFmt;
        p.speed = speed;
        p.saver = saver;
        p.panel = panel;
        p.band = band;
        p.tilt = tilt;
        return p;
    }

    void reset() {
        Prefs d = new Prefs();
        cols = d.cols;
        iconScale = d.iconScale;
        nameScale = d.nameScale;
        names = d.names;
        uiScale = d.uiScale;
        font = d.font;
        dim = d.dim;
        blur = d.blur;
        timeFmt = d.timeFmt;
        speed = d.speed;
        saver = d.saver;
        panel = d.panel;
        band = d.band;
        tilt = d.tilt;
    }

    /** True when nothing that affects the grid / panel layout differs. */
    boolean sameLayout(Prefs o) {
        return cols == o.cols && iconScale == o.iconScale && nameScale == o.nameScale && names == o.names
                && uiScale == o.uiScale && font == o.font && panel == o.panel && band == o.band && tilt == o.tilt;
    }

    boolean sameWall(Prefs o) {
        return dim == o.dim && blur == o.blur;
    }

    private static int cl(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void clampAll() {
        cols = cl(cols, 3, 6);
        iconScale = cl(iconScale, 60, 150);
        nameScale = cl(nameScale, 70, 150);
        names = cl(names, 0, 2);
        uiScale = cl(uiScale, 70, 130);
        font = cl(font, 0, FONT_CUSTOM);
        dim = cl(dim, 0, 80);
        blur = cl(blur, 0, 2);
        timeFmt = cl(timeFmt, 0, 2);
        speed = cl(speed, 0, SPEEDS.length - 1);
        saver = cl(saver, 0, SAVER_MIN.length - 1);
    }
}
