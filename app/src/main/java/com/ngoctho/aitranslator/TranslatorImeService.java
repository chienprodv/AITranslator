package com.ngoctho.aitranslator;

import android.content.Intent;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.speech.tts.TextToSpeech;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.view.KeyEvent;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private KeyboardSurface surface;
    private LinearLayout candidateRow;
    private boolean numericHost, reverse, voicePending, selecting, alternateNext, readRequested;
    private Draft optionsDraft;
    private TranslationResult optionOne, optionTwo;
    private UndoEdit undoEdit;
    private List<String> swipeCandidates = new ArrayList<>();
    private boolean swipeChoice, templateActive;
    private String templateCategory = "Tất cả";
    private TextToSpeech tts;
    private boolean ttsReady;
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
        if (!autoEnabled || privateEditor || listening || panel != 0 || voicePending) return;
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
        keyboardRoot.setPadding(dp(4), dp(3), dp(4), dp(28) + bottomGap());
        keyboardRoot.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom = Build.VERSION.SDK_INT >= 30
                    ? insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                    : insets.getSystemWindowInsetBottom();
            v.setPadding(dp(4), dp(3), dp(4), Math.max(dp(28), bottom) + bottomGap());
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
            Intent i = new Intent(this, KeyboardOptionsActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });
        header.addView(settings, new LinearLayout.LayoutParams(dp(60), dp(34)));
        keyboardRoot.addView(header);
        header.setVisibility(View.GONE);

        resultScroll = new ScrollView(this);
        resultScroll.setBackgroundColor(0xfff6f7f8);
        resultScroll.setVisibility(View.GONE);
        LinearLayout resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        resultBox.setPadding(dp(8), dp(2), dp(8), dp(2));

        intentView = resultLine("Ý của bạn: —");
        chineseView = resultLine("中文: —");
        backView = resultLine("Dịch ngược: —");

        header.removeView(status);
        resultBox.addView(status);
        resultBox.addView(intentView);
        resultBox.addView(chineseView);
        resultBox.addView(backView);
        resultScroll.addView(resultBox);
        keyboardRoot.addView(
                resultScroll,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(112)));

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

        microphoneButton = smallButton("🎤"); microphoneButton.setContentDescription("Nói; giữ để đổi chiều dịch");
        microphoneButton.setOnClickListener(v -> toggleVoice());
        microphoneButton.setOnLongClickListener(v -> { setDirection(!reverse); return true; });
        actions.addView(microphoneButton, weighted(0.8f, 36));
        addTool(actions, "☰", "Câu đã lưu và câu mẫu", () -> { panel = panel == 1 ? 0 : 1; rebuildKeyboard(); });
        addTool(actions, "😊", "Emoji", () -> { panel = panel == 2 ? 0 : 2; rebuildKeyboard(); });
        addTool(actions, "⋯", "Sửa chữ, dịch và cài đặt", () -> { panel = panel == 3 ? 0 : 3; rebuildKeyboard(); });

        keyboardRoot.addView(actions);
        buildKeys();

        return keyboardRoot;
    }

    private void buildKeys() {
        if (panel == 1) { showPhrasePanel(); return; }
        if (panel == 2) { showEmojiPanel(); return; }
        if (panel == 3) { showMorePanel(); return; }
        if (panel == 4) { showClipboardPanel(); return; }
        if (panel == 5) { showTemplatePanel(); return; }
        candidateRow = new LinearLayout(this); keyboardRoot.addView(candidateRow); updateCandidates();
        surface = new KeyboardSurface(this, new KeyboardSurface.Listener() {
            public void key(String code) { surfaceKey(code); }
            public void cursor(int delta) { moveCursor(delta); }
            public void gesture(List<KeyboardModel.Point> points) { glide(points); }
            public void longKey(String code) {
                if (code.equals("SHIFT")) { capsLock = !capsLock; shifted = capsLock; refreshSurface(); }
                else if (code.equals("LANGUAGE")) {
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showInputMethodPicker();
                }
            }
        });
        keyboardRoot.addView(surface); refreshSurface();
    }

    private void rebuildKeyboard() {
        autoHandler.removeCallbacks(deleteRepeat);
        surface = null; candidateRow = null;
        // First three children are the hidden header, preview and toolbar.
        while (keyboardRoot.getChildCount() > 3) {
            keyboardRoot.removeViewAt(3);
        }
        buildKeys();
    }

    private void onCharKey(char c, boolean letter) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        stopVoice();
        invalidateTranslation();
        swipeChoice = false;
        KeyboardData.prefs(this).edit().remove("alternative_source").remove("alternative_reference").apply();
        if (letter && !english && !privateEditor && TelexComposer.handle(ic, c)) {
            if (shifted && !capsLock) { shifted = false; if (surface != null) surface.setCase(false, false); }
            updateCandidates();
            scheduleAutoTranslate();
            return;
        }

        ic.commitText(String.valueOf(c), 1);

        if (shifted && letter && !capsLock) {
            shifted = false;
            if (surface != null) surface.setCase(false, false);
        }

        updateCandidates();
        scheduleAutoTranslate();
    }

    private void scheduleAutoTranslate() {
        if (templateActive) {
            if (Pattern.compile("\\[[^\\]\\n]{1,60}\\]").matcher(currentSource()).find()) return;
            templateActive = false;
        }
        if (!autoEnabled || privateEditor || listening || panel != 0 || voicePending) return;
        autoHandler.removeCallbacks(autoTranslateRunnable);
        autoHandler.postDelayed(autoTranslateRunnable, AppPrefs.autoDelayMs(this));
    }

    private void translateCurrent(boolean automatic) {
        if (templateActive && Pattern.compile("\\[[^\\]\\n]{1,60}\\]").matcher(currentSource()).find()) { if (!automatic) toast("Điền hết các ô [ ] trước khi dịch."); return; }
        if (!alternateNext) KeyboardData.prefs(this).edit().remove("alternative_source").remove("alternative_reference").apply();
        if (!privateEditor && (reverse || voicePending)) { boolean speech = voicePending; voicePending = false; translateDirectionalInline(speech); return; }
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
        final boolean secondOption = alternateNext; alternateNext = false;
        final TranslationResult previousOption = secondOption ? lastResult : null;
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
                        optionsDraft = requestedDraft;
                        if (secondOption) { optionOne = previousOption; optionTwo = r; } else { optionOne = r; optionTwo = null; }
                        if (KeyboardData.prefs(TranslatorImeService.this).getBoolean("auto_favorite", false)) HistoryStore.savePhrase(TranslatorImeService.this, r.source, r.chinese, r.backVi);

                        intentView.setText(highlight("Ý của bạn: " + r.intentVi));
                        chineseView.setText(highlight("中文: " + r.chinese));

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
        String removed = draft == null ? "" : append ? draft.selected : draft.selected.isEmpty() ? draft.left + draft.right : draft.selected;
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
        undoEdit = new UndoEdit(Draft.read(ic), removed, result.chinese);
        TelexComposer.reset(); updateCandidates();
        status.setText("Đã chèn · ↶ để hoàn tác · chưa gửi");
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
            TelexComposer.reset(); swipeChoice = false;
            ic.finishComposingText();
            ic.commitText(s, 1);
            updateCandidates();
            scheduleAutoTranslate();
        }
    }

    private void backspace() {
        TelexComposer.reset(); swipeChoice = false;
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
            updateCandidates(); scheduleAutoTranslate();
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
        numericHost = kind == InputType.TYPE_CLASS_NUMBER || kind == InputType.TYPE_CLASS_PHONE;
        int variation = type & InputType.TYPE_MASK_VARIATION;
        privateEditor = KeyboardData.prefs(this).getBoolean("private_mode", false) || kind == InputType.TYPE_CLASS_PHONE || kind == InputType.TYPE_CLASS_NUMBER
                || (kind == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_URI || variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS || variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
                || (kind == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
                || (info != null && (info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0);
        optionOne = null; optionTwo = null; optionsDraft = null; templateActive = false;
        stopVoice(); if (!restarting) TelexComposer.reset(); undoEdit = null; swipeChoice = false; voicePending = false;
        reverse = KeyboardData.prefs(this).getBoolean("reverse", false); voiceLanguage = reverse ? "zh-CN" : "vi-VN";
        KeyboardData.prefs(this).edit().remove("alternative_source").remove("alternative_reference").apply();
        autoEnabled = autoEnabled && !privateEditor;
        english = getSharedPreferences("keyboard_ui", MODE_PRIVATE).getBoolean("english", false);
        panel = 0; symbolPage = 0; capsLock = false;
        shifted = false;
        if (keyboardRoot != null) {
            rebuildKeyboard();
            autoButton.setText(autoEnabled ? "AUTO ✓" : "AUTO");
            status.setText(privateEditor ? "Riêng tư · AI tắt" : directionLabel());
            autoButton.setEnabled(!privateEditor);
            translateButton.setEnabled(!privateEditor); microphoneButton.setEnabled(!privateEditor);
            keyboardRoot.setBackgroundColor(isDark() ? 0xff202124 : 0xffe4e5e7);
            LinearLayout toolbar = (LinearLayout) keyboardRoot.getChildAt(2);
            for (int i = 0; i < toolbar.getChildCount(); i++) {
                View child = toolbar.getChildAt(i);
                if (child instanceof Button) styleButton((Button) child, true);
            }
            resultScroll.setBackgroundColor(isDark() ? 0xff292c31 : 0xfff6f7f8);
            for (TextView view : new TextView[]{status, intentView, chineseView, backView}) view.setTextColor(isDark() ? 0xfff1f3f4 : 0xff202124);
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
        stopVoice(); TelexComposer.reset(); readRequested = false; if (tts != null) tts.stop();
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
        addTool(tabs, "Mẫu", "Câu mẫu theo nhóm", () -> { panel = 5; rebuildKeyboard(); });
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
                voicePending = true;
                if (KeyboardData.prefs(TranslatorImeService.this).getBoolean("voice_review", true)) {
                    status.setText("Sửa câu nhận dạng nếu cần rồi bấm DỊCH.");
                    chineseView.setText("Chưa dịch; chưa gửi yêu cầu AI.");
                    backView.setText("Có thể tắt bước xem lại giọng nói trong Cài đặt bàn phím.");
                } else translateCurrent(false);
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

    @Override public void onDestroy() { if (tts != null) { tts.stop(); tts.shutdown(); } stopVoice(); autoHandler.removeCallbacksAndMessages(null); super.onDestroy(); }

    private boolean isDark() {
        String theme = KeyboardData.prefs(this).getString("theme", "system");
        return "dark".equals(theme) || "system".equals(theme) && (getResources().getConfiguration().uiMode & 48) == 32;
    }
    private void refreshSurface() { if (surface != null) surface.state(symbolPage, shifted, capsLock, english, numericHost, privateEditor); }
    private void surfaceKey(String code) {
        switch (code) {
            case "SHIFT": if (symbolPage > 0) symbolPage = symbolPage == 1 ? 2 : 1; else { shifted = !shifted; capsLock = false; } refreshSurface(); return;
            case "SYMBOLS": symbolPage = symbolPage == 0 ? 1 : 0; shifted = false; capsLock = false; refreshSurface(); return;
            case "LANGUAGE": english = !english; KeyboardData.prefs(this).edit().putBoolean("english", english).apply(); TelexComposer.reset(); refreshSurface(); updateCandidates(); return;
            case "SPACE": smartSpace(); return;
            case "DELETE": backspace(); return;
            case "ENTER": enter(); return;
            default:
                if (code.length() == 1 && Character.isLetter(code.charAt(0))) onCharKey(shifted && symbolPage == 0 ? Character.toUpperCase(code.charAt(0)) : code.charAt(0), true);
                else commit(code);
        }
    }
    private void updateCandidates() {
        if (candidateRow == null) return;
        candidateRow.removeAllViews();
        InputConnection ic = getCurrentInputConnection();
        if (privateEditor || !KeyboardData.prefs(this).getBoolean("suggestions", true)) {
            addTool(candidateRow, privateEditor ? "Riêng tư · AI tắt" : reverse ? "中 → VI" : "VI → 中", "Trạng thái bàn phím", () -> { panel = 3; rebuildKeyboard(); });
        } else {
            String prefix = ic == null ? "" : WordTools.wordBefore(Draft.text(ic.getTextBeforeCursor(80, 0)));
            List<String> choices = swipeChoice && undoEdit != null && undoEdit.after != null && undoEdit.after.matches(ic)
                    ? swipeCandidates : WordTools.suggest(prefix, KeyboardData.learned(this));
            for (String word : choices) addTool(candidateRow, word, "Chèn gợi ý " + word, () -> chooseSuggestion(word));
        }
        addTool(candidateRow, "↶", "Hoàn tác lần chèn hoặc tự sửa", this::undo);
    }
    private void chooseSuggestion(String word) {
        InputConnection ic = getCurrentInputConnection(); if (ic == null || privateEditor) return;
        if (swipeChoice && undoEdit != null && undoEdit.after != null && undoEdit.after.matches(ic)) {
            String original = undoEdit.removed;
            replaceBefore(undoEdit.inserted, word + " ");
            undoEdit = new UndoEdit(Draft.read(ic), original, word + " ");
        } else replaceBefore(WordTools.wordBefore(Draft.text(ic.getTextBeforeCursor(80, 0))), word + " ");
        KeyboardData.learn(this, word);
    }
    private void glide(List<KeyboardModel.Point> points) {
        if (privateEditor) return;
        List<String> choices = WordTools.swipe(points, KeyboardData.learned(this));
        if (choices.isEmpty()) { toast("Chưa nhận được từ; gõ hoặc thêm từ vào bộ từ đã học."); return; }
        chooseSuggestion(choices.get(0)); swipeCandidates = new ArrayList<>(choices); swipeChoice = true; updateCandidates();
    }
    private void smartSpace() {
        InputConnection ic = getCurrentInputConnection(); if (ic == null) return;
        String before = Draft.text(ic.getTextBeforeCursor(256, 0));
        Matcher shortcut = Pattern.compile("(?:^|\\s)(/[A-Za-z0-9_-]{1,24})$").matcher(before);
        if (!privateEditor && shortcut.find()) {
            String template = KeyboardData.shortcut(this, shortcut.group(1));
            if (template != null) { replaceBefore(shortcut.group(1), template); templateActive = true; selectPlaceholder(); return; }
        }
        String word = WordTools.wordBefore(before);
        if (!privateEditor && KeyboardData.prefs(this).getBoolean("autocorrect", false)) {
            String correction = WordTools.correction(word, KeyboardData.learned(this));
            if (!correction.equals(word)) { replaceBefore(word, correction + " "); KeyboardData.learn(this, correction); return; }
        }
        if (!privateEditor) KeyboardData.learn(this, word);
        commit(" ");
    }
    private void replaceBefore(String old, String replacement) {
        InputConnection ic = getCurrentInputConnection(); if (ic == null) return;
        String before = Draft.text(ic.getTextBeforeCursor(Math.max(512, old.length()), 0));
        if (!before.endsWith(old)) return;
        String selected = Draft.text(ic.getSelectedText(0));
        stopVoice(); invalidateTranslation(); TelexComposer.reset(); swipeChoice = false;
        ic.beginBatchEdit();
        try { ic.finishComposingText(); if (selected.isEmpty()) ic.deleteSurroundingText(old.length(), 0); ic.commitText(replacement, 1); }
        finally { ic.endBatchEdit(); }
        undoEdit = new UndoEdit(Draft.read(ic), selected.isEmpty() ? old : selected, replacement);
        updateCandidates(); scheduleAutoTranslate();
    }
    private static final class UndoEdit {
        final Draft after; final String removed, inserted;
        UndoEdit(Draft after, String removed, String inserted) { this.after = after; this.removed = removed; this.inserted = inserted; }
    }
    private void undo() {
        InputConnection ic = getCurrentInputConnection(); if (ic == null) return;
        if (undoEdit != null && undoEdit.after != null && undoEdit.after.matches(ic)) {
            String removed = undoEdit.removed, inserted = undoEdit.inserted; undoEdit = null;
            stopVoice(); invalidateTranslation(); TelexComposer.reset(); swipeChoice = false;
            ic.beginBatchEdit(); try { ic.deleteSurroundingText(inserted.length(), 0); ic.commitText(removed, 1); } finally { ic.endBatchEdit(); }
            status.setText("Đã hoàn tác; chưa gửi tin nhắn."); updateCandidates();
        } else { undoEdit = null; editAction(android.R.id.undo); toast("Đã yêu cầu ứng dụng hoàn tác nếu được hỗ trợ."); }
    }
    private void moveCursor(int delta) {
        InputConnection ic = getCurrentInputConnection(); if (ic == null) return;
        stopVoice(); invalidateTranslation(); TelexComposer.reset(); swipeChoice = false;
        ExtractedText text = ic.getExtractedText(new ExtractedTextRequest(), 0);
        if (text != null && text.text != null) {
            String value = text.text.toString(); int position = Math.max(0, Math.min(value.length(), text.selectionEnd)), n = Math.max(-100, Math.min(100, delta));
            while (n > 0 && position < value.length()) { position += Character.charCount(value.codePointAt(position)); n--; }
            while (n < 0 && position > 0) { position -= Character.charCount(value.codePointBefore(position)); n++; }
            ic.setSelection(text.startOffset + (selecting ? text.selectionStart : position), text.startOffset + position);
        } else {
            int key = delta < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
            for (int i = 0; i < Math.min(100, Math.abs(delta)); i++) {
                ic.sendKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, key, 0, selecting ? KeyEvent.META_SHIFT_ON : 0));
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
            }
        }
        updateCandidates();
    }
    private void editAction(int action) {
        InputConnection ic = getCurrentInputConnection(); if (ic == null) return;
        stopVoice(); invalidateTranslation(); TelexComposer.reset(); ic.performContextMenuAction(action); updateCandidates();
    }
    private String currentSource() { return selectedOrCurrentText(getCurrentInputConnection()); }
    private String directionLabel() { return reverse ? "中 → VI" : "VI → 中"; }
    private void setDirection(boolean backwards) {
        stopVoice(); invalidateTranslation(); reverse = backwards; voiceLanguage = backwards ? "zh-CN" : "vi-VN";
        KeyboardData.prefs(this).edit().putBoolean("reverse", backwards).apply(); toast(directionLabel()); rebuildKeyboard();
    }
    private CharSequence highlight(String value) {
        SpannableString text = new SpannableString(value); Matcher facts = Pattern.compile("[A-Za-z]*\\d[\\dA-Za-z.,_/-]*").matcher(value);
        while (facts.find()) text.setSpan(new BackgroundColorSpan(isDark() ? 0xff705e20 : 0xffffedaa), facts.start(), facts.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }
    private void translateDirectionalInline(boolean fromVoice) {
        if (privateEditor) return;
        final Draft draft = Draft.read(getCurrentInputConnection()); if (draft == null || draft.source.isEmpty()) { toast("Gõ hoặc chọn câu trước."); return; }
        if (draft.source.length() > 500) { toast("Chọn đoạn tối đa 500 ký tự."); return; }
        final boolean second = alternateNext; alternateNext = false;
        final TranslationResult previous = second ? lastResult : null;
        invalidateTranslation(); final long request = revision; resultScroll.setVisibility(View.VISIBLE); status.setText("Đang dịch " + directionLabel() + "…");
        DirectionalTranslationEngine.translate(this, draft.source, reverse ? DirectionalTranslationEngine.ZH_TO_VI : DirectionalTranslationEngine.VI_TO_ZH,
                !fromVoice, new DirectionalTranslationEngine.Callback() {
            public void onSuccess(BiTranslationResult result, boolean cached) {
                if (request != revision || !draft.matches(getCurrentInputConnection())) return;
                TranslationResult value = MeaningCheck.check(new TranslationResult(result.source, result.interpretedSource, result.translated, result.backTranslated, result.score, result.warning), KeyboardData.prefs(TranslatorImeService.this).getString("lock_terms", ""));
                optionsDraft = draft;
                if (second) { optionOne = previous; optionTwo = value; } else { optionOne = value; optionTwo = null; }
                showChoice(draft, value, false);
                if (KeyboardData.prefs(TranslatorImeService.this).getBoolean("auto_favorite", false)) HistoryStore.savePhrase(TranslatorImeService.this, value.source, value.chinese, value.backVi);
            }
            public void onError(String message) { if (request == revision && draft.matches(getCurrentInputConnection())) { status.setText("Chưa có bản dịch."); backView.setText(message); } }
        });
    }
    private void showChoice(Draft draft, TranslationResult result, boolean append) {
        translatedDraft = draft; lastResult = result; insertOnly = append;
        intentView.setText(highlight("Câu gốc: " + result.source)); chineseView.setText(highlight("Bản dịch: " + result.chinese));
        backView.setText("Dịch ngược: " + result.backVi + (result.warning.isEmpty() ? "" : "\n⚠ " + result.warning));
        status.setText("Xem trước → CHÈN · chưa gửi"); insertButton.setText("CHÈN"); insertButton.setEnabled(!privateEditor && draft != null); resultScroll.setVisibility(View.VISIBLE);
    }
    private void alternate() {
        if (privateEditor || lastResult == null || translatedDraft == null || !translatedDraft.matches(getCurrentInputConnection())) { toast("Dịch câu hiện tại trước để tạo phương án 2."); return; }
        KeyboardData.prefs(this).edit().putString("alternative_source", lastResult.source).putString("alternative_reference", lastResult.chinese).apply();
        alternateNext = true; translateCurrent(false);
    }
    private void chooseOption(boolean second) {
        TranslationResult value = second ? optionTwo : optionOne;
        if (value == null || optionsDraft == null || !optionsDraft.matches(getCurrentInputConnection()) || privateEditor) { toast("Chưa có phương án hoặc câu gốc đã thay đổi."); return; }
        showChoice(optionsDraft, value, false); panel = 0; rebuildKeyboard();
    }
    private LinearLayout vertical() { LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); return list; }
    private Button panelAction(LinearLayout list, String title, Runnable action) {
        Button button = actionButton(title); button.setSingleLine(false); button.setMaxLines(3); button.setTextSize(13);
        button.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE); button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPadding(dp(9), dp(4), dp(9), dp(4)); button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(title.contains("\n") ? 68 : 46)); params.setMargins(dp(2), dp(2), dp(2), dp(2)); list.addView(button, params); return button;
    }
    private void panelScroll(LinearLayout list, int height) { ScrollView scroll = new ScrollView(this); scroll.addView(list); keyboardRoot.addView(scroll, new LinearLayout.LayoutParams(-1, dp(height))); }
    private void openPanel(int value) { panel = value; autoHandler.removeCallbacks(autoTranslateRunnable); rebuildKeyboard(); }
    private void showMorePanel() {
        LinearLayout list = vertical();
        panelAction(list, "ABC · Trở về phím", () -> openPanel(0));
        panelAction(list, directionLabel() + " · đổi chiều dịch", () -> setDirection(!reverse));
        panelAction(list, autoEnabled ? "AUTO đang bật · tắt" : "AUTO đang tắt · bật", () -> {
            if (privateEditor) { toast("Ô riêng tư không dùng AI."); return; }
            autoEnabled = !autoEnabled; AppPrefs.setAutoTranslate(this, autoEnabled); rebuildKeyboard();
        });
        panelAction(list, "Mở / thu gọn bản dịch", () -> resultScroll.setVisibility(resultScroll.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        panelAction(list, "☆ Lưu câu đang xem", this::saveCurrentPhrase); panelAction(list, "🔊 Đọc bản dịch", this::readResult);
        panelAction(list, "Tạo phương án dịch 2 (có thể tính phí API)", this::alternate);
        LinearLayout choices = new LinearLayout(this); addTool(choices, "Chọn PA 1", "Phương án 1", () -> chooseOption(false)); addTool(choices, "Chọn PA 2", "Phương án 2", () -> chooseOption(true)); list.addView(choices);
        panelAction(list, "Ngữ cảnh: " + AppPrefs.businessContext(this) + " · đổi", () -> {
            String[] values = {"shopping", "negotiation", "technical", "general"}; int at = Arrays.asList(values).indexOf(AppPrefs.businessContext(this));
            AppPrefs.prefs(this).edit().putString("business_context", values[(at + 1) % values.length]).apply(); invalidateTranslation(); rebuildKeyboard();
        });
        panelAction(list, "Giọng văn: " + KeyboardData.prefs(this).getString("tone", "polite") + " · đổi", () -> {
            String[] values = {"polite", "friendly", "concise", "firm"}; int at = Arrays.asList(values).indexOf(KeyboardData.prefs(this).getString("tone", "polite"));
            KeyboardData.prefs(this).edit().putString("tone", values[(at + 1) % values.length]).apply(); invalidateTranslation(); rebuildKeyboard();
        });
        LinearLayout moves = new LinearLayout(this); addTool(moves, "←", "Con trỏ trái", () -> moveCursor(-1)); addTool(moves, selecting ? "Chọn ✓" : "Chọn", "Chọn văn bản", () -> { selecting = !selecting; rebuildKeyboard(); }); addTool(moves, "→", "Con trỏ phải", () -> moveCursor(1)); addTool(moves, "↶", "Hoàn tác", this::undo); list.addView(moves);
        LinearLayout clip = new LinearLayout(this); addTool(clip, "Tất cả", "Chọn tất cả", () -> editAction(android.R.id.selectAll)); addTool(clip, "Copy", "Sao chép", () -> editAction(android.R.id.copy)); addTool(clip, "Cắt", "Cắt", () -> editAction(android.R.id.cut)); addTool(clip, "Dán", "Dán", () -> editAction(android.R.id.paste)); list.addView(clip);
        panelAction(list, "Clipboard ghim", () -> openPanel(4)); panelAction(list, "Câu mẫu có ô điền", () -> openPanel(5)); panelAction(list, "Chọn ô [ ] tiếp theo", this::selectPlaceholder);
        panelAction(list, KeyboardData.prefs(this).getBoolean("private_mode", false) ? "Tắt riêng tư thủ công" : "Bật riêng tư thủ công", () -> {
            KeyboardData.prefs(this).edit().putBoolean("private_mode", !KeyboardData.prefs(this).getBoolean("private_mode", false)).apply(); onStartInputView(getCurrentInputEditorInfo(), false);
        });
        panelAction(list, "⚙ Bố cục, từ điển, sao lưu, số dư", () -> startActivity(new Intent(this, KeyboardOptionsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        panelAction(list, "Cấu hình AI / hạn mức VNĐ", () -> startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        panelScroll(list, 245);
    }
    private void showClipboardPanel() {
        LinearLayout controls = new LinearLayout(this);
        addTool(controls, "Ghim", "Đọc và ghim clipboard", () -> {
            if (privateEditor) { toast("Ô riêng tư không đọc clipboard."); return; }
            ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (manager == null || !manager.hasPrimaryClip() || manager.getPrimaryClip() == null) { toast("Clipboard trống."); return; }
            try { KeyboardData.pin(this, String.valueOf(manager.getPrimaryClip().getItemAt(0).coerceToText(this))); rebuildKeyboard(); } catch (Exception e) { toast(e.getMessage()); }
        });
        addTool(controls, "Dán", "Dán clipboard", () -> editAction(android.R.id.paste)); addTool(controls, "ABC", "Trở về", () -> openPanel(0)); keyboardRoot.addView(controls);
        LinearLayout list = vertical(); JSONArray items = privateEditor ? new JSONArray() : KeyboardData.pins(this);
        for (int i = 0; i < items.length(); i++) {
            String text = items.optJSONObject(i).optString("text"); Button button = panelAction(list, text, () -> { commit(text); openPanel(0); });
            button.setOnLongClickListener(v -> { KeyboardData.unpin(this, text); rebuildKeyboard(); return true; });
        }
        if (items.length() == 0) list.addView(resultLine("Chưa có mục ghim. Giữ một mục để bỏ ghim. Nội dung hết hạn được dọn khi mở."));
        panelScroll(list, 220);
    }
    private void showTemplatePanel() {
        JSONArray items = KeyboardData.templates(this); LinkedHashSet<String> groups = new LinkedHashSet<>(); groups.add("Tất cả");
        for (int i = 0; i < items.length(); i++) groups.add(items.optJSONObject(i).optString("category"));
        List<String> categories = new ArrayList<>(groups); LinearLayout controls = new LinearLayout(this);
        addTool(controls, templateCategory + " ▾", "Chọn nhóm câu mẫu", () -> { int at = categories.indexOf(templateCategory); templateCategory = categories.get((at + 1) % categories.size()); rebuildKeyboard(); });
        addTool(controls, "Ô [ ]", "Chọn ô tiếp theo", this::selectPlaceholder); addTool(controls, "ABC", "Trở về", () -> openPanel(0)); keyboardRoot.addView(controls);
        LinearLayout list = vertical();
        if (!privateEditor) for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i); if (!templateCategory.equals("Tất cả") && !templateCategory.equals(item.optString("category"))) continue;
            String source = item.optString("source");
            panelAction(list, "/" + item.optString("shortcut") + " · " + item.optString("category") + "\n" + source, () -> { commit(source); templateActive = true; autoHandler.removeCallbacks(autoTranslateRunnable); openPanel(0); selectPlaceholder(); });
        }
        panelScroll(list, 225);
    }
    private void selectPlaceholder() {
        InputConnection ic = getCurrentInputConnection(); if (ic == null) return;
        ExtractedText text = ic.getExtractedText(new ExtractedTextRequest(), 0);
        if (text == null || text.text == null) { toast("Chạm chọn ô [ ] để điền."); return; }
        Matcher matcher = Pattern.compile("\\[[^\\]\\n]{1,60}\\]").matcher(text.text);
        int first = -1, firstEnd = -1, chosen = -1, end = -1;
        while (matcher.find()) { if (first < 0) { first = matcher.start(); firstEnd = matcher.end(); } if (matcher.start() >= text.selectionEnd) { chosen = matcher.start(); end = matcher.end(); break; } }
        if (chosen < 0) { chosen = first; end = firstEnd; }
        if (chosen < 0) { templateActive = false; toast("Đã điền hết ô [ ]."); return; }
        invalidateTranslation(); TelexComposer.reset(); ic.setSelection(text.startOffset + chosen, text.startOffset + end); toast("Gõ để thay ô đã chọn.");
    }
    private void readResult() {
        if (privateEditor || lastResult == null) { toast("Chưa có bản dịch để đọc."); return; }
        readRequested = true;
        if (tts == null) tts = new TextToSpeech(this, code -> { ttsReady = code == TextToSpeech.SUCCESS; if (ttsReady && readRequested && isInputViewShown()) speakResult(); else if (!ttsReady) toast("Không khởi tạo được giọng đọc."); });
        else if (ttsReady) speakResult();
    }
    private void speakResult() {
        if (tts == null || lastResult == null || privateEditor) return;
        Locale language = Pattern.compile("[\\u4e00-\\u9fff]").matcher(lastResult.chinese).find() ? Locale.SIMPLIFIED_CHINESE : new Locale("vi", "VN");
        int available = tts.setLanguage(language);
        if (available == TextToSpeech.LANG_MISSING_DATA || available == TextToSpeech.LANG_NOT_SUPPORTED) { toast("Máy chưa có giọng đọc cho ngôn ngữ này."); return; }
        tts.setSpeechRate(0.95f); tts.speak(lastResult.chinese, TextToSpeech.QUEUE_FLUSH, null, "ngoctho-preview"); readRequested = false;
    }

    private void styleButton(Button b, boolean special) {
        b.setTextColor(new ColorStateList(new int[][] { new int[] {-android.R.attr.state_enabled}, new int[] {} },
                new int[] {0xff9aa0a6, isDark() ? 0xfff1f3f4 : 0xff202124}));
        b.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        b.setFocusable(false);
        b.setFocusableInTouchMode(false);
        b.setSingleLine(true);
        b.setAutoSizeTextTypeUniformWithConfiguration(10, special ? 22 : 23, 1, TypedValue.COMPLEX_UNIT_SP);
        b.setBackgroundTintList(null);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(isDark() ? 0xff35373c : special ? 0xffcdd0d4 : 0xffffffff);
        shape.setCornerRadius(dp(7));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), shape, null));
        b.setStateListAnimator(null);
        b.setElevation(dp(1));
    }

    private int bottomGap() { return dp(KeyboardLighting.bottomGap(KeyboardData.prefs(this).getInt("bottom_gap", 28))); }

    private int dp(int v) {
        return Math.round(
                v * getResources().getDisplayMetrics().density);
    }
}
