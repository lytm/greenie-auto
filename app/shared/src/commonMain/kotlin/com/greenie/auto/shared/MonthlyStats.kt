package com.greenie.auto.shared

data class MonthlyDayStat(
    val day: String,
    val avgTemp: Float,
    val avgSoil: Float,
    val pumpOnCount: Int,
    val tempSampleCount: Int,
)

data class MonthlyStats(
    val monthKey: String,
    val days: List<MonthlyDayStat>,
)
