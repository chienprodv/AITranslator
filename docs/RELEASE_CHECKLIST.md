# Ngọc Thọ AI Translator 1.0 — Release checklist

## Trước khi build
- Giữ `compileSdk 36` / `targetSdk 36` nếu mục tiêu là phát hành app điện thoại mới/cập nhật trên Google Play hiện tại.
- Chọn `DEMO` nếu chỉ thử giao diện, không cần API key.
- Nếu gọi API trực tiếp để thử cá nhân, nhập API key trong app. Key được mã hóa bằng Android Keystore khi lưu.
- Nếu phát hành cho nhiều người, dùng `Proxy` và giữ API key trên server, không nhúng key vào APK.
- Kiểm tra `Business context` và glossary trước khi test câu mua hàng thực tế.

## Test tối thiểu
1. Bật `Ngọc Thọ AI Keyboard` trong Android Settings.
2. Gõ: `bên bạn còn hàng không tôi lấy 100 cái`.
3. Kiểm tra đủ 4 vùng: Ý của bạn / 中文 / Dịch ngược / độ sát nghĩa-cảnh báo.
4. Bấm `CHÈN 中文`, xác nhận chỉ chèn chứ không tự gửi.
5. Bật dịch màn hình, cấp quyền overlay + screen capture, kéo chọn một vùng chữ Trung rồi dịch.
6. Mở Voice, thử VI→ZH và ZH→VI, kiểm tra dịch ngược và nút đọc.
7. Mở Lịch sử, thử copy và xóa lịch sử.

## Release
- Tăng `versionCode` và `versionName` trong `app/build.gradle` khi phát hành bản mới.
- Tạo keystore riêng và giữ ngoài repository.
- Điền `signing.properties` từ mẫu.
- Build `assembleRelease` cho APK hoặc `bundleRelease` cho AAB.
- Cài APK release lên ít nhất một máy Android 10–13 và một máy Android 14+ nếu có.
- Không phát hành nếu API key production nằm trong app.
