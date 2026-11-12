package com.ngoctho.aitranslator;

public final class ScreenLayoutGeometry {
    private ScreenLayoutGeometry(){}
    public static double[] fit(int sourceWidth,int sourceHeight,int viewWidth,int viewHeight){
        if(sourceWidth<=0||sourceHeight<=0||viewWidth<=0||viewHeight<=0)return new double[]{0,0,0,0};
        double scale=Math.min(viewWidth/(double)sourceWidth,viewHeight/(double)sourceHeight),w=sourceWidth*scale,h=sourceHeight*scale;
        return new double[]{(viewWidth-w)/2,(viewHeight-h)/2,(viewWidth+w)/2,(viewHeight+h)/2};
    }
    public static double[] project(ScreenRegion region,double[] frame){double w=frame[2]-frame[0],h=frame[3]-frame[1];return new double[]{frame[0]+region.left*w,frame[1]+region.top*h,frame[0]+region.right*w,frame[1]+region.bottom*h};}
    public static int bubbleEdge(int x,int displayWidth,int touchWidth){int max=Math.max(0,displayWidth-touchWidth);return x+touchWidth/2<displayWidth/2?0:max;}
}
