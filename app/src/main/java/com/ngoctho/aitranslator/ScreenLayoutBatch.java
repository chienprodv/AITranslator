package com.ngoctho.aitranslator;

import org.json.*;import java.util.*;

/** Bounded one-request protocol: IDs bind translated blocks to their original geometry. */
public final class ScreenLayoutBatch {
    private ScreenLayoutBatch(){}
    public static List<ScreenRegion> select(List<ScreenRegion> input){
        List<ScreenRegion> sorted=new ArrayList<>(input);sorted.sort(Comparator.comparingDouble((ScreenRegion r)->r.top).thenComparingDouble(r->r.left));
        List<ScreenRegion> out=new ArrayList<>();int chars=0;
        for(ScreenRegion r:sorted){if(!r.valid()||!ScreenRegion.hasChinese(r.source)||r.source.length()>1200||chars+r.source.length()>3200||out.size()>=32)continue;
            ScreenRegion next=new ScreenRegion(out.size(),r.source,r.left,r.top,r.right,r.bottom,r.lines,"");out.add(next);
            if(payload(out).length()>5500){out.remove(out.size()-1);continue;}chars+=r.source.length();}
        return out;
    }
    public static String payload(List<ScreenRegion> regions){
        JSONArray items=new JSONArray();try{for(ScreenRegion r:regions)items.put(new JSONObject().put("id",r.id).put("text",r.source));return new JSONObject().put("items",items).toString();}catch(Exception e){throw new IllegalArgumentException("Không tạo được dữ liệu OCR.",e);}
    }
    public static List<ScreenRegion> parse(List<ScreenRegion> input,String raw)throws Exception{
        JSONArray items=new JSONObject(raw).getJSONArray("items");if(items.length()!=input.size())throw new IllegalStateException("AI trả thiếu/thừa khối chữ. Bấm dịch lại hoặc chọn vùng nhỏ hơn.");
        Map<Integer,String> translated=new HashMap<>();Set<Integer> expected=new HashSet<>();for(ScreenRegion r:input)expected.add(r.id);
        int total=0;for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);Object identifier=item.get("id"),text=item.get("vi");
            if(!(identifier instanceof Number)||((Number)identifier).doubleValue()!=((Number)identifier).intValue()||!(text instanceof String))throw new IllegalStateException("Định dạng khối dịch không hợp lệ.");
            int id=((Number)identifier).intValue();String vi=((String)text).trim();
            total+=vi.length();if(!expected.contains(id)||translated.containsKey(id)||vi.isEmpty()||vi.length()>2400||total>20000)throw new IllegalStateException("Không ghép được bản dịch vào đúng khối chữ.");translated.put(id,vi);
        }
        List<ScreenRegion> result=new ArrayList<>();for(ScreenRegion r:input)result.add(r.translate(translated.get(r.id)));return result;
    }
}
