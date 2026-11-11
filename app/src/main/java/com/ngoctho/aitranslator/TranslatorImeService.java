package com.ngoctho.aitranslator;

import android.content.Intent;
import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.view.MotionEvent;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class TranslatorImeService extends InputMethodService {
    private final Handler autoHandler = new Handler(Looper.getMainLooper());

    private LinearLayout keyboardRoot;
    private TextView status;
    private TextView intentView;
    private TextView chineseView;
    private TextView backView;
    private Button insertButton;
    private Button autoButton;
    private ScrollView resultScroll;
    private long revision;
    private Draft translatedDraft;
    private Button translateButton, microphoneButton;
    private boolean capsLock, english, privateEditor, insertOnly, listening;
    private int symbolPage, panel, emojiCategory;
    private String libraryQuery = "";
    private boolean savedOnly = true;
    private SpeechRecognizer recognizer;
    private long voiceSession;
    private String voiceLanguage = "vi-VN";
    private final Runnable deleteRepeat = new Runnable() {
        @Override public void run() { backspace(); autoHandler.postDelayed(this, 65); }
    };

    private boolean shifted = false;
    private boolean autoEnabled = true;
    private TranslationResult lastResult;

    private final Runnable autoTranslateRunnable = () -> {
        if (!autoEnabled || privateEditor || listening || panel != 0) return;
        if (!AppPrefs.isReady(this)) return;

        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        String source = selectedOrCurrentText(ic);
        if (source.length() < 4) return;

        translateCurrent(true);
    };

    @Override
    public View onCreateInputView() {
        autoEnabled = AppPrefs.autoTranslate(this);

        keyboardRoot = new LinearLayout(this);
        keyboardRoot.setOrientation(LinearLayout.VERTICAL);
        keyboardRoot.setBackgroundColor(0xffe4e5e7);
        keyboardRoot.setPadding(dp(4), dp(3), dp(4), dp(28));
        keyboardRoot.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom = Build.VERSION.SDK_INT >= 30
                    ? insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                    : insets.getSystemWindowInsetBottom();
            v.setPadding(dp(4), dp(3), dp(4), Math.max(dp(28), bottom));
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);

        status = new TextView(this);
        status.setText("VI → 中文  ▾");
        status.setOnClickListener(v -> resultScroll.setVisibility(resultScroll.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        status.setContentDescription("Mở hoặc thu gọn bản dịch");
        status.setTextSize(13);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(status, new LinearLayout.LayoutParams(0, dp(34), 1f));

        autoButton = smallButton(autoEnabled ? "AUTO ✓" : "AUTO");
        autoButton.setOnClickListener(v -> {
            autoEnabled = !autoEnabled;
            AppPrefs.setAutoTranslate(this, autoEnabled);
            autoButton.setText(autoEnabled ? "AUTO ✓" : "AUTO");
            status.setText(autoEnabled
                    ? "Tự dịch bật · dừng gõ để dịch"
                    : "Tự dịch tắt · bấm DỊCH");
            if (!autoEnabled) autoHandler.removeCallbacks(autoTranslateRunnable);
        });
        header.addView(autoButton, new LinearLayout.LayoutParams(dp(65), dp(34)));

        Button settings = smallButton("⚙");
        settings.setContentDescription("Cài đặt AI và API key");
        settings.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });
        header.addView(settings, new LinearLayout.LayoutParams(dp(60), dp(34)));
        keyboardRoot.addView(header);

        resultScroll = new ScrollView(this);
        resultScroll.setBackgroundColor(0xfff6f7f8);
        resultScroll.setVisibility(View.GONE);
        LinearLayout resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        resultBox.setPadding(dp(8), dp(2), dp(8), dp(2));

        intentView = resultLine("Ý của bạn: —");
        chineseView = resultLine("中文: —");
        backView = resultLine("Dịch ngược: —");

        resultBox.addView(intentView);
        resultBox.addView(chineseView);
        resultBox.addView(backView);
        resultScroll.addView(resultBox);
        keyboardRoot.addView(
                resultScroll,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(96)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button translate = actionButton("DỊCH");
        translateButton = translate;
        translate.setOnClickListener(v -> translateCurrent(false));
        actions.addView(translate, weighted(1f, 36));

        insertButton = actionButton("CHÈN 中文");
        insertButton.setEnabled(false);
        insertButton.setOnClickListener(v -> insertTranslation());
        actions.addView(insertButton, weighted(1.4f, 36));

        Button next = actionButton("◎ Đổi");
        next.setContentDescription("Đổi bàn phím");
        next.setOnClickListener(v -> {
            try {
                switchToNextInputMethod(false);
            } catch (Exception ignored) {}
        });
        actions.addView(next, weighted(0.8f, 36));

        keyboardRoot.addView(actions);
        buildKeys();

        return keyboardRoot;
    }

    private void buildKeys() {
        LinearLayout tools = new LinearLayout(this);
        addTool(tools, "☆ Lưu", "Lưu bản dịch đang xem", () -> saveCurrentPhrase());
        addTool(tools, "☰ Câu", "Câu đã lưu và lịch sử", () -> { panel = panel == 1 ? 0 : 1; libraryQuery = ""; resultScroll.setVisibility(View.GONE); rebuildKeyboard(); });
        microphoneButton = addTool(tools, listening ? "■ Dừng" : "🎤", "Nhấn để nói; giữ để đổi Việt hoặc Trung", () -> toggleVoice());
        microphoneButton.setOnLongClickListener(v -> { stopVoice(); voiceLanguage = voiceLanguage.equals("vi-VN") ? "zh-CN" : "vi-VN"; toast("Micro: " + (voiceLanguage.equals("vi-VN") ? "Việt → Trung" : "Trung → Việt")); return true; });
        addTool(tools, "😊", "Emoji", () -> { panel = panel == 2 ? 0 : 2; rebuildKeyboard(); });
        addTool(tools, symbolPage == 0 ? "!#1" : "ABC", "Chữ cái hoặc ký hiệu", () -> { panel = 0; symbolPage = symbolPage == 0 ? 1 : 0; shifted = false; capsLock = false; rebuildKeyboard(); });
        keyboardRoot.addView(tools);
        if (panel == 1) { showPhrasePanel(); return; }
        if (panel == 2) { showEmojiPanel(); return; }
        addKeyRow("1234567890", false);
        addKeyRow(symbolPage == 0 ? "qwertyuiop" : symbolPage == 1 ? "@#$%&*()-=" : "€£¥₫¢^°•≈≠", symbolPage == 0);
        addKeyRow(symbolPage == 0 ? "asdfghjkl" : symbolPage == 1 ? "_+/:;!?\"'" : "[]{}<>\\|~", symbolPage == 0);

        LinearLayout row3 = new LinearLayout(this);

        Button shift = keyButton(symbolPage == 0 ? (capsLock ? "⇪" : "⇧") : symbolPage == 1 ? "2/2" : "1/2");
        shift.setOnClickListener(v -> {
            if (symbolPage > 0) symbolPage = symbolPage == 1 ? 2 : 1;
            else { capsLock = false; shifted = !shifted; }
            rebuildKeyboard();
        });
        shift.setOnLongClickListener(v -> { if (symbolPage == 0) { capsLock = !capsLock; shifted = capsLock; rebuildKeyboard(); } return true; });
        row3.addView(shift, weighted(1.25f, 46));

        addCharsToRow(row3, symbolPage == 0 ? "zxcvbnm" : symbolPage == 1 ? ",.\\|<>~" : "×÷±√∞§©", symbolPage == 0);

        Button backspace = keyButton("⌫");
        backspace.setContentDescription("Xóa; giữ để xóa liên tục");
        backspace.setOnClickListener(v -> backspace());
        backspace.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                v.setPressed(true); v.performClick(); autoHandler.postDelayed(deleteRepeat, 350); return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                v.setPressed(false); autoHandler.removeCallbacks(deleteRepeat); return true;
            }
            return true;
        });
        row3.addView(backspace, weighted(1f, 46));

        keyboardRoot.addView(row3);

        LinearLayout bottom = new LinearLayout(this);

        Button comma = keyButton(",");
        comma.setOnClickListener(v -> commit(","));
        bottom.addView(comma, weighted(0.7f, 48));

        Button space = keyButton(english ? "English" : "Tiếng Việt");
        space.setTextSize(15);
        space.setContentDescription("Dấu cách");
        space.setOnClickListener(v -> commit(" "));
        space.setOnLongClickListener(v -> {
            english = !english;
            getSharedPreferences("keyboard_ui", MODE_PRIVATE).edit().putBoolean("english", english).apply();
            rebuildKeyboard(); toast(english ? "English: không gõ dấu Telex" : "Tiếng Việt: bật Telex"); return true;
        });
        bottom.addView(space, weighted(3.6f, 48));

        Button dot = keyButton(".");
        dot.setOnClickListener(v -> commit("."));
        bottom.addView(dot, weighted(0.7f, 48));

        Button enter = keyButton("↵");
        enter.setOnClickListener(v -> enter());
        bottom.addView(enter, weighted(1f, 48));

        keyboardRoot.addView(bottom);
    }

    private void rebuildKeyboard() {
        autoHandler.removeCallbacks(deleteRepeat);
        // Header + result + actions = first 3 children.
        while (keyboardRoot.getChildCount() > 3) {
            keyboardRoot.removeViewAt(3);
        }
        buildKeys();
    }

    private void addKeyRow(String chars, boolean letters) {
        LinearLayout row = new LinearLayout(this);
        boolean inset = letters && chars.equals("asdfghjkl");
        if (inset) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 0.5f));
        addCharsToRow(row, chars, letters);
        if (inset) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 0.5f));
        keyboardRoot.addView(row);
    }

    private void addCharsToRow(LinearLayout row, String chars, boolean letters) {
        for (int i = 0; i < chars.length(); i++) {
            char raw = chars.charAt(i);
            char shown = letters && shifted
                    ? Character.toUpperCase(raw)
                    : raw;

            Button b = keyButton(String.valueOf(shown));
            b.setOnClickListener(v -> onCharKey(shown, letters));
            row.addView(b, weighted(1f, chars.equals("1234567890") ? 38 : 46));
        }
    }

    private void onCharKey(char c, boolean letter) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        stopVoice();
        invalidateTranslation();
        if (letter && !english && !shifted && TelexComposer.handle(ic, c)) {
            scheduleAutoTranslate();
            return;
        }

        ic.commitText(String.valueOf(c), 1);

        if (shifted && letter && !capsLock) {
            shifted = false;
            rebuildKeyboard();
        }

        scheduleAutoTranslate();
    }

    private void scheduleAutoTranslate() {
        if (!autoEnabled || privateEditor || listening || panel != 0) return;
        autoHandler.removeCallbacks(autoTranslateRunnable);
        autoHandler.postDelayed(autoTranslateRunnable, AppPrefs.autoDelayMs(this));
    }

    private void translateCurrent(boolean automatic) {
        if (privateEditor) { if (!automatic) toast("Ô riêng tư: không gửi nội dung lên AI."); return; }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        String source = selectedOrCurrentText(ic);
        if (source.isEmpty()) {
            if (!automatic) {
                Toast.makeText(
                        this,
                        "Chưa có câu tiếng Việt để dịch.",
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (source.length() > 500) {
            if (!automatic) Toast.makeText(this, "Chọn đoạn tối đa 500 ký tự để dịch.", Toast.LENGTH_SHORT).show();
            return;
        }
        final Draft requestedDraft = Draft.read(ic);
        invalidateTranslation();
        final long requestRevision = revision;
        if (!automatic) resultScroll.setVisibility(View.VISIBLE);

        if (!automatic) {
            autoHandler.removeCallbacks(autoTranslateRunnable);
        }

        lastResult = null;
        insertButton.setEnabled(false);

        status.setText(automatic ? "Tự dịch…" : "Đang dịch nhanh…");
        intentView.setText("Ý của bạn: " + source);
        chineseView.setText("中文: …");
        backView.setText("Dịch ngược: …");

        TranslationEngine.translate(
                this,
                source,
                new TranslationEngine.Callback() {
                    @Override
                    public void onSuccess(TranslationResult r, boolean fromCache) {
                        if (requestRevision != revision || requestedDraft == null
                                || !requestedDraft.matches(getCurrentInputConnection())) return;
                        translatedDraft = requestedDraft;
                        insertOnly = false;
                        insertButton.setText("CHÈN 中文");

                        lastResult = r;

                        intentView.setText("Ý của bạn: " + r.intentVi);
                        chineseView.setText("中文: " + r.chinese);

                        String scoreText = r.score > 0 ? (" " + r.score + "%") : "";
                        String quality = r.warning.isEmpty()
                                ? "Đã có dịch ngược" + scoreText
                                : "Cần kiểm tra" + scoreText + " — " + r.warning;

                        backView.setText(
                                "Dịch ngược: " + r.backVi + "\n" + quality);

                        status.setText(
                                fromCache
                                        ? "VI → 中文 · tức thì từ cache"
                                        : "VI → 中文 · đã kiểm tra dịch ngược");

                        insertButton.setEnabled(!r.chinese.isEmpty());
                        resultScroll.setVisibility(View.VISIBLE);
                        status.setText("Xem trước → CHÈN 中文");
                    }

                    @Override
                    public void onError(String message) {
                        if (requestRevision != revision || requestedDraft == null
                                || !requestedDraft.matches(getCurrentInputConnection())) return;

                        status.setText("Lỗi dịch");
                        chineseView.setText("中文: —");
                        backView.setText(AppPrefs.isDemo(AppPrefs.provider(TranslatorImeService.this))
                                ? "DEMO chỉ dịch câu mẫu. Mở ⚙, chọn AI và lưu API key/proxy để dịch câu bất kỳ."
                                : "Lỗi: " + message);
                    }
                });
    }

    private String selectedOrCurrentText(InputConnection ic) {
        Draft draft = Draft.read(ic);
        return draft == null ? "" : draft.source;
    }

    private void invalidateTranslation() {
        revision++;
        autoHandler.removeCallbacks(autoTranslateRunnable);
        lastResult = null;
        translatedDraft = null;
        insertOnly = false;
        if (insertButton != null) insertButton.setEnabled(false);
    }

    private void insertTranslation() {
        InputConnection ic = getCurrentInputConnection();
        Draft draft = translatedDraft;
        TranslationResult result = lastResult;
        boolean append = insertOnly;
        if (ic == null || draft == null || result == null || result.chinese.isEmpty()) return;
        if (!draft.matches(ic)) {
            invalidateTranslation();
            status.setText("Câu gốc đã đổi · bấm DỊCH lại");
            return;
        }
        invalidateTranslation();
        ic.beginBatchEdit();
        try {
            ic.finishComposingText();
            if (!append && draft.selected.isEmpty()) ic.deleteSurroundingText(draft.left.length(), draft.right.length());
            ic.commitText(result.chinese, 1);
        } finally { ic.endBatchEdit(); }
        status.setText("Đã chèn 中文 · chưa gửi");
    }

    /** Preserve whitespace and cursor context when replacing a previewed draft. */
    private static final class Draft {
        final String selected, before, after, left, right, source;
        Draft(String selected, String before, String after) {
            this.selected = selected;
            this.before = before;
            this.after = after;
            int start = Math.max(before.lastIndexOf('\n'), before.lastIndexOf('\r')) + 1;
            int end = after.length();
            for (int i = 0; i < after.length(); i++) {
                if (after.charAt(i) == '\n' || after.charAt(i) == '\r') { end = i; break; }
            }
            left = before.substring(start);
            right = after.substring(0, end);
            source = (selected.isEmpty() ? left + right : selected).trim();
        }
        static String text(CharSequence text) { return text == null ? "" : text.toString(); }
        static Draft read(InputConnection ic) {
            if (ic == null) return null;
            return new Draft(text(ic.getSelectedText(0)), text(ic.getTextBeforeCursor(4096, 0)),
                    text(ic.getTextAfterCursor(4096, 0)));
        }
        boolean matches(InputConnection ic) {
            Draft now = read(ic);
            return now != null && selected.equals(now.selected) && before.equals(now.before) && after.equals(now.after);
        }
    }

    private void commit(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            stopVoice();
            invalidateTranslation();
            ic.finishComposingText();
            ic.commitText(s, 1);
            scheduleAutoTranslate();
        }
    }

    private void backspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            stopVoice();
            invalidateTranslation();
            CharSequence selected = ic.getSelectedText(0);
            if (selected != null && selected.length() > 0) ic.commitText("", 1);
            else {
                CharSequence before = ic.getTextBeforeCursor(128, 0);
                if (before == null || before.length() == 0) ic.deleteSurroundingTextInCodePoints(1, 0);
                else {
                    android.icu.text.BreakIterator graphemes = android.icu.text.BreakIterator.getCharacterInstance(Locale.ROOT);
                    graphemes.setText(before.toString());
                    int boundary = graphemes.preceding(before.length());
                    ic.deleteSurroundingText(before.length() - Math.max(0, boundary), 0);
                }
            }
            scheduleAutoTranslate();
        }
    }

    private void enter() {
        commit("\n");
        autoHandler.removeCallbacks(autoTranslateRunnable);
    }

    @Override public boolean onEvaluateFullscreenMode() { return false; }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        invalidateTranslation();
        autoEnabled = AppPrefs.autoTranslate(this);
        int type = info == null ? 0 : info.inputType;
        int kind = type & InputType.TYPE_MASK_CLASS;
        int variation = type & InputType.TYPE_MASK_VARIATION;
        privateEditor = kind == InputType.TYPE_CLASS_PHONE || kind == InputType.TYPE_CLASS_NUMBER
                || (kind == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_URI || variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS || variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
                || (kind == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
                || (info != null && (info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0);
        stopVoice();
        autoEnabled = autoEnabled && !privateEditor;
        english = getSharedPreferences("keyboard_ui", MODE_PRIVATE).getBoolean("english", false);
        panel = 0; symbolPage = 0; capsLock = false;
        shifted = false;
        if (keyboardRoot != null) {
            rebuildKeyboard();
            autoButton.setText(autoEnabled ? "AUTO ✓" : "AUTO");
            status.setText(privateEditor ? "Riêng tư · AI tắt" : "VI → 中文  ▾");
            autoButton.setEnabled(!privateEditor);
            translateButton.setEnabled(!privateEditor);
            intentView.setText("Gõ tiếng Việt vào ô chat → DỊCH");
            chineseView.setText("中文: —");
            backView.setText("Xem trước rồi bấm CHÈN 中文 để thay câu trong ô chat.");
            resultScroll.setVisibility(View.GONE);
            keyboardRoot.requestApplyInsets();
        }
    }

    @Override public void onUpdateSelection(int oldStart, int oldEnd, int start, int end,
            int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldStart, oldEnd, start, end, candidatesStart, candidatesEnd);
        if (translatedDraft != null && !translatedDraft.matches(getCurrentInputConnection())) invalidateTranslation();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        autoHandler.removeCallbacks(autoTranslateRunnable);
        invalidateTranslation();
        stopVoice();
        autoHandler.removeCallbacks(deleteRepeat);
        super.onFinishInputView(finishingInput);
    }

    private TextView resultLine(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    private Button actionButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(3), 0, dp(3), 0);
        styleButton(b, true);
        b.setAutoSizeTextTypeUniformWithConfiguration(10, 13, 1, TypedValue.COMPLEX_UNIT_SP);
        b.setAutoSizeTextTypeUniformWithConfiguration(10, 13, 1, TypedValue.COMPLEX_UNIT_SP);
        return b;
    }

    private Button smallButton(String s) {
        Button b = actionButton(s);
        b.setTextSize(11);
        b.setAutoSizeTextTypeUniformWithConfiguration(10, 11, 1, TypedValue.COMPLEX_UNIT_SP);
        b.setAutoSizeTextTypeUniformWithConfiguration(10, 11, 1, TypedValue.COMPLEX_UNIT_SP);
        return b;
    }

    private Button keyButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(22);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(1), 0, dp(1), 0);
        styleButton(b, s.equals("⇧") || s.equals("⌫") || s.equals("↵"));
        return b;
    }

    private LinearLayout.LayoutParams weighted(float weight, int heightDp) {
        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(
                        0,
                        dp(heightDp),
                        weight);
        p.setMargins(dp(2), dp(3), dp(2), dp(3));
        return p;
    }


    private Button addTool(LinearLayout row, String label, String description, Runnable action) {
        Button button = smallButton(label);
        button.setContentDescription(description);
        button.setOnClickListener(v -> action.run());
        row.addView(button, weighted(1, 34));
        return button;
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

    private void saveCurrentPhrase() {
        if (privateEditor) { toast("Ô riêng tư: không lưu câu."); return; }
        if (lastResult == null) { toast("Dịch một câu trước rồi bấm ☆ Lưu."); return; }
        HistoryStore.savePhrase(this, lastResult.source, lastResult.chinese, lastResult.backVi);
        toast("Đã lưu vào ☰ Câu; dùng lại không gọi API.");
    }

    private void showPhrasePanel() {
        LinearLayout tabs = new LinearLayout(this);
        addTool(tabs, savedOnly ? "★ Đã lưu" : "☆ Đã lưu", "Các câu yêu thích", () -> { savedOnly = true; rebuildKeyboard(); });
        addTool(tabs, savedOnly ? "Lịch sử" : "✓ Lịch sử", "100 câu gần đây", () -> { savedOnly = false; rebuildKeyboard(); });
        addTool(tabs, libraryQuery.isEmpty() ? "Lọc ô chat" : "Bỏ lọc", "Lọc theo chữ đang gõ trong ô chat", () -> {
            libraryQuery = libraryQuery.isEmpty() ? selectedOrCurrentText(getCurrentInputConnection()).toLowerCase(Locale.ROOT) : "";
            rebuildKeyboard();
        });
        addTool(tabs, "ABC", "Trở về bàn phím", () -> { panel = 0; rebuildKeyboard(); });
        keyboardRoot.addView(tabs);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        JSONArray items = privateEditor ? new JSONArray() : savedOnly ? HistoryStore.savedPhrases(this) : HistoryStore.list(this);
        int count = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String source = item.optString("source"), target = item.optString("target"), back = item.optString("back");
            if (target.isEmpty() || !(source + " " + target).toLowerCase(Locale.ROOT).contains(libraryQuery)) continue;
            Button entry = actionButton(source + "\n" + target);
            entry.setSingleLine(false); entry.setMaxLines(3);
            entry.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);
            entry.setTextSize(13); entry.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            entry.setPadding(dp(10), dp(3), dp(10), dp(3));
            entry.setOnClickListener(v -> {
                invalidateTranslation();
                translatedDraft = Draft.read(getCurrentInputConnection());
                lastResult = new TranslationResult(source, source, target, back, 0, "Câu đã lưu; kiểm tra nội dung trước khi chèn.");
                insertOnly = true;
                intentView.setText("Câu gốc: " + source); chineseView.setText("Bản dịch: " + target);
                backView.setText("Dịch ngược: " + back); status.setText("Câu đã lưu · CHÈN để dùng lại");
                insertButton.setText("CHÈN"); insertButton.setEnabled(translatedDraft != null);
                resultScroll.setVisibility(View.VISIBLE); panel = 0; rebuildKeyboard();
            });
            entry.setOnLongClickListener(v -> {
                if (savedOnly) { HistoryStore.removePhrase(this, source, target); toast("Đã bỏ lưu câu."); rebuildKeyboard(); }
                else { HistoryStore.savePhrase(this, source, target, back); toast("Đã lưu vào yêu thích."); }
                return true;
            });
            list.addView(entry, new LinearLayout.LayoutParams(-1, dp(66))); count++;
        }
        if (count == 0) {
            TextView empty = resultLine(privateEditor ? "Ô riêng tư: không mở bộ nhớ." : "Chưa có câu. Dịch → ☆ Lưu. Giữ một câu để lưu hoặc bỏ lưu.");
            list.addView(empty);
        }
        scroll.addView(list); keyboardRoot.addView(scroll, new LinearLayout.LayoutParams(-1, dp(210)));
    }

    private static final String[] EMOJI = {
        "",
        "😀 😃 😄 😁 😆 😅 😂 🙂 🙃 😉 😊 😍 🥰 😘 😎 🤔 😐 😑 😴 😭 😢 😤 😡 😱 😳 🥳 🤣 🤗 🤫 🤝 🙏",
        "👍 👎 👌 ✌️ 🤞 🤟 🤘 👋 🤚 ✋ 👏 🙌 👐 🤲 💪 🫶 ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 💔 💯 ✅ ❌ ⚠️ 🔥 ⭐ ✨",
        "📦 🚚 🛒 💰 💵 💴 💳 🧾 📅 ⏰ 📞 📱 💻 🔌 🔋 🛠️ 🔧 ⚙️ 📷 📍 🏠 ✈️ ☀️ 🌧️ 🍀 🎉 🎁 ☕ 🍺 🍎 🇻🇳 🇨🇳"
    };

    private void showEmojiPanel() {
        LinearLayout categories = new LinearLayout(this);
        String[] names = {"Gần đây", "Mặt", "Cử chỉ", "Đồ vật"};
        for (int i = 0; i < names.length; i++) {
            final int category = i;
            addTool(categories, names[i], names[i], () -> { emojiCategory = category; rebuildKeyboard(); });
        }
        addTool(categories, "ABC", "Trở về bàn phím", () -> { panel = 0; rebuildKeyboard(); });
        keyboardRoot.addView(categories);
        String data = emojiCategory == 0 ? getSharedPreferences("keyboard_ui", MODE_PRIVATE).getString("recent_emoji", "") : EMOJI[emojiCategory];
        if (data == null || data.isEmpty()) data = EMOJI[1];
        ScrollView scroll = new ScrollView(this);
        LinearLayout grid = new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL);
        String[] entries = data.split(" ");
        LinearLayout row = null;
        for (int i = 0; i < entries.length; i++) {
            if (i % 8 == 0) { row = new LinearLayout(this); grid.addView(row); }
            final String emoji = entries[i];
            Button key = keyButton(emoji);
            key.setOnClickListener(v -> {
                commit(emoji);
                if (!privateEditor) {
                    String old = getSharedPreferences("keyboard_ui", MODE_PRIVATE).getString("recent_emoji", "");
                    StringBuilder recent = new StringBuilder(emoji); int used = 1;
                    for (String previous : (old == null ? "" : old).split(" ")) {
                        if (!previous.isEmpty() && !previous.equals(emoji) && used++ < 24) recent.append(" ").append(previous);
                    }
                    getSharedPreferences("keyboard_ui", MODE_PRIVATE).edit().putString("recent_emoji", recent.toString()).apply();
                }
            });
            row.addView(key, weighted(1, 42));
            if (i == entries.length - 1) for (int j = i % 8 + 1; j < 8; j++) row.addView(new View(this), weighted(1, 42));
        }
        scroll.addView(grid); keyboardRoot.addView(scroll, new LinearLayout.LayoutParams(-1, dp(210)));
    }

    private void stopVoice() {
        voiceSession++;
        listening = false;
        if (recognizer != null) { recognizer.cancel(); recognizer.destroy(); recognizer = null; }
        if (microphoneButton != null) microphoneButton.setText("🎤");
    }

    private void toggleVoice() {
        if (listening) { if (recognizer != null) recognizer.stopListening(); return; }
        if (privateEditor) { toast("Ô riêng tư: không bật micro hoặc AI."); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            startActivity(new Intent(this, AudioPermissionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { toast("Máy chưa có dịch vụ nhận dạng giọng nói."); return; }
        stopVoice(); invalidateTranslation();
        final long session = voiceSession;
        final Draft draft = Draft.read(getCurrentInputConnection());
        if (draft == null) return;
        listening = true; microphoneButton.setText("■ Dừng"); resultScroll.setVisibility(View.VISIBLE);
        status.setText("Đang nghe " + (voiceLanguage.equals("vi-VN") ? "tiếng Việt" : "tiếng Trung") + "…");
        backView.setText("Chỉ dịch sau khi nói xong; bấm ■ để kết thúc.");
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            private boolean current() { return listening && session == voiceSession && draft.matches(getCurrentInputConnection()); }
            public void onReadyForSpeech(Bundle params) {}
            public void onBeginningOfSpeech() {}
            public void onRmsChanged(float rms) {}
            public void onBufferReceived(byte[] buffer) {}
            public void onEndOfSpeech() { if (current()) status.setText("Đang nhận dạng…"); }
            public void onEvent(int type, Bundle params) {}
            public void onError(int error) {
                if (session != voiceSession) return;
                stopVoice(); status.setText("Micro: không nhận được câu (" + error + "). Bấm 🎤 để thử lại.");
            }
            private String first(Bundle results) {
                ArrayList<String> words = results == null ? null : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                return words == null || words.isEmpty() || words.get(0) == null ? "" : words.get(0).trim();
            }
            public void onPartialResults(Bundle results) {
                if (current()) intentView.setText("Đang nghe: " + first(results));
            }
            public void onResults(Bundle results) {
                if (!current()) { stopVoice(); return; }
                String text = first(results);
                boolean chinese = voiceLanguage.equals("zh-CN");
                stopVoice();
                if (text.isEmpty()) { status.setText("Chưa nghe rõ. Bấm 🎤 để nói lại."); return; }
                if (text.length() > 500) { status.setText("Câu quá dài; nói từng đoạn ngắn hơn."); return; }
                commit(text);
                autoHandler.removeCallbacks(autoTranslateRunnable);
                if (!chinese) translateCurrent(false);
                else translateChineseVoice();
            }
        });
        Intent request = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        request.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        request.putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLanguage);
        request.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        request.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try { recognizer.startListening(request); }
        catch (Exception e) { stopVoice(); status.setText("Không mở được micro. Kiểm tra quyền và dịch vụ giọng nói."); }
    }

    private void translateChineseVoice() {
        final Draft draft = Draft.read(getCurrentInputConnection());
        if (draft == null || draft.source.isEmpty()) return;
        invalidateTranslation(); final long request = revision;
        status.setText("Đang dịch Trung → Việt…");
        DirectionalTranslationEngine.translate(this, draft.source, DirectionalTranslationEngine.ZH_TO_VI, new DirectionalTranslationEngine.Callback() {
            public void onSuccess(BiTranslationResult result, boolean cached) {
                if (request != revision || !draft.matches(getCurrentInputConnection())) return;
                translatedDraft = draft; insertOnly = false;
                lastResult = new TranslationResult(result.source, result.interpretedSource, result.translated, result.backTranslated, result.score, result.warning);
                intentView.setText("Đã nghe: " + result.source); chineseView.setText("Tiếng Việt: " + result.translated);
                backView.setText("Dịch ngược: " + result.backTranslated); status.setText("Xem trước → CHÈN VI");
                insertButton.setText("CHÈN VI"); insertButton.setEnabled(true);
            }
            public void onError(String message) { if (request == revision && draft.matches(getCurrentInputConnection())) status.setText("Lỗi: " + message); }
        });
    }

    /** One-time Android permission prompt, then return to the chat keyboard. */
    public static class AudioPermissionActivity extends Activity {
        @Override public void onCreate(Bundle state) {
            super.onCreate(state);
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { finish(); return; }
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 701);
        }
        @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
            super.onRequestPermissionsResult(request, permissions, grants);
            Toast.makeText(this, grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED
                    ? "Đã cho phép micro. Bấm 🎤 trên bàn phím để nói." : "Chưa cho phép micro.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override public void onDestroy() { stopVoice(); autoHandler.removeCallbacksAndMessages(null); super.onDestroy(); }

    private void styleButton(Button b, boolean special) {
        b.setTextColor(new ColorStateList(new int[][] { new int[] {-android.R.attr.state_enabled}, new int[] {} },
                new int[] {0xff9aa0a6, 0xff202124}));
        b.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        b.setFocusable(false);
        b.setFocusableInTouchMode(false);
        b.setSingleLine(true);
        b.setAutoSizeTextTypeUniformWithConfiguration(10, special ? 22 : 23, 1, TypedValue.COMPLEX_UNIT_SP);
        b.setBackgroundTintList(null);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(special ? 0xffcdd0d4 : 0xffffffff);
        shape.setCornerRadius(dp(7));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), shape, null));
        b.setStateListAnimator(null);
        b.setElevation(dp(1));
    }

    private int dp(int v) {
        return Math.round(
                v * getResources().getDisplayMetrics().density);
    }
}
