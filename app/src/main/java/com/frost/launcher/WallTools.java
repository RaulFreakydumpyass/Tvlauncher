package com.frost.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import java.io.File;
import java.io.FileOutputStream;

/**
 * Wallpaper handling. A picked image is COPIED into the launcher's private storage (downscaled), so it keeps
 * working after the USB stick is unplugged. Blur is applied once, on a small bitmap, never per frame.
 */
final class WallTools {
    private WallTools() { }

    static File file(Context c) {
        return new File(c.getFilesDir(), "wallpaper.jpg");
    }

    static int glassH() {
        return Math.max(1, Math.round(160f * Ui.H / Ui.W));
    }

    /** Copies + downsizes the chosen picture into private storage. */
    static boolean importImage(Context c, String path) {
        try {
            Bitmap b = TileArt.decode(path, 1280);
            if (b == null) return false;
            float k = Math.min(1f, 1280f / Math.max(b.getWidth(), b.getHeight()));
            if (k < 1f) {
                b = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * k)),
                        Math.max(1, Math.round(b.getHeight() * k)), true);
            }
            FileOutputStream o = new FileOutputStream(file(c));
            try {
                b.compress(Bitmap.CompressFormat.JPEG, 88, o);
            } finally {
                o.close();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** @return {display bitmap, blurred glass bitmap} */
    static Bitmap[] build(Context c, int blurMode) {
        int gw = 160, gh = glassH();
        Bitmap src = null;
        try {
            File f = file(c);
            if (f.exists()) src = TileArt.decode(f.getPath(), 1280);
        } catch (Throwable ignored) {
            src = null;
        }
        if (src == null) return defaults();
        try {
            Bitmap glass = blur(coverScale(src, gw, gh), gw, gh, 5, 3);
            Bitmap display;
            if (blurMode == 0) {
                display = src;
            } else if (blurMode == 1) {
                display = blur(coverScale(src, 320, gh * 2), 320, gh * 2, 2, 2);
            } else {
                display = glass;
            }
            return new Bitmap[]{display, glass};
        } catch (Throwable t) {
            return defaults();
        }
    }

    static Bitmap[] defaults() {
        int gw = 160, gh = glassH();
        Bitmap d = defaultWall(gw, gh);
        return new Bitmap[]{d, blur(d, gw, gh, 3, 2)};
    }

    static Bitmap defaultWall(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h, 0xFF2C3A68, 0xFF0A0C18, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w * 0.25f, h * 0.2f, w * 0.55f, 0x663E86F0, 0x003E86F0, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w * 0.85f, h * 0.85f, w * 0.5f, 0x55A25BE0, 0x00A25BE0, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        return b;
    }

    private static Bitmap coverScale(Bitmap src, int w, int h) {
        Bitmap b = src;
        while (b.getWidth() / 2 >= w * 2 && b.getHeight() / 2 >= h * 2) {
            b = Bitmap.createScaledBitmap(b, b.getWidth() / 2, b.getHeight() / 2, true);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float k = Math.max(w / (float) b.getWidth(), h / (float) b.getHeight());
        float dw = b.getWidth() * k, dh = b.getHeight() * k;
        c.drawBitmap(b, null, new RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    private static Bitmap blur(Bitmap b, int w, int h, int r, int passes) {
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        TileArt.boxBlur(px, w, h, r, passes);
        Bitmap o = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        o.setPixels(px, 0, w, 0, 0, w, h);
        return o;
    }
}
