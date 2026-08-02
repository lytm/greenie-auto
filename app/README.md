# greenie-auto mobile app (KMP)

Ứng dụng Kotlin Multiplatform cho Android + iOS.

## Module chính

- [androidApp](androidApp): entry Android
- [iosApp](iosApp): entry iOS (SwiftUI)
- [shared](shared): UI + logic dùng chung

Màn hình chính trong [shared/src/commonMain/kotlin/com/greenie/auto/shared/App.kt](shared/src/commonMain/kotlin/com/greenie/auto/shared/App.kt).

---

## Chức năng hiện tại

- Ưu tiên đọc trực tiếp từ ESP32 trong cùng WiFi qua `greenie-auto.local` / `192.168.4.1`
- Tự động fallback sang Firebase (`/sensor_data`) nếu không vào được ESP32 local
- Gửi lệnh bật/tắt relay trực tiếp qua ESP32 khi cùng mạng, hoặc qua Firebase khi phải fallback
- Auto refresh mỗi 2 giây
- Ẩn cảm biến không cắm (`-1`)
- Có nút mở trang setup portal của ESP32 (`http://192.168.4.1`)

---

## Chạy Android

1. Mở thư mục [app](.) bằng Android Studio
2. Sync Gradle
3. Chạy module `androidApp`

---

## Chạy iOS

1. Build shared framework:
   - `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`
2. Mở project trong [iosApp/iosApp.xcodeproj](iosApp/iosApp.xcodeproj)
3. Run bằng iOS Simulator

---

## Lưu ý khi test với ESP32

- Cùng WiFi: app sẽ thử `greenie-auto.local` trước, sau đó mới dùng Firebase
- Khác WiFi hoặc ESP32 không phản hồi: app tự chuyển sang Firebase
- Không có mạng cả hai phía: app sẽ hiện lỗi hướng dẫn xử lý trên màn hình
- Có thể mở `http://192.168.4.1` khi ESP32 ở chế độ setup portal

---

## Troubleshooting

### App báo lỗi kết nối ESP32
- Kiểm tra điện thoại và ESP32 có ở cùng WiFi không.
- Mở thử `http://greenie-auto.local/api/data` hoặc `http://192.168.4.1/api/data` trên trình duyệt điện thoại.
- Nếu cùng WiFi mà vẫn lỗi, kiểm tra lại router/DNS mDNS hoặc thử bấm "Thử lại" trên app.

### Android build lỗi Gradle
- Sync Gradle lại trong Android Studio.
- Đảm bảo mở đúng workspace [app](.) thay vì mở root firmware.

### iOS không thấy dữ liệu shared
- Build lại framework bằng `:shared:linkDebugFrameworkIosSimulatorArm64`.
- Clean build folder trong Xcode rồi chạy lại simulator.

### Test nhanh khi chưa có ESP32
- Dùng Firebase nếu ESP32 đang offline.
- Nếu muốn test UI hoàn toàn offline, giữ lại mock repository trong code và đổi sang mock khi cần.
