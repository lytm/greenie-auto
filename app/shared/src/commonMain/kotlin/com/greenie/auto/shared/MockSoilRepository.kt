package com.greenie.auto.shared

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

class MockSoilRepository : SoilRepository("http://mock") {
    private var mockPumpRunning = false
    private var mockSensor1 = 45
    private var mockSensor2 = 60

    override suspend fun fetchData(): Result<SoilData> = runCatching {
        // Simulate sensor reading changes
        mockSensor1 = (mockSensor1 + Random.nextInt(-5, 6)).coerceIn(0, 100)
        mockSensor2 = (mockSensor2 + Random.nextInt(-5, 6)).coerceIn(0, 100)
        val avg = (mockSensor1 + mockSensor2) / 2

        // Auto pump logic
        if (!mockPumpRunning && avg < 30) mockPumpRunning = true
        if (mockPumpRunning && avg >= 70) mockPumpRunning = false

        SoilData(
            sensor1 = mockSensor1,
            sensor2 = mockSensor2,
            average = avg,
            pump = mockPumpRunning,
            thresholdOn = 30,
            thresholdOff = 70
        )
    }

    override suspend fun setPump(on: Boolean): Result<Unit> = runCatching {
        mockPumpRunning = on
    }
}
