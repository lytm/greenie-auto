package com.greenie.auto.shared

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

// ─── Context storage — set từ MainActivity.onCreate() ───────────
internal var androidApplicationContext: Context? = null

fun initAndroidContext(ctx: Context) {
    androidApplicationContext = ctx.applicationContext
}

// ─── NSD discovery ───────────────────────────────────────────────
actual suspend fun discoverLocalEsp32Url(): String? =
    withTimeoutOrNull(2000L) {
        val ctx = androidApplicationContext ?: return@withTimeoutOrNull null
        val nsdManager = ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: return@withTimeoutOrNull null

        suspendCancellableCoroutine { cont ->
            var discoveryListener: NsdManager.DiscoveryListener? = null

            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo?, errorCode: Int) {
                    logError("NSD", "Resolve thất bại, code=$errorCode")
                    if (cont.isActive) cont.resume(null)
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    val host = info.host?.hostAddress
                    val port = if (info.port > 0) info.port else 80
                    discoveryListener?.let {
                        try { nsdManager.stopServiceDiscovery(it) } catch (_: Exception) {}
                    }
                    if (host != null && cont.isActive) {
                        logInfo("NSD", "Tìm thấy ESP32 @ $host:$port")
                        cont.resume("http://$host${if (port != 80) ":$port" else ""}")
                    } else if (cont.isActive) {
                        cont.resume(null)
                    }
                }
            }

            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    logError("NSD", "Không bắt đầu được discovery, code=$errorCode")
                    if (cont.isActive) cont.resume(null)
                }
                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                override fun onDiscoveryStarted(serviceType: String?) {
                    logInfo("NSD", "Đang tìm ESP32 trên mạng nội bộ…")
                }
                override fun onDiscoveryStopped(serviceType: String?) {}
                override fun onServiceFound(info: NsdServiceInfo) {
                    if (info.serviceName.contains("greenie", ignoreCase = true)) {
                        logInfo("NSD", "Tìm thấy service: ${info.serviceName}, đang resolve…")
                        try {
                            @Suppress("DEPRECATION")
                            nsdManager.resolveService(info, resolveListener)
                        } catch (e: Exception) {
                            logError("NSD", "resolveService thất bại: ${e.message}")
                        }
                    }
                }
                override fun onServiceLost(info: NsdServiceInfo) {}
            }

            try {
                nsdManager.discoverServices(
                    "_http._tcp",
                    NsdManager.PROTOCOL_DNS_SD,
                    discoveryListener
                )
            } catch (e: Exception) {
                logError("NSD", "discoverServices thất bại: ${e.message}")
                if (cont.isActive) cont.resume(null)
            }

            cont.invokeOnCancellation {
                try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {}
            }
        }
    }

// Android không resolve .local qua OkHttp → chỉ dùng AP IP làm fallback
actual val platformEsp32FallbackUrls: List<String> = listOf("http://192.168.4.1")
