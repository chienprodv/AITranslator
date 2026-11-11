package com.ngoctho.aitranslator;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

public class HistoryActivity extends Activity {
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View ui = buildUi();
        UiInsets.apply(ui);
        setContentView(ui);
        refresh();
    }

    private View buildUi() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(16), dp(14), dp(16), dp(20));

        TextView title = text("Lịch sử dịch", 23, true);
        outer.addView(title);

        Button clear = new Button(this);
        clear.setText("XÓA TOÀN BỘ LỊCH SỬ");
        clear.setAllCaps(false);
        clear.setOnClickListener(v -> {
            HistoryStore.clear(this);
            refresh();
            Toast.makeText(this, "Đã xóa lịch sử.", Toast.LENGTH_SHORT).show();
        });
        outer.addView(clear);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        outer.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return outer;
    }

    private void refresh() {
        if (list == null) return;
        list.removeAllViews();
        JSONArray items = HistoryStore.list(this);
        if (items.length() == 0) {
            list.addView(text("Chưa có lịch sử.", 15, false));
            return;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null) continue;
            String type = o.optString("type", "dịch");
            String source = o.optString("source", "");
            String target = o.optString("target", "");
            String back = o.optString("back", "");
            int score = o.optInt("score", 0);
            String when = HistoryStore.formatTime(o.optLong("time", 0));

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(10), dp(10), dp(10), dp(10));

            String meta = when + " · " + type;
            if (score > 0) meta += " · " + score + "%";
            card.addView(text(meta, 12, true));
            card.addView(text("Gốc: " + source, 14, false));
            card.addView(text("Dịch: " + target, 15, true));
            if (!back.isEmpty()) card.addView(text("Ngược: " + back, 13, false));

            Button copy = new Button(this);
            copy.setText("Sao chép bản dịch");
            copy.setAllCaps(false);
            copy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Bản dịch", target));
                Toast.makeText(this, "Đã sao chép.", Toast.LENGTH_SHORT).show();
            });
            card.addView(copy);
            list.addView(card);

            View divider = new View(this);
            divider.setBackgroundColor(0x22000000);
            list.addView(divider, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        }
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        t.setTextIsSelectable(true);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
