# XinayuAITranslator 1.4.2 — LED và cuộn cài đặt

- Bỏ lớp vẽ phần mềm của toàn bàn phím. Vẽ nền và viền neon bằng hình học Canvas; bóng chữ vẫn dùng đường vẽ được Android hỗ trợ trên GPU. Nếu thiết bị không cấp tăng tốc phần cứng, Canvas vẫn có đường dự phòng của hệ thống.
- Dùng `View.postOnAnimation` theo nhịp VSync của màn hình, thay bộ hẹn cố định 33 ms. Chỉ có một callback khung hình chờ cho mỗi view; dừng khi ẩn, tháo khỏi cửa sổ, tắt hoạt ảnh, LED tĩnh/tắt hoặc ngoài vùng nhìn thấy. Giữ các mode, tốc độ, màu và độ sáng hiện có.
- Lưu sẵn khung phím, nhãn, cỡ chữ và đường cơ sở. Tải thiết lập khi thay đổi thay vì đọc preferences và đo chữ trong từng khung hình. Tái sử dụng đường gõ vuốt. Bỏ mảng tạm trong mỗi phép trộn màu.
- Preview ở màn Bố cục tạm dừng khi cuộn và trong quán tính cuộn; chạy lại sau khi dừng 180 ms nếu còn nhìn thấy và Activity đang hoạt động. Đổi sang màn khác cũng dừng preview.
- Cửa sổ app/bàn phím đề nghị tốc độ làm tươi cao nhất đang hỗ trợ ở cùng độ phân giải. Bàn phím trả lại đề nghị cũ khi ẩn. Android/Samsung vẫn có quyền giới hạn theo cài đặt màn hình, pin, nhiệt hoặc ứng dụng đang mở. Đây không phải cam kết đã đo được 120 FPS.
- Vẫn tôn trọng thiết lập tắt hoạt ảnh của Android. LED tĩnh/tắt không tự tạo vòng vẽ liên tục.

Kiểm tra: hồi quy lõi gõ, Enter/nút nổi và màu LED; biên dịch API 36; CI chỉ build APK. Chưa có trace Perfetto/FrameMetrics hoặc thử nghiệm GPU trên điện thoại thật, nên không khẳng định đã hết mọi giật khung hình. Sau khi cài đè, kiểm tra cuộn Bố cục, thay LED, đóng/mở bàn phím và nhập tìm kiếm.

Từ 12/11/2026 đã hủy kế hoạch CH Play: không build AAB, ký bundle hoặc chuẩn bị hồ sơ Play.

Tham khảo: [VSync callback của View](https://developer.android.com/reference/android/view/View#postOnAnimation(java.lang.Runnable)), [tăng tốc phần cứng](https://developer.android.com/topic/performance/views/hardware-accel-views), [đề nghị refresh rate của cửa sổ](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#preferredRefreshRate), [chẩn đoán giật khung hình](https://developer.android.com/topic/performance/issues/render).
