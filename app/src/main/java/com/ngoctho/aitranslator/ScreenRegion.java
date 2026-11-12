package com.ngoctho.aitranslator;

/** OCR text plus an immutable, normalized rectangle in the captured image. */
public final class ScreenRegion {
    public final int id,lines;public final String source,target;public final double left,top,right,bottom;
    public ScreenRegion(int id,String source,double left,double top,double right,double bottom,int lines,String target){
        this.id=id;this.source=source==null?"":source.trim();this.target=target==null?"":target.trim();
        this.left=clip(Math.min(left,right));this.right=clip(Math.max(left,right));this.top=clip(Math.min(top,bottom));this.bottom=clip(Math.max(top,bottom));this.lines=Math.max(1,lines);
    }
    private static double clip(double n){return Double.isFinite(n)?Math.max(0,Math.min(1,n)):0;}
    public boolean valid(){return !source.isEmpty()&&right>left&&bottom>top;}
    public boolean contains(double x,double y){return x>=left&&x<=right&&y>=top&&y<=bottom;}
    public ScreenRegion translate(String vi){return new ScreenRegion(id,source,left,top,right,bottom,lines,vi);}
    public ScreenRegion remap(double x,double y,double width,double height){return new ScreenRegion(id,source,x+left*width,y+top*height,x+right*width,y+bottom*height,lines,target);}
    public static boolean hasChinese(String value){return value.codePoints().anyMatch(c->(c>=0x3400&&c<=0x9fff)||(c>=0xf900&&c<=0xfaff)||(c>=0x20000&&c<=0x2fa1f));}
}
