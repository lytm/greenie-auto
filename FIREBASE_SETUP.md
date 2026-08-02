# Hướng dẫn cấu hình Firebase cho greenie-auto

## 1) Tạo Firebase Project

1. Vào [Firebase Console](https://console.firebase.google.com)
2. Tạo project mới (hoặc dùng project cũ)
3. Chọn **Realtime Database** → tạo database mới
4. Chọn region: **asia-southeast1** (Singapore)
5. Chọn chế độ: **Start in test mode** (tạm thời)

## 2) Lấy Firebase Credentials

Sau khi tạo database, lấy URL:
- URL có dạng: `https://YOUR-PROJECT-rtdb.asia-southeast1.firebasedatabase.app`

Lấy Secret:
- Vào **Project Settings** → **Service Accounts** → **Database Secrets**
- Copy secret (nếu không có, tạo mới)

## 3) Cập nhật platformio.ini

Mở [platformio.ini](platformio.ini) và cập nhật:

```ini
build_flags =
    -D FIREBASE_SECRET='"YOUR_SECRET_HERE"'
    -D FIREBASE_URL='"https://YOUR-PROJECT-rtdb.asia-southeast1.firebasedatabase.app"'
```

**Lưu ý:** Giữ dấu ngoặc kép (có 2 lớp dấu).

## 4) Cấu hình Firebase Rules (Bảo mật)

Vào **Realtime Database** → **Rules** → dán:

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

> ⚠️ Chế độ **test mode** này chỉ dùng tạm, cho phép ai cũng đọc/ghi. 
> Để dùng production, hãy thêm authentication (Firebase Auth hoặc API key).

## 5) Kiểm tra kết nối

1. Upload firmware ESP32
2. Mở Serial Monitor (115200 baud)
3. Tìm log `[Firebase] ✅ Đẩy OK`
4. Vào Firebase Console, tab **Realtime Database**, phải thấy:

```json
{
  "sensor_data": {
    "sensor_count": 6,
    "sensors": [45, 52, -1, -1, 60, 58],
    "average": 52,
    "air_temp": 29.5,
    "air_humidity": 72.0,
    "pump": false,
    "threshold_on": 30,
    "threshold_off": 70
  }
}
```

## 6) Test lệnh bơm từ app

1. Mở app Android/iOS
2. Bấm nút "Bật bơm" / "Tắt bơm"
3. Vào Firebase Console, thấy `/pump_command` thay đổi thành `{"state":"on"}` hoặc `{"state":"off"}`
4. Kiểm tra Serial Monitor xem ESP32 nhận lệnh: `[Firebase] 📱 Lệnh bơm từ app: BẬT`

## 7) Troubleshooting

| Triệu chứng | Nguyên nhân | Fix |
|---|---|---|
| `[Firebase] ❌ Lỗi: -1` | Timeout hoặc WiFi chưa connected | Chờ WiFi ổn định, kiểm tra `[WiFi]` log |
| `[Firebase] ❌ HTTP 401/403` | Firebase Secret sai hoặc hết hạn | Kiểm tra lại build_flags, tạo secret mới |
| Database trống | Chưa kết nối hoặc WiFi mất | Kiểm tra `[WiFi]` log, restart ESP32 |
| App không thấy dữ liệu | Firebase URL sai hoặc không kết nối | Kiểm tra `FIREBASE_URL` trong platformio.ini |
