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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View ui = buildUi();
        UiInsets.apply(ui);
        setContentView(ui);
        loadSettings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateScreenStatus();
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

        root.addView(text("Ngọc Thọ AI Translator", 25, true));
        root.addView(text(
                "Bản 1.0 — bàn phím Việt→Trung, dịch ngược bắt buộc, chọn vùng màn hình để OCR, dịch giọng nói hai chiều, cache/từ điển cục bộ và chế độ proxy an toàn.",
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

        addSpacer(root, 20);
        root.addView(text("3. Bàn phím AI", 19, true));
        root.addView(text(
                "Tự dịch sau khi ngừng gõ. Câu Trung và bản dịch ngược xuất hiện trước khi bấm CHÈN 中文. Không tự gửi tin nhắn.",
                13, false));

        Button enable = button("MỞ CÀI ĐẶT BÀN PHÍM ANDROID");
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(enable);

        Button pick = button("CHỌN NGỌC THỌ AI KEYBOARD");
        pick.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.showInputMethodPicker();
        });
        root.addView(pick);

        addSpacer(root, 20);
        root.addView(text("4. Dịch màn hình", 19, true));
        root.addView(text(
                "Nhấn bong bóng 译 → chụp màn hình → kéo chọn đúng vùng chữ → OCR trên điện thoại → chỉ gửi text lên AI.",
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
                "Đã bật. Nhấn 译 rồi kéo vùng cần dịch; nhấn giữ bong bóng để dừng.",
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
