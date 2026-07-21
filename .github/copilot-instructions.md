<!-- Copilot workspace instructions for greenie-auto -->
# greenie-auto

- **Platform:** ESP32 Dev Module (`esp32dev`)
- **Framework:** Arduino (PlatformIO)
- **Mục tiêu:** Đo độ ẩm đất qua cảm biến Capacitive Soil Moisture v1.2
- **Ngôn ngữ:** C++ (Arduino)
- **Thư viện:** Không cần thư viện ngoài (dùng analogRead() ESP32)

## Quy tắc phát triển
- Mọi code firmware đặt trong `src/`
- Cấu hình board/thư viện trong `platformio.ini`
- Giao tiếp Serial ở 115200 baud
- Sử dụng `#define` để cấu hình pin và loại cảm biến
