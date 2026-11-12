package com.frost.launcher;

import android.graphics.Path;

/**
 * Apple-style rounded rectangle. Apple does not use plain circular arcs for corners: it uses a
 * "continuous" curve that starts further away from the corner and eases in, which is what makes
 * iOS / tvOS shapes look smooth. This builds that curve with three cubic Beziers per corner.
 * {@code rad} is the usual radius; the curve itself reaches about 1.53x that far along each edge.
 */
final class Shape {
    private static final float A = 1.52866483f;

    private Shape() { }

    static void rect(Path p, float l, float t, float r, float b, float rad) {
        float k = Math.min(rad, Math.min(r - l, b - t) / 2f / A);   // never larger than the shape allows
        p.reset();
        if (k < 0.5f) {
            p.addRect(l, t, r, b, Path.Direction.CW);
            return;
        }
        p.moveTo(l + A * k, t);
        p.lineTo(r - A * k, t);
        corner(p, r, t, -1, 1, k, false);          // top-right
        p.lineTo(r, b - A * k);
        corner(p, r, b, -1, -1, k, true);          // bottom-right
        p.lineTo(l + A * k, b);
        corner(p, l, b, 1, -1, k, false);          // bottom-left
        p.lineTo(l, t + A * k);
        corner(p, l, t, 1, 1, k, true);            // top-left
        p.close();
    }

    /**
     * One corner. (u, v) are distances from the corner along the horizontal / vertical edge, in units
     * of k; {@code swap} walks the same curve the other way round (arriving along the vertical edge).
     */
    private static void corner(Path p, float cx, float cy, int sx, int sy, float k, boolean swap) {
        cubic(p, cx, cy, sx, sy, k, swap, 1.08849296f, 0f, 0.86840694f, 0f, 0.63149379f, 0.07491139f);
        cubic(p, cx, cy, sx, sy, k, swap, 0.37282383f, 0.16906013f, 0.16906013f, 0.37282383f, 0.07491139f, 0.63149379f);
        cubic(p, cx, cy, sx, sy, k, swap, 0f, 0.86840694f, 0f, 1.08849296f, 0f, A);
    }

    private static void cubic(Path p, float cx, float cy, int sx, int sy, float k, boolean swap,
                              float u1, float v1, float u2, float v2, float u3, float v3) {
        if (swap) {
            p.cubicTo(cx + sx * v1 * k, cy + sy * u1 * k, cx + sx * v2 * k, cy + sy * u2 * k,
                    cx + sx * v3 * k, cy + sy * u3 * k);
        } else {
            p.cubicTo(cx + sx * u1 * k, cy + sy * v1 * k, cx + sx * u2 * k, cy + sy * v2 * k,
                    cx + sx * u3 * k, cy + sy * v3 * k);
        }
    }
}
