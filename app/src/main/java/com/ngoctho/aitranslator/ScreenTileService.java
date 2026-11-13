package com.ngoctho.aitranslator;
import android.app.PendingIntent;import android.content.Intent;import android.os.Build;import android.service.quicksettings.*;
/** Independent screen translation entry in Android Quick Settings. */
public final class ScreenTileService extends TileService {
 @Override public void onStartListening(){super.onStartListening();Tile tile=getQsTile();if(tile!=null){tile.setLabel("译 Dịch màn hình");tile.setContentDescription("Dịch toàn màn hình không cần mở bàn phím");tile.setState(Tile.STATE_INACTIVE);tile.updateTile();}}
 @Override public void onClick(){super.onClick();if(isLocked())unlockAndRun(this::launch);else launch();}
 @SuppressWarnings("deprecation") private void launch(){Intent intent=new Intent(this,ScreenCaptureActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  if(Build.VERSION.SDK_INT>=34)startActivityAndCollapse(PendingIntent.getActivity(this,301,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));else startActivityAndCollapse(intent);
 }
}
