package com.frost.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Optional helper. Android only lets the system (or an accessibility service) open the power menu, so when you
 * enable "Frost power menu" in Accessibility settings, the launcher's Power button can show it. This service
 * does nothing else: it listens to no events and reads nothing from the screen.
 */
public class PowerService extends AccessibilityService {
    static volatile PowerService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // intentionally empty
    }

    @Override
    public void onInterrupt() {
        // intentionally empty
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }
}
