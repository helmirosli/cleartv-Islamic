package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Rumah TV: the weather for the next-azan card, from Open-Meteo (free, no key). Fetched at most every
 * 30 minutes while Home is on screen, kept in preferences, shown as "28° Mendung · hujan 70% 4 ptg".
 * Theme keys weather_lat / weather_lon; "weather": false turns it off.
 */
final class Weather {
    private static final String TAG = "RumahTV.Weather";
    private static final String PREFS = "rumah_weather";
    private static final long EVERY_MS = 30 * 60_000L;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static volatile boolean running;

    private Weather() {
    }

    /** The last weather line, or "" when there is none or it is over three hours old. */
    static String line(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (System.currentTimeMillis() - p.getLong("at", 0) > 3 * 60 * 60_000L) return "";
        return p.getString("line", "");
    }

    /** Fetches in the background when due; {@code done} runs on the calling thread's looper after. */
    static void refreshIfDue(Context ctx, Runnable done) {
        RumahConfig rc = RumahConfig.load(ctx);
        if (!rc.weather) return;
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (running || System.currentTimeMillis() - p.getLong("tried", 0) < EVERY_MS) return;
        running = true;
        p.edit().putLong("tried", System.currentTimeMillis()).apply();
        final Context app = ctx.getApplicationContext();
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        IO.execute(() -> {
            try {
                String line = fetch(rc.weatherLat, rc.weatherLon);
                if (line != null) {
                    app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putString("line", line).putLong("at", System.currentTimeMillis()).apply();
                    main.post(done);
                }
            } finally {
                running = false;
            }
        });
    }

    private static String fetch(double lat, double lon) {
        HttpURLConnection con = null;
        try {
            URL url = new URL(String.format(Locale.ROOT,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                            + "&current=temperature_2m,weather_code&hourly=precipitation_probability"
                            + "&forecast_hours=8&timezone=Asia%%2FKuala_Lumpur", lat, lon));
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(10_000);
            con.setReadTimeout(10_000);
            if (con.getResponseCode() != 200) return null;
            byte[] buf = new byte[64 * 1024];
            int n = 0, r;
            try (InputStream in = con.getInputStream()) {
                while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            }
            JSONObject o = new JSONObject(new String(buf, 0, n, StandardCharsets.UTF_8));
            JSONObject cur = o.getJSONObject("current");
            long temp = Math.round(cur.getDouble("temperature_2m"));
            StringBuilder s = new StringBuilder().append(temp).append("° ").append(describe(cur.getInt("weather_code")));
            JSONObject hourly = o.optJSONObject("hourly");
            if (hourly != null) {
                JSONArray prob = hourly.optJSONArray("precipitation_probability");
                JSONArray time = hourly.optJSONArray("time");
                if (prob != null && time != null) {
                    int best = -1, bestP = 0;
                    for (int i = 1; i < prob.length() && i < time.length(); i++) {
                        int pr = prob.optInt(i, 0);
                        if (pr > bestP) { bestP = pr; best = i; }
                    }
                    if (best >= 0 && bestP >= 50) {
                        String t = time.optString(best, ""); // 2026-10-30T16:00
                        int hour = t.length() >= 13 ? Integer.parseInt(t.substring(11, 13)) : -1;
                        s.append(" · hujan ").append(bestP).append('%');
                        if (hour >= 0) s.append(' ').append(hourLabel(hour));
                    }
                }
            }
            return s.toString();
        } catch (Exception e) {
            Log.w(TAG, "weather failed: " + e);
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static String hourLabel(int h) {
        int h12 = h % 12 == 0 ? 12 : h % 12;
        String part = h < 12 ? "pg" : h < 19 ? "ptg" : "mlm";
        return h12 + " " + part;
    }

    /** WMO weather codes, as Open-Meteo returns them, in Malay. */
    static String describe(int code) {
        if (code == 0) return "Cerah";
        if (code <= 2) return "Cerah berawan";
        if (code == 3) return "Mendung";
        if (code == 45 || code == 48) return "Berkabus";
        if (code >= 51 && code <= 57) return "Gerimis";
        if (code >= 61 && code <= 67) return code >= 65 ? "Hujan lebat" : "Hujan";
        if (code >= 80 && code <= 82) return code == 82 ? "Hujan lebat" : "Hujan";
        if (code >= 95) return "Ribut petir";
        return "Berawan";
    }
}
