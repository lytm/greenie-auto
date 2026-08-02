package com.greenie.auto.shared

actual fun logError(tag: String, message: String?) {
    println("[ERROR][$tag] ${message ?: "null"}")
}

actual fun logInfo(tag: String, message: String?) {
    println("[INFO][$tag] ${message ?: "null"}")
}
