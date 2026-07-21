package com.greenie.auto.shared

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

open class SoilRepository(private val baseUrl: String) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    open suspend fun fetchData(): Result<SoilData> = runCatching {
        val resp: HttpResponse = client.get("$baseUrl/api/data")
        if (resp.status.isSuccess()) {
            resp.body()
        } else {
            val text = resp.bodyAsText()
            logError("SoilRepository", "In response from `$baseUrl/api/data` -> status=${resp.status}, contentType=${resp.contentType()} body=$text")
            throw Exception("HTTP ${resp.status.value} ${resp.status.description}: $text")
        }
    }

    open suspend fun setPump(on: Boolean): Result<Unit> = runCatching {
        val state = if (on) "on" else "off"
        val resp: HttpResponse = client.get("$baseUrl/api/pump") { parameter("state", state) }
        if (!resp.status.isSuccess()) {
            val text = resp.bodyAsText()
            logError("SoilRepository", "In response from `$baseUrl/api/pump` -> status=${resp.status}, contentType=${resp.contentType()} body=$text")
            throw Exception("HTTP ${resp.status.value} ${resp.status.description}: $text")
        }
        Unit
    }
}
