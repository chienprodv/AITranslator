package com.ngoctho.aitranslator;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.util.*;
import java.util.concurrent.*;

/** One bounded request per captured region batch; no per-line requests or automatic retries. */
public final class ScreenLayoutTranslationEngine {
    public interface Callback {void success(List<ScreenRegion> regions,String warning,boolean cached);void error(String message);}
    private static final ExecutorService EXEC=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final LruCache<String,List<String>> CACHE=new LruCache<>(24);
    private ScreenLayoutTranslationEngine(){}
    public static void translate(Context context,List<ScreenRegion> regions,java.util.function.BooleanSupplier active,Callback callback){
        final List<ScreenRegion> snapshot=new ArrayList<>(regions);final Context app=context.getApplicationContext();
        if(snapshot.isEmpty()){callback.error("Không có khối chữ Trung phù hợp để dịch.");return;}
        final String payload=ScreenLayoutBatch.payload(snapshot);
        final String key=AppPrefs.provider(app)+"|"+AppPrefs.model(app)+"|"+AppPrefs.businessContext(app)+"|"+AppPrefs.glossary(app)+"|"+KeyboardData.styleKey(app)+"|"+payload;
        List<String> cached=CACHE.get(key);
        if(cached!=null&&cached.size()==snapshot.size()){
            List<ScreenRegion> out=new ArrayList<>();for(int i=0;i<snapshot.size();i++)out.add(snapshot.get(i).translate(cached.get(i)));
            String warning=warnings(app,out);MAIN.post(()->{if(active.getAsBoolean())callback.success(out,warning,true);});return;
        }
        EXEC.execute(()->{
            if(!active.getAsBoolean())return;
            try{
                List<ScreenRegion> out;
                if(AppPrefs.isDemo(AppPrefs.provider(app))){
                    out=new ArrayList<>();for(ScreenRegion r:snapshot){ScreenTranslationResult local=LocalPhrasebook.translateScreen(r.source);if(local==null)throw new IllegalStateException("DEMO chỉ dịch các câu mẫu. Chọn DeepSeek/OpenAI và lưu key để dịch đè câu bất kỳ.");out.add(r.translate(local.vietnamese));}
                }else out=ApiClient.translateScreenLayout(app,snapshot);
                List<String> values=new ArrayList<>();for(ScreenRegion r:out)values.add(r.target);
                CACHE.put(key,values);
                String note=warnings(app,out);MAIN.post(()->{if(active.getAsBoolean())callback.success(out,note,false);});
            }catch(Exception e){String message=e.getMessage()==null?"Không dịch được bố cục.":e.getMessage();MAIN.post(()->{if(active.getAsBoolean())callback.error(message);});}
        });
    }
    private static String warnings(Context app,List<ScreenRegion> out){StringBuilder warning=new StringBuilder();for(ScreenRegion r:out){String issue=MeaningCheck.warning(r.source,r.target,KeyboardData.prefs(app).getString("lock_terms",""));if(!issue.isEmpty()&&warning.length()<300)warning.append("Khối ").append(r.id+1).append(": ").append(issue).append(' ');}return warning.toString().trim();}
}
