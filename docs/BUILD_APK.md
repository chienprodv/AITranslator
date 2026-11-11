# Cách đóng gói APK / AAB — Ngọc Thọ AI Translator 1.0

Project dùng `compileSdk 36` / `targetSdk 36` để phù hợp yêu cầu Google Play hiện hành cho app điện thoại mới/cập nhật.

## Cách A — Dễ nhất: Android Studio trên Windows

1. Cài Android Studio bản hiện hành từ trang Android Developers.
2. Giải nén project và mở đúng thư mục `NgocThoAITranslator`.
3. Vào **Tools > SDK Manager** và cài:
   - Android SDK Platform 36
   - Android SDK Build-Tools 35.0.0
   - Android SDK Platform-Tools
4. Project dùng **Java 17** và Android Gradle Plugin 8.13.2.
5. Chờ Gradle Sync tải ML Kit OCR.
6. Cắm điện thoại Android, bật **Developer options > USB debugging**.
7. Bấm **Run** để cài trực tiếp lên máy.

### Xuất APK debug để gửi thử

Trong Android Studio:

**Build > Build App Bundle(s) / APK(s) > Build APK(s)**

File thường nằm tại:

`app/build/outputs/apk/debug/app-debug.apk`

Có thể cài bằng ADB:

`adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Cách B — Build APK không cần cài Android SDK trên máy: GitHub Actions

Project đã có file:

`.github/workflows/build-debug-apk.yml`

Thao tác:

1. Tạo repository GitHub riêng tư.
2. Upload toàn bộ nội dung project.
3. Mở tab **Actions**.
4. Chọn **Build Android Debug APK**.
5. Bấm **Run workflow**.
6. Khi build xong, tải artifact `NgocThoAITranslator-debug-apk`.
7. Giải nén artifact để lấy `app-debug.apk`.

Workflow tự cài Java 17, Android SDK 36, Build Tools 35.0.0 và Gradle 8.13.

## Cách C — APK release có chữ ký

### 1. Tạo keystore một lần duy nhất

Mở Terminal/PowerShell trong thư mục project:

`mkdir keystore`

`keytool -genkeypair -v -keystore keystore/ngoctho-release.jks -alias ngoctho -keyalg RSA -keysize 3072 -validity 10000`

**Không làm mất file `.jks` này.** Nếu cập nhật app về sau, phải dùng đúng khóa cũ.

### 2. Tạo file signing.properties

Copy `signing.properties.example` thành `signing.properties`, rồi điền:

```properties
storeFile=keystore/ngoctho-release.jks
storePassword=MAT_KHAU_KEYSTORE
keyAlias=ngoctho
keyPassword=MAT_KHAU_KEY
```

Không đưa `signing.properties`, mật khẩu hoặc keystore lên repository công khai.

### 3. Build release

Trong Android Studio:

**Build > Generate Signed App Bundle / APK > APK**

Hoặc nếu máy đã có Gradle 8.13:

`gradle :app:assembleRelease`

APK:

`app/build/outputs/apk/release/app-release.apk`

### 4. Build AAB để đưa Google Play

`gradle :app:bundleRelease`

AAB:

`app/build/outputs/bundle/release/app-release.aab`

## Cấu hình dùng thử sau khi cài APK

### Demo không cần API

1. Mở app.
2. Chọn **DEMO — không cần API key**.
3. Bấm **NẠP CÂU DEMO**.
4. Bấm **DỊCH + DỊCH NGƯỢC**.
5. Sau đó bật `Ngọc Thọ AI Keyboard` trong cài đặt bàn phím Android.

### DeepSeek trực tiếp

1. Chọn **DeepSeek trực tiếp**.
2. Model mặc định: `deepseek-flash`.
3. Nhập API key.
4. Bấm **LƯU CẤU HÌNH**.

### OpenAI trực tiếp

1. Chọn **OpenAI trực tiếp**.
2. Model mặc định: `gpt-6-luna`.
3. Nhập API key.
4. Bấm **LƯU CẤU HÌNH**.

### Production bằng proxy

Dùng thư mục `backend/cloudflare-worker` và làm theo README trong đó. Đây là cách nên dùng nếu APK được đưa cho nhiều người vì khóa OpenAI/DeepSeek không nằm trên điện thoại.

## Kiểm thử trước khi phát hành

Nên thử ít nhất:

- Android 10/11.
- Android 13.
- Android 14/15 hoặc mới hơn.
- WeChat/Zalo/Chrome/1688/Taobao.
- Bàn phím với ô chat một dòng và nhiều dòng.
- Dịch màn hình ngang/dọc; khi xoay màn hình nên tắt rồi bật lại phiên MediaProjection.
- Câu có số lượng, mã hàng, giá tiền, phủ định và ngày tháng để kiểm tra bản dịch ngược.
- Micro và TTS tiếng Trung.

## Các file không được chia sẻ công khai

- `signing.properties`
- `*.jks`, `*.keystore`
- API key OpenAI/DeepSeek
- Proxy AUTH_TOKEN

## Cách D — GitHub Actions xuất APK release + AAB có chữ ký

Project có thêm workflow:

`.github/workflows/build-signed-release.yml`

### Chuẩn bị 4 GitHub Actions Secrets

- `ANDROID_KEYSTORE_BASE64`: nội dung file `.jks` được mã hóa Base64.
- `KEYSTORE_PASSWORD`: mật khẩu keystore.
- `KEY_ALIAS`: alias của key, ví dụ `ngoctho`.
- `KEY_PASSWORD`: mật khẩu key.

PowerShell có thể tạo chuỗi Base64 như sau:

`[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore\\ngoctho-release.jks")) | Set-Clipboard`

Sau đó vào **GitHub repository > Settings > Secrets and variables > Actions > New repository secret** để thêm 4 secret trên.

Chạy **Actions > Build Signed Release APK and AAB > Run workflow**. Artifact `NgocThoAITranslator-release` sẽ chứa:

- `app-release.apk`
- `app-release.aab`

Không đưa file keystore hoặc `signing.properties` trực tiếp lên repository.
