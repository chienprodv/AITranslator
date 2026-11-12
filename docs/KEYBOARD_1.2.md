# Bàn phím 1.2.0

Gõ câu gốc trong ô chat → DỊCH → xem câu dịch và dịch ngược → CHÈN để thay câu. Ứng dụng không tự gửi tin nhắn. AUTO chỉ tạo bản xem trước.

## Sửa lỗi gõ

- Vùng chạm liền, nhận cả khoảng cách giữa các phím, xử lý hai ngón chồng nhau theo thứ tự nhấn; trượt ra xa bàn phím sẽ hủy phím.
- Hàng số, QWERTY lệch hàng, Shift, xóa và hàng dưới theo bố cục Samsung trong ảnh tham khảo. Hai trang ký hiệu có + × ÷ / _ dấu ngoặc, nháy và tiền tệ. Nhấn !#1 để mở; Shift đổi trang. Bố cục Samsung có thể khác theo phiên bản/ngôn ngữ.
- Telex lưu chuỗi phím gốc: ass → as, aaa → aa, ddd → dd; asus → asus. Với tên riêng chưa nhận ra, chuyển VI/EN bằng phím quả địa cầu. Trường hợp asa giữ nguyên theo ví dụ của người dùng.
- Nhấn giữ xóa để xóa liên tục; giữ nguyên âm để chọn chữ có dấu, giữ hàng qwerty để chọn số; giữ Shift bật Caps Lock.

## Các mục mới và cách mở

| Nhóm | Tính năng | Vị trí / hành vi |
|---|---|---|
| A1–A3 | Chiều cao, cỡ chữ, rung/âm, sáng/tối/hệ thống, một tay trái/phải | ⋯ → Bố cục, từ điển, sao lưu, số dư |
| A4–A6 | Kéo dấu cách di chuyển con trỏ; chọn/cắt/copy/dán/hoàn tác; ký tự nhấn giữ | Phím cách / ⋯ / giữ phím |
| A7–A8 | Gợi ý từ cục bộ, học từ tùy chọn, tự sửa có hoàn tác, vuốt gõ | Cài đặt bàn phím; tự sửa và vuốt mặc định tắt |
| B1–B3 | Đổi VI→Trung/Trung→VI, ngữ cảnh, giọng văn | ⋯; giữ mic cũng đổi chiều |
| B4–B6 | Tô số/mã, cảnh báo số/mã bị đổi, phương án dịch 2, khóa thuật ngữ | Xem trước / ⋯ / Cài đặt bàn phím |
| B7–B8 | Hoàn tác chèn khi nội dung chưa thay đổi; tùy chọn tự lưu câu | ↶ / Cài đặt bàn phím |
| C1–C3 | Câu mẫu theo nhóm, ô [ ] cần điền, gõ /gia rồi cách để bung mẫu | ☰ → Mẫu hoặc ⋯ → Câu mẫu; đổi mẫu trong cài đặt |
| C4–C6 | Clipboard ghim, tìm câu VI/Trung, nhập/xuất câu và cấu hình | ⋯ → Clipboard; ☰; cài đặt → Sao lưu |
| D1–D2 | Đọc bản dịch, mic ngay bàn phím, xem/sửa lời nhận dạng trước khi dịch | ⋯ → Đọc; 🎤. Mặc định nhận dạng vào ô chat rồi bấm DỊCH |
| D3–D4 | Riêng tư thủ công, tự chặn AI ở trường nhạy cảm, thời hạn lịch sử/clipboard | ⋯; Cài đặt bàn phím. Mặc định 30 ngày / 24 giờ |
| D5–D6 | Biểu đồ lượt gọi, thống kê bàn phím/giọng nói/màn hình, kiểm tra key/số dư/model DeepSeek | Cài đặt bàn phím; kiểm tra tài khoản chỉ khi bấm |
| D7–D8 | Tiết kiệm bằng tắt AUTO, sao lưu có chọn clipboard và loại toàn bộ bí mật | Cài đặt bàn phím |

Câu yêu thích tối đa 100, clipboard ghim 30, từ học 300. Hết hạn được dọn khi truy cập danh sách. Tệp sao lưu JSON không có API key, proxy URL/token, lịch sử chi phí hay clipboard hệ thống; clipboard ghim chỉ kèm khi chọn. Không dùng sao lưu Android tự động để mang key sang máy khác.

## Hạn mức VNĐ và chống gọi dồn

Giữ cơ chế một yêu cầu dịch đang chạy, giới hạn lượt/phút và lượt/ngày, không tự thử lại, tạm nghỉ sau nhiều lỗi; cache câu đã dịch. Cấu hình hạn mức theo ngày hoặc tháng, có 5.000đ và 10.000đ; cảnh báo khi còn tối đa 20%, chặn yêu cầu mới khi dự trù vượt mức. Ngân sách VNĐ là ước tính cục bộ dựa trên token, tỷ giá và giá model đã cấu hình, không phải số dư tài khoản nhà cung cấp. API lỗi không rõ đã tính phí hay chưa vẫn giữ khoản dự trù. Đổi hạn mức/khôi phục sao lưu không xóa chi phí đã dùng. Phương án dịch thứ hai là một yêu cầu có thể tính phí; chọn lại phương án có sẵn không gọi AI.

## Quyền và giới hạn kiểm chứng

Không cần cấp thêm quyền Windows để dùng mã nguồn. Điện thoại cần bật/chọn bàn phím; lần đầu bấm mic sẽ xin RECORD_AUDIO. Nhập/xuất dùng trình chọn tệp Android, không xin quyền toàn bộ bộ nhớ. TTS/nhận dạng tùy dịch vụ đã cài; dịch vụ có thể dùng mạng. Dịch AI cần key hợp lệ và mạng. Bàn phím không có kho từ hay mô hình vuốt quy mô Samsung: dự đoán hiện dùng từ điển cục bộ nhỏ và từ đã học.

Đã chạy 1.343 kiểm tra lõi (Telex, InputConnection giả lập, hình học/vùng chạm, ký hiệu, vuốt và cảnh báo số/mã), 16 kiểm tra dữ liệu/sao lưu. Kiểm tra Java với Android API 36 không có lỗi nguồn (OCR dùng stub cục bộ; build CI mới kiểm tra phụ thuộc thật). Chưa có điện thoại Samsung kết nối để xác nhận cảm giác gõ, mic, TTS và tương thích từng ứng dụng chat. Cần thử thực tế sau khi cài APK.
