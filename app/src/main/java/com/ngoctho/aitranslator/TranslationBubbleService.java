package com.ngoctho.aitranslator;
import android.app.*;import android.content.*;import android.content.pm.ServiceInfo;import android.graphics.*;import android.graphics.drawable.*;import android.os.*;import android.provider.Settings;import android.view.*;import android.widget.TextView;

/** User-enabled floating shortcut. Its lifetime is independent of screen-capture consent. */
public final class TranslationBubbleService extends Service {
 public static final String STOP="stop_bubble";private static TranslationBubbleService instance;
 private WindowManager manager;private TextView bubble;private WindowManager.LayoutParams params;private int width,height;private boolean capturing;
 public static void start(Context context){if(KeyboardData.prefs(context).getBoolean("screen_keep_bubble",true)&&Settings.canDrawOverlays(context))context.startForegroundService(new Intent(context,TranslationBubbleService.class));}
 public static void hideForCapture(){if(instance!=null){instance.capturing=true;if(instance.bubble!=null)instance.bubble.setVisibility(View.INVISIBLE);}}
 public static void restore(){if(instance!=null){instance.capturing=false;if(instance.bubble!=null)instance.bubble.setVisibility(View.VISIBLE);}}
 @Override public void onCreate(){super.onCreate();instance=this;manager=(WindowManager)getSystemService(WINDOW_SERVICE);}
 @Override public int onStartCommand(Intent intent,int flags,int id){
  if(intent!=null&&STOP.equals(intent.getAction())){disable();return START_NOT_STICKY;}
  if(!KeyboardData.prefs(this).getBoolean("screen_keep_bubble",true)||!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}
  try{foreground();show();}catch(Exception e){stopSelf();return START_NOT_STICKY;}return START_STICKY;
 }
 private void foreground(){NotificationManager notifications=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);notifications.createNotificationChannel(new NotificationChannel("translate_shortcut","Phím tắt dịch màn hình",NotificationManager.IMPORTANCE_LOW));
  PendingIntent translate=PendingIntent.getActivity(this,302,new Intent(this,ScreenCaptureActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  PendingIntent stop=PendingIntent.getService(this,303,new Intent(this,TranslationBubbleService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification notification=new Notification.Builder(this,"translate_shortcut").setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("译 Dịch màn hình").setContentText("Chạm để dịch · bong bóng độc lập với bàn phím").setContentIntent(translate).setOngoing(true)
   .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_view,"Dịch",translate).build()).addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Tắt bong bóng",stop).build()).build();
  if(Build.VERSION.SDK_INT>=34)startForeground(2003,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(2003,notification);
 }
 private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
 @SuppressWarnings("deprecation") private void metrics(){android.util.DisplayMetrics display=new android.util.DisplayMetrics();manager.getDefaultDisplay().getRealMetrics(display);width=display.widthPixels;height=display.heightPixels;}
 private void show(){if(bubble!=null)return;metrics();bubble=new TextView(this);bubble.setText("译");bubble.setTextSize(19);bubble.setTextColor(Color.WHITE);bubble.setGravity(Gravity.CENTER);bubble.setContentDescription("Dịch toàn màn hình; kéo để di chuyển; giữ để tắt bong bóng");
  GradientDrawable circle=new GradientDrawable();circle.setShape(GradientDrawable.OVAL);circle.setColor(0xdd163b57);circle.setStroke(dp(1),0xff6bd7ef);bubble.setBackground(new InsetDrawable(circle,dp(6)));
  params=new WindowManager.LayoutParams(dp(44),dp(44),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);params.gravity=Gravity.TOP|Gravity.START;
  params.x=KeyboardData.prefs(this).getInt("screen_bubble_x",Math.max(0,width-dp(44)));params.y=KeyboardData.prefs(this).getInt("screen_bubble_y",dp(160));clamp();
  bubble.setOnClickListener(v->{try{startActivity(new Intent(this,ScreenCaptureActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(Exception e){restore();}});
  final float[] down=new float[2];final int[] origin=new int[2];BubbleGesture gesture=new BubbleGesture(Math.max(dp(12),ViewConfiguration.get(this).getScaledTouchSlop()));
  bubble.setOnTouchListener((v,event)->{switch(event.getActionMasked()){
   case MotionEvent.ACTION_DOWN:down[0]=event.getRawX();down[1]=event.getRawY();origin[0]=params.x;origin[1]=params.y;gesture.begin(down[0],down[1],event.getEventTime());return true;
   case MotionEvent.ACTION_MOVE:if(gesture.move(event.getRawX(),event.getRawY())){params.x=origin[0]+Math.round(event.getRawX()-down[0]);params.y=origin[1]+Math.round(event.getRawY()-down[1]);clamp();update();}return true;
   case MotionEvent.ACTION_UP:BubbleGesture.End end=gesture.end(event.getRawX(),event.getRawY(),event.getEventTime());if(end==BubbleGesture.End.TAP)v.performClick();else if(end==BubbleGesture.End.HOLD){disable();}else if(end==BubbleGesture.End.DRAG){params.x=ScreenLayoutGeometry.bubbleEdge(params.x,width,dp(44));update();KeyboardData.prefs(this).edit().putInt("screen_bubble_x",params.x).putInt("screen_bubble_y",params.y).apply();}return true;
   case MotionEvent.ACTION_CANCEL:gesture.cancel();return true;default:return true;
  }});manager.addView(bubble,params);if(capturing)bubble.setVisibility(View.INVISIBLE);
 }
 private void clamp(){params.x=Math.max(0,Math.min(Math.max(0,width-dp(44)),params.x));params.y=Math.max(dp(24),Math.min(Math.max(dp(24),height-dp(76)),params.y));}
 private void update(){try{manager.updateViewLayout(bubble,params);}catch(Exception ignored){}}
 private void disable(){KeyboardData.prefs(this).edit().putBoolean("screen_keep_bubble",false).apply();stopSelf();}
 @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);if(bubble!=null){metrics();clamp();update();}}
 @Override public void onDestroy(){if(bubble!=null)try{manager.removeView(bubble);}catch(Exception ignored){}bubble=null;if(instance==this)instance=null;stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
 @Override public IBinder onBind(Intent intent){return null;}
}
