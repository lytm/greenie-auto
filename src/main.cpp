#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include <HTTPClient.h>
#include <WiFiManager.h>
#include <ESPmDNS.h>
#include <DHT.h>              // Cảm biến không khí DHT22
#include <time.h>

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
// Tối đa 6 chân ADC1 — cắm cảm biến vào chân nào thì tự hiện, rút ra tự ẩn.
#define SENSOR_COUNT    6
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
// ─── CHẾ ĐỘ TIẾT KIỆM PIN (ms) ──────────────────────────────────
#define READ_INTERVAL_MS               10000
#define FIREBASE_PUSH_INTERVAL_MS      30000
#define PUMP_COMMAND_POLL_INTERVAL_MS  10000
#define MONTHLY_STATS_PUSH_INTERVAL_MS 300000
#define LOOP_IDLE_MS                      50
#define SENSOR_SAMPLE_COUNT                5
#define TIME_SYNC_RETRY_MS             60000
#define GMT_OFFSET_SEC                 (7 * 3600)
#define DAYLIGHT_OFFSET_SEC            0
#define MONTHLY_STATS_SIGNIFICANT_TEMP_DELTA_X10 5
#define MONTHLY_STATS_SIGNIFICANT_SOIL_DELTA     2

// ─── HIỆU CHỈNH (CALIBRATION) ────────────────────────────────────
// 1. Để cảm biến trong KHÔNG KHÍ → đọc ADC → gán vào DRY_VALUE
// 2. Nhúng cảm biến vào NƯỚC     → đọc ADC → gán vào WET_VALUE
#define DRY_VALUE   2800
#define WET_VALUE    800

// ─── NGƯỠNG ĐIỀU KHIỂN MÁY BƠM ──────────────────────────────────
#define PUMP_ON_THRESHOLD   30  // % — độ ẩm TB < 30% → BẬT bơm
#define PUMP_OFF_THRESHOLD  70  // % — độ ẩm TB ≥ 70% → TẮT bơm
// Nếu ADC thô nằm ngoài dải hợp lý → coi như không có cảm biến
// - ADC rất cao: chân thả nổi/pull-up
// - ADC quá thấp: chân thả nổi/pull-down hoặc nhiễu
#define NO_SENSOR_HIGH_THRESHOLD 3800
#define NO_SENSOR_LOW_THRESHOLD   150
// Nếu biên độ dao động ADC trong nhiều mẫu quá lớn thì thường là chân thả nổi
#define NO_SENSOR_NOISE_SPAN      350

// ─── FIREBASE CONNECTION TIMEOUT ────────────────────────────────
#define HTTP_TIMEOUT_MS  8000  // 8 giây timeout cho Firebase request
// ─── HÀM CHUYỂN ĐỔI ADC → % ĐỘ ẨM ──────────────────────────────
int toPercent(int raw) {
    return constrain(map(raw, DRY_VALUE, WET_VALUE, 0, 100), 0, 100);
}

// ─── HÀM ĐỌC TRUNG BÌNH N LẦN (giảm nhiễu) ──────────────────────
int readSensorRaw(int pin, int &span) {
    long sum = 0;
    int minRaw = 4095;
    int maxRaw = 0;
    for (int i = 0; i < SENSOR_SAMPLE_COUNT; i++) {
        int raw = analogRead(pin);
        sum += raw;
        if (raw < minRaw) minRaw = raw;
        if (raw > maxRaw) maxRaw = raw;
        delay(8);
    }
    span = maxRaw - minRaw;
    return sum / SENSOR_SAMPLE_COUNT;
}

int readSensors(int values[]) {
    int active = 0;
    for (int i = 0; i < SENSOR_COUNT; i++) {
        int span = 0;
        int raw = readSensorRaw(SOIL_PINS[i], span);
        if (raw >= NO_SENSOR_HIGH_THRESHOLD || raw <= NO_SENSOR_LOW_THRESHOLD || span >= NO_SENSOR_NOISE_SPAN) {
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
time_t pumpOnStartTime = 0;   // thời điểm bơm bật (cho lịch sử tưới)
int  lastSensorValues[MAX_SENSORS] = {0};
int  lastSensorCount = SENSOR_COUNT;
int  lastAvg = 0;float lastAirTemp = -1.0f;      // -1 = không cắm DHT
float lastAirHumidity = -1.0f;bool resetRequested = false;
unsigned long resetStartMs = 0;
unsigned long lastReadMs = 0;
unsigned long lastFirebasePushMs = 0;
unsigned long lastPumpPollMs = 0;
unsigned long lastTimeSyncAttemptMs = 0;
unsigned long lastMonthlyStatsPushMs = 0;

String statsMonthKey = "";
String statsDayKey = "";
long statsTempSumX10 = 0;
int statsTempSamples = 0;
long statsSoilSum = 0;
int statsSoilSamples = 0;
int statsPumpOnCount = 0;
String pendingStatsMonthKey = "";
String pendingStatsDayKey = "";
long pendingStatsTempSumX10 = 0;
int pendingStatsTempSamples = 0;
long pendingStatsSoilSum = 0;
int pendingStatsSoilSamples = 0;
int pendingStatsPumpOnCount = 0;
bool hasPendingDayStats = false;
String lastPushedStatsMonthKey = "";
String lastPushedStatsDayKey = "";
int lastPushedAvgTempX10 = -10000;
int lastPushedAvgSoil = -1;
int lastPushedPumpOnCount = -1;

int currentAvgTempX10() {
    if (statsTempSamples <= 0) return -10;
    return (int)(statsTempSumX10 / statsTempSamples);
}

int currentAvgSoil() {
    if (statsSoilSamples <= 0) return -1;
    return (int)(statsSoilSum / statsSoilSamples);
}

bool pushMonthlyStatsSnapshot(const String &monthKey, const String &dayKey, long tempSumX10, int tempSamples, long soilSum, int soilSamples, int pumpOnCount, bool forceLog) {
    if (WiFi.status() != WL_CONNECTED || monthKey.length() == 0 || dayKey.length() == 0) {
        return false;
    }

    HTTPClient statsHttp;
    statsHttp.setTimeout(HTTP_TIMEOUT_MS);
    String statsUrl = String(FIREBASE_URL) + "/monthly_stats/" + monthKey + "/" + dayKey + ".json?auth=" + FIREBASE_SECRET;
    statsHttp.begin(statsUrl);
    statsHttp.addHeader("Content-Type", "application/json");

    float avgTemp = tempSamples > 0 ? (tempSumX10 / 10.0f) / tempSamples : -1.0f;
    float avgSoil = soilSamples > 0 ? (float)soilSum / soilSamples : -1.0f;
    String statsBody = "{";
    statsBody += "\"avg_temp\":" + String(avgTemp, 1) + ",";
    statsBody += "\"avg_soil\":" + String(avgSoil, 1) + ",";
    statsBody += "\"temp_samples\":" + String(tempSamples) + ",";
    statsBody += "\"pump_on_count\":" + String(pumpOnCount);
    statsBody += "}";

    int statsCode = statsHttp.PUT(statsBody);
    bool ok = statsCode > 0;
    if (ok) {
        if (forceLog) {
            Serial.printf("[Firebase] 📊 Monthly stats OK (%s/%s, HTTP %d)\n", monthKey.c_str(), dayKey.c_str(), statsCode);
        }
    } else {
        Serial.printf("[Firebase] ❌ Monthly stats lỗi: %s\n", statsHttp.errorToString(statsCode).c_str());
    }
    statsHttp.end();
    return ok;
}

void flushPendingDayStats() {
    if (!hasPendingDayStats) return;
    if (pushMonthlyStatsSnapshot(
            pendingStatsMonthKey,
            pendingStatsDayKey,
            pendingStatsTempSumX10,
            pendingStatsTempSamples,
            pendingStatsSoilSum,
            pendingStatsSoilSamples,
            pendingStatsPumpOnCount,
            true)) {
        hasPendingDayStats = false;
    }
}

void pushMonthlyStatsIfNeeded(bool forcePush) {
    flushPendingDayStats();

    if (statsMonthKey.length() == 0 || statsDayKey.length() == 0) return;
    if (WiFi.status() != WL_CONNECTED) return;

    int avgTempX10 = currentAvgTempX10();
    int avgSoil = currentAvgSoil();
    bool sameDayAsLastPush = (lastPushedStatsMonthKey == statsMonthKey && lastPushedStatsDayKey == statsDayKey);
    bool intervalReached = lastMonthlyStatsPushMs == 0 || millis() - lastMonthlyStatsPushMs >= MONTHLY_STATS_PUSH_INTERVAL_MS;
    bool tempChangedSignificantly = !sameDayAsLastPush || abs(avgTempX10 - lastPushedAvgTempX10) >= MONTHLY_STATS_SIGNIFICANT_TEMP_DELTA_X10;
    bool soilChangedSignificantly = !sameDayAsLastPush || lastPushedAvgSoil < 0 || (avgSoil >= 0 && abs(avgSoil - lastPushedAvgSoil) >= MONTHLY_STATS_SIGNIFICANT_SOIL_DELTA);
    bool pumpCountChanged = !sameDayAsLastPush || statsPumpOnCount != lastPushedPumpOnCount;

    if (!forcePush && !intervalReached && !tempChangedSignificantly && !soilChangedSignificantly && !pumpCountChanged) {
        return;
    }

    if (pushMonthlyStatsSnapshot(statsMonthKey, statsDayKey, statsTempSumX10, statsTempSamples, statsSoilSum, statsSoilSamples, statsPumpOnCount, true)) {
        lastMonthlyStatsPushMs = millis();
        lastPushedStatsMonthKey = statsMonthKey;
        lastPushedStatsDayKey = statsDayKey;
        lastPushedAvgTempX10 = avgTempX10;
        lastPushedAvgSoil = avgSoil;
        lastPushedPumpOnCount = statsPumpOnCount;
    }
}

bool getDateKeys(String &monthKey, String &dayKey) {
    time_t now;
    time(&now);
    struct tm tmNow;
    if (!localtime_r(&now, &tmNow)) return false;
    int year = tmNow.tm_year + 1900;
    if (year < 2024) return false;

    char monthBuf[8];  // YYYY-MM
    char dayBuf[3];    // DD
    strftime(monthBuf, sizeof(monthBuf), "%Y-%m", &tmNow);
    strftime(dayBuf, sizeof(dayBuf), "%d", &tmNow);
    monthKey = String(monthBuf);
    dayKey = String(dayBuf);
    return true;
}

void ensureStatsDay() {
    String month, day;
    if (!getDateKeys(month, day)) return;

    if (statsMonthKey != month || statsDayKey != day) {
        if (statsMonthKey.length() > 0 && statsDayKey.length() > 0) {
            pendingStatsMonthKey = statsMonthKey;
            pendingStatsDayKey = statsDayKey;
            pendingStatsTempSumX10 = statsTempSumX10;
            pendingStatsTempSamples = statsTempSamples;
            pendingStatsSoilSum = statsSoilSum;
            pendingStatsSoilSamples = statsSoilSamples;
            pendingStatsPumpOnCount = statsPumpOnCount;
            hasPendingDayStats = true;
        }
        statsMonthKey = month;
        statsDayKey = day;
        statsTempSumX10 = 0;
        statsTempSamples = 0;
        statsSoilSum = 0;
        statsSoilSamples = 0;
        statsPumpOnCount = 0;
        Serial.printf("[Stats] Chuyển ngày thống kê: %s-%s\n", statsMonthKey.c_str(), statsDayKey.c_str());
    }
}

void recordPumpOnEventIfNeeded(bool previousState, bool newState) {
    if (!previousState && newState) {
        ensureStatsDay();
        if (statsDayKey.length() > 0) {
            statsPumpOnCount++;
        }
    }
}

void pushPumpHistoryEntry(time_t startTime) {
    if (WiFi.status() != WL_CONNECTED || startTime == 0) return;
    time_t endTime;
    time(&endTime);
    int durationS = (int)difftime(endTime, startTime);
    if (durationS < 0) durationS = 0;

    struct tm startTm, endTm;
    localtime_r(&startTime, &startTm);
    localtime_r(&endTime, &endTm);
    if (startTm.tm_year + 1900 < 2024) return;   // NTP chưa sync

    char dateBuf[11];   // YYYY-MM-DD
    char startBuf[9];   // HH:MM:SS
    char endBuf[9];
    char keyBuf[7];     // HHMMSS
    strftime(dateBuf,  sizeof(dateBuf),  "%Y-%m-%d", &startTm);
    strftime(startBuf, sizeof(startBuf), "%H:%M:%S", &startTm);
    strftime(endBuf,   sizeof(endBuf),   "%H:%M:%S", &endTm);
    snprintf(keyBuf, sizeof(keyBuf), "%02d%02d%02d", startTm.tm_hour, startTm.tm_min, startTm.tm_sec);

    HTTPClient http;
    http.setTimeout(HTTP_TIMEOUT_MS);
    String url = String(FIREBASE_URL) + "/pump_history/" + dateBuf + "/" + keyBuf + ".json?auth=" + FIREBASE_SECRET;
    http.begin(url);
    http.addHeader("Content-Type", "application/json");
    String body = "{\"start\":\"" + String(startBuf) + "\",\"end\":\"" + String(endBuf) + "\",\"duration_s\":" + String(durationS) + "}";
    int code = http.PUT(body);
    if (code > 0) {
        Serial.printf("[Firebase] 📋 Pump history (%s %s→%s, %ds)\n", dateBuf, startBuf, endBuf, durationS);
    } else {
        Serial.printf("[Firebase] ❌ Pump history lỗi: %s\n", http.errorToString(code).c_str());
    }
    http.end();
}

void setPumpState(bool newState, const char* source) {
    bool previous = pumpRunning;
    pumpRunning = newState;
    digitalWrite(PUMP_PIN, pumpRunning ? PUMP_ON : PUMP_OFF);
    recordPumpOnEventIfNeeded(previous, newState);
    if (!previous && newState) {
        time(&pumpOnStartTime);            // ghi thời điểm bơm bật
    } else if (previous && !newState) {
        pushPumpHistoryEntry(pumpOnStartTime);  // đẩy lịch sử khi bơm tắt
        pumpOnStartTime = 0;
    }
    if (previous != newState) {
        Serial.printf("[PUMP] %s -> %s (%s)\n", previous ? "ON" : "OFF", newState ? "ON" : "OFF", source);
    }
}

void syncTimeIfNeeded() {
    unsigned long nowMs = millis();
    if (lastTimeSyncAttemptMs != 0 && nowMs - lastTimeSyncAttemptMs < TIME_SYNC_RETRY_MS) {
        return;
    }

    String month, day;
    if (getDateKeys(month, day)) {
        return;
    }

    lastTimeSyncAttemptMs = nowMs;
    configTime(GMT_OFFSET_SEC, DAYLIGHT_OFFSET_SEC, "pool.ntp.org", "time.google.com", "time.windows.com");
    Serial.println("[Time] Đang đồng bộ NTP...");
}

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
        setPumpState(state == "on", "API");
    }
    server.sendHeader("Access-Control-Allow-Origin", "*");
    server.send(200, "application/json", String("{\"pump\":") + String(pumpRunning ? "true" : "false") + "}");
}

// Endpoint điều khiển bơm thủ công từ điện thoại
void handlePump() {
    setPumpState(!pumpRunning, "WEB");
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

    pushMonthlyStatsIfNeeded(false);
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
            setPumpState(newState, "FirebaseCmd");
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
    Serial.println("  greenie-auto | Tối đa 6 cảm biến + Máy bơm");
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
    WiFi.setSleep(true);
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
        WiFi.softAPdisconnect(true);
        WiFi.mode(WIFI_STA);
        syncTimeIfNeeded();
        if (MDNS.begin("greenie-auto")) {
            MDNS.addService("http", "tcp", 80);
            Serial.println("[mDNS] ✅ greenie-auto.local đã sẵn sàng");
        } else {
            Serial.println("[mDNS] ❌ Không khởi tạo được mDNS");
        }
        Serial.println("[WiFi] Đã tắt AP, chạy STA + WiFi sleep để tiết kiệm pin.");
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

    unsigned long now = millis();

    if (now - lastPumpPollMs >= PUMP_COMMAND_POLL_INTERVAL_MS) {
        lastPumpPollMs = now;
        checkPumpCommand();
    }

    if (WiFi.status() == WL_CONNECTED) {
        syncTimeIfNeeded();
    }

    if (lastReadMs != 0 && now - lastReadMs < READ_INTERVAL_MS) {
        delay(LOOP_IDLE_MS);
        return;
    }
    lastReadMs = now;

    // Đọc cảm biến không khí DHT22
    float t = dht.readTemperature();
    float h = dht.readHumidity();
    lastAirTemp     = isnan(t) ? -1.0f : t;
    lastAirHumidity = isnan(h) ? -1.0f : h;
    if (lastAirTemp < 0)
        Serial.println("[Không khí] Không cắm cảm biến");
    else
        Serial.printf("[Không khí] Nhiệt độ: %.1f°C  Độ ẩm: %.1f%%\n", lastAirTemp, lastAirHumidity);

    ensureStatsDay();
    if (lastAirTemp >= 0 && statsDayKey.length() > 0) {
        statsTempSumX10 += (long)(lastAirTemp * 10.0f);
        statsTempSamples++;
    }

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

    if (activeCount > 0 && statsDayKey.length() > 0) {
        statsSoilSum += avgPct;
        statsSoilSamples++;
    }

    // Đẩy Firebase thưa hơn để tiết kiệm pin
    if (lastFirebasePushMs == 0 || now - lastFirebasePushMs >= FIREBASE_PUSH_INTERVAL_MS) {
        pushToFirebase(avgPct, pumpRunning);
        lastFirebasePushMs = now;
    }

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
        setPumpState(true, "Auto");
        Serial.printf("[MÁY BƠM]   ✅ BẬT  — TB %d%% < %d%%\n", avgPct, PUMP_ON_THRESHOLD);
    } else if (pumpRunning && avgPct >= PUMP_OFF_THRESHOLD) {
        setPumpState(false, "Auto");
        Serial.printf("[MÁY BƠM]   ⛔ TẮT  — TB %d%% ≥ %d%%\n", avgPct, PUMP_OFF_THRESHOLD);
    } else {
        Serial.printf("[MÁY BƠM]   %s\n", pumpRunning ? "⚙️  ĐANG CHẠY" : "⏸  ĐỨNG");
    }

    Serial.println("--------------------------------------------");
    delay(LOOP_IDLE_MS);
}
