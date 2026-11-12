# XinayuAITranslator 1.2.1

Copyright by Nguyen xuan chien

- Đổi tên hiển thị ở launcher, danh sách bàn phím, màn hình app, cài đặt và thông báo dịch màn hình. Giữ nguyên ID nội bộ để không tạo thêm một app khác chỉ vì đổi tên.
- Khoảng trống dưới hàng phím tăng thêm **28dp**, cộng với vùng điều hướng hệ thống sẵn có. Có thể chỉnh 12–72dp ở ⋯ → cài đặt bàn phím → Khoảng trống thêm phía dưới. Chạm dưới đáy vùng phím không bị kéo ngược về phím ký tự.
- Ký tự chia ba vùng trái / giữa / phải. Mặc định xanh ngọc / tím / hồng. Mỗi vùng chọn được xanh ngọc, tím, hồng, vàng, xanh lá hoặc xanh dương; màu tự điều chỉnh theo nền sáng/tối. Có thể tắt màu ba vùng.
- Chạm phím làm viền và ký tự sáng RGB, chuyển màu rồi tắt dần trong 600ms. Không có vòng hoạt ảnh chạy khi nghỉ. Có công tắc tắt RGB; tôn trọng cài đặt tắt hoạt ảnh Android.
- Màu, công tắc RGB và khoảng trống đi theo bản sao lưu JSON; dữ liệu nhập được kiểm tra kiểu và giới hạn.

## Cài đặt và chữ ký

**Không thể cài đè lên bản debug 1.2.0 cũ chỉ bằng cách sao chép chữ ký từ APK.** Khóa riêng của bản đó đã được CI tạo tạm và không giữ lại. Chứng chỉ trong APK không phải khóa riêng.

Trước khi chuyển từ 1.2.0, mở app cũ → cài đặt bàn phím → Xuất sao lưu JSON để giữ câu yêu thích/cấu hình, đồng thời giữ API key ở nơi riêng. Chỉ sau khi giữ dữ liệu mới tự gỡ bản cũ, cài APK 1.2.1 đã ký rồi nhập lại JSON/key và bật/chọn bàn phím. JSON không mang theo API key hoặc lịch sử tiền đã dùng; khi gỡ app, dữ liệu cục bộ và bộ đếm tiền cũ bị xóa.

Từ bản 1.2.1 đã giữ khóa cố định trên máy; các bản tiếp theo phải dùng đúng khóa này để cài đè. CI tạo APK unsigned, bước ký cuối dùng `tools/sign-apk.py` trên máy sở hữu. Không cài trực tiếp artifact unsigned. Xem [cách giữ và sử dụng khóa](SIGNING.md).

## Kiểm tra

1.364 kiểm tra lõi, bao gồm ranh giới đáy bàn phím, ba vùng màu, RGB tắt dần và giới hạn khoảng trống; 19 kiểm tra dữ liệu, gồm khôi phục các cài đặt mới. Biên dịch Java với Android API 36 không có lỗi nguồn (OCR dùng stub cục bộ); build CI kiểm tra phụ thuộc Android thật. APK cuối phải xác minh chữ ký v2/v3 và so fingerprint với khóa đã lưu. Chưa đo độ trễ hay thử trực tiếp trên Samsung.

Các tính năng dịch, ngân sách và bộ câu vẫn theo [hướng dẫn 1.2](KEYBOARD_1.2.md).
