package com.ngoctho.aitranslator;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ScreenTranslationEngine {
    public interface Callback {
        void onSuccess(ScreenTranslationResult result, boolean fromCache);
        void onError(String message);
    }

    private static final ExecutorService EXEC = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, ScreenTranslationResult> CACHE = new LruCache<>(48);

    private ScreenTranslationEngine() {}

    public static void translate(Context context, String source, Callback callback) {
        final String text = compact(source);
        if (text.isEmpty()) {
            callback.onError("OCR chưa có nội dung để dịch.");
            return;
        }
        final String provider = AppPrefs.provider(context);
        final String model = AppPrefs.model(context);
        final String key = provider + "|" + model + "|screen|" + text;

        ScreenTranslationResult cached = CACHE.get(key);
        if (cached != null) {
            MAIN.post(() -> callback.onSuccess(cached, true));
            return;
        }

        ScreenTranslationResult local = LocalPhrasebook.translateScreen(text);
        if (local != null) {
            CACHE.put(key, local);
            HistoryStore.add(context, "SCREEN · local", local.source, local.vietnamese, "", 100);
            MAIN.post(() -> callback.onSuccess(local, true));
            return;
        }

        if (!AppPrefs.isReady(context)) {
            callback.onError("Chưa cấu hình AI cho dịch màn hình.");
            return;
        }

        EXEC.execute(() -> {
            try {
                ScreenTranslationResult r = ApiClient.translateScreen(context, text);
                CACHE.put(key, r);
                HistoryStore.add(context, "SCREEN 中→VI", r.source, r.vietnamese, "", 0);
                MAIN.post(() -> callback.onSuccess(r, false));
            } catch (Exception e) {
                MAIN.post(() -> callback.onError(safeMessage(e)));
            }
        });
    }

    private static String compact(String s) {
        if (s == null) return "";
        String t = s.trim().replaceAll("[ \\t]+", " ");
        if (t.length() > 6000) t = t.substring(0, 6000);
        return t;
    }

    private static String safeMessage(Exception e) {
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }
}
