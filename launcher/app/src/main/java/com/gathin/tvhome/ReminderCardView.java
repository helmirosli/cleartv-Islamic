package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
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

/**
 * Rumah TV: the reminder card beside the next-azan card. The meaning of one verse of the Quran from
 * the screensaver's list (res/raw/ayat.json), a different one each day, among those short enough to
 * be read whole on the card: a verse is never cut short.
 */
public class ReminderCardView extends View {
    private static final int WHITE = 0xFFFFFFFF, SOFT = 0xD9FFFFFF;

    private final TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private Typeface face = Typeface.DEFAULT;
    private final List<String[]> verses = new ArrayList<>();
    private StaticLayout layout;
    private String ref = "";
    private int layoutWidth = -1, layoutDay = -1;

    public ReminderCardView(Context c) {
        this(c, null);
    }

    public ReminderCardView(Context c, AttributeSet a) {
        super(c, a);
        try {
            face = c.getResources().getFont(R.font.outfit);
        } catch (RuntimeException ignored) {
            // Keep the system font.
        }
        setFocusable(false);
        setWillNotDraw(false);
        load();
    }

    private void load() {
        try (InputStream in = getResources().openRawResource(R.raw.ayat)) {
            byte[] buf = new byte[64 * 1024];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            JSONArray a = new JSONObject(new String(buf, 0, n, StandardCharsets.UTF_8)).getJSONArray("ayat");
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                verses.add(new String[]{o.getString("tr"), o.getString("ref")});
            }
        } catch (Exception e) {
            Log.w("RumahTV.Reminder", "no verses: " + e.getMessage());
        }
    }

    /** Picks today's verse again when the day or the width changed. */
    private void prepare(int width) {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        if (width == layoutWidth && day == layoutDay) return;
        layoutWidth = width;
        layoutDay = day;
        layout = null;
        ref = "";
        if (verses.isEmpty() || width <= 0) return;
        float room = getHeight() - dp(36) - dp(9);
        // From today's verse onwards, the first that fits whole, at 13sp or 12sp. The
        // layout keeps its own paint: the label and the reference are drawn with another.
        for (int k = 0; k < verses.size() && layout == null; k++) {
            String[] v = verses.get((day + k) % verses.size());
            String text = "“" + v[0].trim() + "”";
            for (int sp = 13; sp >= 12; sp--) {
                TextPaint body = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
                body.setTypeface(face);
                body.setTextSize(sp(sp));
                body.setFontVariationSettings("'wght' 500");
                body.setColor(WHITE);
                StaticLayout l = StaticLayout.Builder.obtain(text, 0, text.length(), body, width)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(dp(1.5f), 1f)
                        .setIncludePad(false)
                        .build();
                if (l.getHeight() <= room) {
                    layout = l;
                    ref = v[1];
                    break;
                }
            }
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float pad = dp(20);
        int width = (int) (getWidth() - 2 * pad);
        prepare(width);
        paint.setTypeface(face);
        paint.setTextSize(sp(11));
        paint.setFontVariationSettings("'wght' 700");
        paint.setLetterSpacing(0.12f);
        paint.setColor(SOFT);
        c.drawText("PERINGATAN", pad, dp(26), paint);
        paint.setLetterSpacing(0f);
        if (layout == null) return;
        // The verse's reference on the label's line, at the right.
        paint.setTextSize(sp(12));
        paint.setFontVariationSettings("'wght' 400");
        c.drawText(ref, getWidth() - pad - paint.measureText(ref), dp(26), paint);
        c.save();
        c.translate(pad, dp(36));
        layout.draw(c);
        c.restore();
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }
}
