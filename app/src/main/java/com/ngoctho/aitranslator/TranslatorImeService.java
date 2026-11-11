package com.ngoctho.aitranslator;

import android.content.Intent;
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

    private boolean shifted = false;
    private boolean autoEnabled = true;
    private TranslationResult lastResult;

    private final Runnable autoTranslateRunnable = () -> {
        if (!autoEnabled) return;
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
        addKeyRow("1234567890", false);
        addKeyRow("qwertyuiop", true);
        addKeyRow("asdfghjkl", true);

        LinearLayout row3 = new LinearLayout(this);

        Button shift = keyButton("⇧");
        shift.setOnClickListener(v -> {
            shifted = !shifted;
            rebuildKeyboard();
        });
        row3.addView(shift, weighted(0.9f, 46));

        addCharsToRow(row3, "zxcvbnm", true);

        Button backspace = keyButton("⌫");
        backspace.setOnClickListener(v -> backspace());
        row3.addView(backspace, weighted(1f, 46));

        keyboardRoot.addView(row3);

        LinearLayout bottom = new LinearLayout(this);

        Button comma = keyButton(",");
        comma.setOnClickListener(v -> commit(","));
        bottom.addView(comma, weighted(0.7f, 48));

        Button space = keyButton("Tiếng Việt");
        space.setTextSize(15);
        space.setContentDescription("Dấu cách");
        space.setOnClickListener(v -> commit(" "));
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
        // Header + result + actions = first 3 children.
        while (keyboardRoot.getChildCount() > 3) {
            keyboardRoot.removeViewAt(3);
        }
        buildKeys();
    }

    private void addKeyRow(String chars, boolean letters) {
        LinearLayout row = new LinearLayout(this);
        addCharsToRow(row, chars, letters);
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

        invalidateTranslation();
        if (letter && !shifted && TelexComposer.handle(ic, c)) {
            scheduleAutoTranslate();
            return;
        }

        ic.commitText(String.valueOf(c), 1);

        if (shifted && letter) {
            shifted = false;
            rebuildKeyboard();
        }

        scheduleAutoTranslate();
    }

    private void scheduleAutoTranslate() {
        if (!autoEnabled) return;
        autoHandler.removeCallbacks(autoTranslateRunnable);
        autoHandler.postDelayed(autoTranslateRunnable, AppPrefs.autoDelayMs(this));
    }

    private void translateCurrent(boolean automatic) {
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
        if (insertButton != null) insertButton.setEnabled(false);
    }

    private void insertTranslation() {
        InputConnection ic = getCurrentInputConnection();
        Draft draft = translatedDraft;
        TranslationResult result = lastResult;
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
            if (draft.selected.isEmpty()) ic.deleteSurroundingText(draft.left.length(), draft.right.length());
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
            invalidateTranslation();
            ic.finishComposingText();
            ic.commitText(s, 1);
            scheduleAutoTranslate();
        }
    }

    private void backspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            invalidateTranslation();
            CharSequence selected = ic.getSelectedText(0);
            if (selected != null && selected.length() > 0) ic.commitText("", 1);
            else ic.deleteSurroundingTextInCodePoints(1, 0);
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
        shifted = false;
        if (keyboardRoot != null) {
            rebuildKeyboard();
            autoButton.setText(autoEnabled ? "AUTO ✓" : "AUTO");
            status.setText("VI → 中文  ▾");
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
