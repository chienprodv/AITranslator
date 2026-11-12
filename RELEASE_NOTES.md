# Release 1.2.1 — XinayuAITranslator

Copyright by Nguyen xuan chien

Khoảng trống dưới bàn phím, màu ký tự ba vùng, RGB khi chạm, đổi tên app và quy trình giữ khóa ký. Xem [cài đặt và giới hạn chữ ký cũ](docs/KEYBOARD_1.2.1.md).

# Release 1.2.0 — 12/11/2026

Xem [tính năng, cách dùng và phạm vi kiểm tra](docs/KEYBOARD_1.2.md).

# Release Notes — 1.0.0

## Mục tiêu
Ngọc Thọ AI Translator 1.0 tập trung vào giao tiếp Việt ↔ Trung nhanh, đặc biệt cho mua hàng/đàm phán/kỹ thuật.

## Hoàn thiện trong 1.0
- `compileSdk 36` / `targetSdk 36` cho Android 16 và yêu cầu Google Play hiện hành.
- Bàn phím Android riêng, gõ tiếng Việt và tự dịch sau khoảng nghỉ có thể chỉnh.
- Luôn hiển thị: Ý của bạn là / 中文 / Dịch ngược / điểm sát nghĩa và cảnh báo.
- Không tự gửi; chỉ chèn câu Trung khi người dùng bấm nút.
- Cache và phrasebook cục bộ cho câu phổ biến.
- Dịch màn hình bằng MediaProjection, bong bóng nổi và kéo chọn vùng OCR.
- ML Kit OCR chữ Trung chạy trên thiết bị.
- Voice Việt ↔ Trung, nhận dạng giọng nói + TTS + dịch ngược.
- Lịch sử dịch, tùy chọn tắt lưu và xóa.
- Ngữ cảnh: mua hàng, đàm phán, kỹ thuật, chat thường.
- Glossary riêng.
- DeepSeek, OpenAI, Demo mode, Production Proxy.
- Mã hóa secret local bằng Android Keystore; tắt Android backup; HTTPS-only cho proxy.
- Cloudflare Worker mẫu để giữ API key ở server.
- GitHub Actions để build APK debug trên môi trường có Android SDK.
- Hướng dẫn build APK/AAB và release checklist.

## Trạng thái xác minh
- XML đã parse thành công.
- JavaScript backend và demo đã kiểm tra cú pháp.
- Java source đã kiểm tra parser-level; không thấy lỗi cú pháp Java.
- Không compile APK trực tiếp trong môi trường tạo release vì không có Android SDK/Gradle/dependency cache.
- Vì vậy file release này là source/build-ready project, không phải APK đã compile.
