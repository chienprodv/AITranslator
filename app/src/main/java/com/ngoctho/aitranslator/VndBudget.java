package com.ngoctho.aitranslator;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Device-local estimated spending limit. Reserve before dispatch; keep reserves on unknown billing. */
public final class VndBudget {
    private static final long SCALE = 1000000L;
    private VndBudget() {}
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("vnd_budget", Context.MODE_PRIVATE);
    }
    public static long limit(Context context) { return prefs(context).getLong("limit_vnd", 5000); }
    public static boolean monthly(Context context) { return prefs(context).getBoolean("monthly", true); }
    public static long exchangeRate(Context context) { return prefs(context).getLong("usd_vnd", 25820); }
    private static String period(Context context) {
        return new SimpleDateFormat(monthly(context) ? "yyyy-MM" : "yyyy-MM-dd", Locale.ROOT).format(new Date());
    }
    public static double inputRate(Context context) { return rate(context, false); }
    public static double outputRate(Context context) { return rate(context, true); }
    private static double rate(Context context, boolean output) {
        SharedPreferences prefs = prefs(context);
        String model = AppPrefs.model(context), provider = AppPrefs.provider(context);
        if (model.equals(prefs.getString("price_model", "")) && provider.equals(prefs.getString("price_provider", ""))) {
            try { return Double.parseDouble(prefs.getString(output ? "output_usd" : "input_usd", "0")); }
            catch (Exception ignored) { return 0; }
        }
        // Peak, cache-miss reference prices checked 2026-11-11; editable in settings.
        if (AppPrefs.PROVIDER_DEEPSEEK.equals(provider) || AppPrefs.PROVIDER_PROXY_DEEPSEEK.equals(provider)) {
            if (model.equals("deepseek-flash")) return output ? 1.2 : 0.3;
            if (model.equals("deepseek-v4-pro")) return output ? 3.96 : 1.32;
        }
        return 0; // Do not silently price an unknown model as Flash.
    }
    public static synchronized void configure(Context context, long limit, boolean monthly, long fx, double input, double output) {
        if (limit < 1000 || limit > 10000000 || fx < 1000 || fx > 1000000
                || !Double.isFinite(input) || !Double.isFinite(output) || input <= 0 || output <= 0 || input > 1000 || output > 1000) {
            throw new IllegalArgumentException("Hạn mức từ 1.000đ đến 10.000.000đ; tỷ giá và đơn giá phải hợp lệ.");
        }
        if (!prefs(context).edit().putLong("limit_vnd", limit).putBoolean("monthly", monthly)
                .putLong("usd_vnd", fx).putString("input_usd", String.valueOf(input)).putString("output_usd", String.valueOf(output))
                .putString("price_provider", AppPrefs.provider(context)).putString("price_model", AppPrefs.model(context)).commit()) {
            throw new IllegalStateException("Không lưu được hạn mức tiền.");
        }
    }
    static long costMicros(long input, long output, double inputRate, double outputRate, long fx) {
        double micros = ((double) input * inputRate + (double) output * outputRate) * fx;
        if (!Double.isFinite(micros) || micros < 0 || micros > Long.MAX_VALUE / 2.0) throw new IllegalArgumentException("Chi phí vượt giới hạn.");
        return (long) Math.ceil(micros);
    }
    public static final class Ticket {
        final String period;
        final long reserved;
        final double inputRate, outputRate;
        final long fx;
        boolean settled;
        Ticket(String period, long reserved, double inputRate, double outputRate, long fx) {
            this.period = period; this.reserved = reserved; this.inputRate = inputRate; this.outputRate = outputRate; this.fx = fx;
        }
    }
    public static synchronized Ticket reserve(Context context, String source, String prompt, int maxOutput) {
        double input = inputRate(context), output = outputRate(context);
        if (input <= 0 || output <= 0) throw new IllegalStateException("Chưa có đơn giá cho model này. Lưu đơn giá tại Hạn mức VNĐ trước khi gọi AI.");
        // A conservative byte-based input allowance, plus message framing. Output is explicitly capped.
        long tokens = (source + prompt).getBytes(StandardCharsets.UTF_8).length + 256L;
        long charge = costMicros(tokens, maxOutput, input, output, exchangeRate(context));
        SharedPreferences prefs = prefs(context);
        String period = period(context);
        long used = prefs.getLong("spent_" + period, 0), cap = limit(context) * SCALE;
        if (charge > cap - used) throw new IllegalStateException("Không đủ hạn mức VNĐ cho lượt dịch tiếp theo. Tăng hạn mức trong Cài đặt hoặc chờ kỳ mới.");
        if (!prefs.edit().putLong("spent_" + period, used + charge).putInt("pending_" + period, prefs.getInt("pending_" + period, 0) + 1).commit()) {
            throw new IllegalStateException("Không lưu được khoản dự trù API; tạm dừng.");
        }
        return new Ticket(period, charge, input, output, exchangeRate(context));
    }
    public static synchronized void settle(Context context, Ticket ticket, long input, long output) {
        if (ticket == null || ticket.settled) return;
        SharedPreferences prefs = prefs(context);
        long cost = costMicros(input, output, ticket.inputRate, ticket.outputRate, ticket.fx);
        long updated = Math.max(0, prefs.getLong("spent_" + ticket.period, 0) - ticket.reserved + cost);
        if (!prefs.edit().putLong("spent_" + ticket.period, updated)
                .putInt("pending_" + ticket.period, Math.max(0, prefs.getInt("pending_" + ticket.period, 0) - 1)).commit()) {
            throw new IllegalStateException("Không lưu được chi phí API; giữ mức dự trù.");
        }
        ticket.settled = true;
    }
    public static synchronized void warnIfLow(Context context) {
        SharedPreferences prefs = prefs(context);
        String period = period(context);
        long cap = limit(context) * SCALE, spent = prefs.getLong("spent_" + period, 0);
        String marker = period + "|" + limit(context);
        if (spent >= cap * 4 / 5 && !marker.equals(prefs.getString("warned", ""))) {
            prefs.edit().putString("warned", marker).apply();
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context.getApplicationContext(),
                    "⚠ Còn tối đa 20% hạn mức VNĐ ước tính. Mở Cài đặt để xem.", Toast.LENGTH_LONG).show());
        }
    }
    private static String money(long micros) {
        NumberFormat format = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        format.setMaximumFractionDigits(2);
        return format.format(micros / (double) SCALE) + "đ";
    }
    public static synchronized String summary(Context context) {
        SharedPreferences prefs = prefs(context);
        String period = period(context);
        long spent = prefs.getLong("spent_" + period, 0), cap = limit(context) * SCALE;
        return "Hạn mức " + (monthly(context) ? "tháng " : "ngày ") + period + ": " + money(cap)
                + "\nĐã dùng/dự trù: " + money(spent) + " · còn " + money(Math.max(0, cap - spent))
                + "\nLượt chưa có usage để đối chiếu: " + prefs.getInt("pending_" + period, 0)
                + ". Không tự hoàn khoản dự trù khi mất mạng hoặc thiếu usage."
                + "\nƯớc tính theo đơn giá và tỷ giá đã đặt; không phải số dư DeepSeek. Không gồm phí thẻ/thuế hoặc sử dụng từ app khác."
                + (spent >= cap * 4 / 5 ? "\n⚠ Còn tối đa 20% hạn mức VNĐ ước tính." : "");
    }
}
