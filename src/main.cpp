#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include <HTTPClient.h>
#include <WiFiManager.h>      // Nhập SSID/Pass từ điện thoại, không cần code cứng

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
#define SOIL_PIN_1      32      // Cảm biến 1 → GPIO 32
#define SOIL_PIN_2      33      // Cảm biến 2 → GPIO 33

// ─── RELAY ĐIỀU KHIỂN MÁY BƠM ────────────────────────────────────
#define PUMP_PIN        26      // Relay IN   → GPIO 26
#define PUMP_ON         LOW     // Relay module kích mức LOW
#define PUMP_OFF        HIGH

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

// ─── HÀM CHUYỂN ĐỔI ADC → % ĐỘ ẨM ──────────────────────────────
int toPercent(int raw) {
    return constrain(map(raw, DRY_VALUE, WET_VALUE, 0, 100), 0, 100);
}

// ─── HÀM ĐỌC TRUNG BÌNH 10 LẦN (giảm nhiễu) ─────────────────────
int readSensor(int pin) {
    long sum = 0;
    for (int i = 0; i < 10; i++) { sum += analogRead(pin); delay(10); }
    return sum / 10;
}

// ─── BIẾN TRẠNG THÁI ─────────────────────────────────────────────
bool pumpRunning = false;
int  lastPct1 = 0, lastPct2 = 0, lastAvg = 0;
bool resetRequested = false;
unsigned long resetStartMs = 0;

// ─── WEB SERVER ──────────────────────────────────────────────────
WebServer server(80);
String lastIpLog = "";
// Trang HTML trả về cho trình duyệt
void handleRoot() {
    String color1 = lastPct1 < 30 ? "#e74c3c" : (lastPct1 < 60 ? "#f39c12" : "#27ae60");
    String color2 = lastPct2 < 30 ? "#e74c3c" : (lastPct2 < 60 ? "#f39c12" : "#27ae60");
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

    html += String("<div class='card'><b>Cảm biến 1</b><div class='pct' style='color:") + color1 + "'>" + String(lastPct1) + "%</div></div>";
    html += String("<div class='card'><b>Cảm biến 2</b><div class='pct' style='color:") + color2 + "'>" + String(lastPct2) + "%</div></div>";
    html += String("<div class='card'><b>Trung bình</b><div class='pct'>") + String(lastAvg) + "%</div></div>";
    html += String("<div class='card'><b>Máy bơm:</b> ") + pumpStatus + "<br>";
    html += String("<form action='/pump' method='POST'><button class='btn' style='background:") + pumpBtnColor + ";color:#fff'>" + pumpBtnLabel + "</button></form></div>";
    html += "<p class='refresh'>Tự động làm mới sau 5 giây</p></body></html>";

    server.send(200, "text/html; charset=utf-8", html);
}

// Endpoint JSON cho app mobile đọc real-time
void handleApiData() {
    String json = "{";
    json += String("\"sensor1\":") + String(lastPct1) + ",";
    json += String("\"sensor2\":") + String(lastPct2) + ",";
    json += String("\"average\":") + String(lastAvg) + ",";
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
//   "sensor_data": { "sensor1": 45, "sensor2": 60, "average": 52, "pump": false,
//                    "threshold_on": 30, "threshold_off": 70 },
//   "pump_command": null
// }
void pushToFirebase(int pct1, int pct2, int avg, bool pump) {
    if (WiFi.status() != WL_CONNECTED) return;
    HTTPClient http;
    String url = String(FIREBASE_URL) + "/sensor_data.json?auth=" + FIREBASE_SECRET;
    http.begin(url);
    http.addHeader("Content-Type", "application/json");
    String body = String("{")
        + "\"sensor1\":"       + String(pct1)  + ","
        + "\"sensor2\":"       + String(pct2)  + ","
        + "\"average\":"       + String(avg)   + ","
        + "\"pump\":"          + String(pump ? "true" : "false") + ","
        + "\"threshold_on\":"  + String(PUMP_ON_THRESHOLD)  + ","
        + "\"threshold_off\":" + String(PUMP_OFF_THRESHOLD)
        + "}";
    int code = http.PATCH(body);
    if (code > 0) Serial.printf("[Firebase] ✅ Đẩy OK (HTTP %d)\n", code);
    else          Serial.printf("[Firebase] ❌ Lỗi: %s\n", http.errorToString(code).c_str());
    http.end();
}

// ─── FIREBASE: NHẬN LỆNH BƠM TỪ APP ───────────────────────────
void checkPumpCommand() {
    if (WiFi.status() != WL_CONNECTED) return;
    HTTPClient http;
    http.begin(String(FIREBASE_URL) + "/pump_command.json?auth=" + FIREBASE_SECRET);
    int code = http.GET();
    if (code == 200) {
        String payload = http.getString();
        if (payload != "null" && payload.length() > 2) {
            bool newState = (payload.indexOf("\"on\"") >= 0);
            pumpRunning = newState;
            digitalWrite(PUMP_PIN, pumpRunning ? PUMP_ON : PUMP_OFF);
            Serial.printf("[Firebase] 📱 Lệnh bơm từ app: %s\n", pumpRunning ? "BẬT" : "TẮT");
            // Xóa lệnh sau khi thực hiện
            http.end();
            http.begin(String(FIREBASE_URL) + "/pump_command.json?auth=" + FIREBASE_SECRET);
            http.addHeader("Content-Type", "application/json");
            http.PUT("null");
        }
    }
    http.end();
}
// ─── SETUP ───────────────────────────────────────────────────────
void setup() {
    Serial.begin(115200);
    analogReadResolution(12);           // ESP32 ADC 12-bit → 0–4095

    pinMode(PUMP_PIN, OUTPUT);
    digitalWrite(PUMP_PIN, PUMP_OFF);   // Tắt bơm khi khởi động

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

    // Đọc 2 cảm biến
    int pct1 = toPercent(readSensor(SOIL_PIN_1));
    int pct2 = toPercent(readSensor(SOIL_PIN_2));
    int avgPct = (pct1 + pct2) / 2;
    lastPct1 = pct1; lastPct2 = pct2; lastAvg = avgPct;   // Lưu cho web

    // Đẩy lên Firebase & kiểm tra lệnh điều khiển từ app
    pushToFirebase(pct1, pct2, avgPct, pumpRunning);
    checkPumpCommand();

    // Nhãn trạng thái
    auto label = [](int p) -> const char* {
        if (p < 20) return "KHÔ  — Cần tưới!";
        if (p < 60) return "VỪA  — Độ ẩm tốt";
        return           "ƯỚT  — Đủ nước";
    };

    Serial.printf("[Cảm biến 1] %3d%%  %s\n", pct1, label(pct1));
    Serial.printf("[Cảm biến 2] %3d%%  %s\n", pct2, label(pct2));
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
