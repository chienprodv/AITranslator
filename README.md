# XinayuAITranslator 1.3.0

Copyright by Nguyen xuan chien

Bản 1.3.0: Studio LED với 16 hiệu ứng, nút 💡 chỉnh mode/độ sáng/tốc độ; xem [hướng dẫn LED](docs/LED_1.3.md) và [hồ sơ chuẩn bị CH Play](docs/PLAY_PREPARATION.md). Bản đã ký dùng cùng khóa 1.2.1 để cài đè. Xem [hướng dẫn bản fix và cài đặt](docs/KEYBOARD_1.2.1.md), [quy trình ký bằng khóa cố định](docs/SIGNING.md).

CI hiện tạo APK unsigned; APK cài đặt cần ký bằng khóa riêng đã giữ trên máy. Chữ ký mới từ 1.2.1 không cài đè bản debug 1.2.0 đã mất khóa riêng.

Ứng dụng Android dịch Việt ↔ Trung tập trung vào **tốc độ + kiểm tra sát nghĩa bằng dịch ngược**.

## Tính năng 1.0

- Bàn phím Android riêng `Ngọc Thọ AI Keyboard`.
- Tự dịch sau khi người dùng dừng gõ; độ trễ mặc định 650 ms và có thể chỉnh.
- Việt → Trung theo ngữ cảnh mua hàng, đàm phán, kỹ thuật hoặc chat thông thường.
- Luôn trả về:
  - `Ý của bạn là…`
  - câu tiếng Trung;
  - dịch ngược tiếng Việt;
  - điểm sát nghĩa và cảnh báo.
- Không tự gửi tin nhắn; người dùng phải bấm `CHÈN 中文`.
- Từ điển cục bộ/cache cho một số câu mua hàng phổ biến để phản hồi gần như tức thì.
- Dịch màn hình bằng MediaProjection + bong bóng `译`.
- **Kéo chọn vùng màn hình cần dịch**, thay vì luôn OCR toàn màn hình.
- OCR chữ Trung bằng ML Kit bundled ngay trên thiết bị; chỉ text OCR mới được gửi cho AI.
- Dịch giọng nói hai chiều Việt ↔ Trung, partial speech recognition và TTS.
- Lịch sử dịch trên máy, có thể tắt và xóa.
- Từ điển thuật ngữ riêng của người dùng.
- Chế độ DEMO không cần API key.
- DeepSeek trực tiếp (`deepseek-flash`).
- OpenAI trực tiếp (`gpt-6-luna`).
- Chế độ Server/Proxy cho bản production.
- API key/token nhập trên điện thoại được mã hóa bằng Android Keystore.

## Mục tiêu tốc độ

1. Câu đã cache/từ điển cục bộ: gần như tức thì.
2. Câu mới: chỉ một request AI trả đồng thời câu dịch + dịch ngược + score để giảm round-trip.
3. DeepSeek chạy non-thinking trong luồng dịch.
4. OCR chạy trên điện thoại, không upload screenshot.
5. Chọn vùng OCR giúp giảm lượng chữ thừa và token.

Tốc độ API thực tế còn phụ thuộc mạng, khu vực server và tải của nhà cung cấp AI.

## Cấu trúc

- `app/`: Android app Java 17.
- `demo/index.html`: demo giao diện chạy trực tiếp trên trình duyệt.
- `backend/cloudflare-worker/`: proxy mẫu cho production.
- `.github/workflows/build-debug-apk.yml`: build APK tự động trên GitHub Actions.
- `.github/workflows/build-signed-release.yml`: build APK release + AAB có chữ ký bằng GitHub Secrets.
- `docs/BUILD_APK.md`: hướng dẫn đóng gói APK/AAB.
- `docs/DEMO.md`: hướng dẫn demo.

## Yêu cầu build

- `compileSdk 36` / `targetSdk 36` để đáp ứng yêu cầu Google Play cho app mới/cập nhật từ 31/08/2026.
- Android Studio.
- Android SDK 36.
- Build Tools 35.0.0.
- Java 17.
- Android Gradle Plugin 8.13.2.
- Gradle 8.13 khi build từ CLI/CI.

## OCR dependencies

```gradle
implementation 'com.google.mlkit:text-recognition:16.0.1'
implementation 'com.google.mlkit:text-recognition-chinese:16.0.1'
```

## Bảo mật

- Khóa nhập trực tiếp được mã hóa at-rest bằng Android Keystore.
- App không nhúng sẵn API key vào source/APK.
- `android:usesCleartextTraffic="false"`; proxy phải là HTTPS.
- Khi phát hành cho nhiều người, nên dùng `Server/Proxy` và giữ API key trên server.
- Không commit keystore, `signing.properties`, API key hay proxy token.

## Quyền Android

- `INTERNET`: gọi AI/proxy.
- `SYSTEM_ALERT_WINDOW`: bong bóng dịch màn hình.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PROJECTION`: phiên chụp màn hình.
- `RECORD_AUDIO`: dịch giọng nói.
- `POST_NOTIFICATIONS`: thông báo foreground service trên Android mới.

## Lưu ý MediaProjection

Android 14+ yêu cầu người dùng đồng ý cho từng phiên capture mới. App tạo một MediaProjection/VirtualDisplay cho mỗi lần người dùng bật phiên dịch màn hình; nếu tắt phiên hoặc hệ thống thu hồi quyền thì phải bật lại từ app.

## Trạng thái kiểm tra trong môi trường tạo project

- XML/Manifest: đã parse hợp lệ.
- Cloudflare Worker JavaScript: đã kiểm tra cú pháp bằng Node.
- Java source: đã chạy kiểm tra parser-level; không phát hiện lỗi cú pháp dạng thiếu dấu ngoặc/dấu chấm phẩy.
- Chưa compile APK ngay trong môi trường này vì không có Android SDK/Google Maven dependency cache.
- Project có GitHub Actions để build `app-debug.apk` trên hạ tầng có Android SDK.

Xem **`docs/BUILD_APK.md`** để đóng gói và **`docs/DEMO.md`** để demo.
