# XinayuAITranslator 1.4.0

## Dịch đè theo bố cục

Nhấn nút nổi **译** để chụp và dịch chữ Trung trên màn hình. Nút tròn hiển thị 32 dp, vùng chạm 44 dp; kéo thả để chuyển sang cạnh khác, nhấn giữ để dừng dịch màn hình.

Bản dịch được vẽ đè lên **ảnh chụp đứng yên**, tại vị trí khối chữ OCR, không thay đổi nội dung ứng dụng bên dưới. Giữ **Xem gốc** để đối chiếu, chạm khối chữ để đọc đầy đủ và sao chép. **Chọn vùng** giới hạn vùng OCR. **Đóng** để tiếp tục cuộn hoặc trò chuyện, sau đó nhấn 译 để dịch ảnh mới. Ảnh không tự cập nhật liên tục.

OCR chạy trên thiết bị; chỉ phần chữ được gửi đến nhà cung cấp dịch khi dùng chế độ trực tuyến. Mỗi lần bấm gửi tối đa một yêu cầu với 32 khối, 3.200 ký tự nguồn và tối đa 2.600 token đầu ra; không tự thử lại. Giới hạn chi phí và chống gọi dồn vẫn áp dụng. Khối không có chữ Trung, quá dài hoặc vượt giới hạn được giữ nguyên. Có thể chọn vùng nhỏ để dịch phần còn lại.

Các ID khối được kiểm tra trước khi ghép bản dịch; thiếu hoặc trùng ID sẽ báo lỗi, không ghép sang vị trí khác. Bản dịch dài tự thu nhỏ và có thể hiện dấu rút gọn; chạm để xem đủ. Nền ảnh phức tạp, chữ quá nhỏ, nghiêng, OCR nhầm và chữ chồng nhau có thể làm kết quả kém đẹp. Đây không phải chỉnh sửa trực tiếp giao diện Samsung/Xianyu.

## Emoji

3.773 emoji và biến thể Unicode 15.1, chia 9 nhóm; thêm nhóm chat mua bán và kaomoji. Có phân trang, mục gần đây và yêu thích; giữ biểu tượng để ghim/bỏ ghim. Chế độ riêng tư không lưu lịch sử emoji. Hình dạng emoji phụ thuộc bộ phông của điện thoại; emoji mới có thể chưa hiển thị trên Android cũ.

Nhóm chat mua bán có cá, giao hàng, thanh toán và biểu cảm hữu ích khi chat Xianyu. **Chưa tích hợp sticker ảnh độc quyền của Xianyu.** Không gắn nhãn các emoji Unicode là sticker chính thức của Xianyu. Gửi ảnh từ bàn phím còn phụ thuộc ứng dụng chat nhận hỗ trợ nội dung ảnh.

Nguồn dữ liệu: [Unicode emoji-test 15.1](https://www.unicode.org/Public/emoji/15.1/emoji-test.txt). Giấy phép đi kèm tại `app/src/main/assets/UNICODE-LICENSE.txt`.

## Phát hành

APK cài thử phải được ký bằng khóa đang dùng cho bản 1.3.0. Tệp build chưa ký trên GitHub không cài được. Bản này vẫn là bản thử nghiệm; cần kiểm tra giao diện và quyền chụp trên điện thoại thật. Chưa phát hành lên Google Play; tài khoản Console, thử nghiệm và URL chính sách công khai vẫn cần hoàn tất.
