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
    public static final String ACTION_TRANSLATE="com.ngoctho.aitranslator.TRANSLATE_SCREEN";
    public static final String EXTRA_CAPTURE_NOW="capture_now";
    private boolean bubbleEnabled;private TextView fullText;private String fullTranslation="";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    public static volatile boolean running = false;
    public static volatile String lastStatus="Chưa bật";

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
    private int captureWidth,captureHeight;
    private void stage(String text){lastStatus=text;if(layoutStatus!=null)layoutStatus.setText(text);}
    private void feedback(String text){stage(text);Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    private boolean busy;
    private volatile long generation;
    private volatile boolean destroyed;
    private Bitmap frozen;
    private ScreenTranslationOverlayView canvas;
    private TextView layoutStatus;
    private boolean selectNext;
    private boolean active(long token){return !destroyed && token==generation;}
    private void closeLayout(){generation++;busy=false;hideResult();hideSelection();frozen=null;stage("Sẵn sàng · nhấn 译");if(bubbleView!=null)bubbleView.setVisibility(View.VISIBLE);}


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

        if(ACTION_TRANSLATE.equals(intent.getAction())&&mediaProjection!=null){selectNext=false;main.postDelayed(this::captureForRegionSelection,450);return START_NOT_STICKY;}
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
            bubbleEnabled=!intent.getBooleanExtra(EXTRA_CAPTURE_NOW,false);
            if(bubbleEnabled)showBubble();
            if(intent.getBooleanExtra(EXTRA_CAPTURE_NOW,false)){selectNext=false;main.postDelayed(this::captureForRegionSelection,650);}
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
                .setContentText("Dịch màn hình đang hoạt động · bấm 译 trên bàn phím")
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
                if(width<=0||height<=0||virtualDisplay==null||(width==captureWidth&&height==captureHeight))return;
                captureWidth=width;captureHeight=height;
                closeLayout();
                try{ImageReader old=imageReader;imageReader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2);
                    virtualDisplay.resize(width,height,densityDpi);virtualDisplay.setSurface(imageReader.getSurface());if(old!=null)old.close();
                }catch(Exception e){stopSelf();}
            }
            @Override public void onStop() {
                main.post(() -> stopSelf());
            }
        }, main);

        captureWidth=screenWidth;captureHeight=screenHeight;
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
        if(!bubbleEnabled||bubbleView!=null)return;
        TextView bubble = new TextView(this);
        bubble.setText("译");
        bubble.setTextSize(19);
        bubble.setTextColor(Color.WHITE);
        bubble.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable circle=new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);circle.setColor(0xdd163b57);circle.setStroke(dp(1),0xff6bd7ef);bubble.setBackground(circle);
        bubble.setBackground(new android.graphics.drawable.InsetDrawable(circle,dp(6)));
        bubble.setPadding(0,0,0,0);
        bubble.setContentDescription("Dịch màn hình. Nhấn để dịch toàn màn hình, kéo để di chuyển; giữ để dừng.");
        bubble.setOnClickListener(v->{selectNext=false;captureForRegionSelection();});

        bubbleParams = new WindowManager.LayoutParams(
                dp(44), dp(44),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = Math.max(0, screenWidth - dp(80));
        bubbleParams.y = dp(160);

        final float[] downRaw=new float[2];final int[] startPos=new int[2];
        final BubbleGesture gesture=new BubbleGesture(Math.max(dp(12),android.view.ViewConfiguration.get(this).getScaledTouchSlop()));
        bubble.setOnTouchListener((v,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    downRaw[0]=event.getRawX();downRaw[1]=event.getRawY();startPos[0]=bubbleParams.x;startPos[1]=bubbleParams.y;
                    gesture.begin(event.getRawX(),event.getRawY(),event.getEventTime());v.setPressed(true);return true;
                case MotionEvent.ACTION_MOVE:
                    if(gesture.move(event.getRawX(),event.getRawY())){
                        bubbleParams.x=clamp(startPos[0]+Math.round(event.getRawX()-downRaw[0]),0,Math.max(0,screenWidth-dp(44)));
                        bubbleParams.y=clamp(startPos[1]+Math.round(event.getRawY()-downRaw[1]),dp(24),Math.max(dp(24),screenHeight-dp(76)));
                        try{windowManager.updateViewLayout(bubbleView,bubbleParams);}catch(Exception ignored){}
                    }return true;
                case MotionEvent.ACTION_UP:
                    v.setPressed(false);BubbleGesture.End end=gesture.end(event.getRawX(),event.getRawY(),event.getEventTime());
                    if(end==BubbleGesture.End.TAP)v.performClick();
                    else if(end==BubbleGesture.End.HOLD){feedback("Đã dừng dịch màn hình.");stopSelf();}
                    else if(end==BubbleGesture.End.DRAG){bubbleParams.x=ScreenLayoutGeometry.bubbleEdge(bubbleParams.x,screenWidth,dp(44));try{windowManager.updateViewLayout(bubbleView,bubbleParams);}catch(Exception ignored){}}
                    return true;
                case MotionEvent.ACTION_CANCEL:gesture.cancel();v.setPressed(false);return true;
                default:return true;
            }
        });
        bubbleView=bubble;stage("Sẵn sàng · nhấn 译");
        windowManager.addView(bubbleView, bubbleParams);
    }

    private void captureForRegionSelection() {
        if(busy){feedback("Đang xử lý. Bấm Đóng trên ảnh để hủy trước khi dịch lại.");return;}
        if(imageReader==null||mediaProjection==null){feedback("Phiên chụp đã dừng. Mở ứng dụng, tắt rồi bật lại dịch màn hình.");return;}
        busy=true;final long token=++generation;hideResult();hideSelection();frozen=null;
        stage("Đang chụp màn hình…");
        if(bubbleView!=null)bubbleView.setVisibility(View.INVISIBLE);
        // Keep the latest available frame: static app-only captures may not produce a new one after hiding the bubble.
        main.postDelayed(()->takeFrame(token,0),200);
        main.postDelayed(()->{if(active(token)&&busy){generation++;finishBusyWithError("Quá thời gian chụp/OCR/dịch. Không tự gọi lại API. Hãy thử lại hoặc bật lại phiên chụp.");}},45000);
    }
    private void takeFrame(long token,int attempt){
        if(!active(token))return;
        try{
            Bitmap bitmap=acquireLatestBitmap();
            if(bitmap==null){if(attempt<8){main.postDelayed(()->takeFrame(token,attempt+1),100);return;}finishBusyWithError("Không nhận được ảnh chụp. Mở lại phiên dịch và chọn chia sẻ toàn màn hình.");return;}
            if(selectNext)showRegionSelector(bitmap);else{selectionBitmap=bitmap;processSelectedBitmap(null);}
        }catch(Exception e){finishBusyWithError("Không mở được ảnh dịch: "+safeMessage(e));}
    }

    private void showRegionSelector(Bitmap bitmap){selectionBitmap=bitmap;processSelectedBitmap(null);}
    private void processSelectedBitmap(Rect ignored){
        Bitmap base=selectionBitmap;selectionBitmap=null;removeSelectionViewOnly();
        if(base==null||base.isRecycled()){finishBusyWithError("Ảnh chụp đã mất, hãy thử lại.");return;}
        frozen=base;busy=true;final long token=++generation;fullTranslation="";
        showFullScreen(base);stage("Đang đọc toàn bộ chữ trên màn hình…");
        main.postDelayed(()->{if(active(token)&&busy){generation++;busy=false;stage("Đã dừng chờ. Bản dịch có thể chưa đủ; không tự gọi lại API.");}},180000);
        Bitmap scaled=downscaleForOcr(base);final Bitmap input=scaled==base?base.copy(Bitmap.Config.ARGB_8888,false):scaled;
        try{OcrEngine.recognizeChineseScreen(input,new OcrEngine.Callback(){
            public void onSuccess(String source){input.recycle();if(!active(token))return;
                ScreenFullTranslationEngine.translate(ScreenTranslateService.this,source,()->active(token),new ScreenFullTranslationEngine.Callback(){
                    public void progress(int done,int total,String translated){fullTranslation=translated;fullText.setText(translated);stage("Đã dịch "+done+"/"+total+" phần…");}
                    public void success(String translated,String warning){busy=false;fullTranslation=translated;fullText.setText(translated);stage("Đã dịch toàn bộ chữ OCR"+(warning.isEmpty()?"":"\n⚠ "+warning));}
                    public void error(String message,String partial){busy=false;fullTranslation=partial;fullText.setText(partial.isEmpty()?"Chưa có bản dịch.":partial);stage("Chưa dịch hết: "+message);}
                });
            }
            public void onError(String message){input.recycle();if(active(token)){busy=false;stage("Không đọc được chữ: "+message);}}
        });}catch(Exception e){if(!input.isRecycled())input.recycle();busy=false;stage("Không mở được OCR: "+safeMessage(e));}
    }
    private void showFullScreen(Bitmap bitmap){
        hideResult();FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(0xfffafafa);
        ImageView original=new ImageView(this);original.setImageBitmap(bitmap);original.setScaleType(ImageView.ScaleType.FIT_CENTER);original.setVisibility(View.GONE);frame.addView(original,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(12),dp(28),dp(12),dp(30));content.setBackgroundColor(0xf8fafafa);
        layoutStatus=new TextView(this);layoutStatus.setTextSize(14);layoutStatus.setTextColor(0xff254e67);content.addView(layoutStatus);
        ScrollView scroll=new ScrollView(this);fullText=new TextView(this);fullText.setText("Đang đọc ảnh chụp…");fullText.setTextSize(17);fullText.setTextColor(0xff152633);fullText.setTextIsSelectable(true);fullText.setPadding(0,dp(12),0,dp(12));scroll.addView(fullText);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout buttons=new LinearLayout(this);
        Button source=overlayButton("Ảnh gốc");source.setOnClickListener(v->{boolean show=original.getVisibility()!=View.VISIBLE;original.setVisibility(show?View.VISIBLE:View.GONE);scroll.setVisibility(show?View.INVISIBLE:View.VISIBLE);content.setBackgroundColor(show?Color.TRANSPARENT:0xf8fafafa);source.setText(show?"Bản dịch":"Ảnh gốc");});buttons.addView(source,new LinearLayout.LayoutParams(0,dp(48),1));
        Button copy=overlayButton("Sao chép");copy.setOnClickListener(v->{if(!fullTranslation.isEmpty())((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Bản dịch màn hình",fullTranslation));});buttons.addView(copy,new LinearLayout.LayoutParams(0,dp(48),1));
        Button refresh=overlayButton("Dịch lại");refresh.setOnClickListener(v->{if(!busy){closeLayout();selectNext=false;captureForRegionSelection();}});buttons.addView(refresh,new LinearLayout.LayoutParams(0,dp(48),1));
        Button close=overlayButton("Đóng");close.setOnClickListener(v->closeLayout());buttons.addView(close,new LinearLayout.LayoutParams(0,dp(48),1));content.addView(buttons);frame.addView(content,new FrameLayout.LayoutParams(-1,-1));
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);params.gravity=Gravity.TOP|Gravity.START;
        selectionOverlay=frame;windowManager.addView(frame,params);frame.setFocusableInTouchMode(true);frame.requestFocus();frame.setOnKeyListener((v,key,event)->{if(key==android.view.KeyEvent.KEYCODE_BACK){if(event.getAction()==android.view.KeyEvent.ACTION_UP)closeLayout();return true;}return false;});
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
        stage(message);Toast.makeText(this,message,Toast.LENGTH_LONG).show();
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
            closeLayout();selectNext=false;
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
            if(canvas!=null){canvas.release();canvas=null;}layoutStatus=null;fullText=null;
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
        running = false;lastStatus="Đã dừng";
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
