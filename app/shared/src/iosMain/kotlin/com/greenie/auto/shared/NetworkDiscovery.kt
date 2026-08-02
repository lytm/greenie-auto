package com.greenie.auto.shared

// iOS resolve .local qua Bonjour natively thông qua URLSession (Ktor Darwin)
actual suspend fun discoverLocalEsp32Url(): String? = "http://greenie-auto.local"

// iOS thử greenie-auto.local trước, AP IP là fallback
actual val platformEsp32FallbackUrls: List<String> =
    listOf("http://greenie-auto.local", "http://192.168.4.1")
