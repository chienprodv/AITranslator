package com.ngoctho.aitranslator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.ScrollView;
import android.widget.TextView;

/** Explicit consent is stored separately and is never restored from keyboard backups. */
public final class PrivacyConsent {
    private PrivacyConsent() {}
    public static final String SUPPORT_EMAIL = "langdinhvi011@gmail.com";
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("privacy_consent",Context.MODE_PRIVATE);}
    public static boolean allowed(Context c){return prefs(c).getInt("version",0)==1 && prefs(c).getBoolean("online",false);}
    public static void require(Context c){if(!allowed(c))throw new IllegalStateException("Mở XinayuAITranslator → Quyền riêng tư để đồng ý xử lý dữ liệu trước khi dùng dịch trực tuyến.");}
    public static void onLaunch(Activity a){if(prefs(a).getInt("reviewed",0)!=1)show(a);}
    public static void show(Activity a){
        String message="Khi bạn bấm DỊCH, câu đang chọn hoặc dòng hiện tại trong ô chat, ngữ cảnh và thuật ngữ sẽ được gửi qua HTTPS tới DeepSeek, OpenAI hoặc máy chủ proxy bạn chọn để dịch. Nếu bật AUTO, việc gửi diễn ra sau khi dừng gõ khi bàn phím đang mở, kể cả trong ứng dụng chat khác.\n\n"
            +"Khi bấm mic, âm thanh được dịch vụ nhận dạng giọng nói của Android xử lý và có thể gửi lên máy chủ của dịch vụ đó; phần chữ nhận dạng được gửi đến AI khi bạn yêu cầu dịch.\n\n"
            +"Dịch màn hình dùng ảnh vùng bạn chọn để OCR trên máy, rồi gửi phần chữ đến AI. Google ML Kit có thể thu thập thông tin thiết bị/ứng dụng, mã cài đặt và dữ liệu chẩn đoán theo chính sách của Google.\n\n"
            +"Bạn có thể từ chối và vẫn gõ, dùng emoji, LED và câu mẫu cục bộ. Có thể thu hồi đồng ý tại đây bất cứ lúc nào.";
        new AlertDialog.Builder(a).setTitle("Dữ liệu khi dùng dịch trực tuyến").setMessage(message)
            .setPositiveButton("Đồng ý xử lý dữ liệu",(d,w)->prefs(a).edit().putInt("reviewed",1).putInt("version",1).putBoolean("online",true).apply())
            .setNegativeButton(allowed(a)?"Thu hồi đồng ý":"Dùng bàn phím cục bộ",(d,w)->{
                prefs(a).edit().putInt("reviewed",1).putInt("version",1).putBoolean("online",false).apply();AppPrefs.setAutoTranslate(a,false);
                a.stopService(new android.content.Intent(a,ScreenTranslateService.class));
            }).setNeutralButton("Quyền riêng tư",(d,w)->policy(a)).show();
    }
    public static void policy(Activity a){
        TextView text=new TextView(a);text.setTextSize(14);text.setPadding(24,16,24,16);
        text.setText("CHÍNH SÁCH QUYỀN RIÊNG TƯ · XINAYUAITRANSLATOR\nCập nhật 12/11/2026\nNgười phát triển: Nguyen xuan chien\n\n"
            +"Bàn phím và LED chạy trên thiết bị. Từ đã học, clipboard ghim, câu yêu thích, cấu hình, lịch sử và thống kê sử dụng được lưu cục bộ. Có thể tắt học/lưu lịch sử và xóa từng loại dữ liệu trong cài đặt. Lịch sử mặc định hết hạn sau 30 ngày, clipboard ghim sau 24 giờ, được dọn khi truy cập; câu yêu thích giữ đến khi xóa.\n\n"
            +"Dịch AI gửi nội dung câu/dòng/đoạn được chọn và dữ liệu cấu hình dịch đến nhà cung cấp bạn chọn khi bấm DỊCH hoặc khi AUTO bật. Nội dung có thể bao gồm tin nhắn riêng tư và thông tin cá nhân do bạn gõ. App không tự gửi tin nhắn. API key lưu mã hóa bằng Android Keystore và chỉ dùng xác thực với nhà cung cấp tương ứng.\n\n"
            +"Mic chỉ hoạt động khi bạn yêu cầu và cấp quyền Android. Dịch vụ nhận dạng/TTS do thiết bị cung cấp có thể xử lý âm thanh hoặc văn bản trực tuyến theo cài đặt của dịch vụ.\n\n"
            +"Dịch màn hình cần quyền chụp cho từng phiên và quyền bong bóng. Ảnh vùng chọn dùng OCR cục bộ; app chỉ gửi chữ OCR đến AI. SDK Google ML Kit có thể gửi thông tin thiết bị, thông tin app, mã cài đặt, dữ liệu hiệu năng và sự kiện sử dụng/chẩn đoán tới Google qua HTTPS.\n\n"
            +"Không có quảng cáo hoặc tài khoản đăng nhập riêng do app tạo. App không bán dữ liệu. Việc lưu giữ/xóa dữ liệu đã gửi tới DeepSeek, OpenAI, dịch vụ giọng nói hoặc proxy chịu chính sách của bên đó và cấu hình tài khoản của bạn. Chúng tôi không thể xóa thay dữ liệu trên dịch vụ của bên thứ ba.\n\n"
            +"Sao lưu JSON chỉ thực hiện khi chọn xuất; API key, token proxy và bộ đếm chi phí không có trong tệp. Clipboard ghim chỉ xuất khi chọn riêng. Người dùng chịu trách nhiệm bảo vệ tệp xuất. Gỡ app hoặc xóa dữ liệu Android xóa dữ liệu cục bộ, kể cả khóa và thống kê. Không dùng đồng ý từ bản sao lưu để tự bật gửi dữ liệu.\n\n"
            +"Bạn có thể thu hồi đồng ý dịch trực tuyến trong mục Quyền riêng tư; yêu cầu đã gửi trước đó không thể thu hồi. Ô mật khẩu/ô được đánh dấu riêng tư và chế độ riêng tư thủ công chặn AI và học từ trong bàn phím.\n\n"
            +(SUPPORT_EMAIL.isEmpty()?"Bản chuẩn bị phát hành: địa chỉ liên hệ hỗ trợ công khai sẽ được xác nhận trước khi nộp Google Play.":"Liên hệ quyền riêng tư và hỗ trợ: "+SUPPORT_EMAIL));
        ScrollView scroll=new ScrollView(a);scroll.addView(text);
        new AlertDialog.Builder(a).setTitle("Quyền riêng tư").setView(scroll).setPositiveButton("Đóng",null).show();
    }
}
