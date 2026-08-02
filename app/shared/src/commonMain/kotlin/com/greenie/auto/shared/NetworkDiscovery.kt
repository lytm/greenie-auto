package com.greenie.auto.shared

/**
 * Tìm URL của ESP32 trong cùng WiFi nội bộ.
 * - Android: dùng NsdManager (Bonjour/mDNS) → trả về "http://<real-ip>:80"
 * - iOS: trả về "http://greenie-auto.local" (URLSession tự resolve qua Bonjour)
 * Trả về null nếu không tìm được trong 2 giây.
 */
expect suspend fun discoverLocalEsp32Url(): String?

/**
 * Danh sách URL fallback khi discovery thất bại (platform-specific).
 * - Android: chỉ thử AP mode IP (greenie-auto.local không resolve trên Android)
 * - iOS: thử greenie-auto.local trước, sau đó AP mode IP
 */
expect val platformEsp32FallbackUrls: List<String>
