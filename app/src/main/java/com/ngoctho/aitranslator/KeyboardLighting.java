package com.ngoctho.aitranslator;

/** Palette and finite key-flash envelope, independent of the Android drawing surface. */
public final class KeyboardLighting {
    private KeyboardLighting() {}
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
        double hue = ((now % 2400) / 2400.0 + x / 30.0) % 1.0;
        double h = hue * 6, f = h - Math.floor(h), v = dark ? 1 : 0.65, low = v * 0.18;
        double falling = v - (v - low) * f, rising = low + (v - low) * f;
        double[][] channels = {{v,rising,low},{falling,v,low},{low,v,rising},{low,falling,v},{rising,low,v},{v,low,falling}};
        double[] c = channels[(int) h % 6];
        return 0xff000000 | ((int)Math.round(c[0]*255)<<16) | ((int)Math.round(c[1]*255)<<8) | (int)Math.round(c[2]*255);
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
