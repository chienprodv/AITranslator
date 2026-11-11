# Demo nhanh

## Demo trên máy tính, không cần Android

Mở file:

`demo/index.html`

Demo mô phỏng ba luồng:

1. Bàn phím: gõ Việt → hiện Trung → dịch ngược → độ sát nghĩa.
2. Màn hình: mô phỏng chọn vùng chữ Trung và dịch sang Việt.
3. Giọng nói: dùng Web Speech API nếu trình duyệt hỗ trợ; nếu không thì tự dùng câu mẫu.

Demo HTML hoàn toàn cục bộ, không gửi dữ liệu lên AI.

## Demo trên Android thật

Sau khi build/cài APK:

1. Giữ chế độ `DEMO`.
2. Nạp câu: `Nếu tôi lấy 500 cái thì giá tốt nhất là bao nhiêu?`
3. Kiểm tra kết quả:
   - 中文: `如果我订500个，你们能给我的最优惠价格是多少？`
   - Dịch ngược: `Nếu tôi đặt 500 cái, mức giá ưu đãi nhất bên bạn có thể đưa ra là bao nhiêu?`
4. Bật bàn phím Ngọc Thọ AI Keyboard.
5. Gõ câu demo trong ô chat và chờ tự dịch.

Muốn dịch tự do mọi câu, chọn DeepSeek/OpenAI hoặc cấu hình proxy.
