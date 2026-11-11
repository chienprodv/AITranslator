package com.ngoctho.aitranslator;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPrefs {
    private static final String FILE = "translator_settings";

    public static final String PROVIDER_DEMO = "demo";
    public static final String PROVIDER_DEEPSEEK = "deepseek";
    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_PROXY_DEEPSEEK = "proxy_deepseek";
    public static final String PROVIDER_PROXY_OPENAI = "proxy_openai";

    public static final String CONTEXT_SHOPPING = "shopping";
    public static final String CONTEXT_NEGOTIATION = "negotiation";
    public static final String CONTEXT_TECHNICAL = "technical";
    public static final String CONTEXT_GENERAL = "general";

    private AppPrefs() {}

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String provider(Context c) {
        return prefs(c).getString("provider", PROVIDER_DEMO);
    }

    public static boolean isOpenAiFamily(String provider) {
        return PROVIDER_OPENAI.equals(provider) || PROVIDER_PROXY_OPENAI.equals(provider);
    }

    public static boolean isProxy(String provider) {
        return PROVIDER_PROXY_DEEPSEEK.equals(provider) || PROVIDER_PROXY_OPENAI.equals(provider);
    }

    public static boolean isDemo(String provider) {
        return PROVIDER_DEMO.equals(provider);
    }

    public static String upstreamProvider(String provider) {
        return isOpenAiFamily(provider) ? PROVIDER_OPENAI : PROVIDER_DEEPSEEK;
    }

    public static String apiKey(Context c) {
        return apiKey(c, provider(c));
    }

    public static String apiKey(Context c, String provider) {
        String upstream = upstreamProvider(provider);
        String secretName = "api_key_" + upstream;
        String encrypted = SecretStore.get(c, secretName);
        if (!encrypted.isEmpty()) return encrypted;

        // One-time migration from MVP 0.2 plaintext SharedPreferences.
        String legacy = prefs(c).getString(secretName, "");
        if (legacy != null && !legacy.trim().isEmpty()) {
            SecretStore.put(c, secretName, legacy);
            prefs(c).edit().remove(secretName).apply();
            return legacy.trim();
        }
        return "";
    }

    public static String model(Context c) {
        return model(c, provider(c));
    }

    public static String model(Context c, String provider) {
        String upstream = upstreamProvider(provider);
        String fallback = PROVIDER_OPENAI.equals(upstream) ? "gpt-6-luna" : "deepseek-flash";
        String value = prefs(c).getString("model_" + upstream, fallback);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    public static String proxyUrl(Context c) {
        String value = prefs(c).getString("proxy_url", "");
        return value == null ? "" : value.trim();
    }

    public static String proxyToken(Context c) {
        return SecretStore.get(c, "proxy_token");
    }

    public static boolean autoTranslate(Context c) {
        return prefs(c).getBoolean("auto_translate", true);
    }

    public static void setAutoTranslate(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("auto_translate", enabled).apply();
    }

    public static int autoDelayMs(Context c) {
        int value = prefs(c).getInt("auto_delay_ms", 650);
        return Math.max(350, Math.min(1800, value));
    }

    public static String businessContext(Context c) {
        String value = prefs(c).getString("business_context", CONTEXT_SHOPPING);
        return value == null ? CONTEXT_SHOPPING : value;
    }

    public static String glossary(Context c) {
        String value = prefs(c).getString("glossary", "");
        return value == null ? "" : value.trim();
    }

    public static boolean saveHistory(Context c) {
        return prefs(c).getBoolean("save_history", true);
    }

    public static void save(
            Context c,
            String provider,
            String apiKey,
            String model,
            String proxyUrl,
            String proxyToken,
            String businessContext,
            String glossary,
            int autoDelayMs,
            boolean saveHistory) {

        String upstream = upstreamProvider(provider);
        if (!isDemo(provider) && !isProxy(provider)) {
            SecretStore.put(c, "api_key_" + upstream, apiKey);
        } else if (apiKey != null && !apiKey.trim().isEmpty()) {
            // Preserve a key entered while switching modes.
            SecretStore.put(c, "api_key_" + upstream, apiKey);
        }
        SecretStore.put(c, "proxy_token", proxyToken);

        String cleanModel = model == null || model.trim().isEmpty()
                ? (PROVIDER_OPENAI.equals(upstream) ? "gpt-6-luna" : "deepseek-flash")
                : model.trim();

        prefs(c).edit()
                .putString("provider", provider)
                .putString("model_" + upstream, cleanModel)
                .putString("proxy_url", proxyUrl == null ? "" : proxyUrl.trim())
                .putString("business_context", businessContext == null ? CONTEXT_SHOPPING : businessContext)
                .putString("glossary", glossary == null ? "" : glossary.trim())
                .putInt("auto_delay_ms", Math.max(350, Math.min(1800, autoDelayMs)))
                .putBoolean("save_history", saveHistory)
                .apply();
    }

    public static boolean isReady(Context c) {
        String p = provider(c);
        if (isDemo(p)) return true;
        if (isProxy(p)) return !proxyUrl(c).isEmpty();
        return !apiKey(c, p).isEmpty();
    }
}
