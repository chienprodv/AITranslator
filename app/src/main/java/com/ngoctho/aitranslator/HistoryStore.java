package com.ngoctho.aitranslator;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class HistoryStore {
    private static final String FILE = "translation_history";
    private static final String KEY = "items";
    private static final int MAX_ITEMS = 100;

    private HistoryStore() {}

    public static synchronized void add(
            Context context,
            String type,
            String source,
            String target,
            String back,
            int score) {
        if (!AppPrefs.saveHistory(context)) return;
        try {
            SharedPreferences p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
            JSONArray old = new JSONArray(p.getString(KEY, "[]"));
            JSONArray next = new JSONArray();
            JSONObject item = new JSONObject();
            item.put("time", System.currentTimeMillis());
            item.put("type", safe(type));
            item.put("source", safe(source));
            item.put("target", safe(target));
            item.put("back", safe(back));
            item.put("score", Math.max(0, Math.min(100, score)));
            next.put(item);
            for (int i = 0; i < old.length() && next.length() < MAX_ITEMS; i++) {
                JSONObject o = old.optJSONObject(i);
                if (o != null) next.put(o);
            }
            p.edit().putString(KEY, next.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static synchronized JSONArray list(Context context) {
        try {
            String s = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "[]");
            return new JSONArray(s == null ? "[]" : s);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static synchronized void clear(Context context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }

    /** Explicitly saved phrases survive cache eviction and app restarts. */
    public static synchronized void savePhrase(Context context, String source, String target, String back) {
        if (target == null || target.trim().isEmpty()) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
            JSONArray old = savedPhrases(context);
            JSONArray next = new JSONArray();
            next.put(new JSONObject().put("source", safe(source)).put("target", safe(target))
                    .put("back", safe(back)).put("time", System.currentTimeMillis()));
            for (int i = 0; i < old.length() && next.length() < MAX_ITEMS; i++) {
                JSONObject item = old.optJSONObject(i);
                if (item != null && !(item.optString("source").equals(safe(source))
                        && item.optString("target").equals(safe(target)))) next.put(item);
            }
            prefs.edit().putString("saved_phrases", next.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static synchronized JSONArray savedPhrases(Context context) {
        try {
            return new JSONArray(context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                    .getString("saved_phrases", "[]"));
        } catch (Exception ignored) { return new JSONArray(); }
    }

    public static synchronized void removePhrase(Context context, String source, String target) {
        JSONArray old = savedPhrases(context);
        JSONArray next = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.optJSONObject(i);
            if (item != null && !(item.optString("source").equals(source)
                    && item.optString("target").equals(target))) next.put(item);
        }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putString("saved_phrases", next.toString()).apply();
    }

    public static String formatTime(long time) {
        return new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(new Date(time));
    }

    private static String safe(String s) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() > 2000 ? t.substring(0, 2000) : t;
    }
}
