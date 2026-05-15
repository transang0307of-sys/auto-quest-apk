# Discord Quest Auto – Android App

Bot tự động hoàn thành Discord Quests, port sang Android Kotlin. **Free, không cần key.**

## Tính năng
- Nhập User Token → tự động scan & hoàn thành tất cả quests
- Hỗ trợ: Video quests (WATCH_VIDEO, WATCH_VIDEO_ON_MOBILE), Game quests (PLAY_ON_DESKTOP)
- UI Discord dark theme
- Chạy nền qua Foreground Service
- Không cần key, không cần đăng nhập

## Build APK tự động (GitHub Actions)

### Cách 1 – Dùng GitHub Actions (khuyên dùng, không cần cài gì)

1. Fork repo này lên GitHub của bạn
2. Vào tab **Actions** → chọn workflow **Build APK** → nhấn **Run workflow**
3. Đợi ~3-5 phút, vào **Artifacts** tải `discord-quest-auto-release.apk`
4. Mỗi lần push code lên `main`, Actions tự build APK mới và tạo Release

### Cách 2 – Build local bằng Android Studio

```
1. Mở Android Studio 2024+
2. File → Open → chọn thư mục này
3. Đợi Gradle sync xong
4. Build → Build Bundle(s) / APK(s) → Build APK(s)
5. APK ở: app/build/outputs/apk/debug/
```

### Cách 3 – Build bằng command line

```bash
# Cần Java 17+
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk

./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

## Cài APK lên điện thoại

1. Tải APK về điện thoại
2. Vào **Cài đặt → Bảo mật → Cài từ nguồn không xác định** → Bật
3. Mở file APK → Cài đặt

## Lấy User Token Discord

> ⚠️ **Không chia sẻ token với ai khác!**

### Trên PC (Chrome DevTools):
1. Mở Discord Web (discord.com)
2. F12 → tab **Network**
3. Reload trang, tìm request bất kỳ
4. Header `Authorization` = token của bạn

### Trên Mobile (cần PC):
- Dùng Termux + frida, hoặc dùng Discord trên web qua PC

## Stack

| Layer | Tech |
|-------|------|
| Language | Kotlin |
| Min SDK | 26 (Android 8.0) |
| HTTP | OkHttp 4 |
| JSON | Gson |
| Async | Coroutines + Flow |
| UI | Material3 + ViewBinding |
| CI/CD | GitHub Actions |

## Cấu trúc project

```
app/src/main/
├── java/com/discordquest/auto/
│   ├── model/Models.kt          # Data classes
│   ├── network/DiscordApiClient.kt  # API calls (port từ quest_runner.py)
│   ├── viewmodel/QuestViewModel.kt  # State management
│   └── ui/
│       ├── MainActivity.kt      # Main UI
│       └── QuestForegroundService.kt
├── res/layout/activity_main.xml
└── AndroidManifest.xml
.github/workflows/build.yml      # GitHub Actions CI/CD
```
