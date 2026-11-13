package com.ngoctho.aitranslator;
import android.app.Activity;import android.content.*;import android.media.projection.*;import android.net.Uri;import android.os.*;import android.provider.Settings;import android.widget.Toast;
/** User-initiated capture permission entry point from the IME; no bubble required. */
public final class ScreenCaptureActivity extends Activity {
 private boolean overlayPending,capturePending;
 @Override public void onCreate(Bundle state){super.onCreate(state);
  if(!PrivacyConsent.allowed(this)){Toast.makeText(this,"Mở Quyền riêng tư trong ứng dụng để đồng ý dịch trực tuyến.",Toast.LENGTH_LONG).show();startActivity(new Intent(this,MainActivity.class));finish();return;}
  if(!Settings.canDrawOverlays(this)){overlayPending=true;startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())),41);Toast.makeText(this,"Bật hiển thị trên ứng dụng khác để xem bản dịch, rồi quay lại.",Toast.LENGTH_LONG).show();}else request();
 }
 private void request(){if(capturePending||isFinishing())return;capturePending=true;MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
  try{Intent capture=Build.VERSION.SDK_INT>=34?manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()):manager.createScreenCaptureIntent();startActivityForResult(capture,40);}catch(Exception e){Toast.makeText(this,"Không mở được quyền chụp màn hình.",Toast.LENGTH_LONG).show();finish();}
 }
 @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==41){overlayPending=false;if(Settings.canDrawOverlays(this))request();else{Toast.makeText(this,"Chưa cấp quyền hiển thị bản dịch.",Toast.LENGTH_LONG).show();finish();}return;}if(request!=40)return;
  if(result==RESULT_OK&&data!=null){Intent service=new Intent(this,ScreenTranslateService.class).putExtra(ScreenTranslateService.EXTRA_RESULT_CODE,result).putExtra(ScreenTranslateService.EXTRA_RESULT_DATA,data).putExtra(ScreenTranslateService.EXTRA_CAPTURE_NOW,true);try{startForegroundService(service);}catch(Exception e){Toast.makeText(this,"Không mở được phiên dịch màn hình. Hãy mở lại app và thử lại.",Toast.LENGTH_LONG).show();}}
  else Toast.makeText(this,"Chưa cấp quyền chụp màn hình.",Toast.LENGTH_SHORT).show();finish();
 }
}
