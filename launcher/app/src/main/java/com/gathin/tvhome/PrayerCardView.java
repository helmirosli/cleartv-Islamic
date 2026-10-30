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
 * Rumah TV: the "next azan" card under the clock. The next prayer and how long until it, and below a
 * line through the day's five prayers with a ring for now. Drawn in one pass, no child views; Home
 * updates it once a minute.
 */
public class PrayerCardView extends View {
    private static final int GOLD = 0xFFF2B544, INK = 0xFF1A1205, WHITE = 0xFFFFFFFF;
    private static final int SOFT = 0xD9FFFFFF, MUTED = 0x99FFFFFF, FAINT = 0x33FFFFFF;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Typeface face = Typeface.DEFAULT;

    private int[] minutes;
    private int next = -1, nowMin, left;
    private boolean is24;

    public PrayerCardView(Context c) {
        this(c, null);
    }

    public PrayerCardView(Context c, AttributeSet a) {
        super(c, a);
        try {
            face = c.getResources().getFont(R.font.outfit);
        } catch (RuntimeException ignored) {
            // Keep the system font.
        }
        setFocusable(false);
        setWillNotDraw(false);
    }

    /** @param next index of the next prayer today, or -1 after Isyak (then it is tomorrow's Subuh). */
    void update(int[] minutes, int next, int nowMin, int minutesLeft, boolean is24) {
        this.minutes = minutes;
        this.next = next;
        this.nowMin = nowMin;
        this.left = minutesLeft;
        this.is24 = is24;
        setContentDescription("Azan seterusnya " + PrayerTimes.NAMES[next >= 0 ? next : 0] + " "
                + PrayerTimes.countdown(minutesLeft));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (minutes == null || minutes.length == 0) return;
        float pad = dp(24), w = getWidth();
        int shown = next >= 0 ? next : 0;

        // Header: label, prayer name, its time; the countdown pill on the right.
        float base = dp(40);
        int at = minutes[shown];
        String when = (is24 ? PrayerTimes.format(at, true) : PrayerTimes.format(at, false) + " "
                + RumahHeaderView.period(at / 60)) + (next < 0 ? " esok" : "");
        String count = PrayerTimes.countdown(left);
        float cw = measure(count, 15, 700) + dp(32);
        // The label goes first when the line is too long for a narrow card.
        float rest = measure(PrayerTimes.NAMES[shown], 30, 700) + dp(12) + measure(when, 19, 600);
        style(12, 700, 0.12f);
        float label = paint.measureText("AZAN SETERUSNYA") + dp(16);
        boolean withLabel = pad + label + rest + dp(16) < w - pad - cw;
        float x = pad;
        if (withLabel) x = draw(c, "AZAN SETERUSNYA", x, base - dp(3), 12, SOFT, 700, 0.12f) + dp(16);
        x = draw(c, PrayerTimes.NAMES[shown], x, base + dp(2), 30, GOLD, 700, 0f) + dp(12);
        draw(c, when, x, base, 19, WHITE, 600, 0f);
        rect.set(w - pad - cw, base - dp(24), w - pad, base + dp(6));
        paint.setColor(GOLD);
        c.drawRoundRect(rect, dp(15), dp(15), paint);
        draw(c, count, rect.left + dp(16), base - dp(4), 15, INK, 700, 0f);

        // Timeline: five stops at the centres of five equal columns, and now as a ring on the line.
        float lineY = dp(70), inner = w - 2 * pad;
        float first = pad + inner / 10f, last = pad + inner * 9f / 10f;
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(4));
        paint.setColor(FAINT);
        c.drawLine(first, lineY, last, lineY, paint);
        float nowX = nowX(first, last);
        paint.setColor(MUTED);
        if (nowX > first) c.drawLine(first, lineY, nowX, lineY, paint);

        for (int i = 0; i < minutes.length && i < 5; i++) {
            float cx = pad + inner * (2 * i + 1) / 10f;
            boolean isNext = i == next || (next < 0 && i == 0);
            boolean past = next >= 0 ? i < next : true;
            paint.setStyle(Paint.Style.FILL);
            if (isNext) {
                paint.setColor(GOLD);
                c.drawCircle(cx, lineY, dp(9), paint);
            } else if (past) {
                paint.setColor(MUTED);
                c.drawCircle(cx, lineY, dp(7), paint);
            } else {
                paint.setColor(0xFF1B2533);
                c.drawCircle(cx, lineY, dp(7), paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(MUTED);
                c.drawCircle(cx, lineY, dp(7), paint);
                paint.setStyle(Paint.Style.FILL);
            }
            center(c, PrayerTimes.NAMES[i], cx, lineY + dp(31), 15, isNext ? GOLD : SOFT, isNext ? 700 : 500);
            String t = PrayerTimes.format(minutes[i], is24) + (is24 ? "" : " " + shortPeriod(minutes[i] / 60));
            center(c, t, cx, lineY + dp(50), 13, isNext ? WHITE : MUTED, isNext ? 600 : 400);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF0C1620);
        c.drawCircle(nowX, lineY, dp(8), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3));
        paint.setColor(WHITE);
        c.drawCircle(nowX, lineY, dp(8), paint);
        paint.setStyle(Paint.Style.FILL);
        center(c, "KINI", nowX, lineY - dp(13), 10, WHITE, 700);
    }

    private static String shortPeriod(int hour) {
        if (hour < 12) return "pg";
        if (hour < 14) return "tgh";
        if (hour < 19) return "ptg";
        return "mlm";
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

    private void center(Canvas c, String s, float cx, float y, int sp, int color, int weight) {
        style(sp, weight, 0f);
        paint.setColor(color);
        c.drawText(s, cx - paint.measureText(s) / 2f, y, paint);
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }
}
