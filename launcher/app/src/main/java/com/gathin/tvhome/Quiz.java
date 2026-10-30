package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Rumah TV: the questions for the quiz before a guarded app. One arithmetic question made up on the
 * spot for the child's level, the rest from assets/quiz.json, avoiding ones asked recently.
 */
final class Quiz {
    private static final String TAG = "RumahTV.Quiz";
    private static final String PREFS = "rumah_quiz";
    private static final int RECENT = 40;

    static final class Question {
        final String id;
        final String category;
        final String prompt;
        final String[] choices;
        final int answer;
        final String explain;

        Question(String id, String category, String prompt, String[] choices, int answer, String explain) {
            this.id = id;
            this.category = category;
            this.prompt = prompt;
            this.choices = choices;
            this.answer = answer;
            this.explain = explain;
        }
    }

    private Quiz() {
    }

    static List<Question> pick(Context ctx, int level, int count) {
        Random rnd = new Random();
        List<Question> out = new ArrayList<>();
        out.add(arithmetic(rnd, level));

        List<Question> bank = bank(ctx, level);
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<String> recent = new ArrayList<>(Arrays.asList(p.getString("recent", "").split(",")));
        Set<String> recentSet = new HashSet<>(recent);
        Collections.shuffle(bank, rnd);
        // Fresh questions first, then recent ones if the bank runs short.
        bank.sort((a, b) -> Boolean.compare(recentSet.contains(a.id), recentSet.contains(b.id)));
        Set<String> categories = new HashSet<>();
        for (Question q : bank) {
            if (out.size() >= count) break;
            if (categories.contains(q.category) && bank.size() > count * 2) continue; // vary subjects
            categories.add(q.category);
            out.add(shuffled(q, rnd));
        }
        for (Question q : bank) {
            if (out.size() >= count) break;
            if (!containsId(out, q.id)) out.add(shuffled(q, rnd));
        }
        while (out.size() < count) out.add(arithmetic(rnd, level));

        for (Question q : out) {
            if (q.id.startsWith("math")) continue;
            recent.remove(q.id);
            recent.add(0, q.id);
        }
        while (recent.size() > RECENT) recent.remove(recent.size() - 1);
        p.edit().putString("recent", String.join(",", recent)).apply();
        Collections.shuffle(out, rnd);
        return out;
    }

    /** Keeps a tally per child per day, for the weekly report later. */
    static void record(Context ctx, String child, boolean correct) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String day = PrayerTimes.key(java.util.Calendar.getInstance());
        String k = "tally|" + (child == null ? "-" : child) + "|" + day;
        int[] t = parse(p.getString(k, "0/0"));
        t[1]++;
        if (correct) t[0]++;
        p.edit().putString(k, t[0] + "/" + t[1]).apply();
    }

    private static int[] parse(String s) {
        try {
            String[] a = s.split("/");
            return new int[]{Integer.parseInt(a[0]), Integer.parseInt(a[1])};
        } catch (RuntimeException e) {
            return new int[]{0, 0};
        }
    }

    private static boolean containsId(List<Question> qs, String id) {
        for (Question q : qs) if (q.id.equals(id)) return true;
        return false;
    }

    private static Question shuffled(Question q, Random rnd) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < q.choices.length; i++) order.add(i);
        Collections.shuffle(order, rnd);
        String[] c = new String[q.choices.length];
        int ans = 0;
        for (int i = 0; i < order.size(); i++) {
            c[i] = q.choices[order.get(i)];
            if (order.get(i) == q.answer) ans = i;
        }
        return new Question(q.id, q.category, q.prompt, c, ans, q.explain);
    }

    private static List<Question> bank(Context ctx, int level) {
        List<Question> out = new ArrayList<>();
        try (InputStream in = ctx.getAssets().open("quiz.json")) {
            byte[] buf = new byte[64 * 1024];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            JSONArray qs = new JSONObject(new String(buf, 0, n, StandardCharsets.UTF_8)).getJSONArray("questions");
            for (int i = 0; i < qs.length(); i++) {
                JSONObject o = qs.getJSONObject(i);
                if (o.optInt("level", 1) > level) continue;
                JSONArray ch = o.getJSONArray("choices");
                String[] c = new String[ch.length()];
                for (int j = 0; j < c.length; j++) c[j] = ch.getString(j);
                int ans = o.optInt("answer", 0);
                if (ans < 0 || ans >= c.length) continue;
                out.add(new Question(o.getString("id"), o.optString("category", ""), o.getString("prompt"),
                        c, ans, o.optString("explain", "")));
            }
        } catch (Exception e) {
            Log.w(TAG, "quiz.json unreadable: " + e);
        }
        return out;
    }

    /** An arithmetic question for the level: 1 = add/subtract to 20, 2 = to 100 and times tables, 3 = harder. */
    static Question arithmetic(Random rnd, int level) {
        int a, b, ans;
        String prompt, explain;
        int kind = rnd.nextInt(level >= 2 ? 3 : 2);
        if (level <= 1) {
            a = 1 + rnd.nextInt(10);
            b = 1 + rnd.nextInt(10);
            if (kind == 0) { ans = a + b; prompt = a + " + " + b + " = ?"; }
            else { if (b > a) { int t = a; a = b; b = t; } ans = a - b; prompt = a + " − " + b + " = ?"; }
        } else if (level == 2) {
            if (kind == 2) { a = 2 + rnd.nextInt(8); b = 2 + rnd.nextInt(9); ans = a * b; prompt = a + " × " + b + " = ?"; }
            else {
                a = 10 + rnd.nextInt(80);
                b = 1 + rnd.nextInt(Math.min(40, 99 - a) + 1);
                if (kind == 0) { ans = a + b; prompt = a + " + " + b + " = ?"; }
                else { ans = a - Math.min(a, b); b = Math.min(a, b); prompt = a + " − " + b + " = ?"; }
            }
        } else {
            if (kind == 0) { a = 12 + rnd.nextInt(88); b = 2 + rnd.nextInt(8); ans = a * b; prompt = a + " × " + b + " = ?"; }
            else if (kind == 1) { b = 2 + rnd.nextInt(8); ans = 2 + rnd.nextInt(11); a = ans * b; prompt = a + " ÷ " + b + " = ?"; }
            else {
                a = 3 + rnd.nextInt(7);
                b = 2 + rnd.nextInt(6);
                ans = a * b;
                prompt = "Ibu beli " + a + " bungkus kuih. Setiap bungkus ada " + b + " biji. Berapa biji semuanya?";
            }
        }
        explain = "Jawapannya " + ans + ".";
        Set<Integer> opts = new HashSet<>();
        opts.add(ans);
        int[] deltas = {1, 2, 10, 3, -1, -2, -10, 5};
        for (int i = 0; opts.size() < 3 && i < 40; i++) {
            int d = deltas[rnd.nextInt(deltas.length)];
            if (ans + d >= 0) opts.add(ans + d);
        }
        while (opts.size() < 3) opts.add(ans + opts.size() + 1);
        List<Integer> list = new ArrayList<>(opts);
        Collections.shuffle(list, rnd);
        String[] c = new String[list.size()];
        int idx = 0;
        for (int i = 0; i < c.length; i++) {
            c[i] = String.valueOf(list.get(i));
            if (list.get(i) == ans) idx = i;
        }
        return new Question("math" + rnd.nextInt(100000), "Matematik", prompt, c, idx, explain);
    }
}
