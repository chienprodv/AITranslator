package com.ngoctho.aitranslator;

import java.util.ArrayList;
import java.util.List;

/** Geometry is independent of Android so hit targets can be regression-tested on the JVM. */
public final class KeyboardModel {
    public static final class Key {
        public final String code;
        public final double x, y, width, height;
        Key(String code, double x, double y, double width, double height) {
            this.code = code; this.x = x; this.y = y; this.width = width; this.height = height;
        }
        public double cx() { return x + width / 2; }
        public double cy() { return y + height / 2; }
    }
    public static final class Point {
        public final double x, y;
        public Point(double x, double y) { this.x = x; this.y = y; }
    }
    public final List<Key> keys = new ArrayList<>();
    public final boolean numeric;
    public KeyboardModel(int symbols, boolean numeric) {
        this.numeric = numeric;
        if (numeric) {
            row("123", 0, 0, 10.0 / 3); row("456", 1, 0, 10.0 / 3); row("789", 2, 0, 10.0 / 3);
            add(".", 0, 3, 10.0 / 3); add("0", 10.0 / 3, 3, 10.0 / 3); add("DELETE", 20.0 / 3, 3, 10.0 / 3);
            add("SYMBOLS", 0, 4, 2); add("SPACE", 2, 4, 5.8); add("ENTER", 7.8, 4, 2.2);
            return;
        }
        row("1234567890", 0, 0, 1);
        row(symbols == 0 ? "qwertyuiop" : symbols == 1 ? "+×÷=/_<>[]" : "€£¥₫¢^°•≈≠", 1, 0, 1);
        row(symbols == 0 ? "asdfghjkl" : symbols == 1 ? "!@#$%&*()" : "{}\\|~`§©®", 2, 0.5, 1);
        add("SHIFT", 0, 3, 1.5);
        row(symbols == 0 ? "zxcvbnm" : symbols == 1 ? "-\"':;?~" : "±√∞«»…·", 3, 1.5, 1);
        add("DELETE", 8.5, 3, 1.5);
        add("SYMBOLS", 0, 4, 1.25); add("LANGUAGE", 1.25, 4, 0.9); add("?", 2.15, 4, 0.9);
        add("SPACE", 3.05, 4, 4.1); add(".", 7.15, 4, 0.9); add("ENTER", 8.05, 4, 1.95);
    }
    private void row(String value, int y, double x, double width) {
        for (int i = 0; i < value.length(); i++) add(value.substring(i, i + 1), x + i * width, y, width);
    }
    private void add(String value, double x, int y, double width) { keys.add(new Key(value, x, y, width, 1)); }
    public Key hit(double x, double y) {
        if (x < -0.3 || x > 10.3 || y < -0.2 || y > 5.2) return null;
        Key nearest = null;
        double distance = Double.MAX_VALUE;
        for (Key key : keys) {
            if (x >= key.x && x <= key.x + key.width && y >= key.y && y <= key.y + key.height) return key;
            double dx = Math.max(0, Math.abs(x - key.cx()) - key.width / 2);
            double dy = Math.max(0, Math.abs(y - key.cy()) - key.height / 2);
            double d = dx * dx + dy * dy;
            if (d < distance) { nearest = key; distance = d; }
        }
        return nearest;
    }
    public Point center(char letter) {
        for (Key key : keys) if (key.code.equals(String.valueOf(Character.toLowerCase(letter)))) return new Point(key.cx(), key.cy());
        return null;
    }
}
