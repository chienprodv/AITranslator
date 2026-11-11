# Ghi chú quyền riêng tư và phát hành

Ứng dụng này là bàn phím/dịch màn hình/dịch giọng nói, vì vậy cần công khai rõ dữ liệu nào rời thiết bị trước khi đưa cho người khác hoặc phát hành lên Google Play.

## Luồng dữ liệu hiện tại
- **Bàn phím:** chỉ nội dung người dùng chủ động yêu cầu dịch được gửi đến AI đã cấu hình. App không tự gửi tin nhắn.
- **Dịch màn hình:** ảnh chụp được OCR ngay trên thiết bị bằng ML Kit; app gửi **text OCR** cho AI, không upload screenshot trong luồng hiện tại.
- **Giọng nói:** Android SpeechRecognizer có thể dùng dịch vụ nhận dạng giọng nói do thiết bị/hệ điều hành cung cấp; sau khi thành text, nội dung được gửi đến AI để dịch.
- **Lịch sử:** lưu cục bộ trên thiết bị nếu người dùng bật tùy chọn lưu lịch sử; có nút xóa.
- **API key/token:** secret nhập trong app được lưu mã hóa bằng Android Keystore. Khi phát hành cho nhiều người, nên dùng Proxy để API key của nhà cung cấp không nằm trên điện thoại.

## Khuyến nghị trước khi phát hành
1. Có Privacy Policy công khai và màn hình giải thích dữ liệu trước lần đầu bật bàn phím/dịch màn hình/voice.
2. Khai báo Data safety trên Google Play đúng với cấu hình thực tế và nhà cung cấp AI thực tế.
3. Không log nội dung dịch, API key hoặc token ở backend production.
4. Đặt thời hạn lưu log tối thiểu; tốt nhất tắt body logging.
5. Nếu dùng proxy, thêm rate limit, authentication và chống abuse.
6. Không dùng Accessibility Service cho luồng hiện tại; dịch màn hình dựa trên MediaProjection + OCR.

Đây là ghi chú kỹ thuật cho phát hành, không phải tư vấn pháp lý.
