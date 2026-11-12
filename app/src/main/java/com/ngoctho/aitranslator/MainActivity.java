package com.ngoctho.aitranslator;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_SCREEN_CAPTURE = 501;
    private static final int REQ_NOTIFICATIONS = 503;

    private Spinner provider;
    private Spinner contextMode;
    private EditText apiKey;
    private EditText model;
    private EditText proxyUrl;
    private EditText proxyToken;
    private EditText glossary;
    private EditText autoDelay;
    private CheckBox saveHistory;
    private EditText testInput;
    private TextView testOutput;
    private TextView configHint;
    private TextView screenStatus;
    private boolean pendingScreenStart;
    private TextView usageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View ui = buildUi();
        UiInsets.apply(ui);
        setContentView(ui);
        loadSettings();
        PrivacyConsent.onLaunch(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateScreenStatus();
        if (usageView != null) usageView.setText(ApiClient.usageSummary(this));
        if (pendingScreenStart && Settings.canDrawOverlays(this)) {
            pendingScreenStart = false;
            requestScreenCapture();
        }
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(40));
        scroll.addView(root);

        root.addView(text("XinayuAITranslator", 25, true));
        root.addView(text("Copyright by Nguyen xuan chien", 13, false));
        Button privacy=button("QUYỀN RIÊNG TƯ / ĐỒNG Ý DỊCH TRỰC TUYẾN");
        privacy.setOnClickListener(v->PrivacyConsent.show(this));root.addView(privacy);
        Button keyboardSettings = button("BÀN PHÍM: BỐ CỤC, CÂU MẪU, RIÊNG TƯ, SAO LƯU");
        keyboardSettings.setOnClickListener(v -> startActivity(new Intent(this, KeyboardOptionsActivity.class)));
        root.addView(keyboardSettings);
        root.addView(text(
                "Bản 1.4.0 — Studio LED với 16 hiệu ứng, độ sáng và tốc độ; dịch Việt ↔ Trung, mic, emoji, câu đã lưu và hạn mức VNĐ. Giữ 💡 trên bàn phím để đổi hiệu ứng nhanh.",
                14, false));

        addSpacer(root, 16);
        root.addView(text("1. Chế độ AI", 19, true));

        provider = new Spinner(this);
        String[] providers = {
                "DEMO — không cần API key",
                "DeepSeek trực tiếp",
                "OpenAI trực tiếp",
                "Server/Proxy → DeepSeek",
                "Server/Proxy → OpenAI"
        };
        provider.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, providers));
        root.addView(provider);

        configHint = text("", 13, false);
        root.addView(configHint);

        model = field("Model", false);
        root.addView(model);

        apiKey = field("API key trực tiếp (được mã hóa bằng Android Keystore)", true);
        root.addView(apiKey);

        proxyUrl = field("Proxy HTTPS, ví dụ https://translator.example.workers.dev", false);
        proxyUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(proxyUrl);

        proxyToken = field("Token bảo vệ proxy (nếu có)", true);
        root.addView(proxyToken);

        provider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private boolean first = true;
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String p = providerValue(position);
                if (!first) {
                    apiKey.setText(AppPrefs.apiKey(MainActivity.this, p));
                    model.setText(AppPrefs.model(MainActivity.this, p));
                }
                first = false;
                updateProviderUi(p);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        addSpacer(root, 14);
        root.addView(text("2. Ngữ cảnh dịch", 19, true));
        contextMode = new Spinner(this);
        String[] contexts = {"Mua hàng Trung Quốc", "Đàm phán", "Kỹ thuật", "Chat thông thường"};
        contextMode.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, contexts));
        root.addView(contextMode);

        glossary = field("Từ điển riêng, ví dụ: đầu ghi=NVR; máy chấm công=考勤机; bảo hành=保修", false);
        glossary.setMinLines(2);
        root.addView(glossary);

        autoDelay = field("Độ trễ tự dịch bàn phím (ms), khuyến nghị 650", false);
        autoDelay.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(autoDelay);

        saveHistory = new CheckBox(this);
        saveHistory.setText("Lưu lịch sử dịch trên máy");
        root.addView(saveHistory);

        Button save = button("LƯU CẤU HÌNH");
        save.setOnClickListener(v -> saveSettings());
        root.addView(save);

        addSpacer(root, 18);
        root.addView(text("Bảo vệ API và sử dụng", 19, true));
        usageView = text(ApiClient.usageSummary(this), 13, false);
        root.addView(usageView);
        EditText daily = field("Giới hạn lượt gọi/ngày (1–10000)", false);
        daily.setInputType(InputType.TYPE_CLASS_NUMBER); daily.setText(String.valueOf(ApiClient.dailyLimit(this)));
        root.addView(daily);
        EditText minute = field("Giới hạn lượt gọi/phút (1–60)", false);
        minute.setInputType(InputType.TYPE_CLASS_NUMBER); minute.setText(String.valueOf(ApiClient.minuteLimit(this)));
        root.addView(minute);
        Button limits = button("LƯU GIỚI HẠN / CẬP NHẬT SỬ DỤNG");
        limits.setOnClickListener(v -> {
            try { ApiClient.setLimits(this, Integer.parseInt(daily.getText().toString()), Integer.parseInt(minute.getText().toString())); }
            catch (NumberFormatException e) { Toast.makeText(this, "Nhập số lượt gọi hợp lệ.", Toast.LENGTH_SHORT).show(); return; }
            daily.setText(String.valueOf(ApiClient.dailyLimit(this))); minute.setText(String.valueOf(ApiClient.minuteLimit(this)));
            usageView.setText(ApiClient.usageSummary(this));
        });
        root.addView(limits);
        root.addView(text("Hạn mức tiền VNĐ (ước tính)", 17, true));
        EditText budgetAmount = field("Hạn mức VNĐ, ví dụ 5000 hoặc 10000", false);
        budgetAmount.setInputType(InputType.TYPE_CLASS_NUMBER);
        budgetAmount.setText(String.valueOf(VndBudget.limit(this))); root.addView(budgetAmount);
        LinearLayout presets = new LinearLayout(this);
        for (int value : new int[]{5000, 10000, 20000, 50000}) {
            Button preset = button(String.format(new java.util.Locale("vi", "VN"), "%,dđ", value));
            preset.setOnClickListener(v -> budgetAmount.setText(String.valueOf(value)));
            presets.addView(preset, new LinearLayout.LayoutParams(0, dp(44), 1));
        }
        root.addView(presets);
        Spinner budgetPeriod = new Spinner(this);
        budgetPeriod.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Mỗi tháng", "Mỗi ngày"}));
        budgetPeriod.setSelection(VndBudget.monthly(this) ? 0 : 1); root.addView(budgetPeriod);
        EditText budgetFx = field("Tỷ giá quy đổi VNĐ cho 1 USD", false);
        budgetFx.setInputType(InputType.TYPE_CLASS_NUMBER); budgetFx.setText(String.valueOf(VndBudget.exchangeRate(this))); root.addView(budgetFx);
        EditText priceIn = field("USD / 1 triệu token đầu vào", false);
        EditText priceOut = field("USD / 1 triệu token đầu ra", false);
        priceIn.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        priceOut.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        double inPrice = VndBudget.inputRate(this), outPrice = VndBudget.outputRate(this);
        priceIn.setText(inPrice > 0 ? String.valueOf(inPrice) : "0.3");
        priceOut.setText(outPrice > 0 ? String.valueOf(outPrice) : "1.2");
        root.addView(priceIn); root.addView(priceOut);
        root.addView(text("Đơn giá tham khảo DeepSeek Flash: vào $0.30, ra $1.20 / triệu token (giờ cao điểm, chưa trừ cache); tỷ giá 25.820đ/USD, kiểm tra 11/11/2026. Nếu dùng model khác, cập nhật đúng đơn giá trước khi lưu. App dự trù trước mỗi lượt, đối chiếu token khi có usage, cảnh báo còn 20% và chặn khi không đủ hạn mức. Không tự nạp tiền.", 12, false));
        Button saveBudget = button("LƯU HẠN MỨC VNĐ + CẤU HÌNH AI");
        saveBudget.setOnClickListener(v -> {
            try {
                long cap = Long.parseLong(budgetAmount.getText().toString().replace(".", "").replace(",", "").replace("đ", "").trim());
                long fx = Long.parseLong(budgetFx.getText().toString().replace(".", "").replace(",", "").trim());
                double inRate = Double.parseDouble(priceIn.getText().toString().replace(',', '.'));
                double outRate = Double.parseDouble(priceOut.getText().toString().replace(',', '.'));
                saveSettings();
                VndBudget.configure(this, cap, budgetPeriod.getSelectedItemPosition() == 0, fx, inRate, outRate);
                usageView.setText(ApiClient.usageSummary(this));
                Toast.makeText(this, "Đã lưu hạn mức VNĐ. Các khoản đã dùng vẫn được giữ.", Toast.LENGTH_LONG).show();
            } catch (Exception e) { Toast.makeText(this, "Không lưu được: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
        });
        root.addView(saveBudget);


        addSpacer(root, 20);
        root.addView(text("3. Bàn phím AI", 19, true));
        root.addView(text(
                "Tự dịch sau khi ngừng gõ. Câu Trung và bản dịch ngược xuất hiện trước khi bấm CHÈN 中文. Không tự gửi tin nhắn.",
                13, false));

        Button enable = button("MỞ CÀI ĐẶT BÀN PHÍM ANDROID");
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(enable);

        Button pick = button("CHỌN BÀN PHÍM XINAYU");
        pick.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.showInputMethodPicker();
        });
        root.addView(pick);

        addSpacer(root, 20);
        root.addView(text("4. Dịch màn hình", 19, true));
        root.addView(text(
                "Nhấn 译 để dịch đè chữ Trung lên ảnh chụp. Giữ xem gốc, chọn vùng hoặc chạm khối chữ để xem đủ. Đóng ảnh để cuộn trang; chỉ chữ OCR được gửi lên AI.",
                13, false));
        screenStatus = text("", 13, false);
        root.addView(screenStatus);

        Button startScreen = button("BẬT BONG BÓNG DỊCH MÀN HÌNH");
        startScreen.setOnClickListener(v -> startScreenTranslation());
        root.addView(startScreen);

        Button stopScreen = button("DỪNG DỊCH MÀN HÌNH");
        stopScreen.setOnClickListener(v -> {
            stopService(new Intent(this, ScreenTranslateService.class));
            updateScreenStatus();
            Toast.makeText(this, "Đã yêu cầu dừng dịch màn hình.", Toast.LENGTH_SHORT).show();
        });
        root.addView(stopScreen);

        if (Build.VERSION.SDK_INT >= 33) {
            Button notification = button("CHO PHÉP THÔNG BÁO DỊCH MÀN HÌNH");
            notification.setOnClickListener(v -> {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
                } else {
                    Toast.makeText(this, "Quyền thông báo đã được cấp.", Toast.LENGTH_SHORT).show();
                }
            });
            root.addView(notification);
        }

        addSpacer(root, 20);
        root.addView(text("5. Dịch giọng nói", 19, true));
        root.addView(text("Bấm 🎤 ngay trên bàn phím để nói và dịch tại ô chat. Giữ 🎤 đổi chiều Việt/Trung. Cho phép micro một lần. Bản nhận dạng phụ thuộc dịch vụ giọng nói Android của máy.", 13, false));
        Button voice = button("MỞ DỊCH GIỌNG NÓI HAI CHIỀU");
        voice.setOnClickListener(v -> startActivity(new Intent(this, VoiceTranslateActivity.class)));
        root.addView(voice);

        addSpacer(root, 20);
        root.addView(text("6. Demo / thử dịch text", 19, true));
        testInput = field("Nhập câu tiếng Việt", false);
        testInput.setMinLines(2);
        root.addView(testInput);

        Button demo = button("NẠP CÂU DEMO");
        demo.setOnClickListener(v -> testInput.setText("Nếu tôi lấy 500 cái thì giá tốt nhất là bao nhiêu?"));
        root.addView(demo);

        Button test = button("DỊCH + DỊCH NGƯỢC");
        test.setOnClickListener(v -> runTest());
        root.addView(test);

        testOutput = text("", 15, false);
        testOutput.setTextIsSelectable(true);
        root.addView(testOutput);

        Button history = button("XEM LỊCH SỬ DỊCH");
        history.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        root.addView(history);

        addSpacer(root, 18);
        root.addView(text(
                "Khuyến nghị: dùng DEMO để thử giao diện; dùng API trực tiếp cho máy cá nhân; khi phát hành cho nhiều người dùng, chọn Server/Proxy để khóa AI không nằm trên điện thoại.",
                13, false));

        return scroll;
    }

    private void updateProviderUi(String p) {
        boolean demo = AppPrefs.isDemo(p);
        boolean proxy = AppPrefs.isProxy(p);
        apiKey.setEnabled(!demo && !proxy);
        proxyUrl.setEnabled(proxy);
        proxyToken.setEnabled(proxy);
        model.setEnabled(!demo);

        if (demo) {
            configHint.setText("DEMO chạy ngay, không gọi mạng. Các câu mẫu phổ biến được dịch bằng từ điển cục bộ.");
        } else if (proxy) {
            configHint.setText("Chế độ phát hành: app chỉ gọi server HTTPS của bạn; API key OpenAI/DeepSeek nằm trên server.");
        } else {
            configHint.setText("Chế độ cá nhân: API key được mã hóa bằng Android Keystore trên thiết bị, nhưng vẫn không nên dùng để phát hành công khai.");
        }
    }

    private void startScreenTranslation() {
        if(!PrivacyConsent.allowed(this)){PrivacyConsent.show(this);return;}
        saveSettings();
        if (!Settings.canDrawOverlays(this)) {
            pendingScreenStart = true;
            Intent overlay = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(overlay);
            Toast.makeText(this,
                    "Hãy bật quyền 'Hiển thị trên ứng dụng khác', sau đó quay lại.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        requestScreenCapture();
    }

    private void requestScreenCapture() {
        if(!PrivacyConsent.allowed(this)){PrivacyConsent.show(this);return;}
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), REQ_SCREEN_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SCREEN_CAPTURE) return;
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Bạn chưa cấp quyền chụp màn hình.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent service = new Intent(this, ScreenTranslateService.class);
        service.putExtra(ScreenTranslateService.EXTRA_RESULT_CODE, resultCode);
        service.putExtra(ScreenTranslateService.EXTRA_RESULT_DATA, data);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
        else startService(service);

        Toast.makeText(this,
                "Đã bật. Nhấn 译 để dịch toàn màn hình, hoặc bấm Chọn vùng; nhấn giữ bong bóng để dừng.",
                Toast.LENGTH_LONG).show();
        updateScreenStatus();
    }

    private void updateScreenStatus() {
        if (screenStatus == null) return;
        if (!Settings.canDrawOverlays(this)) {
            screenStatus.setText("Trạng thái: chưa cấp quyền bong bóng.");
        } else if (ScreenTranslateService.running) {
            screenStatus.setText("Trạng thái: dịch màn hình đang chạy.");
        } else {
            screenStatus.setText("Trạng thái: đã có quyền bong bóng, dịch màn hình chưa chạy.");
        }
    }

    private void loadSettings() {
        String p = AppPrefs.provider(this);
        provider.setSelection(providerPosition(p));
        apiKey.setText(AppPrefs.apiKey(this, p));
        model.setText(AppPrefs.model(this, p));
        proxyUrl.setText(AppPrefs.proxyUrl(this));
        proxyToken.setText(AppPrefs.proxyToken(this));
        contextMode.setSelection(contextPosition(AppPrefs.businessContext(this)));
        glossary.setText(AppPrefs.glossary(this));
        autoDelay.setText(String.valueOf(AppPrefs.autoDelayMs(this)));
        saveHistory.setChecked(AppPrefs.saveHistory(this));
        updateProviderUi(p);
    }

    private void saveSettings() {
        String p = providerValue(provider.getSelectedItemPosition());
        int delay = 650;
        try { delay = Integer.parseInt(autoDelay.getText().toString().trim()); }
        catch (Exception ignored) {}

        AppPrefs.save(
                this,
                p,
                apiKey.getText().toString(),
                model.getText().toString(),
                proxyUrl.getText().toString(),
                proxyToken.getText().toString(),
                contextValue(contextMode.getSelectedItemPosition()),
                glossary.getText().toString(),
                delay,
                saveHistory.isChecked());
        Toast.makeText(this, "Đã lưu cấu hình.", Toast.LENGTH_SHORT).show();
    }

    private void runTest() {
        saveSettings();
        String input = testInput.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "Nhập một câu tiếng Việt để thử.", Toast.LENGTH_SHORT).show();
            return;
        }
        testOutput.setText("Đang dịch…");
        TranslationEngine.translate(this, input, new TranslationEngine.Callback() {
            @Override
            public void onSuccess(TranslationResult r, boolean fromCache) {
                String state = r.warning.isEmpty() ? "Đã có dịch ngược" : "Cần kiểm tra";
                String scoreText = r.score > 0 ? (": " + r.score + "%") : "";
                testOutput.setText(
                        "Ý của bạn là:\n" + r.intentVi +
                                "\n\n中文:\n" + r.chinese +
                                "\n\nDịch ngược:\n" + r.backVi +
                                "\n\n" + state + scoreText +
                                (r.warning.isEmpty() ? "" : "\nCảnh báo: " + r.warning) +
                                (fromCache ? "\n\n⚡ Phản hồi cục bộ/cache." : ""));
            }
            @Override
            public void onError(String message) {
                testOutput.setText("Lỗi: " + message);
            }
        });
    }

    private String providerValue(int position) {
        switch (position) {
            case 1: return AppPrefs.PROVIDER_DEEPSEEK;
            case 2: return AppPrefs.PROVIDER_OPENAI;
            case 3: return AppPrefs.PROVIDER_PROXY_DEEPSEEK;
            case 4: return AppPrefs.PROVIDER_PROXY_OPENAI;
            default: return AppPrefs.PROVIDER_DEMO;
        }
    }

    private int providerPosition(String p) {
        if (AppPrefs.PROVIDER_DEEPSEEK.equals(p)) return 1;
        if (AppPrefs.PROVIDER_OPENAI.equals(p)) return 2;
        if (AppPrefs.PROVIDER_PROXY_DEEPSEEK.equals(p)) return 3;
        if (AppPrefs.PROVIDER_PROXY_OPENAI.equals(p)) return 4;
        return 0;
    }

    private String contextValue(int position) {
        switch (position) {
            case 1: return AppPrefs.CONTEXT_NEGOTIATION;
            case 2: return AppPrefs.CONTEXT_TECHNICAL;
            case 3: return AppPrefs.CONTEXT_GENERAL;
            default: return AppPrefs.CONTEXT_SHOPPING;
        }
    }

    private int contextPosition(String value) {
        if (AppPrefs.CONTEXT_NEGOTIATION.equals(value)) return 1;
        if (AppPrefs.CONTEXT_TECHNICAL.equals(value)) return 2;
        if (AppPrefs.CONTEXT_GENERAL.equals(value)) return 3;
        return 0;
    }

    private EditText field(String hint, boolean password) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(15);
        e.setPadding(dp(10), dp(8), dp(10), dp(8));
        if (password) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        return e;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private void addSpacer(LinearLayout root, int h) {
        View v = new View(this);
        root.addView(v, new LinearLayout.LayoutParams(1, dp(h)));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
