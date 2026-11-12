# Hồ sơ Google Play — bản chuẩn bị 1.3.0

Người phát triển: Nguyen xuan chien. Ứng dụng: XinayuAITranslator. Email hỗ trợ/quyền riêng tư công khai được chủ sở hữu chọn: langdinhvi011@gmail.com.

## Trạng thái

Chưa đăng ký Play Console nên **chưa có bản phát hành trên CH Play**. Chuẩn bị AAB release không có cờ debuggable, ký cục bộ bằng khóa đã giữ. APK cá nhân vẫn dùng ID debug hiện có để cài đè 1.2.1; AAB release dùng ID `com.ngoctho.aitranslator` như cấu hình release của dự án. Chốt ID trước lần upload đầu tiên; tên hiển thị là XinayuAITranslator.

## Nội dung trang ứng dụng

Tên (dưới 30 ký tự): **XinayuAITranslator**

Mô tả ngắn: **Bàn phím RGB, dịch Việt–Trung, giọng nói và bộ câu dùng nhanh.**

Mô tả đầy đủ dự thảo:

XinayuAITranslator kết hợp bàn phím tiếng Việt, Studio LED và công cụ dịch Việt ↔ Trung trong ô nhập văn bản.

- Studio LED có 16 hiệu ứng, chỉnh độ sáng, tốc độ và chọn bảng màu. Nút bóng đèn ngay trên bàn phím giúp đổi nhanh.
- Gõ Telex, ký hiệu, emoji, kéo phím cách để di chuyển con trỏ và tùy chỉnh chiều cao/phông chữ.
- Xem trước câu dịch và dịch ngược, sau đó chủ động bấm CHÈN để thay nội dung; ứng dụng không tự gửi tin nhắn.
- Lưu câu yêu thích, dùng câu mẫu và phím tắt, ghim clipboard, nhập/xuất bản sao lưu JSON.
- Nhận dạng giọng nói khi bấm mic, đọc bản dịch bằng dịch vụ TTS của điện thoại.
- OCR vùng màn hình người dùng chọn và dịch phần chữ được nhận dạng.
- Giới hạn sử dụng bằng VNĐ, cảnh báo ngân sách và thống kê lượt/token. Chi phí là ước tính trên thiết bị, không thay thế hóa đơn/số dư nhà cung cấp.

Gõ, LED và câu mẫu cục bộ dùng không cần tài khoản app. Dịch AI tự do cần mạng và API key hoặc proxy do người dùng cung cấp; nhà cung cấp AI có thể tính phí riêng. Nhận dạng giọng nói/TTS phụ thuộc dịch vụ và ngôn ngữ có trên điện thoại. Cần bật/chọn bàn phím trong cài đặt Android. Mic, bong bóng và chụp màn hình chỉ cần khi dùng tính năng tương ứng. Đọc thông báo dữ liệu trước khi bật dịch trực tuyến hoặc AUTO.

Copyright by Nguyen xuan chien. Hỗ trợ: langdinhvi011@gmail.com.

## Data safety — bảng rà soát để nhập vào Console

Không đánh dấu “không thu thập dữ liệu”. Chưa gửi biểu mẫu nào; phải rà soát đúng phiên bản SDK và điều khoản nhà cung cấp trước khi nộp.

| Luồng | Loại dữ liệu cần rà soát | Mục đích / tùy chọn |
|---|---|---|
| DeepSeek/OpenAI/proxy khi DỊCH hoặc AUTO | Nội dung do người dùng tạo; tin nhắn có thể có thông tin cá nhân; ngữ cảnh/thuật ngữ | Chức năng dịch, người dùng đồng ý; có thể tắt/không cấu hình AI |
| Mic qua dịch vụ nhận dạng Android | Âm thanh/giọng nói; chữ nhận dạng | Chỉ khi bấm mic và cấp quyền; kiểm tra dịch vụ thực tế để khai báo thu thập/chia sẻ |
| TTS của thiết bị | Văn bản bản dịch | Chỉ khi bấm đọc; dịch vụ có thể dùng mạng |
| ML Kit bundled OCR | Thông tin thiết bị/app, mã cài đặt, hiệu năng, sự kiện và chẩn đoán | Google công bố thu thập cho phân tích/chẩn đoán SDK; rà soát mục Diagnostics / Device or other IDs / App interactions |
| Từ đã học, lịch sử, câu lưu, clipboard ghim, thống kê | Lưu cục bộ | Không tự đồng bộ; JSON chỉ xuất khi chọn. Không khai là “thu thập” chỉ vì dữ liệu ở trên thiết bị |
| API key / token proxy | Thông tin xác thực | Lưu mã hóa trên máy; gửi xác thực đến dịch vụ được chọn; không nhúng key vào AAB và không xuất trong JSON |

Việc một bên xử lý có thuộc ngoại lệ “service provider” của phần chia sẻ hay không phải đối chiếu hợp đồng/chính sách thực tế. Không mặc định rằng SDK/AI bên thứ ba không tính vào Data safety. Chỉ đánh dấu xử lý tạm thời nếu đáp ứng định nghĩa của Google; không giả định nhà cung cấp không lưu dữ liệu. App không có đăng ký tài khoản riêng, không quảng cáo; tài khoản API bên ngoài do người dùng quản lý.

## Các bước chủ sở hữu còn phải làm

1. Tự đăng ký/xác minh Play Console, chấp nhận thỏa thuận và thanh toán phí đăng ký một lần 25 USD. Không có bước tài chính/xác minh nào được agent làm thay.
2. Tạo app, chốt ID, khai báo truy cập app/BYOK và hướng dẫn review. Chỉ dùng tài khoản/API key thử nghiệm dành riêng cho review nếu cần; không đưa key thật vào mô tả công khai.
3. Đăng chính sách quyền riêng tư thành trang HTML công khai, truy cập không đăng nhập, điền URL trong Console và app trước khi phát hành. Nội dung HTML đã chuẩn bị trong gói kết quả; chưa có URL công khai.
4. Chụp ảnh màn hình thật trên thiết bị từ chính bản release. Chưa có ảnh màn hình thật và kiểm tra cảm giác gõ/mic/TTS cho bản này; không dùng hình minh họa làm bằng chứng đã chạy trên điện thoại.
5. Hoàn tất Data safety, IARC, đối tượng người dùng, quyền microphone/MediaProjection/foreground service và app access theo tính năng thực tế. Không khẳng định đáp ứng toàn bộ chính sách chỉ vì build thành công.
6. Với tài khoản cá nhân mới, chạy closed test tối thiểu 12 người opt-in liên tục 14 ngày; sau đó xin production access và chờ xét duyệt. Chưa có danh sách tester hoặc quá trình thử nghiệm nào bắt đầu.
7. Tải AAB release đã ký lên đúng track; bật Play App Signing theo hướng dẫn. Giữ bản sao khóa riêng và mật khẩu cục bộ an toàn; không gửi chúng trong source ZIP, APK hoặc qua chat.

Target API hiện là 36. Native OCR ARM64 trong bản trước đã kiểm tra có PT_LOAD alignment 16KB; cần kiểm tra lại AAB/APK mới và chạy trên thiết bị/emulator 16KB trước khi nộp.

## Nguồn chính thức đã đối chiếu 12/11/2026

- [Đăng ký và phí](https://support.google.com/googleplay/android-developer/answer/6112435)
- [12 người / 14 ngày](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Target API](https://support.google.com/googleplay/android-developer/answer/11926878)
- [User Data / quyền riêng tư](https://support.google.com/googleplay/android-developer/answer/10144311)
- [ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure)
- [Hỗ trợ 16KB](https://developer.android.com/guide/practices/page-sizes)
