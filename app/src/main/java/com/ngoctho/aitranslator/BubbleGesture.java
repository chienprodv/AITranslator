package com.ngoctho.aitranslator;

/** Gesture decision independent of moving overlay coordinates. */
public final class BubbleGesture {
    public enum End { TAP, DRAG, HOLD, CANCEL }
    private final float slop;private float x,y;private long time;private boolean down,drag;
    public BubbleGesture(float slop){this.slop=Math.max(1,slop);}
    public void begin(float x,float y,long time){this.x=x;this.y=y;this.time=time;down=true;drag=false;}
    public boolean move(float x,float y){if(!down)return false;float dx=x-this.x,dy=y-this.y;if(dx*dx+dy*dy>slop*slop)drag=true;return drag;}
    public End end(float x,float y,long time){if(!down)return End.CANCEL;move(x,y);down=false;return drag?End.DRAG:time-this.time>=700?End.HOLD:End.TAP;}
    public void cancel(){down=false;drag=false;}
}
