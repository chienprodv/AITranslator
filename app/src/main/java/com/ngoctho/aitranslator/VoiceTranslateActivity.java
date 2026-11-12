package com.ngoctho.aitranslator;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public class VoiceTranslateActivity extends Activity {
    private static final int REQ_AUDIO = 601;

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;

    private TextView status;
    private TextView heard;
    private TextView translated;
    private TextView backTranslated;
    private TextView quality;
    private Button speakResult;
    private Button copyResult;

    private String pendingDirection = DirectionalTranslationEngine.VI_TO_ZH;
    private String lastTargetText = "";
    private boolean targetIsChinese = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View ui = buildUi();
        UiInsets.apply(ui);
        setContentView(ui);
        initSpeech();
        initTts();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(30));
        scroll.addView(root);

        root.addView(text("Dịch giọng nói hai chiều", 23, true));
        root.addView(text(
                "Nhận dạng lời nói hiển thị ngay từng phần. Khi bạn dừng nói, AI dịch và luôn chạy dịch ngược để kiểm tra sát nghĩa.",
                14, false));

        addSpacer(root, 14);

        Button vi = button("🎤 NÓI TIẾNG VIỆT → 中文");
        vi.setOnClickListener(v -> startListening(DirectionalTranslationEngine.VI_TO_ZH));
        root.addView(vi);

        Button zh = button("🎤 说中文 → TIẾNG VIỆT");
        zh.setOnClickListener(v -> startListening(DirectionalTranslationEngine.ZH_TO_VI));
        root.addView(zh);

        Button stop = button("DỪNG NGHE");
        stop.setOnClickListener(v -> {
            if (speechRecognizer != null) speechRecognizer.stopListening();
        });
        root.addView(stop);

        addSpacer(root, 10);
        status = text("Sẵn sàng.", 14, true);
        root.addView(status);

        heard = section("Tôi nghe được", "—");
        root.addView(heard);

        translated = section("Bản dịch", "—");
        root.addView(translated);

        backTranslated = section("Dịch ngược kiểm tra", "—");
        root.addView(backTranslated);

        quality = section("Độ sát nghĩa", "—");
        root.addView(quality);

        addSpacer(root, 10);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        speakResult = button("🔊 ĐỌC BẢN DỊCH");
        speakResult.setEnabled(false);
        speakResult.setOnClickListener(v -> speakLastTranslation());
        actions.addView(speakResult, new LinearLayout.LayoutParams(0, dp(48), 1f));

        copyResult = button("SAO CHÉP");
        copyResult.setEnabled(false);
        copyResult.setOnClickListener(v -> copyLastTranslation());
        actions.addView(copyResult, new LinearLayout.LayoutParams(0, dp(48), 1f));

        root.addView(actions);

        return scroll;
    }

    private void initSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.setText("Thiết bị không có dịch vụ nhận dạng giọng nói.");
            return;
        }

        if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
        } else {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        }
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                status.setText("Đang nghe…");
            }

            @Override
            public void onBeginningOfSpeech() {
                status.setText("Đang nhận giọng nói…");
            }

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                status.setText("Đã nghe xong, đang xử lý…");
            }

            @Override
            public void onError(int error) {
                status.setText("Nhận dạng giọng nói: " + speechError(error));
            }

            @Override
            public void onResults(Bundle results) {
                String text = firstSpeechResult(results);
                if (text.isEmpty()) {
                    status.setText("Không nhận được nội dung.");
                    return;
                }
                heard.setText("Tôi nghe được\n" + text);
                translateVoice(text, pendingDirection);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                String text = firstSpeechResult(partialResults);
                if (!text.isEmpty()) {
                    heard.setText("Tôi đang nghe\n" + text + " …");
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void initTts() {
        tts = new TextToSpeech(this, statusCode -> {
            if (statusCode != TextToSpeech.SUCCESS) {
                Toast.makeText(
                        VoiceTranslateActivity.this,
                        "Không khởi tạo được giọng đọc TTS.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startListening(String direction) {
        if(!PrivacyConsent.allowed(this)){PrivacyConsent.show(this);return;}
        pendingDirection = direction;

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }

        if (speechRecognizer == null) {
            status.setText("Không có dịch vụ nhận dạng giọng nói.");
            return;
        }

        lastTargetText = "";
        speakResult.setEnabled(false);
        copyResult.setEnabled(false);
        translated.setText("Bản dịch\n—");
        backTranslated.setText("Dịch ngược kiểm tra\n—");
        quality.setText("Độ sát nghĩa\n—");
        heard.setText("Tôi nghe được\n…");

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                DirectionalTranslationEngine.ZH_TO_VI.equals(direction)
                        ? "zh-CN"
                        : "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        status.setText("Đang mở micro…");

        try {
            speechRecognizer.cancel();
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            status.setText("Không mở được micro: " + safeMessage(e));
        }
    }

    private void translateVoice(String source, String direction) {
        status.setText("Đang dịch + kiểm tra dịch ngược…");

        DirectionalTranslationEngine.translate(
                this,
                source,
                direction,
                new DirectionalTranslationEngine.Callback() {
                    @Override
                    public void onSuccess(BiTranslationResult r, boolean fromCache) {
                        boolean zhToVi =
                                DirectionalTranslationEngine.ZH_TO_VI.equals(r.direction);

                        heard.setText(
                                "Ý đã nhận\n" + r.interpretedSource);

                        translated.setText(
                                (zhToVi ? "Tiếng Việt" : "中文") +
                                "\n" + r.translated);

                        backTranslated.setText(
                                "Dịch ngược kiểm tra\n" + r.backTranslated);

                        String state = r.warning.isEmpty() ? "Đã có dịch ngược" : "Cần kiểm tra";
                        String scoreText = r.score > 0 ? (": " + r.score + "%") : "";
                        quality.setText(
                                "Kiểm tra ý nghĩa\n" + state + scoreText +
                                (r.warning.isEmpty() ? "" : "\n⚠ " + r.warning));

                        status.setText(
                                fromCache
                                        ? "Hoàn tất — kết quả từ cache."
                                        : "Hoàn tất — đã kiểm tra dịch ngược.");

                        lastTargetText = r.translated;
                        targetIsChinese = !zhToVi;
                        speakResult.setEnabled(true);
                        copyResult.setEnabled(true);
                    }

                    @Override
                    public void onError(String message) {
                        status.setText("Lỗi dịch: " + message);
                    }
                });
    }

    private String firstSpeechResult(Bundle bundle) {
        if (bundle == null) return "";
        ArrayList<String> values =
                bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (values == null || values.isEmpty()) return "";
        String s = values.get(0);
        return s == null ? "" : s.trim();
    }

    private void speakLastTranslation() {
        if (tts == null || lastTargetText.isEmpty()) return;

        Locale locale = targetIsChinese
                ? Locale.SIMPLIFIED_CHINESE
                : new Locale("vi", "VN");

        int availability = tts.setLanguage(locale);
        if (availability == TextToSpeech.LANG_MISSING_DATA
                || availability == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(
                    this,
                    "Máy chưa có giọng đọc cho ngôn ngữ này.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        tts.setSpeechRate(0.95f);
        tts.speak(
                lastTargetText,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "ngoc_tho_translation");
    }

    private void copyLastTranslation() {
        if (lastTargetText.isEmpty()) return;
        ClipboardManager cm =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(
                ClipData.newPlainText("Bản dịch", lastTargetText));
        Toast.makeText(this, "Đã sao chép bản dịch.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_AUDIO) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening(pendingDirection);
            } else {
                status.setText("Cần quyền micro để dịch giọng nói.");
            }
        }
    }

    private String speechError(int code) {
        switch (code) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "lỗi âm thanh.";
            case SpeechRecognizer.ERROR_CLIENT:
                return "yêu cầu bị hủy.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "chưa có quyền micro.";
            case SpeechRecognizer.ERROR_NETWORK:
                return "lỗi mạng.";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "mạng quá chậm.";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "chưa nghe rõ, hãy nói lại.";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "dịch vụ nhận dạng đang bận.";
            case SpeechRecognizer.ERROR_SERVER:
                return "dịch vụ nhận dạng gặp lỗi.";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "không nghe thấy giọng nói.";
            default:
                return "mã lỗi " + code + ".";
        }
    }

    private TextView section(String title, String body) {
        TextView t = text(title + "\n" + body, 15, false);
        t.setPadding(0, dp(10), 0, dp(10));
        t.setTextIsSelectable(true);
        return t;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        return b;
    }

    private void addSpacer(LinearLayout root, int heightDp) {
        View spacer = new View(this);
        root.addView(
                spacer,
                new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage();
        return m == null || m.trim().isEmpty()
                ? e.getClass().getSimpleName()
                : m;
    }

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }

        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }

        super.onDestroy();
    }
}
