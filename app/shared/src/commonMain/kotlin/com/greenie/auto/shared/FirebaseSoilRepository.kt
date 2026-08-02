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
}
