package com.ngoctho.aitranslator;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TranslationEngine {
    public interface Callback {
        void onSuccess(TranslationResult result, boolean fromCache);
        void onError(String message);
    }

    private static final ExecutorService EXEC = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, TranslationResult> CACHE = new LruCache<>(96);

    private TranslationEngine() {}

    public static void translate(Context context, String source, Callback callback) {
        final String text = source == null ? "" : source.trim();
        if (text.isEmpty()) {
            callback.onError("Chưa có nội dung để dịch.");
            return;
        }
        final String provider = AppPrefs.provider(context);
        final String model = AppPrefs.model(context);
        final String ctx = AppPrefs.businessContext(context);
        final String key = provider + "|" + KeyboardData.styleKey(context) + "|" + AppPrefs.glossary(context) + "|" + AppPrefs.businessContext(context) + "|" + model + "|" + ctx + "|" + text;

        TranslationResult cached = CACHE.get(key);
        if (cached != null) {
            MAIN.post(() -> callback.onSuccess(cached, true));
            return;
        }

        TranslationResult local = LocalPhrasebook.translateViToZh(text, ctx);
        if (local != null) {
            CACHE.put(key, local);
            HistoryStore.add(context, "VI→中 · local", local.source, local.chinese, local.backVi, local.score);
            MAIN.post(() -> callback.onSuccess(local, true));
            return;
        }

        if (!AppPrefs.isReady(context)) {
            callback.onError("Chưa cấu hình AI. Có thể chọn DEMO hoặc nhập API/proxy trong ứng dụng.");
            return;
        }

        EXEC.execute(() -> {
            try {
                TranslationResult r = MeaningCheck.check(ApiClient.translate(context, text), KeyboardData.prefs(context).getString("lock_terms", ""));
                CACHE.put(key, r);
                HistoryStore.add(context, "VI→中", r.source, r.chinese, r.backVi, r.score);
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
