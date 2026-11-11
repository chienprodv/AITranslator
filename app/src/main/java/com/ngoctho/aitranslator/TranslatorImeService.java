package com.ngoctho.aitranslator;

import android.content.Intent;
import android.graphics.Typeface;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
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

    private boolean shifted = false;
    private boolean autoEnabled = true;
    private String lastSource = "";
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
        keyboardRoot.setPadding(dp(4), dp(4), dp(4), dp(5));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);

        status = new TextView(this);
        status.setText("VI → 中文 · dịch ngược bắt buộc");
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
        header.addView(autoButton, new LinearLayout.LayoutParams(dp(70), dp(34)));

        Button settings = smallButton("Cài đặt");
        settings.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });
        header.addView(settings, new LinearLayout.LayoutParams(dp(78), dp(34)));
        keyboardRoot.addView(header);

        ScrollView resultScroll = new ScrollView(this);
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
                        dp(102)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button translate = actionButton("DỊCH");
        translate.setOnClickListener(v -> translateCurrent(false));
        actions.addView(translate, weighted(1f, 40));

        insertButton = actionButton("CHÈN 中文");
        insertButton.setEnabled(false);
        insertButton.setOnClickListener(v -> insertTranslation());
        actions.addView(insertButton, weighted(1.2f, 40));

        Button next = actionButton("Đổi bàn phím");
        next.setOnClickListener(v -> {
            try {
                switchToNextInputMethod(false);
            } catch (Exception ignored) {}
        });
        actions.addView(next, weighted(1f, 40));

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

        Button space = keyButton("dấu cách");
        space.setOnClickListener(v -> commit(" "));
        bottom.addView(space, weighted(3f, 48));

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
            row.addView(b, weighted(1f, 46));
        }
    }

    private void onCharKey(char c, boolean letter) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

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

        final String requestedSource = source;

        if (!automatic) {
            autoHandler.removeCallbacks(autoTranslateRunnable);
        }

        lastSource = source;
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
                        if (automatic) {
                            InputConnection current = getCurrentInputConnection();
                            if (current == null) return;

                            String currentSource = selectedOrCurrentText(current);
                            if (!currentSource.equals(requestedSource)) {
                                return;
                            }
                        }

                        lastSource = requestedSource;
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

                        insertButton.setEnabled(true);
                    }

                    @Override
                    public void onError(String message) {
                        if (automatic) {
                            InputConnection current = getCurrentInputConnection();
                            if (current == null) return;
                            if (!selectedOrCurrentText(current).equals(requestedSource)) return;
                        }

                        status.setText("Lỗi dịch");
                        chineseView.setText("中文: —");
                        backView.setText("Lỗi: " + message);
                    }
                });
    }

    private String selectedOrCurrentText(InputConnection ic) {
        CharSequence selected = ic.getSelectedText(0);
        if (selected != null && selected.toString().trim().length() > 0) {
            return selected.toString().trim();
        }

        CharSequence before = ic.getTextBeforeCursor(800, 0);
        if (before == null) return "";

        String s = before.toString();

        int cut = Math.max(
                s.lastIndexOf('\n'),
                s.lastIndexOf('\r'));

        if (cut >= 0) {
            s = s.substring(cut + 1);
        }

        if (s.length() > 500) {
            s = s.substring(s.length() - 500);
        }

        return s.trim();
    }

    private void insertTranslation() {
        if (lastResult == null || lastResult.chinese.isEmpty()) return;

        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        CharSequence selected = ic.getSelectedText(0);

        if (selected != null
                && selected.toString().trim().equals(lastSource.trim())) {
            ic.commitText(lastResult.chinese, 1);
        } else {
            CharSequence before = ic.getTextBeforeCursor(
                    Math.max(800, lastSource.length() + 20),
                    0);

            String b = before == null ? "" : before.toString();

            if (!lastSource.isEmpty() && b.endsWith(lastSource)) {
                ic.deleteSurroundingText(lastSource.length(), 0);
                ic.commitText(lastResult.chinese, 1);
            } else {
                ic.commitText(lastResult.chinese, 1);
            }
        }

        autoHandler.removeCallbacks(autoTranslateRunnable);
        status.setText("Đã chèn câu tiếng Trung");
    }

    private void commit(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(s, 1);
            scheduleAutoTranslate();
        }
    }

    private void backspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.deleteSurroundingText(1, 0);
            scheduleAutoTranslate();
        }
    }

    private void enter() {
        autoHandler.removeCallbacks(autoTranslateRunnable);

        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.sendKeyEvent(
                    new KeyEvent(
                            KeyEvent.ACTION_DOWN,
                            KeyEvent.KEYCODE_ENTER));
            ic.sendKeyEvent(
                    new KeyEvent(
                            KeyEvent.ACTION_UP,
                            KeyEvent.KEYCODE_ENTER));
        }
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        autoHandler.removeCallbacks(autoTranslateRunnable);
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
        return b;
    }

    private Button smallButton(String s) {
        Button b = actionButton(s);
        b.setTextSize(11);
        return b;
    }

    private Button keyButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private LinearLayout.LayoutParams weighted(float weight, int heightDp) {
        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(
                        0,
                        dp(heightDp),
                        weight);
        p.setMargins(dp(1), dp(1), dp(1), dp(1));
        return p;
    }

    private int dp(int v) {
        return Math.round(
                v * getResources().getDisplayMetrics().density);
    }
}
