package com.gathin.tvhome;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rumah TV's own settings, read from the same theme config.json as the upstream keys (Config) so a
 * theme push changes them too. Kept apart from Config so the upstream file stays easy to merge.
 *
 * <pre>
 * "wake_for_azan": "subuh" | "all" | "off"          wake the TV from sleep for the azan (default subuh)
 * "quiz": true | false                              the quiz before guarded apps (default true)
 * "quiz_apps": ["com.google.android.youtube.tv"]    apps that need the quiz first
 * "quiz_unlock_minutes": 30                         how long one quiz opens them
 * "quiz_questions": 3                               questions per quiz
 * "parent_pin": "1234"                              4-digit PIN that opens them without a quiz
 * "children": [{"name": "Adik", "level": 1, "color": "#E58FD0"}, ...]
 * </pre>
 */
final class RumahConfig {
    private static final String TAG = "RumahTV.Config";

    static final class Child {
        final String name;
        final int level;
        final int color;

        Child(String name, int level, int color) {
            this.name = name;
            this.level = level;
            this.color = color;
        }
    }

    final String wakeForAzan;
    final boolean quiz;
    final List<String> quizApps;
    final int quizUnlockMinutes;
    final int quizQuestions;
    final String parentPin;
    final List<Child> children;

    private RumahConfig(String wakeForAzan, boolean quiz, List<String> quizApps, int quizUnlockMinutes,
                        int quizQuestions, String parentPin, List<Child> children) {
        this.wakeForAzan = wakeForAzan;
        this.quiz = quiz;
        this.quizApps = quizApps;
        this.quizUnlockMinutes = quizUnlockMinutes;
        this.quizQuestions = quizQuestions;
        this.parentPin = parentPin;
        this.children = children;
    }

    /** True when the azan for prayer {@code index} (PrayerTimes.NAMES order) may wake the TV. */
    boolean wakesFor(int index) {
        if ("all".equals(wakeForAzan)) return true;
        return "subuh".equals(wakeForAzan) && index == 0;
    }

    boolean guards(String pkg) {
        return quiz && pkg != null && quizApps.contains(pkg);
    }

    static RumahConfig load(Context ctx) {
        JSONObject o = read(ctx);
        String wake = o.optString("wake_for_azan", "subuh").trim().toLowerCase(java.util.Locale.ROOT);
        if (!wake.equals("all") && !wake.equals("off")) wake = "subuh";

        List<String> apps = new ArrayList<>();
        JSONArray a = o.optJSONArray("quiz_apps");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                String p = a.optString(i, "").trim();
                if (!p.isEmpty()) apps.add(p);
            }
        } else {
            apps.add("com.google.android.youtube.tv");
        }

        String pin = o.optString("parent_pin", "").trim();
        if (!pin.matches("\\d{4}")) pin = "";

        List<Child> kids = new ArrayList<>();
        JSONArray c = o.optJSONArray("children");
        if (c != null) {
            for (int i = 0; i < c.length(); i++) {
                JSONObject k = c.optJSONObject(i);
                if (k == null) continue;
                String name = k.optString("name", "").trim();
                if (name.isEmpty()) continue;
                int level = Math.max(1, Math.min(3, k.optInt("level", 1)));
                int color = parseColor(k.optString("color", ""), DEFAULT_COLORS[kids.size() % DEFAULT_COLORS.length]);
                kids.add(new Child(name, level, color));
            }
        }

        return new RumahConfig(wake, o.optBoolean("quiz", true), Collections.unmodifiableList(apps),
                clamp(o.optInt("quiz_unlock_minutes", 30), 5, 240),
                clamp(o.optInt("quiz_questions", 3), 1, 10),
                pin, Collections.unmodifiableList(kids));
    }

    private static final int[] DEFAULT_COLORS = {0xFF7FB3F5, 0xFF8DD27A, 0xFFE58FD0, 0xFFF2A645, 0xFF45BDC5};

    private static int parseColor(String s, int fallback) {
        try {
            return s.isEmpty() ? fallback : android.graphics.Color.parseColor(s);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static JSONObject read(Context ctx) {
        File dir = Config.dir(ctx);
        File f = dir != null ? new File(dir, Config.FILE_NAME) : null;
        if (f == null || !f.isFile()) return new JSONObject();
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) Math.min(f.length(), 256 * 1024)];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            return new JSONObject(new String(buf, 0, n, StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.w(TAG, "config.json unreadable: " + e);
            return new JSONObject();
        }
    }
}
