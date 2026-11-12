package com.ngoctho.aitranslator;

/** Palette and finite key-flash envelope, independent of the Android drawing surface. */
public final class KeyboardLighting {
    private KeyboardLighting() {}
    public static final String[] MODES = {"wave", "waterfall", "diagonal", "spectrum", "breathing", "windmill", "river", "aurora", "rain", "stars", "scanner", "fireworks", "reactive", "ripple", "three_zone", "solid", "off"};
    public static final String[] NAMES = {"Sóng cầu vồng", "Thác màu", "Sóng chéo", "Đổi màu toàn phím", "Nhịp thở", "Cối xay", "Dòng sông", "Cực quang", "Mưa sao băng", "Sao lấp lánh", "Quét ánh sáng", "Pháo hoa", "Chạm sáng", "Gợn sóng khi chạm", "Ba vùng màu", "Sáng một màu", "Tắt LED"};
    public static String mode(String requested) {
        for (String mode : MODES) if (mode.equals(requested)) return mode;
        return "wave";
    }
    public static String name(String requested) {
        for (int i = 0; i < MODES.length; i++) if (MODES[i].equals(requested)) return NAMES[i];
        return NAMES[0];
    }
    public static int brightness(int requested) { return Math.max(0, Math.min(100, requested)); }
    public static int speed(int requested) { return Math.max(1, Math.min(5, requested)); }
    private static final int[] PERIODS = {6000,4200,2800,1800,1000};
    public static int period(int speed) { return PERIODS[speed(speed)-1]; }
    public static boolean continuous(String mode) {
        return !mode.equals("off") && !mode.equals("solid") && !mode.equals("three_zone") && !mode.equals("reactive") && !mode.equals("ripple");
    }
    public static final class Pulse {
        public final double x, y; public final long time;
        public Pulse(double x, double y, long time) { this.x=x;this.y=y;this.time=time; }
    }
    public static final class Sample { public int color; public float level; }
    /** Shared by the keyboard and the live preview. Coordinates are in keyboard units. */
    public static void sample(Sample out, String mode, long now, double x, double y, boolean dark,
            int brightness, int speed, boolean reverse, int solid, int[] zones, java.util.List<Pulse> pulses) {
        double t = (now % (period(speed)*100L)) / (double)period(speed), u = reverse ? -t : t;
        double hue = x/10.0-u, level=1;
        switch (mode) {
            case "off": level=0; break;
            case "solid": out.color=solid;out.level=brightness(brightness)/100f;return;
            case "three_zone": out.color=zones[zone(x)];out.level=brightness(brightness)/100f;return;
            case "waterfall": hue=y/5.0-u;break;
            case "diagonal": hue=x/10.0+y/5.0-u;break;
            case "spectrum": hue=u;break;
            case "breathing":
                out.color=solid;out.level=(float)((0.1+0.9*(1-Math.cos(t*2*Math.PI))/2)*brightness(brightness)/100.0);return;
            case "windmill": hue=Math.atan2(y-2.5,x-5)/(2*Math.PI)-u;level=0.3+0.7*Math.pow((Math.cos(hue*4*Math.PI)+1)/2,2);break;
            case "river": hue=x/13.0-u/2;level=0.15+0.85*Math.exp(-Math.pow((y-2.5-1.4*Math.sin(x*0.7-u*2*Math.PI))/0.9,2));break;
            case "aurora": hue=0.38+0.38*(1+Math.sin(x*0.5+y*0.35-u*2*Math.PI))/2;level=0.3+0.7*(1+Math.sin(x*0.8-y*0.6+u*Math.PI))/2;break;
            case "rain": {
                double column=Math.floor(x), head=fract(u*0.7+hash(column+17))*7;
                double trail=head-y;level=trail>=0 && trail<2.1?Math.exp(-trail*1.7):0.03;
                hue=hash(column+3)+t/8;break;
            }
            case "stars": {double seed=hash(Math.floor(x)+Math.floor(y)*13);hue=seed+t/8;level=0.04+0.96*Math.pow((1+Math.sin(t*2*Math.PI+seed*30))/2,10);break;}
            case "scanner": {double head=(1-Math.cos(u*Math.PI))*5;hue=t/3;level=0.04+0.96*Math.exp(-Math.pow((x-head)/1.0,2));break;}
            case "fireworks": {
                double cycle=Math.floor(t), age=fract(t), cx=1+8*hash(cycle+7),cy=0.5+4*hash(cycle+29);
                double distance=Math.hypot((x-cx)*0.6,y-cy);
                level=Math.exp(-Math.pow((distance-age*6)/0.55,2))*(1-age);hue=hash(cycle+11)+distance/8;break;
            }
            case "reactive": case "ripple": {
                level=0;
                for (Pulse pulse:pulses) {
                    double age=(now-pulse.time)/(double)(period(speed)*0.6);
                    if(age<0 || age>1)continue;
                    double distance=Math.hypot((x-pulse.x)*0.6,y-pulse.y);
                    double value=mode.equals("reactive")?(distance<0.5?1-age:0):Math.exp(-Math.pow((distance-age*6)/0.65,2))*(1-age);
                    if(value>level){level=value;hue=age+distance/8+pulse.x/10;}
                }
                break;
            }
            default: break;
        }
        out.color=hue(hue,dark);out.level=(float)Math.max(0,Math.min(1,level*brightness(brightness)/100.0));
    }
    private static double fract(double value) { return value-Math.floor(value); }
    private static double hash(double value) { return fract(Math.sin(value*127.1+311.7)*43758.5453); }
    public static int zone(double center) { return center < 10.0 / 3 ? 0 : center < 20.0 / 3 ? 1 : 2; }
    public static int color(String name, boolean dark) {
        switch (name) {
            case "cyan": return dark ? 0xff45deff : 0xff00758a;
            case "pink": return dark ? 0xffff84c5 : 0xffb11d65;
            case "amber": return dark ? 0xffffd166 : 0xff946000;
            case "green": return dark ? 0xff66e4b7 : 0xff087f5b;
            case "purple": return dark ? 0xffc5a3ff : 0xff7530bf;
            default: return dark ? 0xff8ab4ff : 0xff1d4ed8;
        }
    }
    public static float strength(long started, long now) {
        return Math.max(0, Math.min(1, 1 - (now - started) / 600f));
    }
    public static int rgb(long now, double x, boolean dark) {
        return hue((now % 2400)/2400.0+x/30.0,dark);
    }
    private static int hue(double hue, boolean dark) {
        double h = fract(hue) * 6, f = h - Math.floor(h), v = dark ? 1 : 0.65, low = v * 0.18;
        double falling = v - (v - low) * f, rising = low + (v - low) * f;
        double r,g,b;
        switch ((int)h%6) {
            case 0:r=v;g=rising;b=low;break;case 1:r=falling;g=v;b=low;break;case 2:r=low;g=v;b=rising;break;
            case 3:r=low;g=falling;b=v;break;case 4:r=rising;g=low;b=v;break;default:r=v;g=low;b=falling;
        }
        return 0xff000000 | ((int)Math.round(r*255)<<16) | ((int)Math.round(g*255)<<8) | (int)Math.round(b*255);
    }
    public static int blend(int base, int light, float fraction) {
        float f = Math.max(0, Math.min(1, fraction)); int result = 0xff000000;
        for (int shift : new int[]{0,8,16}) {
            int a = (base >> shift) & 255, b = (light >> shift) & 255;
            result |= Math.round(a + (b - a) * f) << shift;
        }
        return result;
    }
    public static int bottomGap(int requested) { return Math.max(12, Math.min(72, requested)); }
}
