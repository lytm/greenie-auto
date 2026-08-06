package com.greenie.auto.shared

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

enum class ConnectionSource(val label: String) {
    LocalWifi("ESP32 WiFi"),
    Firebase("Firebase"),
}

class LocalEsp32Repository : SoilRepository() {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 2500
            connectTimeoutMillis = 2500
            socketTimeoutMillis = 2500
        }
    }

    // URL tìm được qua NSD/mDNS (cache lại để không phải discover mỗi lần)
    private var cachedDiscoveredUrl: String? = null
    private var discoveryAttempted = false

    var lastReachableBaseUrl: String? = null
        private set

    var lastFailure: String? = null
        private set

    /** Lấy danh sách URL theo thứ tự ưu tiên:
     *  1. URL từ NSD/mDNS discovery (chính xác nhất, IP thật của ESP32)
     *  2. URL đã kết nối thành công lần trước
     *  3. Platform fallback URLs (greenie-auto.local trên iOS, 192.168.4.1 AP mode)
     */
    private suspend fun resolveUrls(): List<String> {
        if (!discoveryAttempted) {
            discoveryAttempted = true
            cachedDiscoveredUrl = discoverLocalEsp32Url()
            logInfo("LocalEsp32Repository", "Discovery → ${cachedDiscoveredUrl ?: "không tìm được"}")
        }
        return buildList {
            cachedDiscoveredUrl?.let { add(it) }
            lastReachableBaseUrl?.let { if (it !in this) add(it) }
            platformEsp32FallbackUrls.forEach { if (it !in this) add(it) }
        }
    }

    override suspend fun fetchData(): Result<SoilData> = attempt("GET /api/data") { baseUrl ->
        val resp: HttpResponse = client.get("$baseUrl/api/data")
        if (resp.status.isSuccess()) {
            resp.body()
        } else {
            val text = resp.bodyAsText()
            throw Exception("HTTP ${resp.status.value} ${resp.status.description}: $text")
        }
    }

    override suspend fun setPump(on: Boolean): Result<Unit> = attempt("GET /api/pump") { baseUrl ->
        val state = if (on) "on" else "off"
        val resp: HttpResponse = client.get("$baseUrl/api/pump") {
            parameter("state", state)
        }
        if (!resp.status.isSuccess()) {
            val text = resp.bodyAsText()
            throw Exception("HTTP ${resp.status.value} ${resp.status.description}: $text")
        }
        Unit
    }

    override suspend fun fetchWaterSchedule(): Result<WaterSchedule> = attempt("GET /api/schedule") { baseUrl ->
        val resp: HttpResponse = client.get("$baseUrl/api/schedule")
        if (resp.status.isSuccess()) {
            resp.body()
        } else {
            val text = resp.bodyAsText()
            throw Exception("HTTP ${resp.status.value} ${resp.status.description}: $text")
        }
    }

    override suspend fun setWaterSchedule(schedule: WaterSchedule): Result<Unit> = attempt("GET /api/schedule") { baseUrl ->
        val resp: HttpResponse = client.get("$baseUrl/api/schedule") {
            parameter("enabled", if (schedule.enabled) 1 else 0)
            parameter("duration_min", schedule.durationMin.coerceIn(1, 60))
            parameter("times_csv", schedule.timesCsv)
            parameter("weekdays_csv", schedule.weekdaysCsv)
        }
        if (!resp.status.isSuccess()) {
            val text = resp.bodyAsText()
            throw Exception("HTTP ${resp.status.value} ${resp.status.description}: $text")
        }
        Unit
    }

    private suspend fun <T> attempt(
        action: String,
        block: suspend (String) -> T,
    ): Result<T> {
        val urls = resolveUrls()
        var lastError: Throwable? = null

        for (baseUrl in urls) {
            try {
                val result = block(baseUrl)
                // Nếu URL discovery cũ thất bại nhưng URL khác thành công → reset cache
                if (baseUrl != cachedDiscoveredUrl && cachedDiscoveredUrl != null) {
                    logInfo("LocalEsp32Repository", "Discovery cache không còn hợp lệ, reset.")
                    cachedDiscoveredUrl = null
                    discoveryAttempted = false
                }
                lastReachableBaseUrl = baseUrl
                lastFailure = null
                return Result.success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
                logError("LocalEsp32Repository", "$action @ $baseUrl → ${e.message}")
            }
        }

        // Tất cả URL đều thất bại → reset cache để lần sau discovery lại
        cachedDiscoveredUrl = null
        discoveryAttempted = false

        val message = lastError?.message ?: "Không kết nối được ESP32"
        lastFailure = message
        return Result.failure(Exception(message))
    }
}

class AutoSoilRepository(
    private val localRepository: LocalEsp32Repository = LocalEsp32Repository(),
    private val firebaseRepository: FirebaseSoilRepository = FirebaseSoilRepository(),
) : SoilRepository() {

    var lastSuccessfulSource: ConnectionSource? = null
        private set

    var lastFailureSummary: String? = null
        private set

    override suspend fun fetchData(): Result<SoilData> {
        val localResult = localRepository.fetchData()
        if (localResult.isSuccess) {
            lastSuccessfulSource = ConnectionSource.LocalWifi
            lastFailureSummary = null
            return localResult
        }

        val firebaseResult = firebaseRepository.fetchData()
        if (firebaseResult.isSuccess) {
            lastSuccessfulSource = ConnectionSource.Firebase
            lastFailureSummary = "ESP32 không phản hồi qua WiFi, đang dùng Firebase."
            return firebaseResult
        }

        val localError = localResult.exceptionOrNull()?.message ?: localRepository.lastFailure ?: "Không kết nối được ESP32"
        val firebaseError = firebaseResult.exceptionOrNull()?.message ?: "Firebase không phản hồi"
        lastFailureSummary = buildString {
            appendLine("Không kết nối được ESP32 qua WiFi.")
            appendLine("Firebase cũng không phản hồi hoặc máy đang mất mạng.")
            appendLine()
            appendLine("ESP32: $localError")
            appendLine("Firebase: $firebaseError")
        }
        return Result.failure(Exception(lastFailureSummary))
    }

    override suspend fun setPump(on: Boolean): Result<Unit> {
        val preferredOrder = when (lastSuccessfulSource) {
            ConnectionSource.LocalWifi -> listOf(localRepository, firebaseRepository)
            ConnectionSource.Firebase -> listOf(firebaseRepository, localRepository)
            null -> listOf(localRepository, firebaseRepository)
        }

        var lastError: Throwable? = null

        for (repository in preferredOrder) {
            val result = repository.setPump(on)
            if (result.isSuccess) {
                lastSuccessfulSource = if (repository === localRepository) {
                    ConnectionSource.LocalWifi
                } else {
                    ConnectionSource.Firebase
                }
                lastFailureSummary = null
                return result
            }

            lastError = result.exceptionOrNull()
        }

        val message = lastError?.message ?: "Không gửi được lệnh bơm"
        lastFailureSummary = message
        return Result.failure(Exception(message))
    }

    override suspend fun fetchWaterSchedule(): Result<WaterSchedule> {
        val localResult = localRepository.fetchWaterSchedule()
        if (localResult.isSuccess) {
            lastSuccessfulSource = ConnectionSource.LocalWifi
            return localResult
        }

        val firebaseResult = firebaseRepository.fetchWaterSchedule()
        if (firebaseResult.isSuccess) {
            lastSuccessfulSource = ConnectionSource.Firebase
            return firebaseResult
        }

        return Result.failure(
            Exception(
                localResult.exceptionOrNull()?.message
                    ?: firebaseResult.exceptionOrNull()?.message
                    ?: "Không đọc được lịch tưới"
            )
        )
    }

    override suspend fun setWaterSchedule(schedule: WaterSchedule): Result<Unit> {
        val preferredOrder = when (lastSuccessfulSource) {
            ConnectionSource.LocalWifi -> listOf(localRepository, firebaseRepository)
            ConnectionSource.Firebase -> listOf(firebaseRepository, localRepository)
            null -> listOf(localRepository, firebaseRepository)
        }

        var lastError: Throwable? = null
        for (repository in preferredOrder) {
            val result = repository.setWaterSchedule(schedule)
            if (result.isSuccess) {
                lastSuccessfulSource = if (repository === localRepository) ConnectionSource.LocalWifi else ConnectionSource.Firebase
                return result
            }
            lastError = result.exceptionOrNull()
        }

        return Result.failure(Exception(lastError?.message ?: "Không lưu được lịch tưới"))
    }

    override suspend fun fetchMonthlyStats(): Result<MonthlyStats> {
        val result = firebaseRepository.fetchMonthlyStats()
        if (result.isFailure) {
            logError("AutoSoilRepository", "fetchMonthlyStats lỗi: ${result.exceptionOrNull()?.message}")
        }
        return result
    }

    override suspend fun fetchPumpHistory(): Result<PumpHistory> {
        val result = firebaseRepository.fetchPumpHistory()
        if (result.isFailure) {
            logError("AutoSoilRepository", "fetchPumpHistory lỗi: ${result.exceptionOrNull()?.message}")
        }
        return result
    }
}