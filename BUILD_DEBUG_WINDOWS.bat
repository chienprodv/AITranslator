@echo off
setlocal
where gradle >nul 2>nul
if errorlevel 1 (
  echo [LOI] Chua tim thay lenh Gradle.
  echo Cach de nhat: dung GitHub Actions trong .github\workflows\build-debug-apk.yml
  echo Hoac cai Gradle 8.13 va Android SDK 36, sau do chay lai file nay.
  pause
  exit /b 1
)
gradle --no-daemon :app:assembleDebug
if errorlevel 1 (
  echo Build that bai. Xem log phia tren.
  pause
  exit /b 1
)
echo.
echo APK: app\build\outputs\apk\debug\app-debug.apk
pause
