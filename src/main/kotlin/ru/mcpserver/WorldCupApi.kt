package ru.mcpserver

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class WorldCupApi(private val baseUrl: String = "https://worldcup26.ir") {
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

    suspend fun getGroups(): GroupsResponse =
        client.get("$baseUrl/get/groups").body()

    suspend fun getGroupByName(name: String): GroupWrapper =
        client.get("$baseUrl/get/group") {
            parameter("name", name)
        }.body()

    suspend fun getTeams(group: String? = null): TeamsResponse =
        client.get("$baseUrl/get/teams") {
            group?.let { parameter("group", it) }
        }.body()

    suspend fun getTeamById(id: String): TeamWrapper =
        client.get("$baseUrl/get/team/$id").body()

    suspend fun getTeamByName(name: String): TeamWrapper =
        client.get("$baseUrl/get/team") {
            parameter("name", name)
        }.body()

    suspend fun getGames(): GamesResponse =
        client.get("$baseUrl/get/games").body()

    suspend fun getGameById(id: String): GameWrapper =
        client.get("$baseUrl/get/game/$id").body()

    suspend fun getStadiums(): StadiumsResponse =
        client.get("$baseUrl/get/stadiums").body()

    suspend fun getStadiumById(id: String): StadiumWrapper =
        client.get("$baseUrl/get/stadium/$id").body()

    fun close() {
        client.close()
    }
}
