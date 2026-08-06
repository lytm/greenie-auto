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
- Cảm biến đất: coi như không cắm (`-1`) nếu:
  - ADC thô `>= NO_SENSOR_HIGH_THRESHOLD`
  - hoặc ADC thô `<= NO_SENSOR_LOW_THRESHOLD`
  - hoặc độ dao động nhiều mẫu `>= NO_SENSOR_NOISE_SPAN` (chân thả nổi / nhiễu)
- DHT22: nếu đọc lỗi (`NaN`) thì trả `air_temp = -1`, `air_humidity = -1`

### Điều khiển tưới
- Tính trung bình trên các cảm biến đất còn tín hiệu
- Bật relay khi `average < PUMP_ON_THRESHOLD`
- Tắt relay khi `average >= PUMP_OFF_THRESHOLD`
- Hỗ trợ lịch tưới tự động theo giờ:
  - Cấu hình tại app: bật/tắt, nhiều mốc giờ trong ngày, chọn thứ T2..CN, thời lượng tưới (phút)
  - Firmware tự chạy theo từng mốc giờ hợp lệ trong các ngày đã chọn

### Đồng bộ dữ liệu
- Local API: `/api/data`, `/api/pump`
- Firebase Realtime DB: ghi vào `/sensor_data`, đọc lệnh từ `/pump_command`
- Lịch sử tưới: firmware ghi vào `/pump_history/{YYYY-MM-DD}/{HHMMSS}` khi bơm tắt
- Lịch tưới tự động: đọc/ghi ở `/watering_schedule`
- Bản tối ưu pin hiện tại:
  - Đọc cảm biến mỗi khoảng 10 giây
  - Kiểm tra lệnh bơm mỗi khoảng 10 giây
  - Đẩy Firebase mỗi khoảng 30 giây
  - `monthly_stats` chỉ đẩy khi tới chu kỳ 5 phút, khi nhiệt độ/pump count đổi đáng kể, hoặc lúc sang ngày mới
  - Tắt AP sau khi đã vào WiFi và bật WiFi sleep

### Flow WiFi sau khi mất nguồn / thay pin
- ESP32 luôn thử `autoConnect("greenie-auto-setup")` khi boot:
  - Nếu có credentials hợp lệ → tự nối lại WiFi cũ
  - Nếu chưa có / sai mật khẩu → tự mở portal setup
- Giữ nút `BOOT` (GPIO0) > 3 giây khi board **đang chạy** để xóa WiFi đã lưu và quay lại flow setup.
- Credentials được lưu vào NVS (`WiFi.persistent(true)`), nên tắt/mở nguồn vẫn giữ lại.

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

### GET `/api/schedule`
- Đọc lịch hiện tại hoặc cập nhật lịch qua query params.
- Ví dụ cập nhật:

`/api/schedule?enabled=1&times_csv=06:30,12:00,18:15&weekdays_csv=1,2,3,4,5,6,7&duration_min=3`

Response mẫu:

```json
{
  "enabled": true,
  "hour": 6,
  "minute": 30,
  "duration_min": 3,
  "times_csv": "06:30,12:00,18:15",
  "weekdays_csv": "1,2,3,4,5,6,7",
  "running": false
}
```

---

## 5) Cấu hình nhanh

Các hằng số quan trọng nằm trong [src/main.cpp](src/main.cpp):

- `SENSOR_COUNT`, `SOIL_PINS`
- `DRY_VALUE`, `WET_VALUE`
- `NO_SENSOR_HIGH_THRESHOLD`, `NO_SENSOR_LOW_THRESHOLD`, `NO_SENSOR_NOISE_SPAN`
- `PUMP_ON_THRESHOLD`, `PUMP_OFF_THRESHOLD`
- `READ_INTERVAL_MS`, `FIREBASE_PUSH_INTERVAL_MS`, `PUMP_COMMAND_POLL_INTERVAL_MS`

Credential Firebase truyền qua `build_flags` trong [platformio.ini](platformio.ini).

> Khuyến nghị: không commit secret thật lên git public.

### Firebase setup nhanh

Điền vào [platformio.ini](platformio.ini):

```ini
build_flags =
    -D FIREBASE_SECRET='"YOUR_SECRET_HERE"'
    -D FIREBASE_URL='"https://YOUR-PROJECT-rtdb.asia-southeast1.firebasedatabase.app"'
```

Rules test nhanh cho Realtime Database:

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
    },
    "monthly_stats": {
      ".read": true,
      ".write": true
    },
    "pump_history": {
      ".read": true,
      ".write": true
    },
    "watering_schedule": {
      ".read": true,
      ".write": true
    }
  }
}
```

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

App hỗ trợ:
- Cùng WiFi: ưu tiên đọc trực tiếp từ ESP32; khác WiFi: fallback Firebase
- Bật/tắt tưới từ điện thoại (local hoặc Firebase fallback)
- Chế độ Mock để test UI khi chưa có phần cứng
- Nút mở nhanh trang setup WiFi ESP32 (`http://192.168.4.1`)
- Cảnh báo ngưỡng (đất khô / nhiệt độ cao) ngay trên dashboard
- Màn hình lịch sử tưới đọc từ node `/pump_history`
- Cấu hình lịch tưới tự động theo giờ + thời lượng
- Biểu đồ tháng (line + grid) đọc từ node `/monthly_stats`

### Android
- Mở thư mục [app](app)
- Sync Gradle
- Chạy module `androidApp`

### iOS
- Build framework dùng chung:
  - `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`
  - Device thật (arm64): `./gradlew :shared:linkDebugFrameworkIosArm64`
- Mở [app/iosApp/iosApp.xcodeproj](app/iosApp/iosApp.xcodeproj)
- Chạy trên Simulator hoặc device

### Cách app sync dữ liệu
- App refresh UI mỗi 2 giây
- Cùng WiFi: ưu tiên đọc local từ ESP32
- Khác WiFi hoặc local lỗi: fallback sang Firebase (giảm còn 10 giây/lần để tiết kiệm quota)
- Do firmware đang tiết kiệm pin, nếu đọc qua Firebase thì dữ liệu có thể trễ hơn local
- Biểu đồ tháng đọc từ Firebase node `/monthly_stats/{YYYY-MM}/{DD}`:
  - `avg_temp`: nhiệt độ trung bình ngày
  - `avg_soil`: độ ẩm đất trung bình ngày
  - `pump_on_count`: số lần bơm bật trong ngày
- Lịch sử tưới đọc từ Firebase node `/pump_history/{YYYY-MM-DD}/{HHMMSS}`:
  - `start`: giờ bắt đầu tưới
  - `end`: giờ kết thúc tưới
  - `duration_s`: thời lượng (giây)
- Lịch tưới tự động đọc/ghi tại `/watering_schedule`:
  - `enabled`: bật/tắt lịch
  - `times_csv`: nhiều mốc giờ trong ngày, ví dụ `06:30,12:00,18:15`
  - `weekdays_csv`: thứ chạy dạng `1..7` (T2..CN), ví dụ `1,2,3,4,5`
  - `hour`, `minute`: giữ để tương thích ngược (mốc đầu tiên)
  - `duration_min`: thời lượng tưới (1..60 phút)

### Quy ước thêm tính năng app
- Mọi tính năng mới của app phải chạy được trên **cả Android và iOS**.
- Nếu sửa code chung trong `shared`, bắt buộc test lại 2 nền tảng trước khi merge.
- Nếu tính năng có phần native (permission, networking, lifecycle, background), phải cập nhật cả `androidApp` và `iosApp` tương ứng.
- PR/commit cần ghi rõ trạng thái test: Android ✅ / iOS ✅.

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
  - Chờ 5-10 giây lúc boot: board sẽ thử auto reconnect trước, chỉ mở portal khi không vào được WiFi đã lưu.
  - Kiểm tra nguồn cấp đủ ổn định (đặc biệt khi relay/van cùng hoạt động).

### 8.3 App không đọc được dữ liệu Firebase
- Triệu chứng: app báo lỗi kết nối hoặc không có dữ liệu mới.
- Cách xử lý:
  - Nếu app và ESP32 cùng WiFi, kiểm tra mDNS `greenie-auto.local` hoặc thử mở `http://192.168.4.1/api/data`.
  - Nếu app không cùng WiFi với ESP32, app sẽ fallback sang Firebase; kiểm tra ESP32 có Internet để đẩy lên `/sensor_data`.
  - Kiểm tra `FIREBASE_URL` / `FIREBASE_SECRET` trong [platformio.ini](platformio.ini).
  - Kiểm tra rules Firebase cho phép app đọc node `/sensor_data` và ghi `/pump_command`.

### 8.3.1 Pre-build checklist nhanh
- `platformio.ini` đã có `FIREBASE_URL` và `FIREBASE_SECRET`
- Serial baud là `115200`
- Thư viện cần có:
  - `tzapu/WiFiManager`
  - `adafruit/DHT sensor library`
  - `adafruit/Adafruit Unified Sensor`
- Sau khi upload, kiểm tra Serial có log dạng:
  - `[WiFi] Thử autoConnect trước (tự fallback portal nếu cần)...`
  - `[Firebase] ✅ Đẩy OK (HTTP 200)`

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
  - Điều chỉnh `NO_SENSOR_HIGH_THRESHOLD`, `NO_SENSOR_LOW_THRESHOLD` hoặc `NO_SENSOR_NOISE_SPAN` trong [src/main.cpp](src/main.cpp) theo môi trường nhiễu thực tế.

### 8.6 Dữ liệu Firebase cập nhật chậm hơn local
- Triệu chứng: app đang fallback Firebase nhưng số liệu lên chậm hơn WiFi local.
- Nguyên nhân: firmware đang ưu tiên tiết kiệm pin nên chỉ đẩy Firebase mỗi khoảng 30 giây; app fallback Firebase cũng chỉ refresh khoảng 10 giây/lần.
- Cách xử lý:
  - Nếu cần gần real-time hơn, giảm `FIREBASE_PUSH_INTERVAL_MS` trong [src/main.cpp](src/main.cpp).
  - Nếu điện thoại cùng WiFi với ESP32, app sẽ ưu tiên đọc local nhanh hơn.

### 8.7 Relay bật/tắt ngược kỳ vọng
- Triệu chứng: gửi lệnh `on` nhưng relay lại tắt (hoặc ngược lại).
- Nguyên nhân: nhiều relay dùng logic active-LOW.
- Cách xử lý: kiểm tra lại `PUMP_ON` / `PUMP_OFF` trong [src/main.cpp](src/main.cpp).

### 8.8 Firebase đẩy lỗi 401/403
- Triệu chứng: Serial log báo lỗi HTTP auth khi ghi `/sensor_data`.
- Cách xử lý:
  - Kiểm tra `FIREBASE_URL` và `FIREBASE_SECRET` trong [platformio.ini](platformio.ini).
  - Kiểm tra rules của Realtime Database cho đúng môi trường test.
  - Tránh để secret thật trong repository public.

### 8.9 iOS build lỗi
- Triệu chứng: Xcode không thấy module `shared` hoặc app iOS không build.
- Cách xử lý:
  - Chạy lại `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`
  - Mở lại [app/iosApp/iosApp.xcodeproj](app/iosApp/iosApp.xcodeproj)
  - Nếu simulator chậm, test trên device thật
