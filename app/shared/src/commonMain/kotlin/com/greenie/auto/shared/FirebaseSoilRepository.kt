package com.greenie.auto.shared

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val DEFAULT_FIREBASE_URL = "https://greenie-auto-default-rtdb.asia-southeast1.firebasedatabase.app"

class FirebaseSoilRepository(
    private val firebaseUrl: String = DEFAULT_FIREBASE_URL,
) : SoilRepository() {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 4000
            connectTimeoutMillis = 2500
            socketTimeoutMillis = 4000
        }
    }

    override suspend fun fetchData(): Result<SoilData> = runCatching {
        val resp: HttpResponse = client.get("$firebaseUrl/sensor_data.json")
        if (resp.status.isSuccess()) {
            resp.body()
        } else {
            val text = resp.bodyAsText()
            logError(
                "FirebaseSoilRepository",
                "GET /sensor_data.json -> HTTP ${resp.status.value} body=$text"
            )
            throw Exception("HTTP ${resp.status.value} ${resp.status.description}: $text")
        }
    }

    override suspend fun setPump(on: Boolean): Result<Unit> = runCatching {
        val state = if (on) "on" else "off"
        val jsonBody = "{\"state\":\"$state\"}"
        val resp: HttpResponse = client.put("$firebaseUrl/pump_command.json") {
            setBody(jsonBody)
        }
        if (!resp.status.isSuccess()) {
            val text = resp.bodyAsText()
            logError(
                "FirebaseSoilRepository",
                "PUT /pump_command.json {state=$state} -> HTTP ${resp.status.value} body=$text"
            )
            throw Exception("HTTP ${resp.status.value} ${resp.status.description}: $text")
        }
        logInfo("FirebaseSoilRepository", "Gửi lệnh bơm: $state ✅")
        Unit
    }

    override suspend fun fetchMonthlyStats(): Result<MonthlyStats> = runCatching {
        val resp: HttpResponse = client.get("$firebaseUrl/monthly_stats.json")
        if (!resp.status.isSuccess()) {
            val text = resp.bodyAsText()
            throw Exception("HTTP ${resp.status.value} ${resp.status.description}: $text")
        }

        val body = resp.bodyAsText()
        if (body == "null") {
            return@runCatching MonthlyStats(monthKey = "", days = emptyList())
        }

        val root = Json.parseToJsonElement(body).jsonObject
        if (root.isEmpty()) {
            return@runCatching MonthlyStats(monthKey = "", days = emptyList())
        }

        val latestMonth = root.keys.maxOrNull().orEmpty()
        val monthObj = root[latestMonth]?.jsonObject ?: JsonObject(emptyMap())

        val days = monthObj.entries
            .mapNotNull { (day, value) ->
                val obj = value.jsonObjectOrNull() ?: return@mapNotNull null
                MonthlyDayStat(
                    day = day,
                    avgTemp = obj.number("avg_temp"),
                    avgSoil = if (obj.containsKey("avg_soil")) obj.number("avg_soil") else -1f,
                    pumpOnCount = obj.int("pump_on_count"),
                    tempSampleCount = obj.int("temp_samples"),
                )
            }
            .sortedBy { it.day }

        MonthlyStats(monthKey = latestMonth, days = days)
    }

    override suspend fun fetchPumpHistory(): Result<PumpHistory> = runCatching {
        val resp: HttpResponse = client.get("$firebaseUrl/pump_history.json")
        if (!resp.status.isSuccess()) {
            val text = resp.bodyAsText()
            throw Exception("HTTP ${resp.status.value} ${resp.status.description}: $text")
        }
        val body = resp.bodyAsText()
        if (body == "null") return@runCatching PumpHistory(entries = emptyList())

        val root = Json.parseToJsonElement(body).jsonObject
        val entries = mutableListOf<PumpLogEntry>()

        root.entries.sortedByDescending { it.key }.take(7).forEach { (date, dateVal) ->
            val dayObj = dateVal.jsonObjectOrNull() ?: return@forEach
            dayObj.entries.sortedByDescending { it.key }.forEach { (_, sessionVal) ->
                val session = sessionVal.jsonObjectOrNull() ?: return@forEach
                entries.add(PumpLogEntry(
                    date = date,
                    startTime = session["start"]?.jsonPrimitive?.content ?: "--:--",
                    endTime = session["end"]?.jsonPrimitive?.content ?: "--:--",
                    durationSeconds = session.int("duration_s"),
                ))
            }
        }

        PumpHistory(entries = entries)
    }
}

private fun JsonObject.number(key: String): Float {
    val primitive = this[key]?.jsonPrimitive as? JsonPrimitive ?: return 0f
    return primitive.floatOrNull ?: primitive.intOrNull?.toFloat() ?: 0f
}

private fun JsonObject.int(key: String): Int {
    val primitive = this[key]?.jsonPrimitive as? JsonPrimitive ?: return 0
    return primitive.intOrNull ?: primitive.floatOrNull?.toInt() ?: 0
}

private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull(): JsonObject? =
    runCatching { this.jsonObject }.getOrNull()
