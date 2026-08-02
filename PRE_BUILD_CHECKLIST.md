## Pre-Build Checklist: Firebase + ESP32 ✅

### ✅ Code changes đã apply:

- [x] `pushToFirebase()` → dùng `PUT` thay `PATCH`
- [x] Thêm `http.setTimeout(HTTP_TIMEOUT_MS)` để tránh hang
- [x] `checkPumpCommand()` → parse JSON `{"state":"on/off"}` chuẩn
- [x] Thêm error logging chi tiết cho Firebase requests
- [x] Bổ sung check WiFi status rõ ràng

### ✅ Configuration:

- [x] `platformio.ini` có `FIREBASE_URL` và `FIREBASE_SECRET`
- [x] Libraries bắt buộc khai báo:
  - `tzapu/WiFiManager @ ^2.0.17`
  - `adafruit/DHT sensor library @ ^1.4.6`
  - `adafruit/Adafruit Unified Sensor @ ^1.1.14`
- [x] Serial speed: 115200 baud

### ✅ Firebase Credentials:

Trước khi upload, điền vào [platformio.ini](platformio.ini):
```ini
build_flags =
    -D FIREBASE_SECRET='"YOUR_SECRET_HERE"'
    -D FIREBASE_URL='"https://YOUR-PROJECT-rtdb.asia-southeast1.firebasedatabase.app"'
```

Lấy từ: [Firebase Console](https://console.firebase.google.com) → Project Settings → Database Secrets

### ✅ Firebase Rules (Setup 1 lần):

Vào Firebase Console → Realtime Database → **Rules** → dán:
```json
{
  "rules": {
    "sensor_data": {
      ".read": true,
      ".write": true
    },
    "pump_command": {
      ".read": true,
      ".write": true
    }
  }
}
```

Nhấn **Publish**

### ✅ Build command:

```bash
cd /Users/ly.t/Desktop/greenie-auto
pio run
pio run -t upload
pio device monitor -b 115200
```

### ✅ Expected Serial Output (sau khi upload):

```
[WiFi] Bắt đầu cấu hình WiFi.
[WiFi] Đã có SSID lưu, thử autoConnect trước.
[WiFi] Kết nối hoặc portal hoạt động. STA IP: http://192.168.1.xxx

[Firebase] ✅ Đẩy OK (HTTP 200)
[Firebase] 📱 Lệnh bơm từ app: BẬT
```

### ✅ Verify trong Firebase Console:

- Vào **Realtime Database**
- Thấy node `sensor_data` có dữ liệu cảm biến mới nhất
- Khi app gửi lệnh, thấy `pump_command` = `{"state":"on"}` hoặc `{"state":"off"}`

---

**Status:** 🟢 Sẵn sàng build!
