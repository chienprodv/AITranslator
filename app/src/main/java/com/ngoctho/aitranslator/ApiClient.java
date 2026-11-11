package com.ngoctho.aitranslator;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ApiClient {
    private ApiClient() {}

    public static TranslationResult translate(Context context, String source) throws Exception {
        String text = requireSource(source);
        String provider = AppPrefs.provider(context);
        if (AppPrefs.isDemo(provider)) {
            throw new IllegalArgumentException(
                    "Chế độ DEMO chỉ dịch các câu mẫu cục bộ. Hãy chọn DeepSeek/OpenAI hoặc nhập câu mẫu demo.");
        }

        String raw = call(context, "vi_zh", text, "", 360);
        JSONObject obj = parseJsonObject(raw);
        String intent = obj.optString("intent_vi", text);
        String zh = obj.optString("zh", "").trim();
        String back = obj.optString("back_vi", "").trim();
        int score = clampScore(obj.optInt("score", 0));
        String warning = obj.optString("warning", "").trim();
        if (zh.isEmpty() || back.isEmpty()) {
            throw new IllegalStateException("AI trả về thiếu bản dịch hoặc dịch ngược.");
        }
        return new TranslationResult(text, intent, zh, back, score, warning);
    }

    public static ScreenTranslationResult translateScreen(Context context, String source) throws Exception {
        String text = requireSource(source);
        String provider = AppPrefs.provider(context);
        if (AppPrefs.isDemo(provider)) {
            return new ScreenTranslationResult(
                    text,
                    "[DEMO] OCR đã đọc được chữ nhưng chế độ demo không dịch tự do. Hãy cấu hình DeepSeek/OpenAI để dịch màn hình thật.",
                    "Chế độ demo.");
        }

        String raw = call(context, "screen_zh_vi", text, "", 900);
        JSONObject obj = parseJsonObject(raw);
        String vi = obj.optString("vi", "").trim();
        String warning = obj.optString("warning", "").trim();
        if (vi.isEmpty()) throw new IllegalStateException("AI trả về thiếu bản dịch tiếng Việt.");
        return new ScreenTranslationResult(text, vi, warning);
    }

    public static BiTranslationResult translateDirectional(
            Context context,
            String source,
            String direction) throws Exception {
        String text = requireSource(source);
        String dir = DirectionalTranslationEngine.ZH_TO_VI.equals(direction)
                ? DirectionalTranslationEngine.ZH_TO_VI
                : DirectionalTranslationEngine.VI_TO_ZH;

        String provider = AppPrefs.provider(context);
        if (AppPrefs.isDemo(provider)) {
            throw new IllegalArgumentException(
                    "Chế độ DEMO chỉ hỗ trợ một số câu mẫu giọng nói. Hãy cấu hình AI để dịch tự do.");
        }

        String raw = call(context, "voice", text, dir, 420);
        JSONObject obj = parseJsonObject(raw);
        String interpreted = obj.optString("interpreted_source", text).trim();
        String translated = obj.optString("translated", "").trim();
        String back = obj.optString("back_translation", "").trim();
        int score = clampScore(obj.optInt("score", 0));
        String warning = obj.optString("warning", "").trim();
        if (translated.isEmpty() || back.isEmpty()) {
            throw new IllegalStateException("AI trả về thiếu bản dịch hoặc dịch ngược.");
        }
        return new BiTranslationResult(text, interpreted, translated, back, score, warning, dir);
    }

    private static String call(
            Context context,
            String task,
            String source,
            String direction,
            int maxTokens) throws Exception {
        String provider = AppPrefs.provider(context);
        String upstream = AppPrefs.upstreamProvider(provider);
        String model = AppPrefs.model(context, provider);
        String businessContext = AppPrefs.businessContext(context);
        String glossary = AppPrefs.glossary(context);
        String prompt = promptFor(task, direction, businessContext, glossary);

        if (!AppPrefs.isReady(context)) throw new IllegalArgumentException("Chưa cấu hình API/proxy.");
        acquire(context);
        boolean success = false;
        try {
            REQUEST_TICKET.set(VndBudget.reserve(context, source, prompt, maxTokens));
            String result;
            if (AppPrefs.isProxy(provider)) {
                result = callProxy(context, upstream, model, task, direction, source, businessContext, glossary);
            } else {
                String apiKey = AppPrefs.apiKey(context, provider);
                if (apiKey.isEmpty()) throw new IllegalArgumentException("Chưa nhập API key.");
                result = AppPrefs.PROVIDER_OPENAI.equals(upstream)
                        ? callOpenAI(apiKey, model, prompt, source, maxTokens, task)
                        : callDeepSeek(apiKey, model, prompt, source, maxTokens);
            }
            success = true;
            return result;
        } finally { VndBudget.warnIfLow(context); release(success); }
    }

    private static String promptFor(
            String task,
            String direction,
            String businessContext,
            String glossary) {
        String contextText;
        if (AppPrefs.CONTEXT_NEGOTIATION.equals(businessContext)) {
            contextText = "Context: supplier negotiation. Be polite, commercially natural, and do not strengthen commitments.";
        } else if (AppPrefs.CONTEXT_TECHNICAL.equals(businessContext)) {
            contextText = "Context: technical products. Preserve model numbers, interfaces, units and technical terminology exactly.";
        } else if (AppPrefs.CONTEXT_GENERAL.equals(businessContext)) {
            contextText = "Context: ordinary conversation. Keep the tone natural and neutral.";
        } else {
            contextText = "Context: purchasing from Chinese suppliers. Use natural business Chinese/Vietnamese, preserving MOQ, price, sample, delivery and payment meaning.";
        }

        String glossaryText = glossary == null || glossary.trim().isEmpty()
                ? ""
                : " Preferred glossary supplied by the user; use it only when applicable and never invent terms: " + trim(glossary, 1500) + ".";

        String common = " Preserve every number, quantity, product code, model, unit, currency, price, date, negation, condition, payment term and delivery term exactly. " +
                "Never add a promise, discount, warranty, stock status or fact that the source did not state. " + contextText + glossaryText;

        if ("screen_zh_vi".equals(task)) {
            return "You are a fast Chinese-to-Vietnamese screen translator. Input is OCR text and may contain line breaks or minor OCR errors." +
                    common +
                    " Translate only visible meaning, keep reading order when practical, and do not invent missing text. " +
                    "Return ONLY one compact valid JSON object: {\"vi\":\"...\",\"warning\":\"\"}. " +
                    "warning is empty unless OCR ambiguity may materially change meaning.";
        }

        if ("voice".equals(task)) {
            boolean zhToVi = DirectionalTranslationEngine.ZH_TO_VI.equals(direction);
            String language = zhToVi
                    ? "Source is Chinese; target is Vietnamese; independently back-translate the Vietnamese result into Chinese."
                    : "Source is Vietnamese; target is natural Simplified Chinese; independently back-translate the Chinese result into Vietnamese.";
            return "You are a very fast two-way Vietnamese-Chinese voice translator. " + language + common +
                    " Normalize only obvious speech-recognition punctuation or spacing errors without changing meaning. " +
                    "Return ONLY one compact valid JSON object: " +
                    "{\"interpreted_source\":\"...\",\"translated\":\"...\",\"back_translation\":\"...\",\"score\":0,\"warning\":\"\"}. " +
                    "score is 0-100 semantic faithfulness; warning is empty when there is no material meaning change.";
        }

        return "You are a very fast Vietnamese-to-Chinese business translator." + common +
                " First understand the intended meaning, translate it to natural Simplified Chinese, then independently back-translate the Chinese into Vietnamese for verification. " +
                "Return ONLY one compact valid JSON object: " +
                "{\"intent_vi\":\"...\",\"zh\":\"...\",\"back_vi\":\"...\",\"score\":0,\"warning\":\"\"}. " +
                "score is 0-100 semantic faithfulness; warning is empty when there is no material meaning change.";
    }

    private static String callOpenAI(
            String apiKey,
            String model,
            String systemPrompt,
            String source,
            int maxTokens,
            String task) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", blankTo(model, "gpt-6-luna"));
        body.put("instructions", systemPrompt);
        body.put("input", source);
        body.put("max_output_tokens", maxTokens);
        body.put("text", new JSONObject()
                .put("verbosity", "low")
                .put("format", openAiJsonFormat(task)));

        JSONObject response = postJson(
                "https://api.openai.com/v1/responses",
                apiKey,
                body,
                true);
        String text = extractOpenAiText(response);
        if (text.trim().isEmpty()) throw new IllegalStateException("OpenAI trả về nội dung rỗng.");
        return text;
    }

    private static JSONObject openAiJsonFormat(String task) throws Exception {
        JSONObject properties = new JSONObject();
        JSONArray required = new JSONArray();

        if ("screen_zh_vi".equals(task)) {
            properties.put("vi", new JSONObject().put("type", "string"));
            properties.put("warning", new JSONObject().put("type", "string"));
            required.put("vi").put("warning");
        } else if ("voice".equals(task)) {
            properties.put("interpreted_source", new JSONObject().put("type", "string"));
            properties.put("translated", new JSONObject().put("type", "string"));
            properties.put("back_translation", new JSONObject().put("type", "string"));
            properties.put("score", new JSONObject().put("type", "integer"));
            properties.put("warning", new JSONObject().put("type", "string"));
            required.put("interpreted_source").put("translated").put("back_translation").put("score").put("warning");
        } else {
            properties.put("intent_vi", new JSONObject().put("type", "string"));
            properties.put("zh", new JSONObject().put("type", "string"));
            properties.put("back_vi", new JSONObject().put("type", "string"));
            properties.put("score", new JSONObject().put("type", "integer"));
            properties.put("warning", new JSONObject().put("type", "string"));
            required.put("intent_vi").put("zh").put("back_vi").put("score").put("warning");
        }

        JSONObject schema = new JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", required)
                .put("additionalProperties", false);

        return new JSONObject()
                .put("type", "json_schema")
                .put("name", "translation_result")
                .put("strict", true)
                .put("schema", schema);
    }

    private static String extractOpenAiText(JSONObject response) {
        String direct = response.optString("output_text", "");
        if (!direct.isEmpty()) return direct;
        JSONArray output = response.optJSONArray("output");
        if (output != null) {
            StringBuilder all = new StringBuilder();
            for (int i = 0; i < output.length(); i++) {
                JSONObject item = output.optJSONObject(i);
                if (item == null) continue;
                JSONArray content = item.optJSONArray("content");
                if (content == null) continue;
                for (int j = 0; j < content.length(); j++) {
                    JSONObject part = content.optJSONObject(j);
                    if (part == null) continue;
                    String text = part.optString("text", "");
                    if (!text.isEmpty()) all.append(text);
                }
            }
            return all.toString();
        }
        return "";
    }

    private static String callDeepSeek(
            String apiKey,
            String model,
            String systemPrompt,
            String source,
            int maxTokens) throws Exception {
        for (int attempt = 0; attempt < 1; attempt++) {
            JSONObject body = new JSONObject();
            body.put("model", blankTo(model, "deepseek-flash"));
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
            messages.put(new JSONObject().put("role", "user").put("content", source));
            body.put("messages", messages);
            body.put("thinking", new JSONObject().put("type", "disabled"));
            body.put("max_tokens", maxTokens);
            body.put("response_format", new JSONObject().put("type", "json_object"));

            JSONObject response = postJson(
                    "https://api.deepseek.com/chat/completions",
                    apiKey,
                    body,
                    true);
            JSONArray choices = response.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject choice = choices.optJSONObject(0);
                JSONObject msg = choice == null ? null : choice.optJSONObject("message");
                String content = msg == null ? "" : msg.optString("content", "");
                if (!content.trim().isEmpty()) return content;
            }
        }
        throw new IllegalStateException("DeepSeek trả về nội dung rỗng. Không tự thử lại để tránh phát sinh phí.");
    }

    private static String callProxy(
            Context context,
            String upstream,
            String model,
            String task,
            String direction,
            String source,
            String businessContext,
            String glossary) throws Exception {
        String base = AppPrefs.proxyUrl(context);
        if (base.isEmpty()) throw new IllegalArgumentException("Chưa nhập URL server/proxy.");
        String endpoint = base.endsWith("/translate")
                ? base
                : (base.endsWith("/") ? base + "translate" : base + "/translate");
        if (!endpoint.toLowerCase().startsWith("https://")) {
            throw new IllegalArgumentException("Proxy production phải dùng HTTPS.");
        }

        JSONObject body = new JSONObject();
        body.put("provider", upstream);
        body.put("model", model);
        body.put("task", task);
        body.put("direction", direction == null ? "" : direction);
        body.put("source", source);
        body.put("context", businessContext);
        body.put("glossary", glossary == null ? "" : glossary);

        String token = AppPrefs.proxyToken(context);
        JSONObject response = postJson(endpoint, token, body, !token.isEmpty());
        if (response.has("result")) {
            Object result = response.opt("result");
            if (result instanceof JSONObject) return result.toString();
            if (result instanceof String) return (String) result;
        }
        return response.toString();
    }

    private static JSONObject postJson(
            String endpoint,
            String bearer,
            JSONObject body,
            boolean sendAuth) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(16000);
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Accept-Encoding", "identity");
        if (sendAuth && bearer != null && !bearer.trim().isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + bearer.trim());
        }

        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }

        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String text = readAll(stream);
        conn.disconnect();

        if (code < 200 || code >= 300) {
            String detail = text;
            try {
                JSONObject err = new JSONObject(text);
                JSONObject e = err.optJSONObject("error");
                if (e != null) detail = e.optString("message", text);
                else detail = err.optString("message", text);
            } catch (Exception ignored) {}
            throw new IllegalStateException("API lỗi " + code + ": " + trim(detail, 700));
        }
        if (text.trim().isEmpty()) throw new IllegalStateException("Server trả về nội dung rỗng.");
        JSONObject response = new JSONObject(text);
        recordUsage(response);
        return response;
    }


    private static final Object SPEND_LOCK = new Object();
    private static final ArrayDeque<Long> RECENT_CALLS = new ArrayDeque<>();
    private static final ThreadLocal<Context> REQUEST_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<VndBudget.Ticket> REQUEST_TICKET = new ThreadLocal<>();
    private static boolean activeCall;
    private static int failures;
    private static long pausedUntil;

    private static SharedPreferences usagePrefs(Context context) {
        return context.getSharedPreferences("api_usage", Context.MODE_PRIVATE);
    }
    private static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()); }
    private static int bounded(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    public static int dailyLimit(Context context) { return bounded(usagePrefs(context).getInt("daily_limit", 200), 1, 10000); }
    public static int minuteLimit(Context context) { return bounded(usagePrefs(context).getInt("minute_limit", 12), 1, 60); }
    public static void setLimits(Context context, int day, int minute) {
        usagePrefs(context).edit().putInt("daily_limit", bounded(day, 1, 10000))
                .putInt("minute_limit", bounded(minute, 1, 60)).apply();
    }

    private static void acquire(Context context) {
        synchronized (SPEND_LOCK) {
            long now = SystemClock.elapsedRealtime();
            if (activeCall) throw new IllegalStateException("Đang có yêu cầu AI chạy. Chờ hoàn tất; không gửi thêm.");
            if (now < pausedUntil) throw new IllegalStateException("AI lỗi liên tiếp; tạm nghỉ 60 giây để tránh gọi lặp.");
            while (!RECENT_CALLS.isEmpty() && now - RECENT_CALLS.peekFirst() >= 60000) RECENT_CALLS.removeFirst();
            if (RECENT_CALLS.size() >= minuteLimit(context)) throw new IllegalStateException("Đã chạm giới hạn lượt gọi/phút. Chờ một lát.");
            SharedPreferences prefs = usagePrefs(context);
            String day = today();
            boolean sameDay = day.equals(prefs.getString("day", ""));
            int used = sameDay ? prefs.getInt("calls", 0) : 0;
            int limit = dailyLimit(context);
            if (used >= limit) throw new IllegalStateException("Đã hết " + limit + " lượt gọi hôm nay. Đổi giới hạn trong Cài đặt nếu cần.");
            SharedPreferences.Editor editor = prefs.edit().putString("day", day).putInt("calls", used + 1);
            if (!sameDay) editor.putLong("input_tokens", 0).putLong("output_tokens", 0).putInt("metered", 0);
            if ((used + 1) * 5 >= limit * 4 && !day.equals(prefs.getString("warned_day", ""))) {
                editor.putString("warned_day", day);
                Context app = context.getApplicationContext();
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(app,
                        "Còn tối đa 20% lượt gọi AI/ngày. Đây là hạn mức lượt gọi, không phải số dư tiền.", Toast.LENGTH_LONG).show());
            }
            // Save before network dispatch; failed attempts also count toward the anti-loop limit.
            if (!editor.commit()) throw new IllegalStateException("Không lưu được bộ đếm API; tạm dừng để bảo vệ hạn mức.");
            activeCall = true; RECENT_CALLS.addLast(now);
            REQUEST_CONTEXT.set(context.getApplicationContext());
        }
    }

    private static void release(boolean success) {
        synchronized (SPEND_LOCK) {
            activeCall = false;
            if (success) failures = 0;
            else if (++failures >= 3) { pausedUntil = SystemClock.elapsedRealtime() + 60000; failures = 0; }
            REQUEST_CONTEXT.remove();
            REQUEST_TICKET.remove();
        }
    }

    private static void recordUsage(JSONObject response) {
        Context context = REQUEST_CONTEXT.get();
        JSONObject usage = response.optJSONObject("usage");
        if (context == null || usage == null) return;
        if (!(usage.has("prompt_tokens") || usage.has("input_tokens"))
                || !(usage.has("completion_tokens") || usage.has("output_tokens"))) return;
        synchronized (SPEND_LOCK) {
            SharedPreferences prefs = usagePrefs(context);
            long input = Math.max(0, usage.optLong("prompt_tokens", usage.optLong("input_tokens", 0)));
            long output = Math.max(0, usage.optLong("completion_tokens", usage.optLong("output_tokens", 0)));
            VndBudget.settle(context, REQUEST_TICKET.get(), input, output);
            prefs.edit().putLong("input_tokens", prefs.getLong("input_tokens", 0) + input)
                    .putLong("output_tokens", prefs.getLong("output_tokens", 0) + output)
                    .putInt("metered", prefs.getInt("metered", 0) + 1).commit();
        }
    }

    public static String usageSummary(Context context) {
        SharedPreferences prefs = usagePrefs(context);
        boolean current = today().equals(prefs.getString("day", ""));
        int calls = current ? prefs.getInt("calls", 0) : 0;
        int limit = dailyLimit(context);
        int metered = current ? prefs.getInt("metered", 0) : 0;
        return "Hôm nay: " + calls + "/" + limit + " lượt thử API; còn " + Math.max(0, limit - calls)
                + ". Giới hạn " + minuteLimit(context) + "/phút."
                + "\nToken nhà cung cấp báo: vào " + (current ? prefs.getLong("input_tokens", 0) : 0)
                + ", ra " + (current ? prefs.getLong("output_tokens", 0) : 0) + " (" + metered + " phản hồi có usage)."
                + "\nCâu cục bộ, cache và câu đã lưu không dùng lượt API. Lượt lỗi vẫn tính vào hạn mức."
                + "\n" + VndBudget.summary(context)
                + (calls * 5 >= limit * 4 ? "\n⚠ Còn tối đa 20% lượt gọi hôm nay." : "");
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString().trim();
    }

    private static JSONObject parseJsonObject(String raw) throws Exception {
        if (raw == null) throw new IllegalArgumentException("Phản hồi AI rỗng.");
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl >= 0) s = s.substring(firstNl + 1);
            int endFence = s.lastIndexOf("```");
            if (endFence >= 0) s = s.substring(0, endFence);
            s = s.trim();
        }
        int a = s.indexOf('{');
        int b = s.lastIndexOf('}');
        if (a >= 0 && b > a) s = s.substring(a, b + 1);
        return new JSONObject(s);
    }

    private static int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private static String requireSource(String source) {
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("Chưa có nội dung để dịch.");
        }
        return trim(source.trim(), 6000);
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
