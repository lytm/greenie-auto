package com.greenie.auto.shared

import android.util.Log

actual fun logError(tag: String, message: String?) {
    Log.e(tag, message ?: "null")
}

actual fun logInfo(tag: String, message: String?) {
    Log.i(tag, message ?: "null")
}
