package com.ngoctho.aitranslator;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DirectionalTranslationEngine {
    public interface Callback {
        void onSuccess(BiTranslationResult result, boolean fromCache);
        void onError(String message);
    }

    public static final String VI_TO_ZH = "vi_zh";
    public static final String ZH_TO_VI = "zh_vi";

    private static final ExecutorService EXEC = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, BiTranslationResult> CACHE = new LruCache<>(64);

    private DirectionalTranslationEngine() {}

    public static void translate(Context context, String source, String direction, Callback callback) {
        translate(context, source, direction, false, callback);
    }
    public static void translate(Context context, String source, String direction, boolean typed, Callback callback) {
        final String text = source == null ? "" : source.trim();
        if (text.isEmpty()) {
            callback.onError("Chưa có nội dung để dịch.");
            return;
        }
        final String dir = ZH_TO_VI.equals(direction) ? ZH_TO_VI : VI_TO_ZH;
        final String provider = AppPrefs.provider(context);
        final String model = AppPrefs.model(context);
        final String ctx = AppPrefs.businessContext(context);
        final String key = provider + "|" + KeyboardData.styleKey(context) + "|" + AppPrefs.glossary(context) + "|" + AppPrefs.businessContext(context) + "|" + model + "|" + ctx + "|" + dir + "|" + typed + "|" + text;

        BiTranslationResult cached = CACHE.get(key);
        if (cached != null) {
            MAIN.post(() -> callback.onSuccess(cached, true));
            return;
        }

        boolean useLocal = "polite".equals(KeyboardData.prefs(context).getString("tone", "polite"))
                && KeyboardData.prefs(context).getString("lock_terms", "").isEmpty()
                && !text.equals(KeyboardData.prefs(context).getString("alternative_source", ""));
        BiTranslationResult local = useLocal ? LocalPhrasebook.translateDirectional(text, dir, ctx) : null;
        if (local != null) {
            CACHE.put(key, local);
            HistoryStore.add(context, dir.equals(VI_TO_ZH) ? "VOICE VI→中 · local" : "VOICE 中→VI · local",
                    local.source, local.translated, local.backTranslated, local.score);
            MAIN.post(() -> callback.onSuccess(local, true));
            return;
        }

        if (!AppPrefs.isReady(context)) {
            callback.onError("Chưa cấu hình AI cho câu này.");
            return;
        }

        EXEC.execute(() -> {
            try {
                BiTranslationResult r = ApiClient.translateDirectional(context, text, dir, typed);
                CACHE.put(key, r);
                HistoryStore.add(context, dir.equals(VI_TO_ZH) ? "VOICE VI→中" : "VOICE 中→VI",
                        r.source, r.translated, r.backTranslated, r.score);
                MAIN.post(() -> callback.onSuccess(r, false));
            } catch (Exception e) {
                MAIN.post(() -> callback.onError(safeMessage(e)));
            }
        });
    }

    private static String safeMessage(Exception e) {
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }
}
