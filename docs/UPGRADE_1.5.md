# XinayuAITranslator 1.5.0

## Thanh bàn phím

DỊCH · CHÈN · 译 · mic · emoji · 📋 · ⋯. Chữ DỊCH/CHÈN thu gọn vừa nút. Bộ câu đã lưu và Studio LED vẫn ở ⋯. Dấu bên trái phím cách là `.`; dấu bên phải là `,`. Trong bảng ký tự trang đầu, `~` và `?` đổi chỗ.

Telex nhận cả `choiw` và `chowi` thành `chơi`; hỗ trợ W gõ muộn sau i/y, vẫn giữ cơ chế gõ lặp để gỡ dấu và các mẫu cũ.

Không hiện thanh cuộn ngang đè tên nhóm emoji. Khung bản dịch tự thu gọn sau 5 giây từ khi hiện kết quả hoặc mở lại, và khi ô nhập trống. Thu gọn không tự gửi tin nhắn.

Chạm lại ô chat đưa các bảng phụ về bàn phím chữ khi ứng dụng gửi sự kiện chạm/con trỏ cho IME. Một số ô chat tùy biến không thông báo khi chạm đúng vị trí con trỏ cũ; trường hợp đó dùng ABC. Không thêm dịch vụ Trợ năng để theo dõi chạm bên ngoài.

## Dịch toàn màn hình

Bấm 译 trên thanh bàn phím. Bàn phím ẩn đi và app chụp toàn màn hình, OCR cục bộ, hiện bản dịch thành trang đọc cuộn được; có xem ảnh gốc, sao chép, dịch lại và đóng. Không cần giữ bong bóng và không còn nút chọn vùng.

Nếu phiên chụp còn sống, app dùng lại. Nếu Android/OEM đã dừng phiên, bấm 译 sẽ xin quyền chụp mới. Lần đầu còn cần quyền hiển thị trên ứng dụng khác để mở trang dịch. App không vượt qua quyền Android hoặc màn hình chặn chụp.

Toàn bộ chữ OCR được dịch theo thứ tự, không lọc theo 32 hộp và không âm thầm cắt ở 3.200 ký tự. Nội dung dài chia phần tối đa 1.500 ký tự, tổng tối đa 12.000 ký tự và 12 phần; vượt giới hạn báo lỗi trước khi gọi AI. Mỗi phần là một yêu cầu, tối đa 3.200 token đầu ra; vẫn dùng giới hạn lượt/ngân sách VNĐ, không tự thử lại. Có báo tiến độ; khi lỗi sẽ ghi rõ chưa dịch hết và giữ phần đã có. Đóng trang sẽ ngừng các phần chưa gửi; yêu cầu đang gửi không thể thu hồi và vẫn có thể tính phí. Chỉ dịch những chữ OCR nhận ra trong ảnh hiện tại, không nội dung nằm ngoài màn hình hoặc OCR không nhận được.

## Bộ nhớ sao chép 📋

Bật công tắc trong 📋 để lưu clipboard tự động cục bộ khi Android cho phép. Mặc định chưa bật. Lưu tối đa 30 mục văn bản gần đây, tối đa 6.000 ký tự/mục, hết hạn sau 24 giờ khi truy cập. Có chạm để dán, ghim, xóa từng mục và xóa tất cả. Mục ghim tuân theo thời hạn clipboard ghim đã đặt trong Cài đặt. Xóa bộ nhớ không xóa clipboard Android hiện tại.

Không lưu ô riêng tư hoặc mục được ứng dụng nguồn đánh dấu nhạy cảm. Nếu nguồn không gắn cờ, app không thể tự nhận biết mọi bí mật trong văn bản; có thể tắt lưu và xóa bộ nhớ. Android có thể hạn chế truy cập khi app không hoạt động hoặc không phải bàn phím mặc định; không khôi phục được lịch sử sao chép trước khi bật chức năng. Lịch sử tự động không xuất qua sao lưu và không tự gửi AI.

## Ảnh nền và trong suốt

Chỉ ở ⋯ → Cài đặt → Giao diện bàn phím. Chọn ảnh qua trình chọn của Android, lưu bản sao tối đa 2.048 px trong app, không cần quyền đọc toàn thư viện. Có xóa ảnh, chỉnh độ đậm nền bàn phím và nền từng phím riêng. Chữ giữ độ rõ. Độ trong suốt nhìn thấy nội dung phía sau phụ thuộc cửa sổ IME/app, không đảm bảo thấy xuyên vào mọi ứng dụng. Ảnh không có trong sao lưu JSON, không gửi AI. Ảnh được tải ngoài luồng giao diện và dùng lại trong bộ vẽ LED theo VSync.

Vẫn chỉ phát hành APK ký bằng khóa đã giữ, cài đè bản 1.4.2. Không chuẩn bị AAB/CH Play.

Kiểm tra mã và build không thay thế thử nghiệm trên Samsung thật: cần thử quyền chụp mới, thao tác ô chat, clipboard và chọn ảnh trên điện thoại.
