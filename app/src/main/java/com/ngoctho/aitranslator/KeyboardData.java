package com.ngoctho.aitranslator;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Device-local convenience data. Backup is an explicit allowlist, never a preferences dump. */
public final class KeyboardData {
    private KeyboardData() {}
    public static SharedPreferences prefs(Context context) { return context.getSharedPreferences("keyboard_ui", Context.MODE_PRIVATE); }
    public static String ledMode(Context context) {
        SharedPreferences p=prefs(context);
        return KeyboardLighting.mode(p.getString("led_mode",p.getBoolean("rgb_press",true)?"wave":"off"));
    }
    public static List<String> learned(Context context) {
        String data = prefs(context).getString("learned_words", "");
        return data == null || data.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(data.split("\n")));
    }
    public static void learn(Context context, String word) {
        if (!prefs(context).getBoolean("learn_words", true) || word.length() < 2 || word.length() > 32 || !word.matches("\\p{L}+")) return;
        LinkedHashSet<String> words = new LinkedHashSet<>(); words.add(word); words.addAll(learned(context));
        StringBuilder out = new StringBuilder(); int count = 0;
        for (String item : words) { if (count++ == 300) break; if (out.length() > 0) out.append('\n'); out.append(item); }
        prefs(context).edit().putString("learned_words", out.toString()).apply();
    }
    public static synchronized void pin(Context context, String text) throws Exception {
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("Clipboard chưa có chữ.");
        if (text.length() > 6000) throw new IllegalArgumentException("Chỉ ghim đoạn tối đa 6.000 ký tự.");
        JSONArray old = pins(context), next = new JSONArray();
        next.put(new JSONObject().put("text", text).put("time", System.currentTimeMillis()));
        for (int i = 0; i < old.length() && next.length() < 30; i++) {
            JSONObject item = old.optJSONObject(i); if (item != null && !text.equals(item.optString("text"))) next.put(item);
        }
        prefs(context).edit().putString("clipboard_pins", next.toString()).apply();
    }
    public static synchronized JSONArray pins(Context context) {
        JSONArray next = new JSONArray();
        try {
            JSONArray old = new JSONArray(prefs(context).getString("clipboard_pins", "[]"));
            long ttl = Math.max(1, prefs(context).getInt("clip_ttl_hours", 24)) * 3600000L;
            for (int i = 0; i < old.length(); i++) {
                JSONObject item = old.optJSONObject(i);
                if (item != null && System.currentTimeMillis() - item.optLong("time", 0) < ttl) next.put(item);
            }
            prefs(context).edit().putString("clipboard_pins", next.toString()).apply();
        } catch (Exception ignored) {}
        return next;
    }
    public static void unpin(Context context, String text) {
        JSONArray old = pins(context), next = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.optJSONObject(i); if (item != null && !text.equals(item.optString("text"))) next.put(item);
        }
        prefs(context).edit().putString("clipboard_pins", next.toString()).apply();
    }
    public static final String DEFAULT_TEMPLATES =
            "gia|Hỏi giá|Nếu tôi lấy [số lượng] cái thì giá tốt nhất là bao nhiêu?\n"
            + "kho|Tồn kho|Mẫu [mã hàng] hiện còn [số lượng] cái sẵn kho không?\n"
            + "bh|Bảo hành|Sản phẩm được bảo hành [số tháng] tháng phải không?\n"
            + "ship|Vận chuyển|Gửi đến [địa chỉ kho], tổng phí vận chuyển là bao nhiêu?\n"
            + "anh|Kiểm hàng|Vui lòng gửi ảnh thật và video kiểm tra mẫu [mã hàng].\n"
            + "giam|Đàm phán|Nếu đặt [số lượng] cái, bạn có thể giảm còn [giá] mỗi cái không?\n"
            + "loi|Khiếu nại|Đơn [mã đơn] có [số lượng] sản phẩm bị lỗi [mô tả]. Vui lòng kiểm tra giúp.\n"
            + "hoan|Đổi trả|Bạn có thể đổi hàng hoặc hoàn lại [số tiền] cho phần bị lỗi không?\n"
            + "ck|Thanh toán|Vui lòng xác nhận số tiền [số tiền] và điều kiện thanh toán [điều kiện].\n"
            + "camon|Chat|Cảm ơn bạn. Tôi sẽ kiểm tra lại và phản hồi sau.";
    public static JSONArray templates(Context context) {
        JSONArray result = new JSONArray();
        String lines = prefs(context).getString("templates", DEFAULT_TEMPLATES);
        for (String line : (lines == null ? "" : lines).split("\n")) {
            String[] fields = line.split("\\|", 3);
            if (fields.length != 3 || !fields[0].trim().matches("[a-zA-Z0-9_-]{1,24}") || fields[2].trim().isEmpty()) continue;
            try { result.put(new JSONObject().put("shortcut", fields[0].trim()).put("category", fields[1].trim())
                    .put("source", fields[2].trim().substring(0, Math.min(2000, fields[2].trim().length())))); } catch (Exception ignored) {}
            if (result.length() == 100) break;
        }
        return result;
    }
    public static String shortcut(Context context, String value) {
        JSONArray templates = templates(context);
        for (int i = 0; i < templates.length(); i++) {
            JSONObject item = templates.optJSONObject(i);
            if (item != null && value.equals("/" + item.optString("shortcut"))) return item.optString("source");
        }
        return null;
    }
    public static String styleKey(Context context) {
        SharedPreferences prefs = prefs(context);
        return prefs.getString("tone", "polite") + "|" + prefs.getString("lock_terms", "") + "|"
                + prefs.getString("alternative_source", "") + "|" + prefs.getString("alternative_reference", "");
    }
    private static final Set<String> UI_KEYS = new HashSet<>(Arrays.asList(
            "english", "theme", "one_hand", "key_height", "key_font", "haptic", "key_sound", "suggestions", "autocorrect",
            "swipe", "learn_words", "learned_words", "templates", "clip_ttl_hours", "history_ttl_days", "auto_favorite", "voice_review", "tone", "lock_terms",
            "bottom_gap", "three_colors", "color_left", "color_middle", "color_right", "rgb_press",
            "led_mode", "led_brightness", "led_speed", "led_reverse", "led_color", "favorite_emoji_v2"));
    private static final Set<String> APP_KEYS = new HashSet<>(Arrays.asList("provider", "model_deepseek", "model_openai", "business_context",
            "glossary", "auto_translate", "auto_delay_ms", "save_history"));
    private static JSONObject copy(SharedPreferences prefs, Set<String> keys) throws Exception {
        JSONObject data = new JSONObject();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) if (keys.contains(entry.getKey())) data.put(entry.getKey(), entry.getValue());
        return data;
    }
    public static String backup(Context context, boolean includeClipboard) throws Exception {
        JSONObject backup = new JSONObject().put("format", "ngoctho-keyboard-1").put("created", System.currentTimeMillis())
                .put("keyboard", copy(prefs(context), UI_KEYS)).put("app", copy(AppPrefs.prefs(context), APP_KEYS))
                .put("phrases", HistoryStore.savedPhrases(context));
        if (includeClipboard) backup.put("clipboard", pins(context));
        return backup.toString(2);
    }
    private static void restorePrefs(JSONObject data, SharedPreferences prefs, Set<String> allowed) throws Exception {
        if (data == null) return;
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : allowed) {
            if (!data.has(key)) continue;
            Object value = data.get(key);
            boolean booleanKey = key.matches("english|haptic|key_sound|suggestions|autocorrect|swipe|learn_words|auto_favorite|voice_review|auto_translate|save_history|three_colors|rgb_press|led_reverse");
            boolean numericKey = key.matches("key_height|key_font|clip_ttl_hours|history_ttl_days|auto_delay_ms|bottom_gap|led_brightness|led_speed");
            if (value instanceof Boolean && booleanKey) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Number && numericKey) {
                int n = ((Number) value).intValue();
                if (key.equals("key_height")) n = Math.max(38, Math.min(62, n));
                else if (key.equals("key_font")) n = Math.max(80, Math.min(125, n));
                else if (key.equals("auto_delay_ms")) n = Math.max(350, Math.min(1800, n));
                else if (key.equals("bottom_gap")) n = KeyboardLighting.bottomGap(n);
                else if (key.equals("led_brightness")) n = KeyboardLighting.brightness(n);
                else if (key.equals("led_speed")) n = KeyboardLighting.speed(n);
                else n = Math.max(1, Math.min(365, n));
                editor.putInt(key, n);
            } else if (value instanceof String && !booleanKey && !numericKey && ((String) value).length() <= 100000)
                editor.putString(key,key.equals("led_mode")?KeyboardLighting.mode((String)value):(String)value);
        }
        if (!editor.commit()) throw new IllegalStateException("Không lưu được cấu hình khôi phục.");
    }
    public static void restore(Context context, String text, boolean includeClipboard) throws Exception {
        if (text.length() > 2000000) throw new IllegalArgumentException("Tệp sao lưu quá lớn.");
        JSONObject backup = new JSONObject(text);
        if (!backup.optString("format").equals("ngoctho-keyboard-1")) throw new IllegalArgumentException("Không đúng định dạng sao lưu bàn phím.");
        restorePrefs(backup.optJSONObject("keyboard"), prefs(context), UI_KEYS);
        restorePrefs(backup.optJSONObject("app"), AppPrefs.prefs(context), APP_KEYS);
        JSONArray phrases = backup.optJSONArray("phrases");
        if (phrases != null) for (int i = Math.min(phrases.length(), 100) - 1; i >= 0; i--) {
            JSONObject item = phrases.optJSONObject(i); if (item != null) HistoryStore.savePhrase(context, item.optString("source"), item.optString("target"), item.optString("back"));
        }
        JSONArray pins = backup.optJSONArray("clipboard");
        if (includeClipboard && pins != null) for (int i = Math.min(pins.length(), 30) - 1; i >= 0; i--) {
            JSONObject item = pins.optJSONObject(i); if (item != null) pin(context, item.optString("text"));
        }
    }
}
