package ru.mcpserver

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class WorldCupApi(private val baseUrl: String = "https://worldcup26.ir") {
    private var token: String? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
        }
    }

    fun setToken(newToken: String) {
        token = newToken
    }

    fun clearToken() {
        token = null
    }

    private fun HttpRequestBuilder.applyAuth() {
        token?.let { header("Authorization", "Bearer $it") }
    }

    suspend fun register(name: String, email: String, password: String): AuthResponse {
        return client.post("$baseUrl/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(AuthRequest(name = name, email = email, password = password))
        }.body()
    }

    suspend fun login(email: String, password: String): AuthResponse {
        return client.post("$baseUrl/auth/authenticate") {
            contentType(ContentType.Application.Json)
            setBody(AuthRequest(email = email, password = password))
        }.body()
    }

    suspend fun getGroups(): GroupsResponse {
        return client.get("$baseUrl/get/groups") { applyAuth() }.body()
    }

    suspend fun getGroupByName(name: String): GroupWrapper {
        return client.get("$baseUrl/get/group") {
            applyAuth()
            parameter("name", name)
        }.body()
    }

    suspend fun getTeams(): TeamsResponse {
        return client.get("$baseUrl/get/teams") { applyAuth() }.body()
    }

    suspend fun getTeamByName(name: String): TeamWrapper {
        return client.get("$baseUrl/get/team") {
            applyAuth()
            parameter("name", name)
        }.body()
    }

    suspend fun getGames(): GamesResponse {
        return client.get("$baseUrl/get/games") { applyAuth() }.body()
    }

    suspend fun getGameById(id: String): GameWrapper {
        return client.get("$baseUrl/get/game/$id") { applyAuth() }.body()
    }

    suspend fun getStadiums(): StadiumsResponse {
        return client.get("$baseUrl/get/stadiums") { applyAuth() }.body()
    }

    suspend fun rawGet(path: String): String {
        val url = if (path.startsWith("http")) path else "$baseUrl$path"
        return client.get(url) { applyAuth() }.bodyAsText()
    }

    fun close() {
        client.close()
    }
}
