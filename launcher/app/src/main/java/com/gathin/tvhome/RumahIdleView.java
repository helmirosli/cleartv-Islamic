package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Rumah TV's screensaver face, over IslamicDream's mosque photos: a large clock with the Malay and
 * Hijri dates at the top left; at the bottom the next azan with the day's five prayers, and the
 * reminder verse beside it. Drawn in one pass; the screensaver redraws it once a minute.
 */
public class RumahIdleView extends View {
    private static final int GOLD = 0xFFF2B544, WHITE = 0xFFFFFFFF, SOFT = 0xE6E3E9EE, MUTED = 0xB3E3E9EE;
    private static final int CARD = 0x66101A28, RIM = 0x33FFFFFF;

    private final String zone;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Typeface face = Typeface.DEFAULT;
    private final List<String[]> verses = new ArrayList<>();
    private StaticLayout verse;
    private String verseRef = "";
    private int verseWidth = -1, verseIndex;

    public RumahIdleView(Context c, String zone) {
        super(c);
        this.zone = zone;
        try {
            face = c.getResources().getFont(R.font.outfit);
        } catch (RuntimeException ignored) {
            // Keep the system font.
        }
        loadVerses();
        verseIndex = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
    }

    private final android.content.BroadcastReceiver tick = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context c, android.content.Intent i) {
            invalidate();
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        android.content.IntentFilter f = new android.content.IntentFilter(android.content.Intent.ACTION_TIME_TICK);
        f.addAction(android.content.Intent.ACTION_TIME_CHANGED);
        if (android.os.Build.VERSION.SDK_INT >= 33) getContext().registerReceiver(tick, f, Context.RECEIVER_NOT_EXPORTED);
        else getContext().registerReceiver(tick, f);
    }

    @Override
    protected void onDetachedFromWindow() {
        try {
            getContext().unregisterReceiver(tick);
        } catch (IllegalArgumentException ignored) {
            // Never registered.
        }
        super.onDetachedFromWindow();
    }

    /** The next verse that fits, every few minutes. */
    void nextVerse() {
        verseIndex++;
        verseWidth = -1;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        float side = dp(56);
        Calendar now = Calendar.getInstance();
        boolean is24 = android.text.format.DateFormat.is24HourFormat(getContext());
        int hour = now.get(Calendar.HOUR_OF_DAY), min = now.get(Calendar.MINUTE);
        String time = is24 ? String.format(Locale.ROOT, "%02d:%02d", hour, min)
                : String.format(Locale.ROOT, "%d:%02d", hour % 12 == 0 ? 12 : hour % 12, min);

        // The clock and the dates, top left.
        paint.setShadowLayer(dp(14), 0, dp(2), 0x73000000);
        float x = draw(c, time, side - dp(4), dp(146), 124, WHITE, 300, -0.04f);
        if (!is24) draw(c, RumahHeaderView.period(hour), x + dp(14), dp(146), 26, SOFT, 500, 0f);
        String date = RumahHeaderView.DAYS[now.get(Calendar.DAY_OF_WEEK) - 1] + ", " + now.get(Calendar.DAY_OF_MONTH)
                + " " + RumahHeaderView.MONTHS[now.get(Calendar.MONTH)] + " " + now.get(Calendar.YEAR);
        draw(c, date, side, dp(184), 21, WHITE, 600, 0f);
        PrayerTimes.Day today = PrayerTimes.day(getContext(), zone, PrayerTimes.key(now));
        if (today != null && !today.hijri.isEmpty()) draw(c, today.hijri + "H", side, dp(210), 17, SOFT, 400, 0f);

        // The footer.
        draw(c, "Tekan mana-mana butang untuk kembali", side, h - dp(26), 13, MUTED, 400, 0f);
        paint.clearShadowLayer();

        // The cards along the bottom.
        float bottom = h - dp(52), top = bottom - dp(132), right = w - side;
        float remW = Math.min(dp(320), (right - side) * 0.38f);
        float prayRight = right - remW - dp(16);
        if (today != null) prayerCard(c, today, now, is24, side, top, prayRight, bottom);
        reminderCard(c, right - remW, top, right, bottom);
    }

    private void prayerCard(Canvas c, PrayerTimes.Day today, Calendar now, boolean is24,
                            float l, float t, float r, float b) {
        card(c, l, t, r, b);
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int n = PrayerTimes.next(today.minutes, nowMin);
        int shown = n >= 0 ? n : 0;
        int left;
        if (n >= 0) {
            left = today.minutes[n] - nowMin;
        } else {
            Calendar tm = (Calendar) now.clone();
            tm.add(Calendar.DAY_OF_MONTH, 1);
            PrayerTimes.Day tomorrow = PrayerTimes.day(getContext(), zone, PrayerTimes.key(tm));
            left = 24 * 60 - nowMin + (tomorrow != null ? tomorrow : today).minutes[0];
        }
        float pad = dp(22), base = t + dp(38);
        int at = today.minutes[shown];
        String when = (is24 ? PrayerTimes.format(at, true) : PrayerTimes.format(at, false) + " "
                + RumahHeaderView.period(at / 60)) + (n < 0 ? " esok" : "");
        String count = PrayerTimes.countdown(left);
        float countW = measure(count, 15, 500);
        float rest = measure(PrayerTimes.NAMES[shown], 28, 700) + dp(12) + measure(when, 18, 600);
        style(11, 700, 0.12f);
        float label = paint.measureText("AZAN SETERUSNYA") + dp(14);
        float x = l + pad;
        if (x + label + rest + dp(16) < r - pad - countW) {
            x = draw(c, "AZAN SETERUSNYA", x, base - dp(3), 11, SOFT, 700, 0.12f) + dp(14);
        }
        x = draw(c, PrayerTimes.NAMES[shown], x, base + dp(1), 28, GOLD, 700, 0f) + dp(12);
        draw(c, when, x, base, 18, WHITE, 600, 0f);
        draw(c, count, r - pad - countW, base, 15, SOFT, 500, 0f);

        // The five prayers as chips, the next one in gold.
        float gap = dp(8), cl = l + pad, cr = r - pad, ct = t + dp(56), cb = b - dp(16);
        float cw = (cr - cl - gap * 4) / 5f;
        for (int i = 0; i < 5 && i < today.minutes.length; i++) {
            float x0 = cl + i * (cw + gap);
            rect.set(x0, ct, x0 + cw, cb);
            boolean on = i == shown;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(on ? 0x38F2B544 : 0x0FFFFFFF);
            c.drawRoundRect(rect, dp(13), dp(13), paint);
            if (on) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1));
                paint.setColor(0x8CF2B544);
                c.drawRoundRect(rect, dp(13), dp(13), paint);
                paint.setStyle(Paint.Style.FILL);
            }
            draw(c, PrayerTimes.NAMES[i], x0 + dp(12), ct + dp(21), 13, on ? GOLD : SOFT, on ? 700 : 600, 0f);
            String tm = PrayerTimes.format(today.minutes[i], is24);
            float tx = draw(c, tm, x0 + dp(12), ct + dp(44), 18, WHITE, 600, 0f);
            if (!is24) draw(c, " " + shortPeriod(today.minutes[i] / 60), tx, ct + dp(44), 12, MUTED, 500, 0f);
        }
    }

    private void reminderCard(Canvas c, float l, float t, float r, float b) {
        if (verses.isEmpty()) return;
        card(c, l, t, r, b);
        float pad = dp(22);
        int width = (int) (r - l - 2 * pad);
        float room = (b - t) - dp(44) - dp(14);
        if (width != verseWidth) pickVerse(width, room);
        draw(c, "PERINGATAN", l + pad, t + dp(30), 11, SOFT, 700, 0.12f);
        if (verse == null) return;
        draw(c, verseRef, r - pad - measure(verseRef, 12, 400), t + dp(30), 12, SOFT, 400, 0f);
        c.save();
        c.translate(l + pad, t + dp(44));
        verse.draw(c);
        c.restore();
    }

    /** From the current verse onwards, the first that fits whole, at 16sp or down to 13sp. */
    private void pickVerse(int width, float room) {
        verseWidth = width;
        verse = null;
        for (int k = 0; k < verses.size() && verse == null; k++) {
            String[] v = verses.get(Math.floorMod(verseIndex + k, verses.size()));
            String text = "“" + v[0] + "”";
            for (int sp = 16; sp >= 13; sp--) {
                TextPaint body = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
                body.setTypeface(face);
                body.setTextSize(sp(sp));
                body.setFontVariationSettings("'wght' 500");
                body.setColor(WHITE);
                StaticLayout l = StaticLayout.Builder.obtain(text, 0, text.length(), body, Math.max(1, width))
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(dp(2), 1f)
                        .setIncludePad(false)
                        .build();
                if (l.getHeight() <= room) {
                    verse = l;
                    verseRef = v[1];
                    verseIndex = Math.floorMod(verseIndex + k, verses.size());
                    break;
                }
            }
        }
    }

    private void card(Canvas c, float l, float t, float r, float b) {
        rect.set(l, t, r, b);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(CARD);
        c.drawRoundRect(rect, dp(22), dp(22), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(RIM);
        c.drawRoundRect(rect, dp(22), dp(22), paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private static String shortPeriod(int hour) {
        if (hour < 12) return "pg";
        if (hour < 14) return "tgh";
        if (hour < 19) return "ptg";
        return "mlm";
    }

    private void loadVerses() {
        try (InputStream in = getResources().openRawResource(R.raw.ayat)) {
            byte[] buf = new byte[64 * 1024];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            JSONArray a = new JSONObject(new String(buf, 0, n, StandardCharsets.UTF_8)).getJSONArray("ayat");
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                verses.add(new String[]{o.getString("tr").trim(), o.getString("ref")});
            }
        } catch (Exception e) {
            Log.w("RumahTV.Idle", "no verses: " + e.getMessage());
        }
    }

    private void style(int sp, int weight, float spacing) {
        paint.setTypeface(face);
        paint.setTextSize(sp(sp));
        paint.setFontVariationSettings("'wght' " + weight);
        paint.setLetterSpacing(spacing);
    }

    private float measure(String s, int sp, int weight) {
        style(sp, weight, 0f);
        return paint.measureText(s);
    }

    private float draw(Canvas c, String s, float x, float y, int sp, int color, int weight, float spacing) {
        style(sp, weight, spacing);
        paint.setColor(color);
        c.drawText(s, x, y, paint);
        return x + paint.measureText(s);
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }
}
