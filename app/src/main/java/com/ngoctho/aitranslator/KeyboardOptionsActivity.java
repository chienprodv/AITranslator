package com.ngoctho.aitranslator;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class KeyboardOptionsActivity extends Activity {
    private LinearLayout root;
    private KeyboardSurface preview;
    private TextView previewText, usage, balance;
    private CheckBox includeClipboard;
    private int sampleSymbols;
    private boolean sampleShift;
    private android.content.SharedPreferences prefs() { return KeyboardData.prefs(this); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this); root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(24)); scroll.addView(root); UiInsets.apply(scroll); setContentView(scroll);
        title("XinayuAITranslator 1.3.0");
        text("Copyright by Nguyen xuan chien");
        text("Bố cục theo ảnh Samsung đã cung cấp. Chạm vào bản xem trước để thử vùng phím. Thay đổi áp dụng khi mở lại bàn phím.");
        previewText = text("Xem trước phím vừa bấm: —");
        preview = new KeyboardSurface(this, new KeyboardSurface.Listener() {
            public void key(String code) {
                previewText.setText("Xem trước: " + code);
                if (code.equals("SHIFT")) sampleShift = !sampleShift;
                if (code.equals("SYMBOLS")) sampleSymbols = sampleSymbols == 0 ? 1 : 0;
                refreshPreview();
            }
            public void cursor(int delta) { previewText.setText("Di chuyển con trỏ: " + delta); }
            public void gesture(java.util.List<KeyboardModel.Point> points) { previewText.setText("Gõ vuốt: " + WordTools.swipe(points, KeyboardData.learned(KeyboardOptionsActivity.this))); }
            public void longKey(String code) { previewText.setText("Giữ: " + code); }
        });
        root.addView(preview); refreshPreview();
        slider("Chiều cao hàng phím", "key_height", 38, 62, 48);
        slider("Cỡ chữ (%)", "key_font", 80, 125, 100);
        slider("Khoảng trống thêm phía dưới (dp)", "bottom_gap", 12, 72, 28);
        text("Khoảng trống này cộng thêm phía trên vùng điều hướng/nút đổi bàn phím hệ thống để tránh chạm nhầm. Mở lại bàn phím để áp dụng.");
        check("Màu ký tự chia 3 vùng trái – giữa – phải", "three_colors", true);
        String[] colorNames = {"Xanh ngọc", "Tím", "Hồng", "Vàng", "Xanh lá", "Xanh dương"};
        String[] colorValues = {"cyan", "purple", "pink", "amber", "green", "blue"};
        choice("Màu ký tự vùng trái", "color_left", colorNames, colorValues, "cyan");
        choice("Màu ký tự vùng giữa", "color_middle", colorNames, colorValues, "purple");
        choice("Màu ký tự vùng phải", "color_right", colorNames, colorValues, "pink");
        title("Studio LED");
        choice("Hiệu ứng LED", "led_mode",KeyboardLighting.NAMES,KeyboardLighting.MODES,KeyboardData.ledMode(this));
        slider("Độ sáng LED (%)", "led_brightness",0,100,70);
        slider("Tốc độ LED (1 chậm – 5 nhanh)","led_speed",1,5,3);
        check("Đảo chiều hiệu ứng chuyển động", "led_reverse",false);
        choice("Màu cho Sáng một màu / Nhịp thở", "led_color",colorNames,colorValues,"cyan");
        text("Sóng, thác, cực quang… chạy liên tục khi bàn phím hiện. Chạm sáng và Gợn sóng phản hồi vị trí gõ. Nền tối cho viền LED rõ hơn. Tắt LED hoặc đặt sáng 0% để giữ màu ký tự cơ bản. Hoạt ảnh dừng khi ẩn bàn phím và tôn trọng cài đặt hoạt ảnh Android.");
        choice("Giao diện", "theme", new String[]{"Theo hệ thống", "Sáng", "Tối"}, new String[]{"system", "light", "dark"}, "system");
        choice("Chế độ một tay", "one_hand", new String[]{"Bình thường", "Tay trái", "Tay phải"}, new String[]{"off", "left", "right"}, "off");
        check("Rung khi bấm phím", "haptic", true); check("Âm thanh phím", "key_sound", false);
        check("Gợi ý từ cục bộ", "suggestions", true);
        check("Tự sửa lỗi rõ ràng khi bấm cách (có Hoàn tác)", "autocorrect", false);
        check("Gõ vuốt: nhận dạng bằng từ điển cục bộ", "swipe", false);
        text("Gõ vuốt và gợi ý dùng bộ từ cục bộ nhỏ cùng các từ đã học; có thể chọn lại từ gợi ý hoặc Hoàn tác. Gõ vuốt mặc định tắt để tránh nhầm thao tác trượt tay.");
        check("Học từ đã gõ trên máy", "learn_words", true);
        button("XÓA CÁC TỪ ĐÃ HỌC", () -> { prefs().edit().remove("learned_words").apply(); toast("Đã xóa từ đã học."); });

        title("Dịch và kiểm tra");
        choice("Giọng văn dịch", "tone", new String[]{"Lịch sự", "Thân mật", "Ngắn gọn", "Cứng rắn, chuyên nghiệp"},
                new String[]{"polite", "friendly", "concise", "firm"}, "polite");
        EditText locks = field("Tên/mã cần giữ nguyên; ngăn bằng dấu ; hoặc xuống dòng", prefs().getString("lock_terms", ""), true);
        button("LƯU THUẬT NGỮ KHÓA", () -> { prefs().edit().putString("lock_terms", locks.getText().toString()).apply(); toast("Đã lưu thuật ngữ khóa."); });
        check("Xem và sửa nhận dạng giọng nói trước khi dịch", "voice_review", true);
        check("Tự lưu bản dịch thành công vào câu yêu thích", "auto_favorite", false);
        button("CẤU HÌNH AI, API KEY VÀ HẠN MỨC VNĐ", () -> startActivity(new Intent(this, MainActivity.class)));
        button("BẬT TIẾT KIỆM: TẮT AUTO DỊCH", () -> { AppPrefs.setAutoTranslate(this, false); toast("Chỉ dịch khi bấm DỊCH; câu lưu và cache vẫn dùng được."); });
        balance = text("Kiểm tra key và số dư DeepSeek khi đã lưu API key. Không gửi câu dịch trong bước kiểm tra này.");
        Button verify = button("KIỂM TRA KEY / SỐ DƯ DEEPSEEK", () -> {});
        verify.setOnClickListener(v -> {
            verify.setEnabled(false); balance.setText("Đang kiểm tra DeepSeek…");
            ApiClient.checkDeepSeekAccount(this, message -> { if (!isDestroyed()) { verify.setEnabled(true); balance.setText(message); } });
        });

        title("Bộ câu và phím tắt");
        text("Mỗi dòng: mã|nhóm|câu. Gõ /mã rồi cách để chèn câu mẫu. Các ô [số lượng], [giá] được chọn để điền trực tiếp trong ô chat.");
        EditText templates = field("mã|nhóm|câu", prefs().getString("templates", KeyboardData.DEFAULT_TEMPLATES), true);
        templates.setMinLines(6);
        button("LƯU CÂU MẪU / PHÍM TẮT", () -> {
            if (templates.getText().length() > 100000) { toast("Bộ câu quá lớn."); return; }
            prefs().edit().putString("templates", templates.getText().toString()).apply();
            toast("Đã lưu " + KeyboardData.templates(this).length() + " câu mẫu hợp lệ.");
        });

        title("Riêng tư và dữ liệu");
        check("Riêng tư thủ công: tắt AI, micro và truy cập bộ nhớ từ bàn phím", "private_mode", false);
        number("Tự xóa clipboard ghim sau số giờ", "clip_ttl_hours", 24, 1, 365);
        number("Tự xóa lịch sử sau số ngày (câu yêu thích giữ riêng)", "history_ttl_days", 30, 1, 365);
        button("XÓA CLIPBOARD GHIM", () -> { prefs().edit().remove("clipboard_pins").apply(); toast("Đã xóa clipboard ghim của bàn phím."); });
        text("Clipboard chỉ được đọc khi bấm Dán hoặc Ghim. Ô mật khẩu và ô được ứng dụng đánh dấu riêng tư không dùng AI/học từ. Tệp sao lưu không chứa API key, token proxy, clipboard hệ thống hoặc bộ đếm tiền.");
        includeClipboard = new CheckBox(this); includeClipboard.setText("Bao gồm clipboard ghim khi xuất/nhập tệp (có thể chứa thông tin riêng)"); root.addView(includeClipboard);
        button("XUẤT SAO LƯU JSON", () -> chooseDocument(true));
        button("NHẬP / GỘP SAO LƯU JSON", () -> chooseDocument(false));

        title("Sử dụng API theo ngày");
        usage = text(ApiClient.usageSummary(this));
        root.addView(new UsageChart(this), new LinearLayout.LayoutParams(-1, dp(150)));
        JSONArray records = ApiClient.usageRecords(this);
        StringBuilder breakdown = new StringBuilder();
        for (int i = 0; i < Math.min(14, records.length()); i++) {
            JSONObject item = records.optJSONObject(i); if (item == null) continue;
            breakdown.append(item.optString("day")).append(": bàn phím ").append((item.optInt("vi_zh") + item.optInt("keyboard_reverse")))
                    .append(", giọng nói ").append(item.optInt("voice")).append(", màn hình ").append(item.optInt("screen_zh_vi")+item.optInt("screen_layout_zh_vi"))
                    .append(" · token ").append(item.optLong("input") + item.optLong("output")).append('\n');
        }
        text(breakdown.length() == 0 ? "Chưa có dữ liệu API mới để vẽ biểu đồ." : breakdown.toString());
        button("LÀM MỚI THỐNG KÊ", () -> recreate());
        button("QUAY LẠI", this::finish);
    }
    @Override protected void onResume() { super.onResume(); if (usage != null) usage.setText(ApiClient.usageSummary(this)); }
    private void refreshPreview() { if (preview != null) preview.state(sampleSymbols, sampleShift, false, false, false, false); }
    private void title(String value) { TextView view = text(value); view.setTextSize(21); view.setTypeface(null, Typeface.BOLD); view.setPadding(0, dp(16), 0, dp(6)); }
    private TextView text(String value) { TextView view = new TextView(this); view.setText(value); view.setTextSize(14); view.setPadding(0, dp(4), 0, dp(4)); root.addView(view); return view; }
    private Button button(String name, Runnable action) { Button button = new Button(this); button.setText(name); button.setAllCaps(false); button.setOnClickListener(v -> action.run()); root.addView(button); return button; }
    private EditText field(String hint, String value, boolean multiline) {
        EditText field = new EditText(this); field.setHint(hint); field.setText(value);
        field.setInputType(InputType.TYPE_CLASS_TEXT | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        field.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING); root.addView(field); return field;
    }
    private void check(String name, String key, boolean fallback) {
        CheckBox view = new CheckBox(this); view.setText(name); view.setChecked(prefs().getBoolean(key, fallback));
        view.setOnCheckedChangeListener((button, checked) -> { prefs().edit().putBoolean(key, checked).apply(); refreshPreview(); }); root.addView(view);
    }
    private void slider(String title, String key, int min, int max, int fallback) {
        TextView label = text(title + ": " + prefs().getInt(key, fallback));
        SeekBar bar = new SeekBar(this); bar.setMax(max - min); bar.setProgress(prefs().getInt(key, fallback) - min);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int value, boolean user) { if (user) { prefs().edit().putInt(key, min + value).apply(); label.setText(title + ": " + (min + value)); refreshPreview(); } }
            public void onStartTrackingTouch(SeekBar b) {} public void onStopTrackingTouch(SeekBar b) {}
        }); root.addView(bar);
    }
    private void choice(String title, String key, String[] names, String[] values, String fallback) {
        text(title); Spinner spinner = new Spinner(this); spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        String selected = prefs().getString(key, fallback); int at = 0; for (int i = 0; i < values.length; i++) if (values[i].equals(selected)) at = i;
        spinner.setSelection(at); spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { prefs().edit().putString(key, values[position]).apply(); refreshPreview(); }
            public void onNothingSelected(AdapterView<?> parent) {}
        }); root.addView(spinner);
    }
    private void number(String title, String key, int fallback, int min, int max) {
        EditText field = field(title, String.valueOf(prefs().getInt(key, fallback)), false); field.setInputType(InputType.TYPE_CLASS_NUMBER);
        button("LƯU: " + title, () -> {
            try { int n = Integer.parseInt(field.getText().toString()); if (n < min || n > max) throw new IllegalArgumentException(); prefs().edit().putInt(key, n).apply(); toast("Đã lưu."); }
            catch (Exception e) { toast("Nhập từ " + min + " đến " + max + '.'); }
        });
    }
    private void chooseDocument(boolean save) {
        Intent request = new Intent(save ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT);
        request.addCategory(Intent.CATEGORY_OPENABLE); request.setType("application/json");
        if (save) request.putExtra(Intent.EXTRA_TITLE, "XinayuAITranslator-keyboard-backup.json");
        try { startActivityForResult(request, save ? 801 : 802); } catch (Exception e) { toast("Máy chưa có trình chọn tệp."); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        try {
            if (request == 801) {
                try (OutputStream stream = getContentResolver().openOutputStream(data.getData(), "wt")) {
                    if (stream == null) throw new IOException("Không mở được tệp ghi.");
                    stream.write(KeyboardData.backup(this, includeClipboard.isChecked()).getBytes(StandardCharsets.UTF_8));
                }
                toast("Đã sao lưu; không bao gồm API key hoặc token.");
            } else if (request == 802) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream stream = getContentResolver().openInputStream(data.getData())) {
                    if (stream == null) throw new IOException("Không đọc được tệp.");
                    byte[] buffer = new byte[4096]; int n;
                    while ((n = stream.read(buffer)) != -1) { if (bytes.size() + n > 2000000) throw new IOException("Tệp quá lớn."); bytes.write(buffer, 0, n); }
                }
                KeyboardData.restore(this, bytes.toString("UTF-8"), includeClipboard.isChecked()); toast("Đã gộp dữ liệu; key và hạn mức đã dùng được giữ nguyên."); recreate();
            }
        } catch (Exception e) { toast("Lỗi sao lưu: " + e.getMessage()); }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private static class UsageChart extends View {
        private final JSONArray records; private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        UsageChart(Context context) { super(context); records = ApiClient.usageRecords(context); setContentDescription("Biểu đồ lượt thử API trong 14 ngày có sử dụng gần nhất"); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); int count = Math.min(14, records.length()), max = 1;
            for (int i = 0; i < count; i++) max = Math.max(max, records.optJSONObject(i).optInt("calls"));
            float cell = getWidth() / (float) Math.max(1, count), baseline = getHeight() - 25;
            paint.setTextSize(22); paint.setColor(Color.DKGRAY); canvas.drawText("Lượt thử API / ngày", 8, 25, paint);
            for (int i = 0; i < count; i++) {
                JSONObject item = records.optJSONObject(count - 1 - i); int calls = item.optInt("calls");
                float top = baseline - (baseline - 45) * calls / max;
                paint.setColor(0xff3b82f6); canvas.drawRect(i * cell + 5, top, (i + 1) * cell - 5, baseline, paint);
                paint.setColor(Color.DKGRAY); paint.setTextSize(18); String day = item.optString("day");
                canvas.drawText(day.length() > 2 ? day.substring(day.length() - 2) : day, i * cell + 5, baseline + 20, paint);
            }
        }
    }
}
