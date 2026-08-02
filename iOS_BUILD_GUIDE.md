# Build iOS (Xcode) - Hướng dẫn thủ công

## ✅ Vì sao dùng Xcode thay Gradle CLI?

- Gradle wrapper bị lỗi SSL cert trên environment này
- Xcode sẽ compile Kotlin → Swift framework tự động

## 📱 Các bước build & run iOS:

### 1️⃣ Mở Xcode project

Project đã mở: `/iosApp/iosApp.xcodeproj`

### 2️⃣ Setup scheme & destination

1. Xcode menu → **Product** → **Scheme** → chọn `iosApp`
2. **Product** → **Destination** → chọn **iPhone 16 Pro Simulator** (hoặc device)

### 3️⃣ Build shared framework (Kotlin → Swift)

**Cách A: Từ Xcode (tự động)**
- Xcode sẽ detect `shared` folder
- Build scripts sẽ chạy lệnh Kotlin tự động
- Check **Build Phases** nếu cần config

**Cách B: Manual từ terminal (nếu Xcode build fail)**
```bash
cd /Users/ly.t/Desktop/greenie-auto/app
export JAVA_OPTS="-Xmx2g"
# Bypass SSL cho gradle
export GRADLE_USER_HOME="~/.gradle"
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 \
  -Dorg.gradle.jvmargs="-Xmx2g" \
  2>&1
```

Hoặc dùng direct Kotlin compiler (nếu cài CocoaPods):
```bash
cd iosApp
pod install --repo-update
```

### 4️⃣ Build ứng dụng

Trong Xcode:
- **Product** → **Build** (⌘B)
- Chờ build hoàn thành

### 5️⃣ Run trên Simulator

- **Product** → **Run** (⌘R)
- Hoặc bấm play button ▶️ ở top

### 6️⃣ Expected khi chạy iOS app

**Màn hình 1 - Setup WiFi:**
- Title: "🌱 greenie-auto"
- Nút: "Mở trang setup WiFi ESP32"
- Nút: "Vào Dashboard (Firebase)"
- Toggle: "Chế độ Mock"

**Màn hình 2 - Dashboard (khi bấm Vào Dashboard):**
- Dữ liệu cảm biến từ Firebase
- Thẻ nhiệt độ/độ ẩm không khí (nếu có DHT)
- Trung bình độ ẩm đất
- Nút bật/tắt bơm

---

## ❌ Troubleshooting build iOS

| Lỗi | Fix |
|---|---|
| **Build failed: "Cannot find module 'shared'"** | → Chạy `pod install` trong iosApp folder |
| **"CocoaPods not installed"** | → `sudo gem install cocoapods` |
| **"Kotlin compilation failed"** | → Check `shared/build.gradle.kts` có đúng `iosSimulatorArm64` không |
| **Simulator slow/hang** | → Restart Xcode + Simulator, hoặc test trên device thực |

---

## 🚀 Nếu chạy mock mode:

- Bấm checkbox "Chế độ Mock"
- Bấm "🧪 Test Mock"
- App sẽ hiện dữ liệu giả lập mà không cần Firebase
- Dùng để test UI/flow khi chưa có ESP32

---

**Status:** Xcode project ready to build! 🟢
