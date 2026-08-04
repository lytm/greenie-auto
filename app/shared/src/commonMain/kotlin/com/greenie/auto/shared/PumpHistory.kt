package com.greenie.auto.shared

data class PumpLogEntry(
    val date: String,           // YYYY-MM-DD
    val startTime: String,      // HH:MM:SS
    val endTime: String,        // HH:MM:SS
    val durationSeconds: Int,
)

data class PumpHistory(
    val entries: List<PumpLogEntry>,  // newest-first
)
