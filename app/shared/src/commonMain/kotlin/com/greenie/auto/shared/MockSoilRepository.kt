package com.greenie.auto.shared

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

class MockSoilRepository : SoilRepository() {
    private var mockPumpRunning = false
    private var mockActiveSensors = 1
    private var mockSchedule = WaterSchedule(enabled = true, durationMin = 2, timesCsv = "06:30,18:00", weekdaysCsv = "1,2,3,4,5,6,7")
    // Giả lập tối đa 6 khe cảm biến, -1 = không cắm
    // Mặc định chỉ cắm 1 cảm biến để test đúng logic ẩn/hiện trên app
    private val mockSensors = mutableListOf(45, -1, -1, -1, -1, -1)
    private val mockBaseLevels = listOf(45, 52, 38, 64, 41, 58)

    fun getMockActiveSensors(): Int = mockActiveSensors

    fun setMockActiveSensors(count: Int) {
        mockActiveSensors = count.coerceIn(0, mockSensors.size)
        applyActiveSensorsState()
    }

    private fun applyActiveSensorsState() {
        for (i in mockSensors.indices) {
            if (i < mockActiveSensors) {
                if (mockSensors[i] < 0) {
                    mockSensors[i] = mockBaseLevels[i]
                }
            } else {
                mockSensors[i] = -1
            }
        }
    }

    override suspend fun fetchData(): Result<SoilData> = runCatching {
        applyActiveSensorsState()

        // Simulate sensor reading changes cho các cảm biến đang cắm
        for (i in mockSensors.indices) {
            val value = mockSensors[i]
            if (value >= 0) {
                mockSensors[i] = (value + Random.nextInt(-5, 6)).coerceIn(0, 100)
            }
        }

        val activeSensors = mockSensors.filter { it >= 0 }
        val avg = if (activeSensors.isNotEmpty()) {
            activeSensors.sum() / activeSensors.size
        } else {
            0
        }

        // Auto pump logic
        if (!mockPumpRunning && avg < 30) mockPumpRunning = true
        if (mockPumpRunning && avg >= 70) mockPumpRunning = false

        SoilData(
            sensorCount = 6,
            sensors = mockSensors.toList(),
            average = avg,
            airTemp = 27.5f,
            airHumidity = 65.0f,
            pump = mockPumpRunning,
            thresholdOn = 30,
            thresholdOff = 70
        )
    }

    override suspend fun setPump(on: Boolean): Result<Unit> = runCatching {
        mockPumpRunning = on
        logInfo("MockSoilRepository", "Đặt bơm thành: ${if (on) "BẬT" else "TẮT"}")
    }

    override suspend fun fetchWaterSchedule(): Result<WaterSchedule> = runCatching {
        mockSchedule
    }

    override suspend fun setWaterSchedule(schedule: WaterSchedule): Result<Unit> = runCatching {
        mockSchedule = schedule.copy(
            durationMin = schedule.durationMin.coerceIn(1, 60),
            timesCsv = schedule.timesCsv.ifBlank { "06:00" },
            weekdaysCsv = schedule.weekdaysCsv.ifBlank { "1,2,3,4,5,6,7" }
        )
    }

    override suspend fun fetchMonthlyStats(): Result<MonthlyStats> = runCatching {
        val days = (1..30).map { day ->
            val tempBase = 26f + (day % 7) * 0.6f
            val wave = if (day % 2 == 0) 0.8f else -0.5f
            MonthlyDayStat(
                day = day.toString().padStart(2, '0'),
                avgTemp = (tempBase + wave),
                avgSoil = (42 + ((day * 7) % 33)).toFloat(),
                pumpOnCount = (day % 4) + if (day % 9 == 0) 2 else 0,
                tempSampleCount = 24,
            )
        }
        MonthlyStats(monthKey = "2026-08", days = days)
    }

    override suspend fun fetchPumpHistory(): Result<PumpHistory> = runCatching {
        PumpHistory(entries = listOf(
            PumpLogEntry(date = "2026-08-04", startTime = "07:30:00", endTime = "07:32:15", durationSeconds = 135),
            PumpLogEntry(date = "2026-08-04", startTime = "14:15:00", endTime = "14:17:00", durationSeconds = 120),
            PumpLogEntry(date = "2026-08-03", startTime = "06:45:00", endTime = "06:48:30", durationSeconds = 210),
            PumpLogEntry(date = "2026-08-03", startTime = "13:20:00", endTime = "13:22:10", durationSeconds = 130),
            PumpLogEntry(date = "2026-08-02", startTime = "08:00:00", endTime = "08:03:00", durationSeconds = 180),
            PumpLogEntry(date = "2026-08-02", startTime = "17:45:00", endTime = "17:47:30", durationSeconds = 150),
            PumpLogEntry(date = "2026-08-01", startTime = "09:00:00", endTime = "09:02:00", durationSeconds = 120),
        ))
    }
}
