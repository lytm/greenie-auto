package com.greenie.auto.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SoilData(
    @SerialName("sensor_count") val sensorCount: Int,
    @SerialName("sensors") val sensors: List<Int>,
    @SerialName("average") val average: Int,
    @SerialName("air_temp") val airTemp: Float = -1f,      // -1 = không cắm cảm biến
    @SerialName("air_humidity") val airHumidity: Float = -1f,
    @SerialName("pump") val pump: Boolean,
    @SerialName("threshold_on") val thresholdOn: Int,
    @SerialName("threshold_off") val thresholdOff: Int,
    @SerialName("schedule_enabled") val scheduleEnabled: Boolean = false,
    @SerialName("schedule_hour") val scheduleHour: Int = 6,
    @SerialName("schedule_minute") val scheduleMinute: Int = 0,
    @SerialName("schedule_duration_min") val scheduleDurationMin: Int = 2,
    @SerialName("schedule_running") val scheduleRunning: Boolean = false,
)
