package com.frost.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Custom fonts. A picked .ttf/.otf is COPIED into the launcher's private storage (just like icons and
 * the wallpaper), so it keeps working after the USB drive is unplugged.
 */
final class FontTools {
    private static final long MAX_BYTES = 30L * 1024 * 1024;

    private FontTools() { }

    static File file(Context c) {
        return new File(c.getFilesDir(), "font.ttf");
    }

    static boolean has(Context c) {
        return file(c).isFile();
    }

    static boolean isFontName(String name) {
        String l = name.toLowerCase(Locale.ROOT);
        return l.endsWith(".ttf") || l.endsWith(".otf") || l.endsWith(".ttc");
    }

    /** Name shown in the settings (the original file name without its extension). */
    static String label(Context c) {
        String n = c.getSharedPreferences("ui", Context.MODE_PRIVATE).getString("fontName", "Custom");
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        return n.length() > 18 ? n.substring(0, 17) + "\u2026" : n;
    }

    /** Typeface of the saved custom font, or null when there is none / it cannot be read. */
    static Typeface load(Context c) {
        try {
            File f = file(c);
            if (!f.isFile()) return null;
            Typeface t = Typeface.createFromFile(f);
            return t == Typeface.DEFAULT ? null : t;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Copies the chosen font into private storage. Keeps the old font if the new file is not a usable font. */
    static boolean importFont(Context c, String path, String displayName) {
        File src = new File(path);
        File tmp = new File(c.getFilesDir(), "font.tmp");
        try {
            if (!src.isFile() || src.length() <= 0 || src.length() > MAX_BYTES) return false;
            InputStream in = new FileInputStream(src);
            try {
                OutputStream out = new FileOutputStream(tmp);
                try {
                    byte[] buf = new byte[32768];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                } finally {
                    out.close();
                }
            } finally {
                in.close();
            }
            Typeface t = Typeface.createFromFile(tmp);        // throws or returns the default font if it is not a font
            if (t == null || t == Typeface.DEFAULT) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                return false;
            }
            File dst = file(c);
            //noinspection ResultOfMethodCallIgnored
            dst.delete();
            if (!tmp.renameTo(dst)) return false;
            c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putString("fontName", displayName).apply();
            return true;
        } catch (Throwable e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
    }

    static void remove(Context c) {
        //noinspection ResultOfMethodCallIgnored
        file(c).delete();
    }

    /** A small specimen picture for the file browser's preview box. */
    static Bitmap sample(String path, int size) {
        try {
            Typeface t = Typeface.createFromFile(new File(path));
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setTypeface(t);
            p.setColor(0xFFFFFFFF);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(size * 0.30f);
            c.drawText("Aa", size / 2f, size * 0.36f, p);
            p.setTextSize(size * 0.085f);
            c.drawText("Frost Launcher", size / 2f, size * 0.56f, p);
            c.drawText("ABCDEFGHIJKLM", size / 2f, size * 0.68f, p);
            c.drawText("abcdefghijklm", size / 2f, size * 0.78f, p);
            c.drawText("0123456789  12:45", size / 2f, size * 0.90f, p);
            return b;
        } catch (Throwable e) {
            return null;
        }
    }
}
