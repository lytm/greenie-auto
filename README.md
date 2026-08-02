# greenie-auto

Hệ thống tưới cây tự động gồm:

- Firmware ESP32 (PlatformIO + Arduino) trong thư mục [src](src)
- App Kotlin Multiplatform (Android/iOS) trong thư mục [app](app)

ESP32 đọc tối đa 6 cảm biến độ ẩm đất, tùy chọn 1 cảm biến DHT22, điều khiển relay van nước và cung cấp dữ liệu cho app qua API nội bộ + Firebase.

---

## 1) Phần cứng

| Phần cứng | Số lượng | Ghi chú |
|---|---:|---|
| ESP32 Dev Module | 1 | Board chính |
| Capacitive Soil Moisture Sensor v1.2 | 1..6 | Đọc ADC1 |
| DHT22 (AM2302) | 0..1 | Nhiệt độ + độ ẩm không khí |
| Relay 1 kênh | 1 | Điều khiển van |
| Van điện từ nước | 1 | Cần nguồn ngoài |
| Nguồn ngoài cho van | 1 | Không cấp trực tiếp từ ESP32 |

---

## 2) Sơ đồ chân mặc định (firmware)

| Thiết bị | GPIO |
|---|---|
| Relay IN | 26 |
| DHT22 DATA | 27 |
| BOOT reset WiFi | 0 |
| Cảm biến đất | 32, 33, 34, 35, 36, 39 |

Lưu ý: chỉ dùng ADC1 (32/33/34/35/36/39) khi chạy WiFi.

---

## 3) Cách hệ thống hoạt động

### Tự nhận biết cảm biến cắm/rút
- Cảm biến đất: nếu ADC thô `>= NO_SENSOR_THRESHOLD` thì coi như không cắm (`-1`)
- DHT22: nếu đọc lỗi (`NaN`) thì trả `air_temp = -1`, `air_humidity = -1`

### Điều khiển tưới
- Tính trung bình trên các cảm biến đất còn tín hiệu
- Bật relay khi `average < PUMP_ON_THRESHOLD`
- Tắt relay khi `average >= PUMP_OFF_THRESHOLD`

### Đồng bộ dữ liệu
- Local API: `/api/data`, `/api/pump`
- Firebase Realtime DB: ghi vào `/sensor_data`, đọc lệnh từ `/pump_command`

---

## 4) API từ ESP32

### GET `/api/data`
Ví dụ response:

```json
{
  "sensor_count": 6,
  "sensors": [45, 52, -1, -1, 60, 58],
  "average": 53,
  "air_temp": 29.6,
  "air_humidity": 72.4,
  "pump": false,
  "threshold_on": 30,
  "threshold_off": 70
}
```

### GET `/api/pump?state=on|off`
Trả về trạng thái relay hiện tại:

```json
{ "pump": true }
```

---

## 5) Cấu hình nhanh

Các hằng số quan trọng nằm trong [src/main.cpp](src/main.cpp):

- `SENSOR_COUNT`, `SOIL_PINS`
- `DRY_VALUE`, `WET_VALUE`
- `NO_SENSOR_THRESHOLD`
- `PUMP_ON_THRESHOLD`, `PUMP_OFF_THRESHOLD`
- `READ_INTERVAL_MS`

Credential Firebase truyền qua `build_flags` trong [platformio.ini](platformio.ini).

> Khuyến nghị: không commit secret thật lên git public.

---

## 6) Build / Upload firmware

```bash
pio run
pio run -t upload
pio device monitor -b 115200
```

Nếu dùng macOS/Linux, nhớ chỉnh `upload_port` trong [platformio.ini](platformio.ini) đúng cổng serial hiện tại (ví dụ `/dev/cu.usbserial-*` hoặc `/dev/cu.SLAB_USBtoUART`).

---

## 7) App Android/iOS

Xem hướng dẫn chi tiết tại [app/README.md](app/README.md).

App hỗ trợ:
- Đọc dữ liệu trực tiếp từ Firebase
- Bật/tắt tưới từ điện thoại qua Firebase
- Chế độ Mock để test UI khi chưa có phần cứng
- Nút mở nhanh trang setup WiFi ESP32 (`http://192.168.4.1`)

---

## 8) Troubleshooting

### 8.1 Upload lỗi / không thấy cổng serial
- Triệu chứng: `A fatal error occurred: Failed to connect to ESP32` hoặc không có cổng upload.
- Cách xử lý:
  - Đổi `upload_port` trong [platformio.ini](platformio.ini) theo máy đang dùng.
  - Trên macOS thường là `/dev/cu.usbserial-*` hoặc `/dev/cu.SLAB_USBtoUART`.
  - Nhấn giữ `BOOT` khi bắt đầu upload nếu board khó vào chế độ nạp.

### 8.2 Không vào được WiFi config portal
- Triệu chứng: không thấy AP `greenie-auto-setup`.
- Cách xử lý:
  - Giữ nút `BOOT` (GPIO0) hơn 3 giây sau khi board chạy để reset WiFi đã lưu.
  - Khởi động lại nguồn ESP32.
  - Kiểm tra nguồn cấp đủ ổn định (đặc biệt khi relay/van cùng hoạt động).

### 8.3 App không đọc được dữ liệu Firebase
- Triệu chứng: app báo lỗi kết nối hoặc không có dữ liệu mới.
- Cách xử lý:
  - Nếu app và ESP32 cùng WiFi, kiểm tra mDNS `greenie-auto.local` hoặc thử mở `http://192.168.4.1/api/data`.
  - Nếu app không cùng WiFi với ESP32, app sẽ fallback sang Firebase; kiểm tra ESP32 có Internet để đẩy lên `/sensor_data`.
  - Kiểm tra `FIREBASE_URL` / `FIREBASE_SECRET` trong [platformio.ini](platformio.ini).
  - Kiểm tra rules Firebase cho phép app đọc node `/sensor_data` và ghi `/pump_command`.

### 8.4 DHT22 luôn trả `-1`
- Triệu chứng: `air_temp = -1`, `air_humidity = -1` liên tục.
- Cách xử lý:
  - Kiểm tra dây `DATA` đúng GPIO27, `VCC` 3.3V, `GND` chung mass.
  - Chờ 1-2 chu kỳ đọc sau khi cắm lại cảm biến.
  - Nếu vẫn lỗi, thử đổi cảm biến DHT22 khác.

### 8.5 Cảm biến đất hiển thị `-1`
- Triệu chứng: sensor bị ẩn trên app hoặc luôn `-1`.
- Cách xử lý:
  - Chỉ cắm vào chân ADC1: 32/33/34/35/36/39.
  - Kiểm tra dây `AOUT` không lỏng.
  - Điều chỉnh `NO_SENSOR_THRESHOLD` trong [src/main.cpp](src/main.cpp) theo môi trường nhiễu thực tế.

### 8.6 Relay bật/tắt ngược kỳ vọng
- Triệu chứng: gửi lệnh `on` nhưng relay lại tắt (hoặc ngược lại).
- Nguyên nhân: nhiều relay dùng logic active-LOW.
- Cách xử lý: kiểm tra lại `PUMP_ON` / `PUMP_OFF` trong [src/main.cpp](src/main.cpp).

### 8.7 Firebase đẩy lỗi 401/403
- Triệu chứng: Serial log báo lỗi HTTP auth khi ghi `/sensor_data`.
- Cách xử lý:
  - Kiểm tra `FIREBASE_URL` và `FIREBASE_SECRET` trong [platformio.ini](platformio.ini).
  - Kiểm tra rules của Realtime Database cho đúng môi trường test.
  - Tránh để secret thật trong repository public.
