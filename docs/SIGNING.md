# Khóa ký XinayuAITranslator

API key dùng dịch và khóa ký APK là hai loại khác nhau. Bản debug 1.2.0 trước đây ký bằng khóa tạm do máy CI tạo; không lưu khóa riêng. Không thể khôi phục khóa riêng từ APK. Vì vậy bản 1.2.1 ký bằng khóa mới không cài đè lên APK 1.2.0.

Từ 1.2.1, CI chỉ tạo `app-debug-unsigned.apk`. APK cài đặt phải được ký trên máy chủ sở hữu bằng **cùng thư mục khóa đã giữ lại**. Artifact unsigned trên GitHub chưa cài được. Không đổi applicationId `com.ngoctho.aitranslator.debug` khi chỉ đổi tên hiển thị.

Khóa riêng và mật khẩu nằm ngoài source/output, tại thư mục workspace `private-signing/XinayuAITranslator`. Không commit hoặc đưa thư mục này vào ZIP nguồn. Giữ một bản sao an toàn của cả thư mục để ký những lần sau. Không thể dùng API key thay cho khóa này.

Ví dụ chạy từ workspace với Python và Java 17:

```powershell
python work/github-source/tools/sign-apk.py --input work/fix-1.2.1/app-debug-unsigned.apk --output outputs/keyboard-1.2.1/XinayuAITranslator_1.2.1.apk --key-dir private-signing/XinayuAITranslator --apksigner work/android-signing-tools/apksigner.jar
```

Chỉ lần tạo danh tính mới đầu tiên mới dùng `--create-key`. Nếu khóa biến mất ở bản sau, script từ chối tạo lại để tránh thay chữ ký ngoài ý muốn. Mỗi bản ký xong phải chạy xác minh v2/v3 và so sánh fingerprint chứng chỉ với bản đã phát hành trước đó. Không được ghi rằng cài đè được chỉ vì đã giữ nguyên tên app hoặc tăng versionCode.

Tài liệu Android: https://developer.android.com/studio/publish/app-signing
