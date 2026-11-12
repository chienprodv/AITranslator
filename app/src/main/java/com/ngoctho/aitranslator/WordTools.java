package com.ngoctho.aitranslator;

import java.text.Normalizer;
import java.util.*;

/** A small local language model: prefix suggestions, reversible correction, and path matching. */
public final class WordTools {
    private WordTools() {}
    private static final String WORDS = "xin chào cảm ơn vâng không có được rồi chưa đang đã sẽ tôi bạn anh chị em chúng mình muốn cần hỏi mua bán hàng giá tiền mẫu cái chiếc bộ thùng hộp kiện số lượng bao nhiêu tốt nhất giảm thêm chút lấy gửi nhận giao vận chuyển phí miễn bao gồm đóng gói cẩn thận kiểm tra giúp hình ảnh video thực tế thông số sản phẩm thiết bị máy tính laptop asus dell acer lenovo samsung iphone windows wifi usb màn hình bàn phím chuột nguồn pin sạc dây cáp cổng kết nối bộ nhớ ổ cứng RAM SSD CPU mới cũ nguyên bản chính hãng bảo hành đổi trả lỗi hỏng thiếu đủ còn hết kho sẵn có đặt trước thời gian ngày tuần tháng năm hôm nay mai chiều sáng tối ngày kia nhanh chậm gấp đúng sai màu trắng đen xanh đỏ vàng kích thước dài rộng cao nặng nhẹ cân mét centimet millimet volt ampe watt giá điện áp công suất nhiệt độ hoạt động tiếng Việt Trung Anh địa chỉ nhà kho người điện thoại mã vận đơn hợp đồng hóa đơn thanh toán chuyển khoản cọc hoàn tiền trách nhiệm điều kiện cam kết chi tiết xác nhận đồng ý đồng thời vui lòng xin lỗi chờ một lát lần này lần sau chất lượng ổn định tương thích thử nghiệm chụp rõ nét mặt sau trước bên trong ngoài tất cả từng riêng phần thành phố quận huyện phường đường số nhà Việt Nam Trung Quốc Quảng Châu Thâm Quyến Hà Nội Đà Nẵng Sài Gòn hello thanks thank you please price stock shipping delivery warranty sample order quantity payment address invoice discount product laptop keyboard computer charger adapter cable available original genuine used new yes no today tomorrow good best can could send receive check confirm model number serial size color black white blue red green shop supplier customer return refund receipt package parcel logistics business technical email message translation undo copy paste select save settings voice microphone clipboard memory emoji internet offline online account budget balance token limit reset monthly daily private password screen camera image video amount total unit kilogram pieces yuan dollar vietnam china quote offer negotiation compare expensive cheap included not without with support test defect damaged replacement compatible urgent details specification battery connector resolution storage memory processor screen touch type input output purchase stock quantity availability thankyou ok";
    public static String fold(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }
    public static String wordBefore(String text) {
        int start = text.length();
        while (start > 0 && Character.isLetter(text.charAt(start - 1))) start--;
        return text.substring(start);
    }
    private static LinkedHashSet<String> words(Collection<String> learned) {
        LinkedHashSet<String> words = new LinkedHashSet<>(learned);
        words.addAll(Arrays.asList(WORDS.split(" +")));
        return words;
    }
    public static List<String> suggest(String prefix, Collection<String> learned) {
        String input = fold(prefix);
        List<String> found = new ArrayList<>();
        if (input.isEmpty()) return new ArrayList<>(Arrays.asList("xin", "cảm ơn", "được"));
        List<String> near = new ArrayList<>();
        for (String word : words(learned)) {
            String candidate = fold(word);
            if (candidate.startsWith(input) && !word.equals(prefix)) found.add(word);
            else if (input.length() >= 4 && candidate.length() >= 4 && candidate.charAt(0) == input.charAt(0)
                    && distance(candidate, input) == 1) near.add(word);
            if (found.size() >= 3) break;
        }
        for (String word : near) if (found.size() < 3) found.add(word);
        return found.subList(0, Math.min(3, found.size()));
    }
    public static String correction(String source, Collection<String> learned) {
        if (source.length() < 4 || !source.equals(source.toLowerCase(Locale.ROOT))) return source;
        Set<String> known = words(learned);
        for (String word : known) if (fold(word).equals(fold(source))) return source;
        String result = null;
        for (String word : known) if (Math.abs(word.length() - source.length()) <= 1 && word.charAt(0) == source.charAt(0)
                && distance(word, source) == 1) {
            if (result != null && !result.equals(word)) return source; // Ambiguous: never silently choose.
            result = word;
        }
        return result == null ? source : result;
    }
    public static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] next = new int[b.length() + 1]; next[0] = i;
            for (int j = 1; j <= b.length(); j++) next[j] = Math.min(previous[j] + 1,
                    Math.min(next[j - 1] + 1, previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1)));
            previous = next;
        }
        return previous[b.length()];
    }
    private static List<KeyboardModel.Point> sample(List<KeyboardModel.Point> path) {
        List<KeyboardModel.Point> result = new ArrayList<>();
        if (path.size() < 2) return path;
        double length = 0;
        for (int i = 1; i < path.size(); i++) length += gap(path.get(i - 1), path.get(i));
        for (int n = 0; n < 24; n++) {
            double target = length * n / 23, traversed = 0;
            for (int i = 1; i < path.size(); i++) {
                double segment = gap(path.get(i - 1), path.get(i));
                if (traversed + segment >= target || i == path.size() - 1) {
                    double t = segment == 0 ? 0 : Math.max(0, Math.min(1, (target - traversed) / segment));
                    KeyboardModel.Point a = path.get(i - 1), b = path.get(i);
                    result.add(new KeyboardModel.Point(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))); break;
                }
                traversed += segment;
            }
        }
        return result;
    }
    private static double gap(KeyboardModel.Point a, KeyboardModel.Point b) { return Math.hypot(a.x - b.x, a.y - b.y); }
    public static List<String> swipe(List<KeyboardModel.Point> path, Collection<String> learned) {
        if (path.size() < 3) return Collections.emptyList();
        KeyboardModel model = new KeyboardModel(0, false);
        List<KeyboardModel.Point> observed = sample(path);
        TreeMap<Double, String> scores = new TreeMap<>();
        for (String word : words(learned)) {
            String normalized = fold(word);
            if (normalized.length() < 2 || normalized.length() > 24 || !normalized.matches("[a-z]+")) continue;
            List<KeyboardModel.Point> ideal = new ArrayList<>();
            for (char c : normalized.toCharArray()) { KeyboardModel.Point point = model.center(c); if (point != null) ideal.add(point); }
            if (ideal.size() < 2 || gap(path.get(0), ideal.get(0)) > 0.85 || gap(path.get(path.size() - 1), ideal.get(ideal.size() - 1)) > 0.85) continue;
            ideal = sample(ideal); double score = 0;
            for (int i = 0; i < observed.size(); i++) score += gap(observed.get(i), ideal.get(i));
            score /= observed.size();
            if (score < 1.3) { while (scores.containsKey(score)) score += 0.00001; scores.put(score, word); }
        }
        List<String> result = new ArrayList<>();
        for (String word : scores.values()) { result.add(word); if (result.size() == 3) break; }
        return result;
    }
}
