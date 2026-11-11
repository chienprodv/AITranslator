package com.ngoctho.aitranslator;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;

public class ScreenTranslateService extends Service {
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    public static volatile boolean running = false;

    private static final String CHANNEL_ID = "screen_translate";
    private static final int NOTIFICATION_ID = 2002;

    private final Handler main = new Handler(Looper.getMainLooper());

    private WindowManager windowManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private View bubbleView;
    private WindowManager.LayoutParams bubbleParams;
    private View resultView;
    private View selectionOverlay;
    private Bitmap selectionBitmap;

    private int screenWidth;
    private int screenHeight;
    private int densityDpi;
    private boolean busy;

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Integer.MIN_VALUE);
        Intent resultData = getProjectionData(intent);
        if (resultCode == Integer.MIN_VALUE || resultData == null) {
            Toast.makeText(this, "Thiếu quyền chụp màn hình.", Toast.LENGTH_SHORT).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Cần quyền hiển thị bong bóng trên ứng dụng khác.", Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            startProjection(resultCode, resultData);
            showBubble();
        } catch (Exception e) {
            Toast.makeText(this, "Không khởi động được dịch màn hình: " + safeMessage(e), Toast.LENGTH_LONG).show();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @SuppressWarnings("deprecation")
    private Intent getProjectionData(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        }
        return intent.getParcelableExtra(EXTRA_RESULT_DATA);
    }

    private void startAsForeground() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(
                this, 10, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Ngọc Thọ AI Translator")
                .setContentText("Dịch màn hình đang hoạt động · nhấn bong bóng 译")
                .setContentIntent(pending)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Dịch màn hình", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Thông báo khi dịch màn hình đang chạy.");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }
    }

    @SuppressWarnings("deprecation")
    private void startProjection(int resultCode, Intent resultData) {
        releaseProjection();

        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(dm);
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;
        densityDpi = dm.densityDpi;

        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = manager.getMediaProjection(resultCode, resultData);
        if (mediaProjection == null) throw new IllegalStateException("Không tạo được MediaProjection.");

        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                main.post(() -> stopSelf());
            }
        }, main);

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "NgocThoScreenTranslate",
                screenWidth,
                screenHeight,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                main);
    }

    private void showBubble() {
        if (bubbleView != null) return;
        TextView bubble = new TextView(this);
        bubble.setText("译");
        bubble.setTextSize(24);
        bubble.setTextColor(Color.WHITE);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackgroundResource(android.R.drawable.btn_default);
        bubble.setContentDescription("Dịch màn hình. Nhấn để chọn vùng dịch, nhấn giữ để dừng.");

        bubbleParams = new WindowManager.LayoutParams(
                dp(58), dp(58),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = Math.max(0, screenWidth - dp(80));
        bubbleParams.y = dp(160);

        final float[] downRaw = new float[2];
        final int[] startPos = new int[2];
        final boolean[] moved = new boolean[1];
        final long[] downTime = new long[1];

        bubble.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRaw[0] = event.getRawX();
                    downRaw[1] = event.getRawY();
                    startPos[0] = bubbleParams.x;
                    startPos[1] = bubbleParams.y;
                    downTime[0] = System.currentTimeMillis();
                    moved[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = Math.round(event.getRawX() - downRaw[0]);
                    int dy = Math.round(event.getRawY() - downRaw[1]);
                    if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) moved[0] = true;
                    bubbleParams.x = clamp(startPos[0] + dx, 0, Math.max(0, screenWidth - dp(58)));
                    bubbleParams.y = clamp(startPos[1] + dy, 0, Math.max(0, screenHeight - dp(58)));
                    try { windowManager.updateViewLayout(bubbleView, bubbleParams); } catch (Exception ignored) {}
                    return true;
                case MotionEvent.ACTION_UP:
                    long held = System.currentTimeMillis() - downTime[0];
                    if (!moved[0] && held >= 700) {
                        Toast.makeText(this, "Đã dừng dịch màn hình.", Toast.LENGTH_SHORT).show();
                        stopSelf();
                    } else if (!moved[0]) {
                        captureForRegionSelection();
                    }
                    return true;
                default:
                    return false;
            }
        });

        bubbleView = bubble;
        windowManager.addView(bubbleView, bubbleParams);
    }

    private void captureForRegionSelection() {
        if (busy || imageReader == null) return;
        busy = true;
        hideResult();
        if (bubbleView != null) bubbleView.setVisibility(View.INVISIBLE);

        main.postDelayed(() -> {
            Bitmap bitmap;
            try {
                bitmap = acquireLatestBitmap();
            } catch (Exception e) {
                finishBusyWithError("Không chụp được màn hình: " + safeMessage(e));
                return;
            }
            if (bitmap == null) {
                finishBusyWithError("Chưa lấy được khung hình. Hãy bấm lại bong bóng.");
                return;
            }
            showRegionSelector(bitmap);
        }, 180);
    }

    private void showRegionSelector(Bitmap bitmap) {
        hideSelection();
        selectionBitmap = bitmap;

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);

        ImageView screenshot = new ImageView(this);
        screenshot.setImageBitmap(bitmap);
        screenshot.setScaleType(ImageView.ScaleType.FIT_XY);
        frame.addView(screenshot, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        RegionSelectionView selector = new RegionSelectionView(this);
        frame.addView(selector, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(dp(8), dp(6), dp(8), dp(8));
        controls.setBackgroundColor(0xEE111111);

        Button cancel = overlayButton("Hủy");
        cancel.setOnClickListener(v -> {
            hideSelection();
            busy = false;
            if (bubbleView != null) bubbleView.setVisibility(View.VISIBLE);
        });
        controls.addView(cancel, new LinearLayout.LayoutParams(0, dp(50), 1f));

        Button full = overlayButton("Toàn màn hình");
        full.setOnClickListener(v -> processSelectedBitmap(null));
        controls.addView(full, new LinearLayout.LayoutParams(0, dp(50), 1.2f));

        Button region = overlayButton("DỊCH VÙNG");
        region.setOnClickListener(v -> processSelectedBitmap(selector.selectedRect(
                selectionBitmap.getWidth(), selectionBitmap.getHeight())));
        controls.addView(region, new LinearLayout.LayoutParams(0, dp(50), 1.2f));

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        frame.addView(controls, cp);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;

        selectionOverlay = frame;
        windowManager.addView(selectionOverlay, params);
    }

    private void processSelectedBitmap(Rect rect) {
        Bitmap base = selectionBitmap;
        selectionBitmap = null;
        removeSelectionViewOnly();
        if (bubbleView != null) bubbleView.setVisibility(View.VISIBLE);
        if (base == null || base.isRecycled()) {
            finishBusyWithError("Ảnh chụp đã mất, hãy thử lại.");
            return;
        }

        Bitmap selected = base;
        try {
            if (rect != null && rect.width() > 1 && rect.height() > 1 &&
                    (rect.width() != base.getWidth() || rect.height() != base.getHeight())) {
                selected = Bitmap.createBitmap(base, rect.left, rect.top, rect.width(), rect.height());
                base.recycle();
            }
        } catch (Exception e) {
            if (selected != null && !selected.isRecycled()) selected.recycle();
            finishBusyWithError("Không cắt được vùng đã chọn: " + safeMessage(e));
            return;
        }

        Bitmap ocrBitmap = downscaleForOcr(selected);
        if (ocrBitmap != selected) selected.recycle();
        runOcrAndTranslate(ocrBitmap);
    }

    private void runOcrAndTranslate(Bitmap ocrBitmap) {
        showResult("Đang đọc chữ trong vùng đã chọn…", "");
        OcrEngine.recognizeChineseScreen(ocrBitmap, new OcrEngine.Callback() {
            @Override
            public void onSuccess(String text) {
                if (!ocrBitmap.isRecycled()) ocrBitmap.recycle();
                String source = trimForUi(text, 1500);
                showResult("OCR:\n" + source, "Đang dịch…");
                ScreenTranslationEngine.translate(
                        ScreenTranslateService.this,
                        text,
                        new ScreenTranslationEngine.Callback() {
                            @Override
                            public void onSuccess(ScreenTranslationResult result, boolean fromCache) {
                                busy = false;
                                String extra = result.warning.isEmpty()
                                        ? (fromCache ? "⚡ Kết quả nhanh/cục bộ" : "")
                                        : "⚠ " + result.warning;
                                showResult(
                                        "OCR:\n" + trimForUi(result.source, 1500),
                                        "Tiếng Việt:\n" + result.vietnamese +
                                                (extra.isEmpty() ? "" : "\n\n" + extra));
                            }

                            @Override
                            public void onError(String message) {
                                busy = false;
                                showResult("OCR:\n" + source, "Lỗi dịch: " + message);
                            }
                        });
            }

            @Override
            public void onError(String message) {
                if (!ocrBitmap.isRecycled()) ocrBitmap.recycle();
                finishBusyWithError("OCR: " + message);
            }
        });
    }

    private Bitmap acquireLatestBitmap() {
        Image image = imageReader.acquireLatestImage();
        if (image == null) return null;
        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * screenWidth;
            int paddedWidth = screenWidth + rowPadding / pixelStride;

            Bitmap padded = Bitmap.createBitmap(paddedWidth, screenHeight, Bitmap.Config.ARGB_8888);
            padded.copyPixelsFromBuffer(buffer);
            if (paddedWidth == screenWidth) return padded;

            Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, screenWidth, screenHeight);
            padded.recycle();
            return cropped;
        } finally {
            image.close();
        }
    }

    private Bitmap downscaleForOcr(Bitmap input) {
        final int maxWidth = 1600;
        if (input.getWidth() <= maxWidth) return input;
        float scale = maxWidth / (float) input.getWidth();
        int h = Math.max(1, Math.round(input.getHeight() * scale));
        return Bitmap.createScaledBitmap(input, maxWidth, h, true);
    }

    private void finishBusyWithError(String message) {
        busy = false;
        hideSelection();
        if (bubbleView != null) bubbleView.setVisibility(View.VISIBLE);
        showResult("", message);
    }

    private void showResult(String sourceText, String translatedText) {
        hideResult();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);

        TextView title = new TextView(this);
        title.setText("DỊCH MÀN HÌNH");
        title.setTextSize(15);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        card.addView(title);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView source = new TextView(this);
        source.setText(sourceText);
        source.setTextSize(12);
        source.setTextIsSelectable(true);
        content.addView(source);

        TextView translated = new TextView(this);
        translated.setText(translatedText);
        translated.setTextSize(15);
        translated.setTextIsSelectable(true);
        translated.setPadding(0, dp(8), 0, dp(8));
        content.addView(translated);

        scroll.addView(content);
        card.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(270)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button copy = new Button(this);
        copy.setText("Sao chép");
        copy.setAllCaps(false);
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            String value = translatedText == null ? "" : translatedText;
            cm.setPrimaryClip(ClipData.newPlainText("Bản dịch", value));
            Toast.makeText(this, "Đã sao chép.", Toast.LENGTH_SHORT).show();
        });
        actions.addView(copy, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button again = new Button(this);
        again.setText("Chọn vùng khác");
        again.setAllCaps(false);
        again.setOnClickListener(v -> {
            hideResult();
            captureForRegionSelection();
        });
        actions.addView(again, new LinearLayout.LayoutParams(0, dp(44), 1.2f));

        Button close = new Button(this);
        close.setText("Đóng");
        close.setAllCaps(false);
        close.setOnClickListener(v -> hideResult());
        actions.addView(close, new LinearLayout.LayoutParams(0, dp(44), 0.8f));

        card.addView(actions);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                Math.max(dp(300), screenWidth - dp(24)),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dp(68);

        resultView = card;
        windowManager.addView(resultView, params);
    }

    private Button overlayButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        return b;
    }

    private void hideResult() {
        if (resultView != null) {
            try { windowManager.removeView(resultView); } catch (Exception ignored) {}
            resultView = null;
        }
    }

    private void removeSelectionViewOnly() {
        if (selectionOverlay != null) {
            try { windowManager.removeView(selectionOverlay); } catch (Exception ignored) {}
            selectionOverlay = null;
        }
    }

    private void hideSelection() {
        removeSelectionViewOnly();
        if (selectionBitmap != null) {
            if (!selectionBitmap.isRecycled()) selectionBitmap.recycle();
            selectionBitmap = null;
        }
    }

    private String trimForUi(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }

    private void releaseProjection() {
        if (virtualDisplay != null) {
            try { virtualDisplay.release(); } catch (Exception ignored) {}
            virtualDisplay = null;
        }
        if (imageReader != null) {
            try { imageReader.close(); } catch (Exception ignored) {}
            imageReader = null;
        }
        if (mediaProjection != null) {
            try { mediaProjection.stop(); } catch (Exception ignored) {}
            mediaProjection = null;
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        hideResult();
        hideSelection();
        if (bubbleView != null) {
            try { windowManager.removeView(bubbleView); } catch (Exception ignored) {}
            bubbleView = null;
        }
        releaseProjection();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
