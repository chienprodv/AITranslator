package com.ngoctho.aitranslator;
import android.content.Context;import android.os.*;import android.util.LruCache;
import java.util.*;import java.util.concurrent.*;import java.util.function.BooleanSupplier;
public final class ScreenFullTranslationEngine {
 public interface Callback {void progress(int done,int total,String translated);void success(String translated,String warning);void error(String message,String partial);}
 private static final ExecutorService EXEC=Executors.newSingleThreadExecutor();private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static final LruCache<String,String[]> CACHE=new LruCache<>(8);
 public static void translate(Context context,String source,BooleanSupplier active,Callback callback){
  Context app=context.getApplicationContext();final List<String> chunks;
  try{chunks=ScreenTextChunks.split(source);}catch(Exception e){callback.error(e.getMessage(),"");return;}
  String key=AppPrefs.provider(app)+"|"+AppPrefs.model(app)+"|"+AppPrefs.businessContext(app)+"|"+AppPrefs.glossary(app)+"|"+KeyboardData.styleKey(app)+"|"+source;
  String[] cached=CACHE.get(key);if(cached!=null){if(active.getAsBoolean())callback.success(cached[0],cached[1]);return;}
  EXEC.execute(()->{StringBuilder output=new StringBuilder(),warnings=new StringBuilder();
   try{for(int i=0;i<chunks.size();i++){
    if(!active.getAsBoolean())return;ScreenTranslationResult result;
    if(AppPrefs.isDemo(AppPrefs.provider(app))){result=LocalPhrasebook.translateScreen(chunks.get(i).trim());if(result==null)throw new IllegalStateException("DEMO không dịch màn hình tự do. Chọn DeepSeek/OpenAI và lưu key.");}
    else result=ApiClient.translateScreenFull(app,chunks.get(i));
    if(!active.getAsBoolean())return;if(output.length()>0)output.append("\n\n");output.append(result.vietnamese);
    if(!result.warning.isEmpty())warnings.append(result.warning).append('\n');int done=i+1;String partial=output.toString();
    MAIN.post(()->{if(active.getAsBoolean())callback.progress(done,chunks.size(),partial);});
   }
   String translated=output.toString(),warning=warnings.toString().trim();CACHE.put(key,new String[]{translated,warning});MAIN.post(()->{if(active.getAsBoolean())callback.success(translated,warning);});
   }catch(Exception e){String partial=output.toString(),message=e.getMessage()==null?"Không dịch được màn hình.":e.getMessage();MAIN.post(()->{if(active.getAsBoolean())callback.error(message,partial);});}
  });
 }
}
