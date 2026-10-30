package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import java.util.Calendar;

/**
 * Rumah TV: wakes the TV from sleep for the azan (Subuh by default). The alarm fires with the screen
 * off; this turns the screen on and brings Home to the front, and Home gives the azan full screen at
 * its next check (Azan.takeDue keeps it to once). The TV itself must still be on: on a TV that does
 * not answer HDMI-CEC the stick cannot switch it on, only its own screen.
 */
final class RumahWake {
    private static final String TAG = "RumahTV.Wake";
    /** Same window as Azan: an alarm a little late still counts. */
    private static final int WINDOW_MIN = 3;
    private static final long HOLD_MS = 30_000L;

    private RumahWake() {
    }

    /** The prayer whose time is now (within the window), without marking it given; -1 if none. */
    static int dueIndex(Context ctx, String zone) {
        if (zone == null || zone.isEmpty()) return -1;
        Calendar now = Calendar.getInstance();
        PrayerTimes.Day today = PrayerTimes.day(ctx, zone, PrayerTimes.key(now));
        if (today == null) return -1;
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        for (int i = 0; i < today.minutes.length; i++) {
            if (nowMin >= today.minutes[i] && nowMin < today.minutes[i] + WINDOW_MIN) return i;
        }
        return -1;
    }

    /** Called by the azan alarm while the screen is off. Returns true when it woke the TV. */
    @SuppressWarnings("deprecation") // SCREEN_BRIGHT_WAKE_LOCK: the only way an app can turn the screen on
    static boolean maybeWake(Context ctx, PowerManager pm, String zone) {
        RumahConfig rc = RumahConfig.load(ctx);
        int due = dueIndex(ctx, zone);
        if (due < 0 || !rc.wakesFor(due)) {
            Log.i(TAG, "screen off, no wake (due=" + due + ", wake_for_azan=" + rc.wakeForAzan + ")");
            return false;
        }
        try {
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                    | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "RumahTV:azan");
            wl.acquire(HOLD_MS);
        } catch (RuntimeException e) {
            Log.w(TAG, "wake lock failed: " + e);
            return false;
        }
        try {
            Intent home = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setPackage(ctx.getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            ctx.startActivity(home);
        } catch (RuntimeException e) {
            Log.w(TAG, "could not bring Home to front: " + e);
        }
        Log.i(TAG, "woke the TV for " + PrayerTimes.NAMES[due]);
        return true;
    }
}
