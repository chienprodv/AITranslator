NGOC THO AI TRANSLATOR 1.0.0

1) XEM DEMO NGAY TREN WINDOWS
   Chay: OPEN_DEMO_WINDOWS.bat
   Hoac mo: demo\index.html

2) BUILD APK DE TEST
   Cach de nhat: dua project len GitHub -> Actions -> Build Android Debug APK -> Run workflow.
   Tai artifact NgocThoAITranslator-debug-apk -> app-debug.apk.

3) BUILD BANG ANDROID STUDIO
   Cai Android SDK Platform 36 + Build Tools 35.0.0 + Java 17.
   Mo thu muc project nay. Xem docs\BUILD_APK.md.

4) SAU KHI CAI APK
   - DEMO: khong can API key.
   - Muon dich tu do: cau hinh DeepSeek/OpenAI hoac Proxy.
   - Bat Ngoc Tho AI Keyboard trong Android Settings.
   - Dịch màn hình cần quyền bong bóng + xác nhận screen capture.
   - Voice cần quyền micro.

5) QUAN TRONG
   App KHONG tu gui tin nhan. Ban Trung chi duoc chen khi nguoi dung bam CHEN.
   Dịch ngược luôn được trả về trong luồng gửi câu/voice để kiểm tra sát nghĩa.
