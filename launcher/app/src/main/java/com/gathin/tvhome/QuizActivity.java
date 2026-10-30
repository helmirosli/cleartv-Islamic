package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Rumah TV: the quiz in front of a guarded app. Who is watching, then a few questions (right or
 * wrong, the screen shows the answer), and the app opens for a while. The parents' PIN opens it
 * without a quiz. Back goes Home, so the app stays closed. Built in code, no layouts, to stay small.
 */
public class QuizActivity extends Activity {
    static final String EXTRA_PACKAGE = "pkg";
    static final String EXTRA_EXPIRED = "expired";

    private static final int BG = 0xFF0C1620, CARD = 0xFF15222E, TILE = 0xFF1C2C3A, LINE = 0xFF2A3D4D;
    private static final int TEXT = 0xFFEEF3F6, MUTED = 0xFF93A7B5, ACCENT = 0xFFF2B544;
    private static final int GOOD = 0xFF4CC9A8, GOOD_BG = 0xFF173A35, BAD = 0xFFF07489, BAD_BG = 0xFF3A1F27;
    private static final int PIN_TRIES = 5;
    private static final long PIN_LOCK_MS = 5 * 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private Typeface font;
    private RumahConfig rc;
    private boolean expired;

    private RumahConfig.Child child;
    private List<Quiz.Question> questions;
    private int index, correct;
    private final StringBuilder pin = new StringBuilder();

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        try {
            font = getResources().getFont(R.font.outfit);
        } catch (RuntimeException e) {
            font = Typeface.DEFAULT;
        }
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);
        start(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        start(intent);
    }

    private void start(Intent intent) {
        rc = RumahConfig.load(this);
        expired = intent != null && intent.getBooleanExtra(EXTRA_EXPIRED, false);
        if (Gate.remainingMs(this) > 0) {
            finish();
            return;
        }
        String recent = Gate.recentChild(this);
        if (recent != null && !expired) {
            for (RumahConfig.Child c : rc.children) {
                if (c.name.equals(recent)) {
                    startQuiz(c);
                    return;
                }
            }
        }
        if (rc.children.size() == 1) startQuiz(rc.children.get(0));
        else if (rc.children.isEmpty()) startQuiz(null);
        else showProfiles();
    }

    // ---- screens ---------------------------------------------------------------------------------

    private void showProfiles() {
        LinearLayout page = page();
        page.addView(text(expired ? "Masa YouTube dah habis." : "Siapa tengok?", 40, TEXT, true));
        page.addView(text(expired ? "Jawab kuiz lagi untuk sambung, atau minta PIN ibu bapa."
                : "YouTube perlu dibuka dengan kuiz dulu.", 20, MUTED, false), spaced(6));
        LinearLayout row = row();
        View first = null;
        for (RumahConfig.Child c : rc.children) {
            SpannableString s = new SpannableString("●  " + c.name);
            s.setSpan(new ForegroundColorSpan(c.color), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            TextView b = button(s, 24);
            b.setOnClickListener(v -> startQuiz(c));
            row.addView(b, tileParams(220, 96));
            if (first == null) first = b;
        }
        page.addView(row, spaced(36));
        page.addView(footer(), spaced(36));
        show(page, first);
    }

    private void startQuiz(RumahConfig.Child c) {
        child = c;
        if (c != null) Gate.rememberChild(this, c.name);
        int level = c != null ? c.level : 2;
        questions = Quiz.pick(this, level, rc.quizQuestions);
        index = 0;
        correct = 0;
        showQuestion();
    }

    private void showQuestion() {
        Quiz.Question q = questions.get(index);
        LinearLayout page = page();

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout hello = new LinearLayout(this);
        hello.setOrientation(LinearLayout.VERTICAL);
        String name = child != null ? child.name : "";
        hello.addView(text(name.isEmpty() ? "Assalamualaikum." : "Assalamualaikum, " + name + ".", 34, TEXT, true));
        hello.addView(text((expired ? "Masa dah habis. " : "") + "Jawab " + questions.size()
                + " soalan dulu, lepas tu YouTube dibuka " + rc.quizUnlockMinutes + " minit.", 18, MUTED, false));
        head.addView(hello, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(text("Soalan " + (index + 1) + " daripada " + questions.size(), 16, MUTED, true));
        page.addView(head);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(CARD, 0, 20));
        card.setPadding(dp(36), dp(28), dp(36), dp(28));
        TextView chip = text(q.category.toUpperCase(java.util.Locale.ROOT), 13, BG, true);
        chip.setBackground(rounded(0xFFA9CCF8, 0, 99));
        chip.setPadding(dp(12), dp(4), dp(12), dp(4));
        chip.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        card.addView(chip, chipLp);
        card.addView(text(q.prompt, 30, TEXT, true), spaced(18));

        LinearLayout options = row();
        List<TextView> buttons = new ArrayList<>();
        TextView feedback = text("", 20, MUTED, true);
        TextView explain = text("", 17, MUTED, false);
        TextView next = button(index + 1 < questions.size() ? "Soalan seterusnya  →" : "Selesai  →", 19);
        next.setVisibility(View.INVISIBLE);
        for (int i = 0; i < q.choices.length; i++) {
            final int chosen = i;
            TextView b = button(q.choices[i], 26);
            b.setOnClickListener(v -> answer(q, chosen, buttons, feedback, explain, next));
            buttons.add(b);
            options.addView(b, tileParams(220, 84));
        }
        card.addView(options, spaced(24));
        LinearLayout after = new LinearLayout(this);
        after.setOrientation(LinearLayout.HORIZONTAL);
        after.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(feedback);
        words.addView(explain);
        after.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        next.setOnClickListener(v -> {
            index++;
            if (index < questions.size()) showQuestion();
            else showDone();
        });
        after.addView(next, tileParams(260, 60));
        card.addView(after, spaced(20));
        page.addView(card, spaced(22));
        page.addView(footer(), spaced(18));
        show(page, buttons.get(0));
    }

    private void answer(Quiz.Question q, int chosen, List<TextView> buttons, TextView feedback,
                        TextView explain, TextView next) {
        boolean right = chosen == q.answer;
        if (right) correct++;
        Quiz.record(this, child != null ? child.name : null, right);
        for (int i = 0; i < buttons.size(); i++) {
            TextView b = buttons.get(i);
            b.setFocusable(false);
            b.setClickable(false);
            if (i == q.answer) {
                b.setBackground(rounded(GOOD_BG, GOOD, 16));
                b.setTextColor(TEXT);
            } else if (i == chosen) {
                b.setBackground(rounded(BAD_BG, BAD, 16));
                b.setTextColor(TEXT);
            } else {
                b.setTextColor(MUTED);
            }
        }
        feedback.setText(right ? "Betul!" : "Jawapan betul: " + q.choices[q.answer]);
        feedback.setTextColor(right ? GOOD : ACCENT);
        explain.setText(right ? q.explain : "Tak apa, sekarang dah tahu. " + q.explain);
        next.setVisibility(View.VISIBLE);
        next.requestFocus();
    }

    private void showDone() {
        long ms = rc.quizUnlockMinutes * 60_000L;
        Gate.unlock(this, child != null ? child.name : null, ms, "quiz");
        LinearLayout page = page();
        String name = child != null ? ", " + child.name : "";
        page.addView(text("Syabas" + name + "!", 44, TEXT, true));
        page.addView(text("Betul " + correct + " daripada " + questions.size() + " soalan.", 24, GOOD, true), spaced(10));
        page.addView(text("YouTube dibuka " + rc.quizUnlockMinutes + " minit.", 22, MUTED, false), spaced(6));
        TextView go = button("Tonton  →", 22);
        go.setOnClickListener(v -> finish());
        page.addView(go, tileParams(240, 64, 30));
        show(page, go);
    }

    private void showPin() {
        LinearLayout page = page();
        SharedPreferences p = getSharedPreferences("rumah_gate", MODE_PRIVATE);
        long lockedUntil = p.getLong("pinLockedUntil", 0);
        if (lockedUntil > System.currentTimeMillis()) {
            long min = (lockedUntil - System.currentTimeMillis()) / 60_000L + 1;
            page.addView(text("PIN dikunci", 40, TEXT, true));
            page.addView(text("Terlalu banyak cubaan salah. Cuba lagi dalam " + min + " minit.", 20, MUTED, false), spaced(8));
            TextView back = button("Kembali", 20);
            back.setOnClickListener(v -> start(getIntent()));
            page.addView(back, tileParams(220, 60, 28));
            show(page, back);
            return;
        }
        pin.setLength(0);
        page.addView(text("PIN ibu bapa", 40, TEXT, true));
        page.addView(text("Buka YouTube selama 2 jam tanpa kuiz.", 20, MUTED, false), spaced(6));
        TextView dots = text("○ ○ ○ ○", 40, TEXT, true);
        dots.setLetterSpacing(0.15f);
        page.addView(dots, spaced(20));
        TextView status = text("", 18, BAD, true);
        page.addView(status, spaced(6));

        GridLayout pad = new GridLayout(this);
        pad.setColumnCount(3);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "Batal"};
        View first = null;
        for (String k : keys) {
            TextView b = button(k, 24);
            b.setOnClickListener(v -> {
                if (k.equals("Batal")) {
                    start(getIntent());
                    return;
                }
                if (k.equals("⌫")) {
                    if (pin.length() > 0) pin.setLength(pin.length() - 1);
                } else if (pin.length() < 4) {
                    pin.append(k);
                }
                StringBuilder d = new StringBuilder();
                for (int i = 0; i < 4; i++) d.append(i < pin.length() ? "● " : "○ ");
                dots.setText(d.toString().trim());
                if (pin.length() == 4) checkPin(status, dots, p);
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(120);
            lp.height = dp(60);
            lp.setMargins(dp(6), dp(6), dp(6), dp(6));
            pad.addView(b, lp);
            if (first == null) first = b;
        }
        page.addView(pad, spaced(14));
        show(page, first);
    }

    private void checkPin(TextView status, TextView dots, SharedPreferences p) {
        if (!rc.parentPin.isEmpty() && pin.toString().equals(rc.parentPin)) {
            p.edit().putInt("pinWrong", 0).apply();
            Gate.unlock(this, child != null ? child.name : null, Gate.PIN_UNLOCK_MS, "pin");
            finish();
            return;
        }
        int wrong = p.getInt("pinWrong", 0) + 1;
        SharedPreferences.Editor e = p.edit().putInt("pinWrong", wrong);
        if (wrong >= PIN_TRIES) {
            e.putLong("pinLockedUntil", System.currentTimeMillis() + PIN_LOCK_MS).putInt("pinWrong", 0);
        }
        e.apply();
        pin.setLength(0);
        status.setText(wrong >= PIN_TRIES ? "PIN salah. Dikunci 5 minit." : "PIN salah. Cuba lagi.");
        handler.postDelayed(() -> dots.setText("○ ○ ○ ○"), 400);
        if (wrong >= PIN_TRIES) handler.postDelayed(this::showPin, 1_500);
    }

    // ---- navigation ------------------------------------------------------------------------------

    @Override
    public void onBackPressed() {
        goHome();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            goHome();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void goHome() {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setPackage(getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ignored) {
            // Home is this app; it is always there.
        }
        finish();
    }

    // ---- building blocks -------------------------------------------------------------------------

    private LinearLayout footer() {
        LinearLayout f = row();
        if (!rc.parentPin.isEmpty()) {
            TextView pinButton = button("PIN ibu bapa", 17);
            pinButton.setOnClickListener(v -> showPin());
            f.addView(pinButton, tileParams(200, 52));
        }
        TextView home = button("Kembali ke Home", 17);
        home.setOnClickListener(v -> goHome());
        f.addView(home, tileParams(220, 52));
        return f;
    }

    private LinearLayout page() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(64), dp(40), dp(64), dp(30));
        return p;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private void show(View page, View focus) {
        root.removeAllViews();
        root.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        if (focus != null) focus.post(focus::requestFocus);
    }

    private TextView text(CharSequence s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(font, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    private TextView button(CharSequence s, int sp) {
        TextView b = text(s, sp, TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        b.setPadding(dp(18), dp(8), dp(18), dp(8));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, rounded(0xFFFFFFFF, 0, 16));
        bg.addState(new int[]{}, rounded(TILE, LINE, 16));
        b.setBackground(bg);
        b.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{BG, TEXT}));
        b.setOnFocusChangeListener((v, has) -> v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f)
                .setDuration(120).start());
        return b;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(2), stroke);
        return g;
    }

    private LinearLayout.LayoutParams tileParams(int wDp, int hDp) {
        return tileParams(wDp, hDp, 0);
    }

    private LinearLayout.LayoutParams tileParams(int wDp, int hDp, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(wDp), dp(hDp));
        lp.setMargins(0, dp(topDp), dp(16), 0);
        return lp;
    }

    private LinearLayout.LayoutParams spaced(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
