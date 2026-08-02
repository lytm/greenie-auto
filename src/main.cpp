#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include <HTTPClient.h>
#include <WiFiManager.h>
#include <ESPmDNS.h>
#include <DHT.h>              // Cảm biến không khí DHT22

// ─── FIREBASE REALTIME DATABASE ─────────────────────────────────
// ↳ FIREBASE_URL và FIREBASE_SECRET được truyền từ platformio.ini (build_flags)
//   ⇒ Sửa credentials trong platformio.ini, không để lộ trong code

// ─── NÚT RESET WIFI ─────────────────────────────────────────────
// Giữ nút BOOT (GPIO 0) > 3 giây sau khi ESP32 đã chạy để xóa WiFi đã lưu.
#define RESET_PIN       0       // GPIO 0 = nút BOOT có sẵn trên ESP32 Dev Module
#define RESET_HOLD_MS   3000    // Giữ 3 giây để reset

// ─── CẢM BIẾN ĐỘ ẨM (ADC1 — an toàn khi dùng WiFi/Bluetooth) ────
// ⚠️  ADC2 bị vô hiệu hoá khi dùng WiFi/Bluetooth.
//     Chỉ dùng ADC1: GPIO 32, 33, 34, 35, 36, 39.
//
// Để tối đa 6 chân — cắm cảm biến vào chân nào thì tự hiện, rút ra tự ẩn.
// Không cần upload lại khi thêm/bớt cảm biến.
#define SENSOR_COUNT    6       // Quét toàn bộ 6 chân ADC1
#define MAX_SENSORS     6
const uint8_t SOIL_PINS[SENSOR_COUNT] = {32, 33, 34, 35, 36, 39};

// ─── RELAY ĐIỀU KHIỂN MÁY BƠM ────────────────────────────────────
#define PUMP_PIN        26      // Relay IN   → GPIO 26
#define PUMP_ON         LOW     // Relay module kích mức LOW
#define PUMP_OFF        HIGH
// ─── CẢM BIẾN KHÔNG KHÍ (DHT22) ──────────────────────────────
// Cắm: VCC → 3.3V | GND → GND | DATA → GPIO 27
// Không cắm → DHT đọc NaN → gửi -1 → app tự ẩn card
#define DHT_PIN         27
#define DHT_TYPE        DHT22
DHT dht(DHT_PIN, DHT_TYPE);
// ─── KHOẢNG THỜI GIAN ĐỌC (ms) ───────────────────────────────────
#define READ_INTERVAL_MS  2000

// ─── HIỆU CHỈNH (CALIBRATION) ────────────────────────────────────
// 1. Để cảm biến trong KHÔNG KHÍ → đọc ADC → gán vào DRY_VALUE
// 2. Nhúng cảm biến vào NƯỚC     → đọc ADC → gán vào WET_VALUE
#define DRY_VALUE   2800
#define WET_VALUE    800

// ─── NGƯỠNG ĐIỀU KHIỂN MÁY BƠM ──────────────────────────────────
#define PUMP_ON_THRESHOLD   30  // % — độ ẩm TB < 30% → BẬT bơm
#define PUMP_OFF_THRESHOLD  70  // % — độ ẩm TB ≥ 70% → TẮT bơm
// Nếu ADC thô ≥ ngưỡng này → chân thả nổi → không có cảm biến
// Khi không cắm cảm biến, ESP32 ADC thường đọc ≈ 4000-4095
#define NO_SENSOR_THRESHOLD 3800

// ─── FIREBASE CONNECTION TIMEOUT ────────────────────────────────
#define HTTP_TIMEOUT_MS  8000  // 8 giây timeout cho Firebase request
// ─── HÀM CHUYỂN ĐỔI ADC → % ĐỘ ẨM ──────────────────────────────
int toPercent(int raw) {
    return constrain(map(raw, DRY_VALUE, WET_VALUE, 0, 100), 0, 100);
}

// ─── HÀM ĐỌC TRUNG BÌNH 10 LẦN (giảm nhiễu) ─────────────────────
int readSensorRaw(int pin) {
    long sum = 0;
    for (int i = 0; i < 10; i++) { sum += analogRead(pin); delay(10); }
    return sum / 10;
}

int readSensors(int values[]) {
    int active = 0;
    for (int i = 0; i < SENSOR_COUNT; i++) {
        int raw = readSensorRaw(SOIL_PINS[i]);
        if (raw >= NO_SENSOR_THRESHOLD) {
            values[i] = -1;   // không có cảm biến
        } else {
            values[i] = toPercent(raw);
            active++;
        }
    }
    return active;   // trả số cảm biến có tín hiệu thực sự
}

const char* sensorLabel(int p) {
    if (p < 20) return "KHÔ  — Cần tưới!";
    if (p < 60) return "VỪA  — Độ ẩm tốt";
    return       "ƯỚT  — Đủ nước";
}

// ─── BIẾN TRẠNG THÁI ─────────────────────────────────────────────
bool pumpRunning = false;
int  lastSensorValues[MAX_SENSORS] = {0};
int  lastSensorCount = SENSOR_COUNT;
int  lastAvg = 0;float lastAirTemp = -1.0f;      // -1 = không cắm DHT
float lastAirHumidity = -1.0f;bool resetRequested = false;
unsigned long resetStartMs = 0;

// ─── WEB SERVER ──────────────────────────────────────────────────
WebServer server(80);
String lastIpLog = "";
// Trang HTML trả về cho trình duyệt
void handleRoot() {
    String pumpStatus = pumpRunning ? "🟢 ĐANG CHẠY" : "🔴 ĐỨNG";
    String pumpBtnLabel = pumpRunning ? "Tắt bơm" : "Bật bơm";
    String pumpBtnColor = pumpRunning ? "#e74c3c" : "#27ae60";

    String html = R"(
<!DOCTYPE html><html lang="vi">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>greenie-auto</title>
  <style>
    body{font-family:sans-serif;background:#1a1a2e;color:#eee;text-align:center;padding:20px}
    h1{color:#00d4aa}  
    .card{background:#16213e;border-radius:16px;padding:20px;margin:12px auto;max-width:360px}
    .pct{font-size:3em;font-weight:bold}
    .btn{padding:14px 36px;border:none;border-radius:10px;font-size:1.1em;cursor:pointer;margin-top:10px}
    .refresh{color:#aaa;font-size:.85em;margin-top:20px}
  </style>
  <meta http-equiv="refresh" content="5">
</head>
<body>
  <h1>🌱 greenie-auto</h1>
)";

    for (int i = 0; i < lastSensorCount; i++) {
        int pct = lastSensorValues[i];
        String color = pct < 30 ? "#e74c3c" : (pct < 60 ? "#f39c12" : "#27ae60");
        html += String("<div class='card'><b>Cảm biến ") + String(i + 1) +
                "</b><div class='pct' style='color:" + color + "'>" + String(pct) + "%</div>" +
                "<div style='margin-top:8px;color:#ccc;font-size:13px'>" + String(sensorLabel(pct)) + "</div></div>";
    }

    html += String("<div class='card'><b>Trung bình</b><div class='pct'>") + String(lastAvg) + "%</div></div>";
    html += String("<div class='card'><b>Máy bơm:</b> ") + pumpStatus + "<br>";
    html += String("<form action='/pump' method='POST'><button class='btn' style='background:") + pumpBtnColor + ";color:#fff'>" + pumpBtnLabel + "</button></form></div>";
    html += "<p class='refresh'>Tự động làm mới sau 5 giây</p></body></html>";

    server.send(200, "text/html; charset=utf-8", html);
}

// Endpoint JSON cho app mobile đọc real-time
void handleApiData() {
    String json = "{";
    json += String("\"sensor_count\":") + String(lastSensorCount) + ",";
    json += "\"sensors\":[";
    for (int i = 0; i < lastSensorCount; i++) {
        if (i > 0) json += ",";
        json += String(lastSensorValues[i]);
    }
    json += "],";
    json += String("\"average\":") + String(lastAvg) + ",";
    json += String("\"air_temp\":") + String(lastAirTemp, 1) + ",";
    json += String("\"air_humidity\":") + String(lastAirHumidity, 1) + ",";
    json += String("\"pump\":") + String(pumpRunning ? "true" : "false") + ",";
    json += String("\"threshold_on\":") + String(PUMP_ON_THRESHOLD) + ",";
    json += String("\"threshold_off\":") + String(PUMP_OFF_THRESHOLD);
    json += "}";
    server.sendHeader("Access-Control-Allow-Origin", "*");
    server.send(200, "application/json", json);
}

// Endpoint bật/tắt bơm từ app (GET /api/pump?state=on|off)
void handleApiPump() {
    if (server.hasArg("state")) {
        String state = server.arg("state");
        pumpRunning = (state == "on");
        digitalWrite(PUMP_PIN, pumpRunning ? PUMP_ON : PUMP_OFF);
    }
    server.sendHeader("Access-Control-Allow-Origin", "*");
    server.send(200, "application/json", String("{\"pump\":") + String(pumpRunning ? "true" : "false") + "}");
}

// Endpoint điều khiển bơm thủ công từ điện thoại
void handlePump() {
    pumpRunning = !pumpRunning;
    digitalWrite(PUMP_PIN, pumpRunning ? PUMP_ON : PUMP_OFF);
    Serial.printf("[WEB] Bơm được %s thủ công\n", pumpRunning ? "BẬT" : "TẮT");
    server.sendHeader("Location", "/");
    server.send(303);
}
// ─── FIREBASE: ĐẨY DỮ LIỆU CẢM BIẾN LÊN CLOUD ─────────────────────
// Cấu trúc tự tạo trong Firebase:
// {
//   "sensor_data": { "sensor_count": 2, "sensors": [45, 60], "average": 52, "pump": false,
//                    "threshold_on": 30, "threshold_off": 70 },
//   "pump_command": null
// }
void pushToFirebase(int avg, bool pump) {
    if (WiFi.status() != WL_CONNECTED) {
        Serial.println("[Firebase] ⚠️  WiFi chưa kết nối, bỏ qua đẩy dữ liệu");
        return;
    }
    
    HTTPClient http;
    http.setTimeout(HTTP_TIMEOUT_MS);  // Set timeout để tránh hang
    String url = String(FIREBASE_URL) + "/sensor_data.json?auth=" + FIREBASE_SECRET;
    http.begin(url);
    http.addHeader("Content-Type", "application/json");
    String body = "{";
    body += "\"sensor_count\":" + String(lastSensorCount) + ",";
    body += "\"sensors\":[";
    for (int i = 0; i < lastSensorCount; i++) {
        if (i > 0) body += ",";
        body += String(lastSensorValues[i]);
    }
    body += "],";
    body += "\"average\":" + String(avg) + ",";
    body += "\"air_temp\":" + String(lastAirTemp, 1) + ",";
    body += "\"air_humidity\":" + String(lastAirHumidity, 1) + ",";
    body += "\"pump\":" + String(pump ? "true" : "false") + ",";
    body += "\"threshold_on\":" + String(PUMP_ON_THRESHOLD) + ",";
    body += "\"threshold_off\":" + String(PUMP_OFF_THRESHOLD);
    body += "}";
    int code = http.PUT(body);
    if (code > 0) {
        Serial.printf("[Firebase] ✅ Đẩy OK (HTTP %d)\n", code);
    } else {
        Serial.printf("[Firebase] ❌ Lỗi: %s\n", http.errorToString(code).c_str());
    }
    http.end();
}

// ─── FIREBASE: NHẬN LỆNH BƠM TỪ APP ───────────────────────────
void checkPumpCommand() {
    if (WiFi.status() != WL_CONNECTED) {
        return;
    }
    
    HTTPClient http;
    http.setTimeout(HTTP_TIMEOUT_MS);  // Set timeout để tránh hang
    http.begin(String(FIREBASE_URL) + "/pump_command.json?auth=" + FIREBASE_SECRET);
    http.addHeader("Content-Type", "application/json");
    int code = http.GET();
    if (code == 200) {
        String payload = http.getString();
        // Payload = {"state":"on"} hoặc {"state":"off"} hoặc null
        if (payload != "null" && payload.indexOf("state") >= 0) {
            bool newState = (payload.indexOf("\"on\"") >= 0);
            pumpRunning = newState;
            digitalWrite(PUMP_PIN, pumpRunning ? PUMP_ON : PUMP_OFF);
            Serial.printf("[Firebase] 📱 Lệnh bơm từ app: %s (payload=%s)\n", pumpRunning ? "BẬT" : "TẮT", payload.c_str());
            // Xóa lệnh sau khi thực hiện
            http.end();
            http.begin(String(FIREBASE_URL) + "/pump_command.json?auth=" + FIREBASE_SECRET);
            http.addHeader("Content-Type", "application/json");
            http.setTimeout(HTTP_TIMEOUT_MS);
            http.PUT("null");
            return;
        }
    } else if (code > 0) {
        Serial.printf("[Firebase] ❌ GET /pump_command lỗi HTTP %d\n", code);
    } else {
        Serial.printf("[Firebase] ❌ GET /pump_command timeout/lỗi kết nối: %s\n", http.errorToString(code).c_str());
    }
    http.end();
}
// ─── SETUP ───────────────────────────────────────────────────────
void setup() {
    Serial.begin(115200);
    analogReadResolution(12);           // ESP32 ADC 12-bit → 0–4095

    pinMode(PUMP_PIN, OUTPUT);
    digitalWrite(PUMP_PIN, PUMP_OFF);   // Tắt bơm khi khởi động
    dht.begin();                         // Khởi động cảm biến không khí

    Serial.println("============================================");
    Serial.println("  greenie-auto | 2 Cảm biến + Máy bơm");
    Serial.println("  Cảm biến: Capacitive Soil Moisture v1.2");
    Serial.println("  Board   : ESP32 Dev Module");
    Serial.println("============================================\n");
    Serial.println("[!] Giữ nút BOOT (GPIO 0) > 3 giây khi bật nguồn để reset WiFi.\n");

    // ─── KIỂM TRA NÚT RESET WIFI ─────────────────────────────────
    pinMode(RESET_PIN, INPUT_PULLUP);
    Serial.println("[Reset] Giữ nút BOOT trong 3 giây sau khi ESP32 đã khởi động để xóa WiFi đã lưu.");

    // Nếu nút BOOT được giữ sau khi ESP đã khởi động,
    // xóa cấu hình WiFi để bắt đầu lại với portal AP.
    if (digitalRead(RESET_PIN) == LOW) {
        Serial.println("[Reset] Nút BOOT đang giữ, bắt đầu đếm 3 giây...");
        unsigned long pressedMs = millis();
        while (digitalRead(RESET_PIN) == LOW && millis() - pressedMs < RESET_HOLD_MS) {
            delay(50);
        }
        if (digitalRead(RESET_PIN) == LOW) {
            Serial.println("[Reset] ✅ Đã giữ đủ 3 giây. Xóa WiFi và khởi động lại...");
            WiFiManager wm;
            wm.resetSettings();
            WiFi.disconnect(true);
            delay(500);
            ESP.restart();
        }
        Serial.println("[Reset] Nút BOOT đã thả trước 3 giây, tiếp tục quá trình bình thường.");
    }

    // ─── KẾT NỐI WIFI QUA ĐIỆN THOẠI ─────────────────────────────
    // Lần đầu (hoặc chưa có thông tin WiFi):
    //   1. ESP32 phát sóng WiFi tên "greenie-auto-setup"
    //   2. Điện thoại kết nối vào mạng đó
    //   3. Trình duyệt tự mở (hoặc vào 192.168.4.1)
    //   4. Chọn WiFi nhà → nhập mật khẩu → Lưu
    //   5. ESP32 tự kết nối và nhớ mãi (lưu vào flash)

    // Khởi tạo các handler trước, nhưng chưa mở server để tránh xung đột port với WiFiManager.
    server.on("/",          HTTP_GET,  handleRoot);
    server.on("/pump",      HTTP_POST, handlePump);
    server.on("/api/data",  HTTP_GET,  handleApiData);
    server.on("/api/pump",  HTTP_GET,  handleApiPump);

    WiFi.mode(WIFI_AP_STA);
    WiFi.setHostname("greenie-auto");
    WiFiManager wm;
    wm.setConfigPortalTimeout(180);   // Tự thoát portal sau 3 phút nếu không cài
    wm.setAPStaticIPConfig(IPAddress(192,168,4,1), IPAddress(192,168,4,1), IPAddress(255,255,255,0));
    Serial.println("[WiFi] Bắt đầu cấu hình WiFi.");
    Serial.printf("[WiFi] Hiện tại mode: %d, SSID lưu: %s\n", WiFi.getMode(), WiFi.SSID().c_str());

    bool portalStarted = false;
    if (WiFi.SSID().length() == 0) {
        Serial.println("[WiFi] Chưa có cấu hình lưu. Mở portal cấu hình AP ngay.");
        portalStarted = wm.startConfigPortal("greenie-auto-setup");
    } else {
        Serial.println("[WiFi] Đã có SSID lưu, thử autoConnect trước.");
        if (!wm.autoConnect("greenie-auto-setup")) {
            Serial.println("[WiFi] autoConnect thất bại. Bắt đầu portal cấu hình AP.");
            portalStarted = wm.startConfigPortal("greenie-auto-setup");
        } else {
            portalStarted = true;
        }
    }

    if (!portalStarted) {
        Serial.println("[WiFi] Không thể mở portal AP. Khởi động lại...");
        ESP.restart();
    }

    if (WiFi.status() == WL_CONNECTED) {
        if (MDNS.begin("greenie-auto")) {
            MDNS.addService("http", "tcp", 80);
            Serial.println("[mDNS] ✅ greenie-auto.local đã sẵn sàng");
        } else {
            Serial.println("[mDNS] ❌ Không khởi tạo được mDNS");
        }
    }

    // Chỉ mở web server sau khi WiFiManager đã xử lý xong, tránh xung đột TCP/IP.
    server.begin();
    Serial.printf("[WiFi] Kết nối hoặc portal hoạt động. STA IP: http://%s  AP IP: http://%s\n\n",
                  WiFi.localIP().toString().c_str(), WiFi.softAPIP().toString().c_str());
    lastIpLog = WiFi.localIP().toString();
    Serial.println("[Web] Server đã khởi động. Nếu đang ở AP mode, kết nối vào SSID greenie-auto-setup và mở 192.168.4.1.");
}

// ─── LOOP ────────────────────────────────────────────────────────
void loop() {
    delay(READ_INTERVAL_MS);

    server.handleClient();   // Xử lý request từ điện thoại

    // Kiểm tra nút reset WiFi sau khi ESP32 đã khởi động
    if (digitalRead(RESET_PIN) == LOW) {
        if (resetStartMs == 0) {
            resetStartMs = millis();
            Serial.println("[Reset] Đang giữ nút BOOT để xóa WiFi...");
        } else if (!resetRequested && millis() - resetStartMs >= RESET_HOLD_MS) {
            resetRequested = true;
            WiFiManager wm;
            wm.resetSettings();
            WiFi.disconnect(true);
            Serial.println("[Reset] ✅ Đã xóa WiFi! Khởi động lại...");
            delay(500);
            ESP.restart();
        }
    } else {
        resetStartMs = 0;
        resetRequested = false;
    }

    // Đọc cảm biến không khí DHT22
    float t = dht.readTemperature();
    float h = dht.readHumidity();
    lastAirTemp     = isnan(t) ? -1.0f : t;
    lastAirHumidity = isnan(h) ? -1.0f : h;
    if (lastAirTemp < 0)
        Serial.println("[Không khí] Không cắm cảm biến");
    else
        Serial.printf("[Không khí] Nhiệt độ: %.1f°C  Độ ẩm: %.1f%%\n", lastAirTemp, lastAirHumidity);

    // Đọc cảm biến theo SENSOR_COUNT
    int values[MAX_SENSORS] = {0};
    readSensors(values);   // -1 = không cắm cảm biến
    lastSensorCount = SENSOR_COUNT;   // tổng số khe, gồm cả -1
    int activeCount = 0, sum = 0;
    for (int i = 0; i < SENSOR_COUNT; i++) {
        lastSensorValues[i] = values[i];
        if (values[i] >= 0) { sum += values[i]; activeCount++; }
    }
    int avgPct = activeCount > 0 ? sum / activeCount : 0;
    lastAvg = avgPct;   // Lưu cho web

    // Đẩy lên Firebase & kiểm tra lệnh điều khiển từ app
    pushToFirebase(avgPct, pumpRunning);
    checkPumpCommand();

    for (int i = 0; i < SENSOR_COUNT; i++) {
        if (lastSensorValues[i] < 0) {
            Serial.printf("[Cảm biến %d] không có tín hiệu\n", i + 1);
        } else {
            Serial.printf("[Cảm biến %d] %3d%%  %s\n", i + 1, lastSensorValues[i], sensorLabel(lastSensorValues[i]));
        }
    }
    Serial.printf("[Trung bình] %3d%%\n", avgPct);

    String currentIp = WiFi.localIP().toString();
    if (currentIp != lastIpLog) {
        lastIpLog = currentIp;
        Serial.printf("[IP] ESP32 STA IP: %s\n", currentIp.c_str());
    }

    // ─── LOGIC ĐIỀU KHIỂN MÁY BƠM ────────────────────────────
    if (!pumpRunning && avgPct < PUMP_ON_THRESHOLD) {
        digitalWrite(PUMP_PIN, PUMP_ON);
        pumpRunning = true;
        Serial.printf("[MÁY BƠM]   ✅ BẬT  — TB %d%% < %d%%\n", avgPct, PUMP_ON_THRESHOLD);
    } else if (pumpRunning && avgPct >= PUMP_OFF_THRESHOLD) {
        digitalWrite(PUMP_PIN, PUMP_OFF);
        pumpRunning = false;
        Serial.printf("[MÁY BƠM]   ⛔ TẮT  — TB %d%% ≥ %d%%\n", avgPct, PUMP_OFF_THRESHOLD);
    } else {
        Serial.printf("[MÁY BƠM]   %s\n", pumpRunning ? "⚙️  ĐANG CHẠY" : "⏸  ĐỨNG");
    }

    Serial.println("--------------------------------------------");
}
