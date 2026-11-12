# Bản sửa 1.4.1

- Phím Enter đọc hành động của ô nhập: Tìm, Gửi, Đi, Tiếp, Trước, Xong hoặc nhãn tùy chỉnh. Gửi một editor action, không chèn dấu cách/xuống dòng hoặc gửi thêm Enter khi host trả false (giao thức bất đồng bộ).
- Ô nhiều dòng không yêu cầu hành động vẫn xuống dòng; ô một dòng hoặc ô nhập thô không khai báo action nhận cặp sự kiện Enter xuống/lên.
- Bong bóng dùng một TextView nhận chạm duy nhất, nền tròn 32 dp và vùng chạm 44 dp. Có click handler cho cả chạm và trợ năng. Rung tay dưới ngưỡng 12 dp không di chuyển cửa sổ hoặc hủy cú bấm. Kéo thật vẫn di chuyển và hút cạnh; nhấn giữ để dừng.
- Nhấn 译 báo ngay “Đang chụp màn hình”; báo đang OCR/đang dịch trên ảnh. Màn hình chính hiển thị giai đoạn gần nhất, không ghi nội dung OCR/tin nhắn/key vào chẩn đoán.
- Không xóa khung hình sẵn có trước khi chụp. Chờ tối đa 8 lần 100 ms khi chưa có ảnh; đây chỉ là chờ ảnh cục bộ, không gọi API lại. Giữ nguyên ImageReader nếu thông báo kích thước bằng kích thước hiện tại. Android 14+ yêu cầu chia sẻ toàn màn hình cho tính năng này (hệ thống vẫn quyết định giao diện cấp quyền).
- Chụp/OCR/dịch có giới hạn chờ và báo lỗi, không giữ trạng thái bận mãi. Đóng hoặc xoay màn hình bỏ qua kết quả cũ; không tự gọi lại API. Một yêu cầu đã gửi có thể vẫn hoàn tất và được tính phí.

Kiểm tra: hồi quy editor action (kể cả host trả false), multiline, custom action, sự kiện Enter và tap/drag/hold/cancel; kiểm tra lõi gõ phím và biên dịch API 36. Chưa có log hoặc kết quả chạy trực tiếp trên điện thoại của người dùng nên không khẳng định đã tái hiện nguyên nhân duy nhất của lỗi bong bóng.
