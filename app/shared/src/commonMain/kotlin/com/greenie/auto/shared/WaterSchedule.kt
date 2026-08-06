package com.greenie.auto.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WaterSchedule(
    @SerialName("enabled") val enabled: Boolean = false,
    @SerialName("duration_min") val durationMin: Int = 2,
    // Ví dụ: "06:30,18:00"
    @SerialName("times_csv") val timesCsv: String = "06:00",
    // 1..7 tương ứng T2..CN. Ví dụ: "1,2,3,4,5,6,7"
    @SerialName("weekdays_csv") val weekdaysCsv: String = "1,2,3,4,5,6,7",
    @SerialName("running") val running: Boolean = false,
)
