## App Sync Status ✅ (Android + iOS)

### ✅ Cấu trúc Repository:

```
SoilRepository (abstract)
├── FirebaseSoilRepository ← Dùng cho cả Android & iOS (chính)
├── LocalSoilRepository ← Deprecated (dùng local API HTTP)
└── MockSoilRepository ← Test mode
```

### ✅ Flow lấy dữ liệu từ Firebase:

1. App khởi động → hiển thị `IpInputScreen`
2. Chọn **Vào Dashboard (Firebase)** → `DashboardScreen`
3. Tạo `FirebaseSoilRepository()` (default Firebase URL)
4. Mỗi 2 giây:
   - Gọi `repository.fetchData()` → GET `/sensor_data.json`
   - Parse `SoilData` từ response
   - Hiển thị card cảm biến
5. Khi bấm nút bơm:
   - Gọi `repository.setPump(true/false)`
   - PUT `{"state":"on"/"off"}` vào `/pump_command.json`

### ✅ Đồng bộ giữa Android & iOS:

| Thành phần | Android | iOS | Chung |
|---|---|---|---|
| HTTP Client | ktor-client-okhttp | ktor-client-darwin | ✅ |
| UI Framework | Compose | SwiftUI + Compose | ✅ Compose |
| JSON Serialization | Ktor content negotiation | Ktor content negotiation | ✅ |
| Logger | Android Log.e/i | println | ✅ `expect/actual` |
| Firebase Repository | FirebaseSoilRepository | FirebaseSoilRepository | ✅ chung |

### ✅ Gradle Dependencies:

```toml
[versions]
ktor = "3.0.3"

[libraries]
ktor-client-okhttp = "3.0.3"      ← Android HTTP backend
ktor-client-darwin = "3.0.3"      ← iOS HTTP backend
ktor-serialization-json = "3.0.3" ← JSON decode/encode chung
```

### ✅ Quy trình test:

1. **Build Android:**
   ```bash
   cd app
   ./gradlew :androidApp:build
   ```

2. **Build iOS:**
   ```bash
   cd app
   ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
   ```
   Rồi mở Xcode project, build từ đó.

3. **Expected output:**
   - Màn hình setup WiFi ESP32 (`http://192.168.4.1`)
   - Nút vào Dashboard (Firebase)
   - Dữ liệu cảm biến từ Firebase
   - Nút bật/tắt bơm → ghi `/pump_command.json`

### ✅ Troubleshooting:

| Lỗi | Nguyên nhân | Fix |
|---|---|---|
| `FirebaseSoilRepository` không tìm được class | Chưa sync gradle | `./gradlew --refresh-dependencies` |
| Android không kết nối Firebase | WiFi/Internet mất | Kiểm tra WiFi, chạy app trên device |
| iOS kết nối từ từ | iPhone simulator có latency mạng | Test trên device thực |
| Parse SoilData lỗi | JSON từ Firebase không match | Kiểm tra `/sensor_data.json` structure |

---

**Status: 🟢 Sẵn sàng build & test cả 2 platform!**
