package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Rumah TV: when a guarded app (YouTube by default) comes to the front and nobody has unlocked it,
 * puts the quiz in front of it. Only window changes wake it, plus one timer for when an unlock runs
 * out while the app is still open. Enabled over adb by the install script (GATE_SERVICE=1).
 */
public class GateService extends AccessibilityService {
    private static final String TAG = "RumahTV.Gate";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String front = "";
    private long lastOpen;
    private final Runnable expiry = () -> check(front, true);

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence p = event.getPackageName();
        if (p == null) return;
        String pkg = p.toString();
        // Overlays that sit over the app (system bars, the keyboard, our own azan band) are not a
        // change of app.
        if (pkg.equals("com.android.systemui") || isKeyboard(pkg)) return;
        front = pkg;
        check(pkg, false);
    }

    private void check(String pkg, boolean fromTimer) {
        handler.removeCallbacks(expiry);
        if (pkg == null || pkg.equals(getPackageName())) return;
        RumahConfig rc = RumahConfig.load(this);
        if (!rc.guards(pkg)) return;
        long left = Gate.remainingMs(this);
        if (left > 0) {
            handler.postDelayed(expiry, left + 1_000L);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastOpen < 1_500L) return;
        lastOpen = now;
        Log.i(TAG, "guarded " + pkg + (fromTimer ? " (time is up)" : ""));
        Gate.openQuiz(this, pkg, fromTimer);
    }

    private boolean isKeyboard(String pkg) {
        String ime = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return ime != null && ime.startsWith(pkg + "/");
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(expiry);
        super.onDestroy();
    }
}
