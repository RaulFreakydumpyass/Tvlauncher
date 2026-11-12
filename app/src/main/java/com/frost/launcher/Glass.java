package com.frost.launcher;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

/**
 * "Frosted glass" without real-time blur: the wallpaper was blurred ONCE into a tiny bitmap; here we just
 * paint with that bitmap, lined up with whatever part of the screen the view covers. Costs almost nothing.
 * Every view owns its own Glass (shaders must not be shared between views).
 */
final class Glass {
    private Bitmap bmp;
    private BitmapShader shader;
    private final Matrix m = new Matrix();
    private final int[] loc = new int[2];

    void set(Bitmap b) {
        bmp = b;
        shader = b == null ? null : new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    }

    boolean ready() {
        return shader != null;
    }

    /** Makes {@code p} paint the blurred wallpaper exactly as it lies behind view {@code v}. */
    void apply(View v, Paint p) {
        v.getLocationInWindow(loc);
        m.setScale(Ui.W / (float) bmp.getWidth(), Ui.H / (float) bmp.getHeight());
        m.postTranslate(-loc[0], -loc[1]);
        shader.setLocalMatrix(m);
        p.setShader(shader);
    }
}
