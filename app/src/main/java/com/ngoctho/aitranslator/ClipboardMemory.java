package com.ngoctho.aitranslator;
import android.content.*;import org.json.*;
/** Local text history, enabled explicitly; pinned items retain the existing pin store. */
public final class ClipboardMemory {
 private ClipboardMemory(){}
 public static void changed(Context context,boolean privateEditor){
  KeyboardData.prefs(context).edit().putBoolean("clipboard_private_block",privateEditor).remove("clipboard_last_hash").apply();
  capture(context,privateEditor);
 }
 public static JSONArray history(Context context){
  JSONArray out=new JSONArray();try{JSONArray old=new JSONArray(KeyboardData.prefs(context).getString("clipboard_history","[]"));long now=System.currentTimeMillis();
   for(int i=0;i<old.length()&&out.length()<30;i++){JSONObject item=old.optJSONObject(i);if(item!=null&&now-item.optLong("time")<86400000L)out.put(item);}
  }catch(Exception ignored){}return out;
 }
 public static void capture(Context context,boolean privateEditor){
  if(privateEditor||KeyboardData.prefs(context).getBoolean("clipboard_private_block",false)||!KeyboardData.prefs(context).getBoolean("clipboard_auto",false)||KeyboardData.prefs(context).getBoolean("private_mode",false))return;
  try{ClipboardManager manager=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);ClipData clip=manager==null?null:manager.getPrimaryClip();if(clip==null||clip.getItemCount()==0)return;
   android.os.PersistableBundle extras=clip.getDescription().getExtras();if(extras!=null&&extras.getBoolean("android.content.extra.IS_SENSITIVE",false))return;
   CharSequence value=clip.getItemAt(0).getText();if(value==null)return;String text=value.toString();if(text.trim().isEmpty()||text.length()>6000)return;
   String hash=android.util.Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)),android.util.Base64.NO_WRAP);
   if(hash.equals(KeyboardData.prefs(context).getString("clipboard_last_hash","")))return;
   JSONArray next=new JSONArray().put(new JSONObject().put("text",text).put("time",System.currentTimeMillis()));JSONArray old=history(context);
   for(int i=0;i<old.length()&&next.length()<30;i++)if(!old.getJSONObject(i).optString("text").equals(text))next.put(old.getJSONObject(i));
   KeyboardData.prefs(context).edit().putString("clipboard_history",next.toString()).putString("clipboard_last_hash",hash).apply();
  }catch(Exception ignored){}
 }
 public static void remove(Context context,String text){JSONArray next=new JSONArray(),old=history(context);for(int i=0;i<old.length();i++){JSONObject item=old.optJSONObject(i);if(item!=null&&!item.optString("text").equals(text))next.put(item);}KeyboardData.prefs(context).edit().putString("clipboard_history",next.toString()).apply();}
 public static void clear(Context context){KeyboardData.prefs(context).edit().remove("clipboard_history").remove("clipboard_pins").apply();}
}
