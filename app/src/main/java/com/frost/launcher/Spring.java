package com.frost.launcher;

import android.animation.TimeInterpolator;

/**
 * Damped-spring step response used as an interpolator. This is what gives the soft
 * "settle" of Apple's motion: fast start, gentle landing, a tiny overshoot on pops.
 * Pure maths, no allocations, so it costs nothing on weak GPUs/CPUs.
 */
final class Spring implements TimeInterpolator {
    /** Focus pop: slight overshoot (~3%). */
    static final Spring POP = new Spring(0.72f, 19f, 0.62f);
    /** Scrolling / moving things: critically damped, no visible overshoot. */
    static final Spring EASE = new Spring(0.94f, 13f, 0.70f);
    /** Un-focusing. */
    static final Spring OUT = new Spring(0.92f, 16f, 0.50f);

    /** Global animation-speed multiplier from the settings (1 = normal, small = nearly instant). */
    static float speed = 1f;

    private final double z, w, wd, t;

    Spring(float zeta, float omega, float seconds) {
        z = zeta;
        w = omega;
        wd = omega * Math.sqrt(1.0 - zeta * zeta);
        t = seconds;
    }

    int ms() {
        return Math.max(1, Math.round((float) (t * 1000) * speed));
    }

    /** Scales a fixed duration by the global animation speed. */
    static int dur(int ms) {
        return Math.max(1, Math.round(ms * speed));
    }

    @Override
    public float getInterpolation(float x) {
        if (x <= 0f) return 0f;
        if (x >= 1f) return 1f;
        double s = x * t;
        double e = Math.exp(-z * w * s);
        return (float) (1.0 - e * (Math.cos(wd * s) + (z * w / wd) * Math.sin(wd * s)));
    }
}
