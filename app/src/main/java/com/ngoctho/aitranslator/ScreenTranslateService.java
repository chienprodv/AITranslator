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
    private volatile long generation;
    private volatile boolean destroyed;
    private Bitmap frozen;
    private ScreenTranslationOverlayView canvas;
    private TextView layoutStatus;
    private boolean selectNext;
    private boolean active(long token){return !destroyed && token==generation;}
    private void closeLayout(){generation++;busy=false;hideResult();hideSelection();frozen=null;if(bubbleView!=null)bubbleView.setVisibility(View.VISIBLE);}


    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
    }

    @Override @SuppressWarnings("deprecation")
    public void onConfigurationChanged(android.content.res.Configuration config){
        super.onConfigurationChanged(config);closeLayout();
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();windowManager.getDefaultDisplay().getRealMetrics(metrics);
        screenWidth=metrics.widthPixels;screenHeight=metrics.heightPixels;
        if(bubbleParams!=null&&bubbleView!=null){bubbleParams.x=ScreenLayoutGeometry.bubbleEdge(bubbleParams.x,screenWidth,dp(44));bubbleParams.y=clamp(bubbleParams.y,dp(24),Math.max(dp(24),screenHeight-dp(76)));try{windowManager.updateViewLayout(bubbleView,bubbleParams);}catch(Exception ignored){}}
        if(Build.VERSION.SDK_INT<34&&virtualDisplay!=null){try{ImageReader old=imageReader;imageReader=ImageReader.newInstance(screenWidth,screenHeight,PixelFormat.RGBA_8888,2);virtualDisplay.resize(screenWidth,screenHeight,densityDpi);virtualDisplay.setSurface(imageReader.getSurface());if(old!=null)old.close();}catch(Exception e){stopSelf();}}
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
            if(mediaProjection==null) startProjection(resultCode, resultData);
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
                .setContentTitle("XinayuAITranslator")
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
            public void onCapturedContentResize(int width,int height){
                if(width<=0||height<=0||virtualDisplay==null)return;
                closeLayout();
                try{ImageReader old=imageReader;imageReader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2);
                    virtualDisplay.resize(width,height,densityDpi);virtualDisplay.setSurface(imageReader.getSurface());if(old!=null)old.close();
                }catch(Exception e){stopSelf();}
            }
            @Override public void onStop() {
                main.post(() -> stopSelf());
            }
        }, main);

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "XinayuScreenTranslate",
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
        bubble.setTextSize(19);
        bubble.setTextColor(Color.WHITE);
        bubble.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable circle=new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);circle.setColor(0xdd163b57);circle.setStroke(dp(1),0xff6bd7ef);bubble.setBackground(circle);
        FrameLayout touch=new FrameLayout(this);touch.addView(bubble,new FrameLayout.LayoutParams(dp(32),dp(32),Gravity.CENTER));
        bubble.setContentDescription("Dịch màn hình. Nhấn để dịch toàn màn hình, nhấn giữ để dừng.");

        bubbleParams = new WindowManager.LayoutParams(
                dp(44), dp(44),
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

        touch.setOnTouchListener((v, event) -> {
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
                    bubbleParams.x = clamp(startPos[0] + dx, 0, Math.max(0, screenWidth - dp(44)));
                    bubbleParams.y = clamp(startPos[1] + dy, 0, Math.max(0, screenHeight - dp(44)));
                    try { windowManager.updateViewLayout(bubbleView, bubbleParams); } catch (Exception ignored) {}
                    return true;
                case MotionEvent.ACTION_UP:
                    long held = System.currentTimeMillis() - downTime[0];
                    if (!moved[0] && held >= 700) {
                        Toast.makeText(this, "Đã dừng dịch màn hình.", Toast.LENGTH_SHORT).show();
                        stopSelf();
                    } else if (!moved[0]) {
                        selectNext=false;captureForRegionSelection();
                    }
                    if(moved[0]){bubbleParams.x=bubbleParams.x+dp(22)<screenWidth/2?0:Math.max(0,screenWidth-dp(44));try{windowManager.updateViewLayout(bubbleView,bubbleParams);}catch(Exception ignored){}}
                    return true;
                default:
                    return false;
            }
        });

        bubbleView = touch;
        windowManager.addView(bubbleView, bubbleParams);
    }

    private void captureForRegionSelection() {
        if (busy || imageReader == null) return;
        busy = true;
        final long token=++generation;
        hideResult();hideSelection();frozen=null;
        try{Image stale=imageReader.acquireLatestImage();if(stale!=null)stale.close();}catch(Exception ignored){}
        if (bubbleView != null) bubbleView.setVisibility(View.INVISIBLE);

        main.postDelayed(() -> {
            if(!active(token))return;
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
            if(selectNext)showRegionSelector(bitmap);else{selectionBitmap=bitmap;processSelectedBitmap(null);}
        }, 250);
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
        Bitmap base=selectionBitmap;selectionBitmap=null;removeSelectionViewOnly();
        if(base==null||base.isRecycled()){finishBusyWithError("Ảnh chụp đã mất, hãy thử lại.");return;}
        frozen=base;final long token=++generation;busy=true;
        if(bubbleView!=null)bubbleView.setVisibility(View.INVISIBLE);
        Rect crop=rect==null?new Rect(0,0,base.getWidth(),base.getHeight()):new Rect(rect);
        crop.intersect(0,0,base.getWidth(),base.getHeight());
        showLayout(base,"Đang đọc chữ Trung…");
        final Bitmap input;
        try{Bitmap cut=Bitmap.createBitmap(base,crop.left,crop.top,crop.width(),crop.height());
            Bitmap scaled=downscaleForOcr(cut);input=scaled==base?base.copy(Bitmap.Config.ARGB_8888,false):scaled;
            if(cut!=base&&cut!=input)cut.recycle();
        }catch(Exception e){busy=false;layoutStatus.setText("Không cắt được ảnh: "+safeMessage(e));return;}
        OcrEngine.recognizeRegions(input,new OcrEngine.RegionCallback(){
            public void onSuccess(java.util.List<ScreenRegion> regions){
                input.recycle();if(!active(token))return;
                java.util.List<ScreenRegion> mapped=new java.util.ArrayList<>();
                for(ScreenRegion r:regions)mapped.add(r.remap(crop.left/(double)base.getWidth(),crop.top/(double)base.getHeight(),crop.width()/(double)base.getWidth(),crop.height()/(double)base.getHeight()));
                java.util.List<ScreenRegion> batch=ScreenLayoutBatch.select(mapped);
                layoutStatus.setText("Đang dịch "+batch.size()+" khối chữ…");
                ScreenLayoutTranslationEngine.translate(ScreenTranslateService.this,batch,()->active(token),new ScreenLayoutTranslationEngine.Callback(){
                    public void success(java.util.List<ScreenRegion> values,String warning,boolean cached){
                        busy=false;canvas.regions(values);layoutStatus.setText((cached?"⚡ ":"")+"Chạm khối chữ để xem đầy đủ. Đóng để cuộn trang."+(warning.isEmpty()?"":"\n⚠ "+warning));
                    }
                    public void error(String message){busy=false;layoutStatus.setText("Lỗi: "+message);}
                });
            }
            public void onError(String message){input.recycle();if(active(token)){busy=false;layoutStatus.setText("OCR: "+message);}}
        });
    }

    private void showLayout(Bitmap image,String message){
        hideResult();FrameLayout frame=new FrameLayout(this);
        canvas=new ScreenTranslationOverlayView(this,r->showResult(r.source,r.target));canvas.frame(image);
        frame.addView(canvas,new FrameLayout.LayoutParams(-1,-1));
        layoutStatus=new TextView(this);layoutStatus.setText(message);layoutStatus.setTextColor(Color.WHITE);layoutStatus.setTextSize(13);layoutStatus.setBackgroundColor(0xdd101820);layoutStatus.setPadding(dp(8),dp(24),dp(8),dp(8));
        frame.addView(layoutStatus,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        LinearLayout bar=new LinearLayout(this);bar.setBackgroundColor(0xee101820);bar.setPadding(dp(4),dp(4),dp(4),dp(28));
        Button original=overlayButton("Giữ xem gốc");original.setOnTouchListener((v,e)->{if(canvas==null)return false;canvas.original(e.getActionMasked()==MotionEvent.ACTION_DOWN||e.getActionMasked()==MotionEvent.ACTION_MOVE);return true;});
        original.setOnClickListener(v->{if(canvas!=null)canvas.original(!canvas.original());});
        bar.addView(original,new LinearLayout.LayoutParams(0,dp(48),1));
        Button refresh=overlayButton("Dịch lại");refresh.setOnClickListener(v->{if(!busy){closeLayout();selectNext=false;captureForRegionSelection();}});bar.addView(refresh,new LinearLayout.LayoutParams(0,dp(48),1));
        Button select=overlayButton("Chọn vùng");select.setOnClickListener(v->{if(!busy&&frozen!=null){Bitmap retained=frozen;generation++;hideResult();busy=true;showRegionSelector(retained);}});bar.addView(select,new LinearLayout.LayoutParams(0,dp(48),1));
        Button close=overlayButton("Đóng");close.setOnClickListener(v->closeLayout());bar.addView(close,new LinearLayout.LayoutParams(0,dp(48),1));
        frame.addView(bar,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.START;
        if(Build.VERSION.SDK_INT>=28)params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        selectionOverlay=frame;windowManager.addView(frame,params);
        frame.setFocusableInTouchMode(true);frame.requestFocus();frame.setOnKeyListener((v,key,event)->{if(key==android.view.KeyEvent.KEYCODE_BACK){if(event.getAction()==android.view.KeyEvent.ACTION_UP)closeLayout();return true;}return false;});
    }

    private Bitmap acquireLatestBitmap() {
        Image image = imageReader.acquireLatestImage();
        if (image == null) return null;
        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int width=image.getWidth(),height=image.getHeight();
            int rowPadding = rowStride - pixelStride * width;
            int paddedWidth = width + rowPadding / pixelStride;

            Bitmap padded = Bitmap.createBitmap(paddedWidth,height,Bitmap.Config.ARGB_8888);
            ByteBuffer complete=ByteBuffer.allocateDirect(paddedWidth*height*4);complete.put(buffer);complete.rewind();padded.copyPixelsFromBuffer(complete);
            if (paddedWidth == width) return padded;

            Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, width, height);
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
        again.setText("Dịch lại");
        again.setAllCaps(false);
        again.setOnClickListener(v -> {
            closeLayout();selectNext=true;
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
            if(canvas!=null){canvas.release();canvas=null;}
        }
    }

    private void hideSelection() {
        removeSelectionViewOnly();
        if (selectionBitmap != null) {
            // Let the removed view release its bitmap reference before garbage collection.
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
        destroyed=true;generation++;main.removeCallbacksAndMessages(null);
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
