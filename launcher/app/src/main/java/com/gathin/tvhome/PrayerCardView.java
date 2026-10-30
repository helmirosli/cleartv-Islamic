package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/**
 * Rumah TV: the "next azan" card under the clock. The next prayer and how long until it, the weather
 * on the right, and below a line through the day's five prayers with a marker for now. Drawn in one
 * pass, no child views; Home updates it once a minute.
 */
public class PrayerCardView extends View {
    private static final int GOLD = 0xFFF2B544, INK = 0xFF1A1205, WHITE = 0xFFFFFFFF;
    private static final int SOFT = 0xCCFFFFFF, MUTED = 0x99FFFFFF, FAINT = 0x33FFFFFF;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Typeface regular = Typeface.DEFAULT, bold = Typeface.DEFAULT_BOLD;

    private int[] minutes;
    private int next = -1, nowMin, left;
    private boolean is24;
    private String weather = "", hijri = "";

    public PrayerCardView(Context c) {
        this(c, null);
    }

    public PrayerCardView(Context c, AttributeSet a) {
        super(c, a);
        try {
            Typeface outfit = c.getResources().getFont(R.font.outfit);
            regular = Typeface.create(outfit, Typeface.NORMAL);
            bold = Typeface.create(outfit, Typeface.BOLD);
        } catch (RuntimeException ignored) {
            // Keep the system font.
        }
        setFocusable(false);
        setWillNotDraw(false);
    }

    /** @param next index of the next prayer today, or -1 after Isyak (then it is tomorrow's Subuh). */
    void update(int[] minutes, int next, int nowMin, int minutesLeft, boolean is24, String weather, String hijri) {
        this.minutes = minutes;
        this.next = next;
        this.nowMin = nowMin;
        this.left = minutesLeft;
        this.is24 = is24;
        this.weather = weather == null ? "" : weather;
        this.hijri = hijri == null ? "" : hijri;
        setContentDescription("Azan seterusnya " + PrayerTimes.NAMES[next >= 0 ? next : 0] + " "
                + PrayerTimes.countdown(minutesLeft));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (minutes == null || minutes.length == 0) return;
        float pad = dp(18), w = getWidth(), h = getHeight();
        int shown = next >= 0 ? next : 0;

        // Header: label, prayer name, its time, the countdown pill; the weather on the right.
        float base = dp(30);
        float x = pad;
        x = draw(c, "AZAN SETERUSNYA", x, base, 11, MUTED, bold, 0.12f) + dp(14);
        x = draw(c, PrayerTimes.NAMES[shown], x, base + dp(1), 20, GOLD, bold, 0f) + dp(10);
        x = draw(c, PrayerTimes.format(minutes[shown], is24) + (next < 0 ? " esok" : ""), x, base, 16, WHITE, bold, 0f) + dp(12);
        String count = PrayerTimes.countdown(left);
        paint.setTextSize(sp(13));
        paint.setTypeface(bold);
        paint.setLetterSpacing(0f);
        float cw = paint.measureText(count) + dp(20);
        rect.set(x, base - dp(17), x + cw, base + dp(6));
        paint.setColor(GOLD);
        c.drawRoundRect(rect, dp(12), dp(12), paint);
        paint.setColor(INK);
        c.drawText(count, x + dp(10), base - dp(1), paint);
        // On the right: the Hijri date and the weather, or whichever of them fits.
        paint.setTextSize(sp(15));
        paint.setTypeface(regular);
        paint.setColor(SOFT);
        String both = hijri.isEmpty() ? weather : weather.isEmpty() ? hijri : hijri + "   ·   " + weather;
        for (String right : new String[]{both, weather, hijri}) {
            if (right.isEmpty()) continue;
            float ww = paint.measureText(right);
            if (x + cw + dp(16) + ww < w - pad) {
                c.drawText(right, w - pad - ww, base, paint);
                break;
            }
        }

        // Timeline: five stops at the centres of five equal columns.
        float lineY = dp(52), inner = w - 2 * pad;
        float first = pad + inner / 10f, last = pad + inner * 9f / 10f;
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(3));
        paint.setColor(FAINT);
        c.drawLine(first, lineY, last, lineY, paint);
        float nowX = nowX(first, last);
        paint.setColor(MUTED);
        if (nowX > first) c.drawLine(first, lineY, nowX, lineY, paint);

        for (int i = 0; i < minutes.length && i < 5; i++) {
            float cx = pad + inner * (2 * i + 1) / 10f;
            boolean isNext = i == next;
            boolean past = next >= 0 ? i < next : true;
            paint.setStyle(Paint.Style.FILL);
            if (isNext) {
                paint.setColor(GOLD);
                c.drawCircle(cx, lineY, dp(7), paint);
            } else if (past) {
                paint.setColor(MUTED);
                c.drawCircle(cx, lineY, dp(5), paint);
            } else {
                paint.setColor(0xFF233240);
                c.drawCircle(cx, lineY, dp(5), paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(MUTED);
                c.drawCircle(cx, lineY, dp(5), paint);
                paint.setStyle(Paint.Style.FILL);
            }
            center(c, PrayerTimes.NAMES[i], cx, dp(76), 13, isNext ? GOLD : SOFT, isNext ? bold : regular);
            center(c, PrayerTimes.format(minutes[i], is24), cx, h - dp(8), 12, isNext ? WHITE : MUTED, isNext ? bold : regular);
        }

        // Now: a ring on the line.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF0C1620);
        c.drawCircle(nowX, lineY, dp(6), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2.5f));
        paint.setColor(WHITE);
        c.drawCircle(nowX, lineY, dp(6), paint);
        paint.setStyle(Paint.Style.FILL);
    }

    /** Where now falls on the line: between the last prayer and the next, at the ends otherwise. */
    private float nowX(float first, float last) {
        int n = Math.min(minutes.length, 5);
        if (n < 2) return first;
        float step = (last - first) / (n - 1);
        if (nowMin <= minutes[0]) return first;
        for (int i = 1; i < n; i++) {
            if (nowMin < minutes[i]) {
                float f = (nowMin - minutes[i - 1]) / (float) Math.max(1, minutes[i] - minutes[i - 1]);
                return first + step * (i - 1 + f);
            }
        }
        return last;
    }

    private float draw(Canvas c, String s, float x, float y, int sp, int color, Typeface tf, float spacing) {
        paint.setTextSize(sp(sp));
        paint.setTypeface(tf);
        paint.setColor(color);
        paint.setLetterSpacing(spacing);
        c.drawText(s, x, y, paint);
        float wdt = paint.measureText(s);
        paint.setLetterSpacing(0f);
        return x + wdt;
    }

    private void center(Canvas c, String s, float cx, float y, int sp, int color, Typeface tf) {
        paint.setTextSize(sp(sp));
        paint.setTypeface(tf);
        paint.setColor(color);
        paint.setLetterSpacing(0f);
        c.drawText(s, cx - paint.measureText(s) / 2f, y, paint);
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }
}
