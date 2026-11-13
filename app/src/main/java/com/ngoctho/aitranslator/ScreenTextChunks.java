package com.ngoctho.aitranslator;
import java.util.*;
/** Bounded complete input; never silently drop OCR text. */
public final class ScreenTextChunks {
 private ScreenTextChunks(){}
 public static List<String> split(String source){
  String text=source==null?"":source.trim();if(text.isEmpty())throw new IllegalArgumentException("Không nhận được chữ trên màn hình.");
  if(text.length()>12000)throw new IllegalArgumentException("Màn hình có hơn 12.000 ký tự OCR. Chưa gửi API; không tự cắt bỏ nội dung.");
  List<String> chunks=new ArrayList<>();int start=0;
  while(start<text.length()){
   int end=Math.min(text.length(),start+1500);
   if(end<text.length()){
    int line=text.lastIndexOf('\n',end-1);if(line>=start+900)end=line+1;
    else {int space=text.lastIndexOf(' ',end-1);if(space>=start+1100)end=space+1;}
    if(end>start&&Character.isHighSurrogate(text.charAt(end-1)))end--;
   }
   chunks.add(text.substring(start,end));start=end;
  }
  if(chunks.size()>12)throw new IllegalArgumentException("OCR quá dài cho một lượt dịch. Chưa gửi API.");return chunks;
 }
}
