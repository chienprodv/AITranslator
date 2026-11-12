package com.ngoctho.aitranslator;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.view.accessibility.*;
import java.util.*;

/** One touch surface: no gaps between independent Button hit targets, and independent pointers. */
public final class KeyboardSurface extends View {
    public interface Listener {
        void key(String code);
        void cursor(int delta);
        void gesture(List<KeyboardModel.Point> points);
        void longKey(String code);
    }
    private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, Press> presses = new LinkedHashMap<>();
    private KeyboardModel model = new KeyboardModel(0, false);
    private boolean shifted, caps, english, numeric, privateEditor;
    private int symbols, focused = -1;
    private double xOffset, widthUnits, rowHeight;
    private String[] variants;
    private int variantPointer = -1, variantIndex;
    private RectF variantBounds;
    private static final class Press {
        int id, cursorSteps; boolean held, cursorMode, committed;
        KeyboardModel.Key initial, current;
        KeyboardModel.Point start;
        List<KeyboardModel.Point> path = new ArrayList<>();
        Runnable hold;
    }
    public KeyboardSurface(Context context, Listener listener) {
        super(context); this.listener = listener;
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setFocusable(false); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("Bàn phím Ngọc Thọ; giữ phím cách rồi kéo để di chuyển con trỏ");
    }
    private android.content.SharedPreferences prefs() { return getContext().getSharedPreferences("keyboard_ui", Context.MODE_PRIVATE); }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    public void state(int symbols, boolean shifted, boolean caps, boolean english, boolean numeric, boolean privateEditor) {
        this.symbols = symbols; this.shifted = shifted; this.caps = caps; this.english = english;
        this.numeric = numeric; this.privateEditor = privateEditor;
        model = new KeyboardModel(symbols, numeric && symbols == 0); requestLayout(); invalidate();
    }
    public void setCase(boolean shifted, boolean caps) { this.shifted = shifted; this.caps = caps; invalidate(); }
    @Override protected void onMeasure(int width, int height) {
        rowHeight = dp(Math.max(38, Math.min(62, prefs().getInt("key_height", 48))));
        setMeasuredDimension(MeasureSpec.getSize(width), (int) Math.ceil(rowHeight * 5 + dp(6)));
        String hand = prefs().getString("one_hand", "off");
        double available = getMeasuredWidth() * ("off".equals(hand) ? 1 : 0.8);
        xOffset = "right".equals(hand) ? getMeasuredWidth() - available : 0;
        widthUnits = available / 10;
    }
    private KeyboardModel.Point point(float x, float y) { return new KeyboardModel.Point((x - xOffset) / widthUnits, (y - dp(3)) / rowHeight); }
    private RectF rect(KeyboardModel.Key key) {
        return new RectF((float) (xOffset + key.x * widthUnits + dp(2)), (float) (dp(3) + key.y * rowHeight + dp(3)),
                (float) (xOffset + (key.x + key.width) * widthUnits - dp(2)), (float) (dp(3) + (key.y + 1) * rowHeight - dp(3)));
    }
    private String label(String code) {
        switch (code) {
            case "SHIFT": return symbols == 0 ? caps ? "⇪" : "⇧" : symbols == 1 ? "2/2" : "1/2";
            case "DELETE": return "⌫";
            case "SPACE": return english ? "English" : "Tiếng Việt";
            case "SYMBOLS": return symbols == 0 ? "!#1" : "ABC";
            case "LANGUAGE": return "◎";
            case "ENTER": return "↵";
            default: return shifted && symbols == 0 ? code.toUpperCase(Locale.ROOT) : code;
        }
    }
    private String spoken(String code) {
        switch (code) {
            case "SHIFT": return "Chữ hoa; giữ để khóa chữ hoa";
            case "DELETE": return "Xóa; giữ để xóa liên tục";
            case "SPACE": return "Dấu cách; giữ và kéo để di chuyển con trỏ";
            case "SYMBOLS": return "Chữ cái và ký hiệu";
            case "LANGUAGE": return "Đổi ngôn ngữ; giữ để chọn bàn phím";
            case "ENTER": return "Xuống dòng";
            default: return label(code);
        }
    }
    private boolean dark() {
        String theme = prefs().getString("theme", "system");
        return "dark".equals(theme) || ("system".equals(theme)
                && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        boolean dark = dark();
        canvas.drawColor(dark ? 0xff202124 : 0xffe4e5e7);
        float fontScale = Math.max(0.8f, Math.min(1.25f, prefs().getInt("key_font", 100) / 100f));
        for (KeyboardModel.Key key : model.keys) {
            boolean special = key.code.length() > 1 && !key.code.equals("SPACE");
            boolean pressed = false;
            for (Press p : presses.values()) if (p.current == key) { pressed = true; break; }
            paint.setColor(pressed ? (dark ? 0xff64748b : 0xffb9d5ff) : special ? (dark ? 0xff484b50 : 0xffc9ccd0) : dark ? 0xff35373c : 0xffffffff);
            paint.setShadowLayer(dp(1), 0, dp(1), 0x33000000); canvas.drawRoundRect(rect(key), dp(6), dp(6), paint); paint.clearShadowLayer();
            paint.setColor(key.code.equals("ENTER") || key.code.equals("SHIFT") && shifted ? 0xff3b82f6 : dark ? 0xfff1f3f4 : 0xff15171a);
            String text = label(key.code);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            float size = dp(key.code.equals("SPACE") ? 15 : text.length() > 2 ? 16 : 23) * fontScale;
            paint.setTextSize(size);
            RectF bounds = rect(key);
            while (paint.measureText(text) > bounds.width() - dp(4) && size > dp(9)) paint.setTextSize(--size);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(text, bounds.centerX(), bounds.centerY() - (paint.ascent() + paint.descent()) / 2, paint);
        }
        if (prefs().getBoolean("swipe", false) && presses.size() == 1) {
            Press p = presses.values().iterator().next();
            if (!p.cursorMode && p.path.size() > 2 && p.initial.code.matches("[a-z]")) {
                Path path = new Path(); boolean first = true;
                for (KeyboardModel.Point point : p.path) {
                    float x = (float) (xOffset + point.x * widthUnits), y = (float) (dp(3) + point.y * rowHeight);
                    if (first) { path.moveTo(x, y); first = false; } else path.lineTo(x, y);
                }
                paint.setColor(0x994285f4); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(3)); canvas.drawPath(path, paint); paint.setStyle(Paint.Style.FILL);
            }
        }
        if (variants != null && variantBounds != null) {
            paint.setColor(dark ? 0xff525762 : 0xffd1e3ff); canvas.drawRoundRect(variantBounds, dp(8), dp(8), paint);
            float cell = variantBounds.width() / variants.length;
            paint.setColor(0xff3b82f6);
            canvas.drawRoundRect(new RectF(variantBounds.left + cell * variantIndex, variantBounds.top,
                    variantBounds.left + cell * (variantIndex + 1), variantBounds.bottom), dp(6), dp(6), paint);
            paint.setTextSize(dp(20));
            for (int i = 0; i < variants.length; i++) {
                paint.setColor(i == variantIndex ? Color.WHITE : dark ? Color.WHITE : Color.BLACK);
                canvas.drawText(variants[i], variantBounds.left + cell * (i + 0.5f), variantBounds.centerY() - (paint.ascent() + paint.descent()) / 2, paint);
            }
        }
    }
    private void emit(String code) {
        if (prefs().getBoolean("haptic", true)) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (prefs().getBoolean("key_sound", false)) playSoundEffect(SoundEffectConstants.CLICK);
        listener.key(code); sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
    }
    private void down(MotionEvent event, int index) {
        // Commit older taps in press order when thumbs overlap, rather than reversing release order.
        for (Press old : new ArrayList<>(presses.values())) {
            handler.removeCallbacks(old.hold);
            if (!old.committed && !old.held && !old.cursorMode && old.current != null && variantPointer != old.id) emit(old.current.code);
            old.committed = true;
        }
        KeyboardModel.Point point = point(event.getX(index), event.getY(index));
        KeyboardModel.Key key = model.hit(point.x, point.y);
        if (key == null) return;
        Press p = new Press(); p.id = event.getPointerId(index); p.start = point; p.initial = key; p.current = key; p.path.add(point);
        presses.put(p.id, p);
        p.hold = new Runnable() {
            @Override public void run() {
                if (!presses.containsKey(p.id) || p.committed || p.current != p.initial) return;
                p.held = true;
                if (key.code.equals("DELETE")) { emit("DELETE"); handler.postDelayed(this, 65); }
                else if (key.code.equals("SPACE")) p.cursorMode = true;
                else if (key.code.equals("SHIFT") || key.code.equals("LANGUAGE")) listener.longKey(key.code);
                else showVariants(p);
                invalidate();
            }
        };
        handler.postDelayed(p.hold, key.code.equals("DELETE") ? 320 : 450);
    }
    private void showVariants(Press p) {
        String code = p.initial.code;
        String alternatives;
        switch (code) {
            case "a": alternatives = "ă â á à ả ã ạ"; break;
            case "e": alternatives = "ê é è ẻ ẽ ẹ"; break;
            case "o": alternatives = "ô ơ ó ò ỏ õ ọ"; break;
            case "u": alternatives = "ư ú ù ủ ũ ụ"; break;
            case "i": alternatives = "í ì ỉ ĩ ị"; break;
            case "y": alternatives = "ý ỳ ỷ ỹ ỵ"; break;
            case "d": alternatives = "đ"; break;
            case "?": alternatives = ", ! ; :"; break;
            case ".": alternatives = ", … : ;"; break;
            default:
                String row = "qwertyuiop"; int at = row.indexOf(code);
                alternatives = at >= 0 ? String.valueOf((at + 1) % 10) : "1234567890".contains(code) ? "+ - / _" : "";
        }
        if (alternatives.isEmpty()) { p.held = false; return; }
        variants = alternatives.split(" "); variantIndex = 0; variantPointer = p.id;
        float width = Math.min(getWidth() - dp(6), dp(42) * variants.length);
        RectF key = rect(p.initial);
        float left = Math.max(dp(3), Math.min(getWidth() - width - dp(3), key.centerX() - width / 2));
        float top = Math.max(dp(1), key.top - dp(45));
        variantBounds = new RectF(left, top, left + width, top + dp(43));
    }
    private void move(Press p, float x, float y) {
        if (p.committed) return;
        KeyboardModel.Point point = point(x, y);
        if (p.path.size() < 180) p.path.add(point);
        if (variantPointer == p.id && variants != null) {
            variantIndex = Math.max(0, Math.min(variants.length - 1, (int) ((x - variantBounds.left) / (variantBounds.width() / variants.length)))); return;
        }
        if (p.initial.code.equals("SPACE") && Math.abs(point.x - p.start.x) > 0.4) p.cursorMode = true;
        if (p.cursorMode) {
            handler.removeCallbacks(p.hold);
            int steps = (int) Math.round((point.x - p.start.x) / 0.22);
            if (steps != p.cursorSteps) { listener.cursor(steps - p.cursorSteps); p.cursorSteps = steps; }
        } else if (Math.hypot(point.x - p.start.x, point.y - p.start.y) > 0.32) {
            p.current = model.hit(point.x, point.y); handler.removeCallbacks(p.hold);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked(), index = event.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) down(event, index);
        else if (action == MotionEvent.ACTION_MOVE) {
            for (Press p : new ArrayList<>(presses.values())) { int at = event.findPointerIndex(p.id); if (at >= 0) move(p, event.getX(at), event.getY(at)); }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            Press p = presses.get(event.getPointerId(index));
            if (p != null) {
                move(p, event.getX(index), event.getY(index)); handler.removeCallbacks(p.hold); presses.remove(p.id);
                if (variantPointer == p.id && variants != null) {
                    String selected = variants[variantIndex]; variants = null; variantBounds = null; variantPointer = -1;
                    emit(shifted ? selected.toUpperCase(Locale.ROOT) : selected);
                } else if (!p.committed && !p.held && !p.cursorMode && p.current != null) {
                    double travelled = 0;
                    for (int i = 1; i < p.path.size(); i++) travelled += Math.hypot(p.path.get(i).x - p.path.get(i - 1).x, p.path.get(i).y - p.path.get(i - 1).y);
                    if (!privateEditor && !numeric && symbols == 0 && prefs().getBoolean("swipe", false)
                            && p.initial.code.matches("[a-z]") && p.current.code.matches("[a-z]") && travelled > 2.3 && p.path.size() > 5) listener.gesture(p.path);
                    else emit(p.current.code);
                }
            }
        } else if (action == MotionEvent.ACTION_CANCEL) cancelPresses();
        invalidate(); return true;
    }
    private void cancelPresses() { for (Press p : presses.values()) handler.removeCallbacks(p.hold); presses.clear(); variants = null; variantBounds = null; variantPointer = -1; invalidate(); }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override protected void onDetachedFromWindow() { cancelPresses(); super.onDetachedFromWindow(); }

    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        return new AccessibilityNodeProvider() {
            @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int id) {
                AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
                if (id == View.NO_ID) { onInitializeAccessibilityNodeInfo(info); for (int i = 0; i < model.keys.size(); i++) info.addChild(KeyboardSurface.this, i); return info; }
                if (id < 0 || id >= model.keys.size()) return null;
                KeyboardModel.Key key = model.keys.get(id);
                info.setSource(KeyboardSurface.this, id); info.setParent(KeyboardSurface.this);
                info.setClassName("android.widget.Button"); info.setPackageName(getContext().getPackageName());
                info.setContentDescription(spoken(key.code)); info.setText(label(key.code));
                info.setEnabled(true); info.setVisibleToUser(isShown()); info.setClickable(true); info.setFocusable(true); info.setAccessibilityFocused(focused == id);
                Rect bounds = new Rect(); rect(key).roundOut(bounds); info.setBoundsInParent(bounds);
                int[] location = new int[2]; getLocationOnScreen(location); bounds.offset(location[0], location[1]); info.setBoundsInScreen(bounds);
                info.addAction(AccessibilityNodeInfo.ACTION_CLICK); info.addAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
                info.addAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS); info.addAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
                return info;
            }
            @Override public boolean performAction(int id, int action, Bundle args) {
                if (id < 0 || id >= model.keys.size()) return false;
                if (action == AccessibilityNodeInfo.ACTION_CLICK) { emit(model.keys.get(id).code); return true; }
                if (action == AccessibilityNodeInfo.ACTION_LONG_CLICK) { listener.longKey(model.keys.get(id).code); return true; }
                if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) { focused = id; invalidate(); return true; }
                if (action == AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS) { focused = -1; invalidate(); return true; }
                return false;
            }
        };
    }
    @Override public boolean onHoverEvent(MotionEvent event) {
        AccessibilityManager manager = (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager != null && manager.isTouchExplorationEnabled()) {
            KeyboardModel.Point point = point(event.getX(), event.getY()); KeyboardModel.Key key = model.hit(point.x, point.y);
            if (key != null) {
                int id = model.keys.indexOf(key);
                if (focused != id) {
                    focused = id; AccessibilityEvent e = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
                    e.setSource(this, id); e.setPackageName(getContext().getPackageName()); e.setClassName("android.widget.Button"); e.setContentDescription(spoken(key.code));
                    if (getParent() != null) getParent().requestSendAccessibilityEvent(this, e);
                }
            }
            return true;
        }
        return super.onHoverEvent(event);
    }
}
