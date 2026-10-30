package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Rumah TV: who unlocked the guarded apps (YouTube) and until when. GateService checks it whenever
 * an app comes to the front; QuizActivity sets it after a quiz or the parents' PIN.
 */
final class Gate {
    private static final String TAG = "RumahTV.Gate";
    private static final String PREFS = "rumah_gate";
    /** A child picked on the "who is watching" screen is remembered this long. */
    static final long CHILD_REMEMBER_MS = 10 * 60_000L;
    /** How long the parents' PIN opens the guarded apps. */
    static final long PIN_UNLOCK_MS = 2 * 60 * 60_000L;

    private Gate() {
    }

    static long remainingMs(Context ctx) {
        long until = prefs(ctx).getLong("until", 0);
        return Math.max(0, until - System.currentTimeMillis());
    }

    static void unlock(Context ctx, String child, long ms, String how) {
        long until = System.currentTimeMillis() + ms;
        prefs(ctx).edit().putLong("until", until).putString("by", child == null ? "" : child)
                .putString("how", how).apply();
        Log.i(TAG, "unlocked for " + (ms / 60_000) + " min by " + child + " (" + how + ")");
    }

    static void lock(Context ctx) {
        prefs(ctx).edit().putLong("until", 0).apply();
    }

    static void rememberChild(Context ctx, String child) {
        prefs(ctx).edit().putString("child", child == null ? "" : child)
                .putLong("childAt", System.currentTimeMillis()).apply();
    }

    /** The child picked within the last few minutes, or null. */
    static String recentChild(Context ctx) {
        SharedPreferences p = prefs(ctx);
        long at = p.getLong("childAt", 0);
        String c = p.getString("child", "");
        if (c.isEmpty() || System.currentTimeMillis() - at > CHILD_REMEMBER_MS) return null;
        return c;
    }

    static void openQuiz(Context ctx, String pkg, boolean expired) {
        try {
            Intent i = new Intent(ctx, QuizActivity.class)
                    .putExtra(QuizActivity.EXTRA_PACKAGE, pkg)
                    .putExtra(QuizActivity.EXTRA_EXPIRED, expired)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            ctx.startActivity(i);
        } catch (RuntimeException e) {
            Log.w(TAG, "could not open the quiz: " + e);
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
