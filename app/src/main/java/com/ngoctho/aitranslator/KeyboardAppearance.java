package com.ngoctho.aitranslator;
import android.content.*;import android.graphics.*;import android.net.Uri;import android.os.*;import java.io.*;import java.util.concurrent.*;
public final class KeyboardAppearance {
 public interface Callback {void done(Bitmap image,String error);}
 private static final ExecutorService EXEC=Executors.newSingleThreadExecutor();private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static volatile Bitmap cached;private static volatile long revision=Long.MIN_VALUE;
 private static File file(Context c){return new File(c.getFilesDir(),"keyboard-wallpaper.jpg");}
 public static void load(Context c,Callback callback){Context app=c.getApplicationContext();long rev=KeyboardData.prefs(c).getLong("wallpaper_revision",0);
  if(revision==rev){callback.done(cached,"");return;}EXEC.execute(()->{Bitmap image=file(app).isFile()?BitmapFactory.decodeFile(file(app).getAbsolutePath()):null;cached=image;revision=rev;MAIN.post(()->callback.done(image,""));});
 }
 public static void choose(Context c,Uri uri,Callback callback){Context app=c.getApplicationContext();EXEC.execute(()->{try{
  BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;try(InputStream in=app.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,bounds);}
  if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("Ảnh không đọc được.");BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
  while(bounds.outWidth/options.inSampleSize>2048||bounds.outHeight/options.inSampleSize>2048)options.inSampleSize*=2;
  Bitmap decoded;try(InputStream in=app.getContentResolver().openInputStream(uri)){decoded=BitmapFactory.decodeStream(in,null,options);}if(decoded==null)throw new IOException("Không giải mã được ảnh.");
  try(InputStream in=app.getContentResolver().openInputStream(uri)){android.media.ExifInterface exif=new android.media.ExifInterface(in);int orientation=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);Matrix matrix=new Matrix();
   switch(orientation){case 2:matrix.setScale(-1,1);break;case 3:matrix.setRotate(180);break;case 4:matrix.setScale(1,-1);break;case 5:matrix.setRotate(90);matrix.postScale(-1,1);break;case 6:matrix.setRotate(90);break;case 7:matrix.setRotate(270);matrix.postScale(-1,1);break;case 8:matrix.setRotate(270);break;}
   if(!matrix.isIdentity()){Bitmap rotated=Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),decoded.getHeight(),matrix,true);if(rotated!=decoded)decoded.recycle();decoded=rotated;}
  }catch(Exception ignored){}
  File temp=new File(app.getFilesDir(),"keyboard-wallpaper.tmp");try(OutputStream out=new FileOutputStream(temp)){if(!decoded.compress(Bitmap.CompressFormat.JPEG,90,out))throw new IOException("Không lưu được ảnh.");}
  java.nio.file.Files.move(temp.toPath(),file(app).toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  cached=decoded;revision=System.currentTimeMillis();KeyboardData.prefs(app).edit().putLong("wallpaper_revision",revision).apply();Bitmap result=decoded;MAIN.post(()->callback.done(result,""));
 }catch(Exception|OutOfMemoryError e){MAIN.post(()->callback.done(null,"Không mở được ảnh. Hãy chọn ảnh nhỏ hơn."));}});}
 public static void remove(Context c){file(c).delete();cached=null;revision=System.currentTimeMillis();KeyboardData.prefs(c).edit().putLong("wallpaper_revision",revision).apply();}
 public static int opacity(Context c,String key){return Math.max(0,Math.min(100,KeyboardData.prefs(c).getInt(key,100)));}
}
