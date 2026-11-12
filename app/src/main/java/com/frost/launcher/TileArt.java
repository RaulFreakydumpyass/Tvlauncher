package com.frost.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Turns a transparent PNG into a finished 16:9 tile, ONCE, when you add the app:
 *   1. trims the empty transparent margins and scales the logo to fit,
 *   2. picks a background colour that contrasts with the logo,
 *   3. builds a heavily blurred colour field from the logo itself (done on a tiny 96x54 bitmap, so it is instant),
 *   4. keeps the tile surface square: the Apple-style rounded corners and the thin border are applied when the
 *      tile is drawn (see TileView), so there is one single corner radius for everything.
 * A picture that fills the whole image (a photo / banner, JPG or opaque PNG) is not treated as a logo: it is
 * centre-cropped to 16:9 and becomes the whole tile, shown with the same Apple corners.
 * At runtime the launcher only draws these ready-made bitmaps, so scrolling stays smooth on weak hardware.
 */
final class TileArt {
    static final int W = 480, H = 270;
    /** Corner radius as a fraction of tile height. Drawn with Apple's continuous-corner curve (see Shape). */
    static final float RADIUS = 0.11f;
    /** iOS app-icon corner radius as a fraction of the icon's side (used for square pictures). */
    private static final float ICON_RADIUS = 0.2237f;
    /** The logo bitmap is stored at this multiple of its on-tile size so it stays crisp when focused. */
    static final float FG_SCALE = 1.25f;
    private static final int FG_MAX_W = Math.round(350 * FG_SCALE);
    private static final int FG_MAX_H = Math.round(150 * FG_SCALE);

    private TileArt() { }

    /** @return null on success, otherwise a message to show to the user. */
    static String build(Context ctx, TileStore store, TileStore.Tile t, String pngPath) {
        try {
            Bitmap src = pngPath != null ? decode(pngPath, 1400) : iconBitmap(ctx, t.pkg);
            if (src == null) return "Couldn't read that image.";

            float opaque = opaqueFraction(src);
            boolean wide = src.getWidth() >= src.getHeight() * 1.3f;
            if (pngPath != null && opaque >= 0.97f && wide) {        // a photo / banner: the picture IS the tile
                buildPhoto(store, t, src);
                return null;
            }

            Rect box = opaqueBounds(src);
            if (box == null) return "That image is completely transparent.";
            Bitmap crop = Bitmap.createBitmap(src, box.left, box.top, box.width(), box.height());
            float k = Math.min((float) FG_MAX_W / crop.getWidth(), (float) FG_MAX_H / crop.getHeight());
            k = Math.min(k, 2f);
            Bitmap fg = scaleSmooth(crop, Math.max(1, Math.round(crop.getWidth() * k)),
                    Math.max(1, Math.round(crop.getHeight() * k)));
            if (opaque >= 0.97f) fg = roundIcon(fg);                  // square picture: give it Apple's icon corners

            // --- colour choice: make sure logo and background never melt into each other ---
            float[] st = analyze(fg);
            float lum = st[3];
            float[] hsv = new float[3];
            Color.colorToHSV(Color.rgb((int) st[0], (int) st[1], (int) st[2]), hsv);
            int base;
            float glowAlpha;
            ColorFilter glowFilter = null;
            if (lum > 0.72f) {                       // light logo -> dark background
                hsv[1] = Math.min(hsv[1], 0.35f);
                hsv[2] = 0.20f;
                base = Color.HSVToColor(hsv);
                glowAlpha = 0.35f;
                glowFilter = new LightingColorFilter(0xFF808080, 0);
            } else if (lum < 0.22f) {                // dark logo -> light background
                hsv[1] = Math.min(hsv[1], 0.18f);
                hsv[2] = 0.93f;
                base = Color.HSVToColor(hsv);
                glowAlpha = 0.30f;
            } else {                                 // coloured logo -> deep tinted background + soft glow
                hsv[1] = Math.min(1f, hsv[1] * 0.9f);
                hsv[2] = 0.30f;
                base = Color.HSVToColor(hsv);
                glowAlpha = 0.85f;
                glowFilter = new LightingColorFilter(0xFFA6A6A6, 0);
            }

            // --- blurred colour field, computed on a tiny bitmap ---
            final int bw = 96, bh = 54;
            Bitmap small = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas smallCanvas = new Canvas(small);
            smallCanvas.drawColor(base);
            Paint gp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            gp.setAlpha((int) (glowAlpha * 255));
            if (glowFilter != null) gp.setColorFilter(glowFilter);
            float gh = bh * 1.35f;
            float gw = gh * fg.getWidth() / fg.getHeight();
            if (gw > bw * 1.5f) {
                gw = bw * 1.5f;
                gh = gw * fg.getHeight() / fg.getWidth();
            }
            smallCanvas.drawBitmap(fg, null,
                    new RectF((bw - gw) / 2f, (bh - gh) / 2f, (bw + gw) / 2f, (bh + gh) / 2f), gp);
            int[] px = new int[bw * bh];
            small.getPixels(px, 0, bw, 0, 0, bw, bh);
            boxBlur(px, bw, bh, 4, 3);
            small.setPixels(px, 0, bw, 0, 0, bw, bh);

            // --- full-size tile surface: upscale the blur (smooth) + subtle light from the top ---
            Bitmap raw = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas rc = new Canvas(raw);
            rc.drawBitmap(small, null, new Rect(0, 0, W, H), new Paint(Paint.FILTER_BITMAP_FLAG));
            Paint lp = new Paint();
            lp.setShader(new LinearGradient(0, 0, 0, H * 0.55f, 0x26FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            rc.drawRect(0, 0, W, H * 0.55f, lp);
            lp.setShader(new LinearGradient(0, H * 0.55f, 0, H, 0x00000000, 0x38000000, Shader.TileMode.CLAMP));
            rc.drawRect(0, H * 0.55f, W, H, lp);

            // (the surface stays square; rounded corners and the border are drawn by TileView)
            save(raw, store.bg(t));
            save(fg, store.fg(t));
            return null;
        } catch (OutOfMemoryError e) {
            return "Not enough memory for that image. Try a smaller PNG.";
        } catch (Exception e) {
            return "Couldn't create the icon: " + e.getMessage();
        }
    }

    /** Photo mode: centre-crop the picture to 16:9 and use it as the whole tile (no logo layer). */
    private static void buildPhoto(TileStore store, TileStore.Tile t, Bitmap src) throws IOException {
        float k = Math.max(W / (float) src.getWidth(), H / (float) src.getHeight());
        int dw = Math.max(W, Math.round(src.getWidth() * k)), dh = Math.max(H, Math.round(src.getHeight() * k));
        Bitmap s = k < 1f ? scaleSmooth(src, dw, dh) : src;
        Bitmap raw = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas rc = new Canvas(raw);
        rc.drawBitmap(s, null, new RectF((W - dw) / 2f, (H - dh) / 2f, (W + dw) / 2f, (H + dh) / 2f),
                new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        save(raw, store.bg(t));
        //noinspection ResultOfMethodCallIgnored
        store.fg(t).delete();                                     // no logo layer for a full picture
    }

    /** Clips a square-ish opaque picture to the iOS app-icon shape. */
    private static Bitmap roundIcon(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        Bitmap o = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        Path path = new Path();
        Shape.rect(path, 0, 0, w, h, ICON_RADIUS * Math.min(w, h));
        new Canvas(o).drawPath(path, p);
        return o;
    }

    /** Share of (sampled) pixels that are almost fully opaque: ~1 for a photo, much less for a logo. */
    private static float opaqueFraction(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int sx = Math.max(1, w / 64), sy = Math.max(1, h / 64);
        int total = 0, solid = 0;
        for (int y = 0; y < h; y += sy) {
            for (int x = 0; x < w; x += sx) {
                total++;
                if ((b.getPixel(x, y) >>> 24) >= 250) solid++;
            }
        }
        return total == 0 ? 0f : solid / (float) total;
    }

    // ---------------------------------------------------------------- helpers

    static Bitmap decode(String path, int max) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int s = 1;
            while (o.outWidth / (s * 2) >= max || o.outHeight / (s * 2) >= max) s *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(path, o);
        } catch (Throwable t) {
            return null;
        }
    }

    static Bitmap drawableToBitmap(Drawable d, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, size, size);
        d.draw(c);
        return b;
    }

    static Bitmap iconBitmap(Context ctx, String pkg) {
        try {
            return drawableToBitmap(ctx.getPackageManager().getApplicationIcon(pkg), 256);
        } catch (Exception e) {
            return null;
        }
    }

    /** Bounding box of all pixels that are not (almost) fully transparent. */
    private static Rect opaqueBounds(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int[] row = new int[w];
        int left = w, right = -1, top = -1, bottom = -1;
        for (int y = 0; y < h; y++) {
            b.getPixels(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                if ((row[x] >>> 24) > 24) {
                    if (x < left) left = x;
                    if (x > right) right = x;
                    if (top < 0) top = y;
                    bottom = y;
                }
            }
        }
        if (right < 0) return null;
        return new Rect(left, top, right + 1, bottom + 1);
    }

    /** Average colour (r,g,b 0-255) and luminance (0-1) of the visible pixels. */
    private static float[] analyze(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int sx = Math.max(1, w / 40), sy = Math.max(1, h / 40);
        double r = 0, g = 0, bl = 0, ws = 0;
        for (int y = 0; y < h; y += sy) {
            for (int x = 0; x < w; x += sx) {
                int p = b.getPixel(x, y);
                int a = p >>> 24;
                if (a < 96) continue;
                double wt = a / 255.0;
                r += ((p >> 16) & 255) * wt;
                g += ((p >> 8) & 255) * wt;
                bl += (p & 255) * wt;
                ws += wt;
            }
        }
        if (ws <= 0) return new float[]{128f, 128f, 128f, 0.5f};
        r /= ws;
        g /= ws;
        bl /= ws;
        return new float[]{(float) r, (float) g, (float) bl, (float) ((0.299 * r + 0.587 * g + 0.114 * bl) / 255.0)};
    }

    /** Halves repeatedly before the final resize so big logos downscale without jagged edges. */
    private static Bitmap scaleSmooth(Bitmap b, int w, int h) {
        while (b.getWidth() / 2 >= w && b.getHeight() / 2 >= h) {
            b = Bitmap.createScaledBitmap(b, b.getWidth() / 2, b.getHeight() / 2, true);
        }
        return Bitmap.createScaledBitmap(b, w, h, true);
    }

    private static void save(Bitmap b, File f) throws IOException {
        FileOutputStream o = new FileOutputStream(f);
        try {
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
        } finally {
            o.close();
        }
    }

    // ---------------------------------------------------------------- blur (used for tiles and for the menu backdrop)

    static void boxBlur(int[] px, int w, int h, int r, int passes) {
        int[] tmp = new int[px.length];
        for (int p = 0; p < passes; p++) {
            blurH(px, tmp, w, h, r);
            blurV(tmp, px, w, h, r);
        }
    }

    private static void blurH(int[] s, int[] d, int w, int h, int r) {
        int div = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            int o = y * w, ra = 0, ga = 0, ba = 0;
            for (int i = -r; i <= r; i++) {
                int p = s[o + Math.min(Math.max(i, 0), w - 1)];
                ra += (p >> 16) & 255;
                ga += (p >> 8) & 255;
                ba += p & 255;
            }
            for (int x = 0; x < w; x++) {
                d[o + x] = 0xFF000000 | ((ra / div) << 16) | ((ga / div) << 8) | (ba / div);
                int add = s[o + Math.min(x + r + 1, w - 1)];
                int sub = s[o + Math.max(x - r, 0)];
                ra += ((add >> 16) & 255) - ((sub >> 16) & 255);
                ga += ((add >> 8) & 255) - ((sub >> 8) & 255);
                ba += (add & 255) - (sub & 255);
            }
        }
    }

    private static void blurV(int[] s, int[] d, int w, int h, int r) {
        int div = 2 * r + 1;
        for (int x = 0; x < w; x++) {
            int ra = 0, ga = 0, ba = 0;
            for (int i = -r; i <= r; i++) {
                int p = s[x + Math.min(Math.max(i, 0), h - 1) * w];
                ra += (p >> 16) & 255;
                ga += (p >> 8) & 255;
                ba += p & 255;
            }
            for (int y = 0; y < h; y++) {
                d[x + y * w] = 0xFF000000 | ((ra / div) << 16) | ((ga / div) << 8) | (ba / div);
                int add = s[x + Math.min(y + r + 1, h - 1) * w];
                int sub = s[x + Math.max(y - r, 0) * w];
                ra += ((add >> 16) & 255) - ((sub >> 16) & 255);
                ga += ((add >> 8) & 255) - ((sub >> 8) & 255);
                ba += (add & 255) - (sub & 255);
            }
        }
    }
}
