package com.greenie.auto.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SoilData(
    @SerialName("sensor1") val sensor1: Int,
    @SerialName("sensor2") val sensor2: Int,
    @SerialName("average") val average: Int,
    @SerialName("pump") val pump: Boolean,
    @SerialName("threshold_on") val thresholdOn: Int,
    @SerialName("threshold_off") val thresholdOff: Int
)
