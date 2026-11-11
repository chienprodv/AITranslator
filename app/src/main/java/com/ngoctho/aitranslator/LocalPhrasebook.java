package com.ngoctho.aitranslator;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Curated ultra-fast business phrases used before any network request. */
public final class LocalPhrasebook {
    private LocalPhrasebook() {}

    public static TranslationResult translateViToZh(String source, String context) {
        if (source == null) return null;
        String raw = source.trim();
        String n = normalize(raw);
        if (n.isEmpty()) return null;

        Matcher stock = Pattern.compile(".*con hang.*?(\\d+)\\s*(cai|chiec|bo|san pham)?.*").matcher(n);
        if (stock.matches()) {
            String q = stock.group(1);
            return result(raw,
                    "Bên bạn còn hàng không? Tôi muốn lấy " + q + " cái.",
                    "你们还有现货吗？我想订" + q + "个。",
                    "Bên bạn còn hàng sẵn không? Tôi muốn đặt " + q + " cái.");
        }

        Matcher bestPrice = Pattern.compile(".*(?:lay|dat)\\s*(\\d+)\\s*(?:cai|chiec|bo|san pham)?.*(?:gia tot nhat|gia thap nhat|gia bao nhieu).*").matcher(n);
        if (bestPrice.matches()) {
            String q = bestPrice.group(1);
            return result(raw,
                    "Nếu tôi đặt " + q + " cái, giá tốt nhất bên bạn có thể báo là bao nhiêu?",
                    "如果我订" + q + "个，你们能给我的最优惠价格是多少？",
                    "Nếu tôi đặt " + q + " cái, mức giá ưu đãi nhất bên bạn có thể đưa ra là bao nhiêu?");
        }

        if (n.equals("ben ban con hang khong") || n.equals("con hang khong")) {
            return result(raw, "Bên bạn còn hàng không?", "你们还有现货吗？", "Bên bạn còn hàng sẵn không?");
        }
        if (n.equals("gia bao nhieu") || n.equals("gia la bao nhieu")) {
            return result(raw, "Giá bao nhiêu?", "请问价格是多少？", "Xin hỏi giá là bao nhiêu?");
        }
        if (n.equals("gia cao qua") || n.equals("gia nay cao qua")) {
            String zh = AppPrefs.CONTEXT_NEGOTIATION.equals(context)
                    ? "这个价格有点高，如果长期合作，能不能再优惠一些？"
                    : "这个价格有点高，可以再优惠一些吗？";
            String back = AppPrefs.CONTEXT_NEGOTIATION.equals(context)
                    ? "Giá này hơi cao. Nếu hợp tác lâu dài, bên bạn có thể ưu đãi thêm một chút không?"
                    : "Giá này hơi cao, có thể ưu đãi thêm một chút không?";
            return result(raw, back, zh, back);
        }
        if (n.contains("mau") && (n.contains("truoc") || n.contains("thu"))) {
            return result(raw,
                    "Tôi muốn xem hoặc lấy mẫu trước khi đặt số lượng lớn.",
                    "我想先看一下样品，再决定是否大批量订购。",
                    "Tôi muốn xem mẫu trước, sau đó mới quyết định có đặt số lượng lớn hay không.");
        }
        if (n.contains("thoi gian giao hang") || n.contains("bao lau giao")) {
            return result(raw,
                    "Thời gian chuẩn bị và giao hàng dự kiến là bao lâu?",
                    "请问预计备货和发货需要多长时间？",
                    "Xin hỏi dự kiến cần bao lâu để chuẩn bị hàng và giao hàng?");
        }
        if (n.contains("giam gia") && n.contains("so luong")) {
            return result(raw,
                    "Nếu mua số lượng lớn thì có được giảm giá không?",
                    "如果大量采购，价格可以优惠吗？",
                    "Nếu mua số lượng lớn, giá có thể được ưu đãi không?");
        }
        if (n.equals("cam on") || n.equals("cam on ban")) {
            return result(raw, "Cảm ơn bạn.", "谢谢你。", "Cảm ơn bạn.");
        }
        return null;
    }

    public static BiTranslationResult translateDirectional(String source, String direction, String context) {
        if (DirectionalTranslationEngine.VI_TO_ZH.equals(direction)) {
            TranslationResult r = translateViToZh(source, context);
            if (r == null) return null;
            return new BiTranslationResult(r.source, r.intentVi, r.chinese, r.backVi, r.score, r.warning, direction);
        }

        String raw = source == null ? "" : source.trim();
        String s = raw.replace(" ", "");
        if (s.equals("你们还有现货吗？") || s.equals("你们还有现货吗")) {
            return bi(raw, "Bên bạn còn hàng sẵn không?", "你们还有现货吗？", direction);
        }
        if (s.equals("请问价格是多少？") || s.equals("请问价格是多少")) {
            return bi(raw, "Xin hỏi giá là bao nhiêu?", "请问价格是多少？", direction);
        }
        if (s.contains("最低起订") || s.contains("起订量")) {
            return bi(raw, "Số lượng đặt hàng tối thiểu là bao nhiêu?", raw, direction);
        }
        return null;
    }

    public static ScreenTranslationResult translateScreen(String source) {
        if (source == null) return null;
        String s = source.trim();
        if (s.isEmpty()) return null;
        if (s.contains("现货") && s.contains("起订")) {
            String vi = s
                    .replace("现货供应", "Có sẵn hàng")
                    .replace("现货", "Có sẵn hàng")
                    .replace("最低", "Tối thiểu ")
                    .replace("起订", " bắt đầu đặt hàng")
                    .replace("批发价", "Giá sỉ")
                    .replace("预计", "Dự kiến ")
                    .replace("发货", " giao hàng");
            return new ScreenTranslationResult(s, vi, "");
        }
        return null;
    }

    private static TranslationResult result(String source, String intent, String zh, String back) {
        return new TranslationResult(source, intent, zh, back, 100, "");
    }

    private static BiTranslationResult bi(String source, String translated, String back, String direction) {
        return new BiTranslationResult(source, source, translated, back, 100, "", direction);
    }

    private static String normalize(String s) {
        String x = s.toLowerCase(Locale.ROOT).trim();
        x = Normalizer.normalize(x, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        x = x.replace('đ', 'd');
        x = x.replaceAll("[^a-z0-9]+", " ").trim();
        return x.replaceAll("\\s+", " ");
    }
}
