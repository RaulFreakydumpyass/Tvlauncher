package com.frost.launcher;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Tiny vector icons drawn with canvas primitives (no image files, no icon fonts). */
final class Glyphs {
    static final int GEAR = 0, SLIDERS = 1, WIFI = 2, BOLT = 3, USB = 4, GLOBE = 5;

    private static final Path path = new Path();
    private static final RectF rf = new RectF();

    private Glyphs() { }

    static void draw(Canvas c, int type, float cx, float cy, float s, Paint p, int color) {
        p.setShader(null);
        p.setColor(color);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        switch (type) {
            case GEAR: {
                float r = s * 0.24f;
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 8; i++) {
                    c.save();
                    c.rotate(i * 45f, cx, cy);
                    rf.set(cx - s * 0.055f, cy - r - s * 0.13f, cx + s * 0.055f, cy - r + s * 0.03f);
                    c.drawRoundRect(rf, s * 0.025f, s * 0.025f, p);
                    c.restore();
                }
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.12f);
                c.drawCircle(cx, cy, r * 0.92f, p);
                break;
            }
            case SLIDERS: {
                float[] ys = {-0.2f, 0f, 0.2f};
                float[] ks = {-0.12f, 0.14f, -0.02f};
                for (int i = 0; i < 3; i++) {
                    float y = cy + ys[i] * s;
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(s * 0.07f);
                    c.drawLine(cx - s * 0.3f, y, cx + s * 0.3f, y, p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx + ks[i] * s, y, s * 0.09f, p);
                }
                break;
            }
            case WIFI: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.075f);
                float oy = cy + s * 0.22f;
                for (int i = 1; i <= 3; i++) {
                    float r = s * 0.17f * i;
                    rf.set(cx - r, oy - r, cx + r, oy + r);
                    c.drawArc(rf, 225f, 90f, false, p);
                }
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, oy, s * 0.06f, p);
                break;
            }
            case BOLT: {
                p.setStyle(Paint.Style.FILL);
                path.reset();
                path.moveTo(cx + s * 0.08f, cy - s * 0.36f);
                path.lineTo(cx - s * 0.2f, cy + s * 0.04f);
                path.lineTo(cx - s * 0.02f, cy + s * 0.04f);
                path.lineTo(cx - s * 0.08f, cy + s * 0.36f);
                path.lineTo(cx + s * 0.2f, cy - s * 0.06f);
                path.lineTo(cx + s * 0.02f, cy - s * 0.06f);
                path.close();
                c.drawPath(path, p);
                break;
            }
            case USB: {
                p.setStyle(Paint.Style.FILL);
                rf.set(cx - s * 0.17f, cy - s * 0.1f, cx + s * 0.17f, cy + s * 0.36f);
                c.drawRoundRect(rf, s * 0.06f, s * 0.06f, p);
                rf.set(cx - s * 0.11f, cy - s * 0.38f, cx + s * 0.11f, cy - s * 0.04f);
                c.drawRect(rf, p);
                break;
            }
            default: {                                    // GLOBE
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.07f);
                float r = s * 0.34f;
                c.drawCircle(cx, cy, r, p);
                rf.set(cx - r * 0.45f, cy - r, cx + r * 0.45f, cy + r);
                c.drawOval(rf, p);
                c.drawLine(cx - r, cy, cx + r, cy, p);
                break;
            }
        }
        p.setStyle(Paint.Style.FILL);
        p.setStrokeCap(Paint.Cap.BUTT);
    }
}
