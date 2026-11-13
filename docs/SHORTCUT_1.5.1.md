# 1.5.1 — mở dịch khi không dùng bàn phím

Chỉ thay đổi lối mở dịch màn hình; giữ nguyên chức năng 1.5.0.

- Mở app → **THÊM Ô 译 VÀO THANH CÀI ĐẶT NHANH**. Android 13+ hiện yêu cầu thêm ô; bản cũ hướng dẫn tự kéo ô vào phần sửa cài đặt nhanh. Sau đó có thể kéo thanh trên xuống và bấm **译 Dịch màn hình** mà không mở bàn phím. Ô yêu cầu mở khóa trước khi chụp nếu điện thoại đang khóa.
- **Giữ bong bóng 译 khi đóng bàn phím** bật mặc định; cần quyền hiển thị trên ứng dụng khác. Bong bóng chạy bằng dịch vụ foreground riêng, không kết thúc theo phiên MediaProjection. Đóng bản dịch hoặc phiên chụp bị dừng sẽ trả lại bong bóng. Kéo để di chuyển, giữ để tắt, hoặc tắt trong app/thông báo.
- Thông báo phím tắt có **Dịch** và **Tắt bong bóng**. Cho phép thông báo nếu muốn thấy các nút này trên Android yêu cầu quyền thông báo.
- Nếu phiên chụp còn sống sẽ dùng lại; nếu đã dừng, bấm shortcut xin quyền chụp mới. Không tái sử dụng token hết hiệu lực, không bỏ qua màn hình cấp quyền.
- Android/Samsung vẫn có thể dừng app hoặc người dùng có thể buộc dừng. Không cam kết bong bóng không bao giờ bị hệ thống tắt. Dịch vụ đề nghị khởi động lại khi bị hệ thống thu hồi, tôn trọng lựa chọn tắt của người dùng; không chạy dịch tự động hoặc tự gọi API khi khởi động lại.

Kiểm tra: biên dịch API 36, manifest và hồi quy lõi/nút bấm. Chưa xác nhận trực tiếp trên Samsung thật. Chỉ tạo APK, ký cùng khóa cũ để cài đè 1.5.0.

Tham khảo: [TileService](https://developer.android.com/reference/android/service/quicksettings/TileService), [yêu cầu thêm ô](https://developer.android.com/reference/android/app/StatusBarManager#requestAddTileService(android.content.ComponentName,%20java.lang.CharSequence,%20android.graphics.drawable.Icon,%20java.util.concurrent.Executor,%20java.util.function.Consumer%3Cjava.lang.Integer%3E)), [dịch vụ specialUse](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use).
