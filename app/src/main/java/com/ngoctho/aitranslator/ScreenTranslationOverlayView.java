package com.ngoctho.aitranslator;

import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.view.*;
import java.util.*;

/** Frozen screenshot plus fitted, clipped text blocks; never changes the underlying chat. */
public final class ScreenTranslationOverlayView extends View {
    public interface Listener {void selected(ScreenRegion region);}
    private final Listener listener;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<RenderBlock> blocks=new ArrayList<>();private List<ScreenRegion> regions=new ArrayList<>();
    private Bitmap image;private final RectF frame=new RectF();private boolean original;
    private static final class RenderBlock {ScreenRegion region;RectF bounds;StaticLayout layout;int background;boolean clipped;}
    public ScreenTranslationOverlayView(Context c,Listener listener){super(c);this.listener=listener;setContentDescription("Ảnh dịch màn hình. Chạm một khối chữ để xem bản dịch đầy đủ.");}
    public void frame(Bitmap bitmap){image=bitmap;rebuild();invalidate();}
    public void regions(List<ScreenRegion> values){regions=new ArrayList<>(values);rebuild();invalidate();}
    public void original(boolean show){original=show;invalidate();}
    public boolean original(){return original;}
    public void release(){image=null;regions.clear();blocks.clear();invalidate();}
    private float sp(float n){return n*getResources().getDisplayMetrics().scaledDensity;}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);rebuild();}
    private StaticLayout layout(String text,TextPaint p,int width,int maxLines){return StaticLayout.Builder.obtain(text,0,text.length(),p,Math.max(1,width))
        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(0,1)
        .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY).setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).build();}
    private void rebuild(){
        blocks.clear();if(image==null||image.isRecycled()||getWidth()<=0||getHeight()<=0)return;
        double[] fitted=ScreenLayoutGeometry.fit(image.getWidth(),image.getHeight(),getWidth(),getHeight());
        frame.set((float)fitted[0],(float)fitted[1],(float)fitted[2],(float)fitted[3]);
        for(ScreenRegion region:regions){
            if(region.target.isEmpty()||!region.valid())continue;
            double[] box=ScreenLayoutGeometry.project(region,fitted);RenderBlock block=new RenderBlock();block.region=region;
            block.bounds=new RectF((float)box[0],(float)box[1],(float)box[2],(float)box[3]);
            if(block.bounds.width()<2||block.bounds.height()<2)continue;
            block.background=background(region);float padding=Math.min(dp(2),Math.min(block.bounds.width(),block.bounds.height())*.08f);
            int width=Math.max(1,(int)(block.bounds.width()-2*padding));float height=Math.max(1,block.bounds.height()-2*padding);
            TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);text.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
            int color=block.background;double brightness=.2126*Color.red(color)+.7152*Color.green(color)+.0722*Color.blue(color);text.setColor(brightness>145?0xff111111:0xfffafafa);
            float low=Math.min(sp(10),Math.max(8,block.bounds.height()*.65f)),high=Math.max(low,Math.min(sp(32),block.bounds.height()/region.lines));
            float best=low;for(int i=0;i<8;i++){float size=(low+high)/2;text.setTextSize(size);StaticLayout candidate=layout(region.target,text,width,Integer.MAX_VALUE);if(candidate.getHeight()<=height){best=size;low=size;}else high=size;}
            text.setTextSize(best);StaticLayout full=layout(region.target,text,width,Integer.MAX_VALUE);
            block.clipped=full.getHeight()>height;int maxLines=Math.max(1,(int)(height/Math.max(1,text.getFontSpacing())));
            block.layout=block.clipped?layout(region.target,text,width,maxLines):full;
            block.bounds.inset(0,0);blocks.add(block);
        }
    }
    private int background(ScreenRegion region){
        Map<Integer,int[]> bins=new HashMap<>();int width=image.getWidth(),height=image.getHeight();
        int left=(int)(region.left*width),right=(int)(region.right*width),top=(int)(region.top*height),bottom=(int)(region.bottom*height);
        for(int i=0;i<24;i++){float t=(i+.5f)/24;int x=(int)(left+(right-left)*t),y=(int)(top+(bottom-top)*t);
            for(int[] point:new int[][]{{x,top-2},{x,bottom+2},{left-2,y},{right+2,y}}){int c=image.getPixel(Math.max(0,Math.min(width-1,point[0])),Math.max(0,Math.min(height-1,point[1])));
                int key=(Color.red(c)>>4)<<8|(Color.green(c)>>4)<<4|(Color.blue(c)>>4);int[] bin=bins.computeIfAbsent(key,k->new int[4]);bin[0]++;bin[1]+=Color.red(c);bin[2]+=Color.green(c);bin[3]+=Color.blue(c);}
        }
        int[] best={1,255,255,255};for(int[] value:bins.values())if(value[0]>best[0])best=value;
        return Color.rgb(best[1]/best[0],best[2]/best[0],best[3]/best[0]);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(0xff101318);if(image==null||image.isRecycled())return;canvas.drawBitmap(image,null,frame,paint);if(original)return;
        for(RenderBlock block:blocks){
            paint.setColor(block.background);canvas.drawRect(block.bounds,paint);
            float pad=Math.min(dp(2),Math.min(block.bounds.width(),block.bounds.height())*.08f);
            canvas.save();canvas.clipRect(block.bounds);canvas.translate(block.bounds.left+pad,block.bounds.top+pad);block.layout.draw(canvas);canvas.restore();
            if(block.clipped){paint.setColor(0xff2488e6);canvas.drawCircle(block.bounds.right-dp(2),block.bounds.bottom-dp(2),dp(1.5f),paint);}
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_UP&&!original){for(int i=blocks.size()-1;i>=0;i--)if(blocks.get(i).bounds.contains(event.getX(),event.getY())){listener.selected(blocks.get(i).region);performClick();break;}}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
