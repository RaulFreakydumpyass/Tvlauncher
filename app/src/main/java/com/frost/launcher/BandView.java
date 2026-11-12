package com.frost.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** The frosted "taskbar" strip that sits behind the selected row of apps. */
final class BandView extends View {
    private final Glass glass = new Glass();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();

    BandView(Context c) {
        super(c);
        line.setStyle(Paint.Style.STROKE);
    }

    void setGlass(Bitmap b) {
        glass.set(b);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float r = Math.min(h / 2f, Ui.px(44));
        rf.set(0, 0, w, h);
        if (glass.ready()) {
            glass.apply(this, fill);
        } else {
            fill.setShader(null);
            fill.setColor(0x33FFFFFF);
        }
        c.drawRoundRect(rf, r, r, fill);
        fill.setShader(null);
        fill.setColor(0x33000000);                 // slight darkening keeps white text readable on bright wallpapers
        c.drawRoundRect(rf, r, r, fill);
        fill.setColor(0x1CFFFFFF);                 // frost tint
        c.drawRoundRect(rf, r, r, fill);
        float bw = Math.max(1.5f, Ui.px(2));
        line.setStrokeWidth(bw);
        line.setColor(0x52FFFFFF);
        rf.inset(bw / 2f, bw / 2f);
        c.drawRoundRect(rf, r - bw / 2f, r - bw / 2f, line);
    }
}
