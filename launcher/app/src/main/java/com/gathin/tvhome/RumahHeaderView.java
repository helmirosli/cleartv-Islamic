package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;

/**
 * Rumah TV: the top of Home. On the left a large clock with the time of day in Malay ("malam"), the
 * date in Malay and the Hijri date; on the right the weather on a glass card, and under it the
 * mosque in the background picture with its photo credit. Drawn in one pass; it ticks itself on the
 * minute, like a TextClock.
 */
public class RumahHeaderView extends View {
    private static final int WHITE = 0xFFFFFFFF, SOFT = 0xD9FFFFFF, MUTED = 0x99FFFFFF;
    private static final int CARD = 0x66101A28, RIM = 0x33FFFFFF;
    static final String[] DAYS = {"Ahad", "Isnin", "Selasa", "Rabu", "Khamis", "Jumaat", "Sabtu"};
    static final String[] MONTHS = {"Januari", "Februari", "Mac", "April", "Mei", "Jun", "Julai", "Ogos",
            "September", "Oktober", "November", "Disember"};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Typeface face = Typeface.DEFAULT;

    private String hijri = "";
    private String temp = "", desc = "", rain = "";
    private String placeName = "", placeWhere = "", credit = "";
    private boolean ticking;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            invalidate();
        }
    };

    public RumahHeaderView(Context c) {
        this(c, null);
    }

    public RumahHeaderView(Context c, AttributeSet a) {
        super(c, a);
        try {
            face = c.getResources().getFont(R.font.outfit);
        } catch (RuntimeException ignored) {
            // Keep the system font.
        }
        setFocusable(false);
        setWillNotDraw(false);
    }

    void setHijri(String h) {
        h = h == null || h.isEmpty() ? "" : h + "H";
        if (!h.equals(hijri)) {
            hijri = h;
            invalidate();
        }
    }

    /** {"24°", "Cerah berawan", "Hujan 70% sekitar 4 ptg"}, or null for none. */
    void setWeather(String[] w) {
        String t = w != null ? w[0] : "", d = w != null ? w[1] : "", r = w != null ? w[2] : "";
        if (t.equals(temp) && d.equals(desc) && r.equals(rain)) return;
        temp = t;
        desc = d;
        rain = r;
        invalidate();
    }

    void setPlace(String name, String where, String photoCredit) {
        placeName = name == null ? "" : name;
        placeWhere = where == null ? "" : where;
        credit = photoCredit == null ? "" : photoCredit;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if (android.os.Build.VERSION.SDK_INT >= 33) getContext().registerReceiver(tick, f, Context.RECEIVER_NOT_EXPORTED);
        else getContext().registerReceiver(tick, f);
        ticking = true;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (ticking) {
            getContext().unregisterReceiver(tick);
            ticking = false;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), w), Math.round(dp(118)));
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        Calendar now = Calendar.getInstance();
        boolean is24 = android.text.format.DateFormat.is24HourFormat(getContext());
        int h = now.get(Calendar.HOUR_OF_DAY), m = now.get(Calendar.MINUTE);
        String time = is24 ? String.format(Locale.ROOT, "%02d:%02d", h, m)
                : String.format(Locale.ROOT, "%d:%02d", h % 12 == 0 ? 12 : h % 12, m);
        float w = getWidth();

        paint.setShadowLayer(dp(10), 0, dp(2), 0x66000000);
        // The clock, and beside it the time of day in words.
        float x = draw(c, time, 0, dp(56), 66, WHITE, 400, -0.03f);
        if (!is24) draw(c, period(h), x + dp(10), dp(56), 20, SOFT, 500, 0f);
        String date = DAYS[now.get(Calendar.DAY_OF_WEEK) - 1] + ", " + now.get(Calendar.DAY_OF_MONTH) + " "
                + MONTHS[now.get(Calendar.MONTH)] + " " + now.get(Calendar.YEAR);
        draw(c, date, dp(2), dp(87), 19, WHITE, 600, 0f);
        if (!hijri.isEmpty()) draw(c, hijri, dp(2), dp(110), 15, SOFT, 400, 0f);

        // The weather card at the top right.
        float right = w;
        if (!temp.isEmpty()) {
            paint.clearShadowLayer();
            float tw = measure(temp, 32, 500), dw = measure(desc, 16, 600), rw = rain.isEmpty() ? 0 : measure(rain, 13, 400);
            float cw = dp(20) + tw + dp(12) + Math.max(dw, rw) + dp(20);
            rect.set(right - cw, dp(4), right, dp(70));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(CARD);
            c.drawRoundRect(rect, dp(18), dp(18), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(RIM);
            c.drawRoundRect(rect, dp(18), dp(18), paint);
            paint.setStyle(Paint.Style.FILL);
            float tx = rect.left + dp(20);
            draw(c, temp, tx, dp(50), 32, WHITE, 500, -0.02f);
            float dx = tx + tw + dp(12);
            if (rain.isEmpty()) {
                draw(c, desc, dx, dp(43), 16, WHITE, 600, 0f);
            } else {
                draw(c, desc, dx, dp(33), 16, WHITE, 600, 0f);
                draw(c, rain, dx, dp(54), 13, SOFT, 400, 0f);
            }
            paint.setShadowLayer(dp(10), 0, dp(2), 0x66000000);
        }

        // Under it, the picture behind Home: which mosque, and whose photo (its licence asks for it).
        if (!placeName.isEmpty()) {
            String where = placeWhere.isEmpty() ? placeName : placeName + " · " + placeWhere;
            float maxW = w * 0.48f;
            where = fit(where, 13, 500, maxW);
            draw(c, where, right - measure(where, 13, 500), dp(92), 13, SOFT, 500, 0f);
            if (!credit.isEmpty()) {
                String cr = fit(credit, 11, 400, maxW);
                draw(c, cr, right - measure(cr, 11, 400), dp(110), 11, MUTED, 400, 0f);
            }
        }
        paint.clearShadowLayer();
    }

    /** The Malay time of day: pagi, tengah hari, petang, malam. */
    static String period(int hour) {
        if (hour < 12) return "pagi";
        if (hour < 14) return "tengah hari";
        if (hour < 19) return "petang";
        return "malam";
    }

    private String fit(String s, int sp, int weight, float max) {
        if (measure(s, sp, weight) <= max) return s;
        while (s.length() > 4 && measure(s + "…", sp, weight) > max) s = s.substring(0, s.length() - 1);
        return s.trim() + "…";
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
