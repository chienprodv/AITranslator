package com.ngoctho.aitranslator;

import android.content.Context;
import org.json.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Unicode sequences stay intact, including ZWJ families, flags and skin tones. */
public final class EmojiRepository {
    private EmojiRepository(){}
    public static final String[] CATEGORIES={"Gần đây","Yêu thích","Mặt & cảm xúc","Người & cử chỉ","Động vật","Đồ ăn","Đi lại","Hoạt động","Đồ vật","Ký hiệu","Cờ","Chat mua bán","Kaomoji"};
    public static final class Entry {public final String value,label;Entry(String value,String label){this.value=value;this.label=label;}}
    private static final List<List<Entry>> groups=new ArrayList<>();
    private static final Map<String,Entry> all=new LinkedHashMap<>();
    private static synchronized void load(Context c){
        if(!groups.isEmpty())return;
        for(int i=0;i<9;i++)groups.add(new ArrayList<>());
        try(InputStream in=c.getAssets().open("emoji_catalog.json")){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);
            JSONArray items=new JSONObject(bytes.toString("UTF-8")).getJSONArray("items");
            for(int i=0;i<items.length();i++){JSONArray item=items.getJSONArray(i);int group=item.getInt(0);Entry e=new Entry(item.getString(1),item.getString(2));if(group>=0&&group<9){groups.get(group).add(e);all.put(e.value,e);}}
        }catch(Exception ignored){}
        if(all.isEmpty())for(String value:"😀 😄 😅 😂 🙂 😊 😍 🥰 😎 🤔 😭 🙏 🤝 👍 ❤️ 🎉 📦 🚚 💰 🐟".split(" ")){Entry e=new Entry(value,value);groups.get(0).add(e);all.put(value,e);}
    }
    private static List<Entry> values(Context c,String data){
        List<Entry> out=new ArrayList<>();try{JSONArray items=new JSONArray(data);for(int i=0;i<items.length();i++){String value=items.optString(i,"");if(!value.isEmpty())out.add(all.getOrDefault(value,new Entry(value,value)));}}catch(Exception ignored){}return out;
    }
    public static List<Entry> category(Context c,int category,boolean privateMode){
        load(c);if(privateMode&&(category==0||category==1))return new ArrayList<>(groups.get(0));
        if(category==0){String data=KeyboardData.prefs(c).getString("recent_emoji_v2","");if(data.isEmpty()){
            JSONArray old=new JSONArray();for(String s:KeyboardData.prefs(c).getString("recent_emoji","").split(" "))if(!s.isEmpty())old.put(s);data=old.toString();}
            List<Entry> recent=values(c,data);return recent.isEmpty()?new ArrayList<>(groups.get(0).subList(0,Math.min(32,groups.get(0).size()))):recent;
        }
        if(category==1)return values(c,KeyboardData.prefs(c).getString("favorite_emoji_v2","[]"));
        if(category>=2&&category<=10)return new ArrayList<>(groups.get(category-2));
        if(category==11){List<Entry> out=new ArrayList<>();for(String value:"🐟 🐠 🐡 🤝 🙏 👍 👌 🫶 😊 😅 😂 🤔 🥹 🥰 😎 🤗 🤩 😭 ❤️ 💛 💬 💯 ✅ ❌ ⚠️ 📦 🚚 🛒 💰 💴 💳 🧾 📍 🕒 📸 📹 💻 📱 🔌 🔋 🛠️ 🎁 🎉 ☕ 🔥 ⭐ ✨ 🇨🇳 🇻🇳".split(" "))out.add(all.getOrDefault(value,new Entry(value,value)));return out;}
        String[] kaomoji={"(｡•̀ᴗ-)✧","(≧▽≦)","(๑•̀ㅂ•́)و✧","(ง •̀_•́)ง","(づ｡◕‿‿◕｡)づ","(人´∀｀)","(｡･ω･｡)ﾉ♡","(◕‿◕)","(￣▽￣)","(＾▽＾)","(•̀ᴗ•́)و","(╥﹏╥)","(ಥ_ಥ)","(；一_一)","(¬_¬)","(눈_눈)","(・_・;)","(⊙_⊙)","(￣ω￣)","(=^･ω･^=)","ʕ•ᴥ•ʔ","ヽ(•‿•)ノ","(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧","(๑˃̵ᴗ˂̵)و","(｡♥‿♥｡)","(￣^￣)ゞ","(｡•́︿•̀｡)","(•ө•)♡","٩(ˊᗜˋ*)و","( ˘ ³˘)♥","(✿◠‿◠)","(￣ー￣)ｂ"};
        List<Entry> out=new ArrayList<>();for(String value:kaomoji)out.add(new Entry(value,value));return out;
    }
    public static void recent(Context c,String value){load(c);JSONArray next=new JSONArray();next.put(value);for(Entry e:category(c,0,false))if(!e.value.equals(value)&&next.length()<40)next.put(e.value);KeyboardData.prefs(c).edit().putString("recent_emoji_v2",next.toString()).apply();}
    public static boolean toggleFavorite(Context c,String value){load(c);List<Entry> old=category(c,1,false);boolean remove=false;for(Entry e:old)if(e.value.equals(value))remove=true;if(!remove&&old.size()>=80)throw new IllegalStateException("Đã đủ 80 biểu tượng yêu thích; bỏ ghim một mục trước.");JSONArray next=new JSONArray();if(!remove)next.put(value);for(Entry e:old)if(!e.value.equals(value))next.put(e.value);KeyboardData.prefs(c).edit().putString("favorite_emoji_v2",next.toString()).apply();return !remove;}
}
