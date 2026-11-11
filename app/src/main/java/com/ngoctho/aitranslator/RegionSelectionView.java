package com.ngoctho.aitranslator;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Touch overlay used to drag a rectangular OCR region over a captured screenshot. */
public final class RegionSelectionView extends View {
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF selection = new RectF();

    private float downX;
    private float downY;
    private boolean touched;

    public RegionSelectionView(Context context) {
        super(context);
        shade.setColor(0x77000000);
        border.setColor(0xFFFFFFFF);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(2));
        labelBg.setColor(0xCC111111);
        label.setColor(0xFFFFFFFF);
        label.setTextSize(dp(14));
        setFocusable(true);
        setContentDescription("Kéo để chọn vùng cần dịch");
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (!touched && w > 0 && h > 0) {
            selection.set(w * 0.07f, h * 0.20f, w * 0.93f, h * 0.76f);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        RectF r = normalized();

        canvas.drawRect(0, 0, w, r.top, shade);
        canvas.drawRect(0, r.bottom, w, h, shade);
        canvas.drawRect(0, r.top, r.left, r.bottom, shade);
        canvas.drawRect(r.right, r.top, w, r.bottom, shade);
        canvas.drawRect(r, border);

        String text = "Kéo để chọn vùng chữ cần dịch";
        float tw = label.measureText(text);
        float lx = Math.max(dp(8), Math.min(r.left, w - tw - dp(20)));
        float ly = Math.max(dp(28), r.top - dp(10));
        canvas.drawRoundRect(lx - dp(6), ly - dp(20), lx + tw + dp(6), ly + dp(5), dp(6), dp(6), labelBg);
        canvas.drawText(text, lx, ly, label);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touched = true;
                downX = clamp(event.getX(), 0, getWidth());
                downY = clamp(event.getY(), 0, getHeight());
                selection.set(downX, downY, downX, downY);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
                float x = clamp(event.getX(), 0, getWidth());
                float y = clamp(event.getY(), 0, getHeight());
                selection.set(downX, downY, x, y);
                invalidate();
                return true;
            default:
                return true;
        }
    }

    public Rect selectedRect(int bitmapWidth, int bitmapHeight) {
        RectF r = normalized();
        if (r.width() < dp(32) || r.height() < dp(32) || getWidth() <= 0 || getHeight() <= 0) {
            return new Rect(0, 0, bitmapWidth, bitmapHeight);
        }
        float sx = bitmapWidth / (float) getWidth();
        float sy = bitmapHeight / (float) getHeight();
        int left = clamp(Math.round(r.left * sx), 0, Math.max(0, bitmapWidth - 1));
        int top = clamp(Math.round(r.top * sy), 0, Math.max(0, bitmapHeight - 1));
        int right = clamp(Math.round(r.right * sx), left + 1, bitmapWidth);
        int bottom = clamp(Math.round(r.bottom * sy), top + 1, bitmapHeight);
        return new Rect(left, top, right, bottom);
    }

    private RectF normalized() {
        float left = Math.min(selection.left, selection.right);
        float right = Math.max(selection.left, selection.right);
        float top = Math.min(selection.top, selection.bottom);
        float bottom = Math.max(selection.top, selection.bottom);
        return new RectF(left, top, right, bottom);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
