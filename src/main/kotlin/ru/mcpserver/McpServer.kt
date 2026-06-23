package ru.mcpserver

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.*

fun main() {
    val api = WorldCupApi()

    val outputJson = Json {
        prettyPrint = true
    }

    val mcpServer = Server(
        serverInfo = Implementation(
            name = "worldcup-mcp-server",
            version = "1.0.0"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
            ),
        )
    )

    mcpServer.addTool(
        name = "get_groups",
        description = "List all 12 World Cup 2026 groups (A through L) with team standings including matches played, wins, draws, losses, goals for/against, goal difference, and points."
    ) { _ ->
        try {
            val response = api.getGroups()
            val text = outputJson.encodeToString(response)
            CallToolResult(content = listOf(TextContent(text)))
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    mcpServer.addTool(
        name = "get_group",
        description = "Retrieve the full standings for a specific World Cup 2026 group by supplying its letter (A through L). Returns the group with teams, matches played, wins, draws, losses, goals for/against, goal difference, and points.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("name", buildJsonObject {
                    put("type", "string")
                    put("description", "Group letter (A-L)")
                })
            },
            required = listOf("name")
        )
    ) { request ->
        val name = request.arguments?.get("name")?.jsonPrimitive?.content ?: ""
        try {
            val response = api.getGroupByName(name)
            val text = outputJson.encodeToString(response)
            CallToolResult(content = listOf(TextContent(text)))
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    mcpServer.addTool(
        name = "get_teams",
        description = "Retrieve the full list of all 48 national teams participating in the World Cup 2026 tournament. Optionally filter by group letter (A-L). Returns team names in English and Persian, flags, FIFA codes, and group assignments.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("group", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional filter by group letter (A-L)")
                })
            },
            required = listOf()
        )
    ) { request ->
        val group = request.arguments?.get("group")?.jsonPrimitive?.contentOrNull
        try {
            val response = api.getTeams(group)
            val text = outputJson.encodeToString(response)
            CallToolResult(content = listOf(TextContent(text)))
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    mcpServer.addTool(
        name = "get_team",
        description = "Retrieve detailed information about a specific national team. Provide either the team's MongoDB ID (e.g. 679c9c6b5749c4077500ea09) or the team name in English (e.g. Brazil). Returns the team's names in English and Persian, flag, FIFA code, ISO2 code, and group assignment.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("id", buildJsonObject {
                    put("type", "string")
                    put("description", "Team MongoDB ID (e.g. 679c9c6b5749c4077500ea09)")
                })
                put("name", buildJsonObject {
                    put("type", "string")
                    put("description", "Team name in English (e.g. Brazil)")
                })
            },
            required = listOf()
        )
    ) { request ->
        try {
            val id = request.arguments?.get("id")?.jsonPrimitive?.contentOrNull
            val name = request.arguments?.get("name")?.jsonPrimitive?.contentOrNull
            val response = if (id != null) {
                api.getTeamById(id)
            } else if (name != null) {
                api.getTeamByName(name)
            } else {
                return@addTool CallToolResult(content = listOf(TextContent("Error: Provide either 'id' or 'name' parameter.")))
            }
            val text = outputJson.encodeToString(response)
            CallToolResult(content = listOf(TextContent(text)))
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    mcpServer.addTool(
        name = "get_games",
        description = "Retrieve the full schedule of all World Cup 2026 matches including group stage and knockout rounds. Returns each match with teams, scores, date, group, stadium, and status."
    ) { _ ->
        try {
            val response = api.getGames()
            val text = outputJson.encodeToString(response)
            CallToolResult(content = listOf(TextContent(text)))
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    mcpServer.addTool(
        name = "get_game",
        description = "Retrieve detailed information about a specific World Cup 2026 match by its MongoDB ObjectId (e.g. 679c9c8a5749c4077500e004). Returns the match with teams, score, scorers, group, date, stadium, and status.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("id", buildJsonObject {
                    put("type", "string")
                    put("description", "24-character MongoDB ObjectId of the match (e.g. 679c9c8a5749c4077500e004)")
                })
            },
            required = listOf("id")
        )
    ) { request ->
        val id = request.arguments?.get("id")?.jsonPrimitive?.content ?: ""
        try {
            val response = api.getGameById(id)
            val text = outputJson.encodeToString(response)
            CallToolResult(content = listOf(TextContent(text)))
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    mcpServer.addTool(
        name = "get_stadiums",
        description = "List all 16 stadiums hosting World Cup 2026 matches across the United States, Canada, and Mexico. Returns each venue with names in English and Persian, FIFA name, city, country, capacity, and region."
    ) { _ ->
        try {
            val response = api.getStadiums()
            val text = outputJson.encodeToString(response)
            CallToolResult(content = listOf(TextContent(text)))
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    mcpServer.addTool(
        name = "get_stadium",
        description = "Retrieve detailed information about a specific World Cup 2026 stadium by its numeric ID (e.g. 1 for Estadio Azteca). Returns the venue with names in English and Persian, FIFA name, city, country, capacity, region, and creation date.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("id", buildJsonObject {
                    put("type", "string")
                    put("description", "Stadium numeric ID (e.g. 1)")
                })
            },
            required = listOf("id")
        )
    ) { request ->
        val id = request.arguments?.get("id")?.jsonPrimitive?.content ?: ""
        try {
            val response = api.getStadiumById(id)
            val text = outputJson.encodeToString(response)
            CallToolResult(content = listOf(TextContent(text)))
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    println("Starting World Cup MCP Server on port 4455...")
    embeddedServer(CIO, host = "127.0.0.1", port = 4455) {
        mcpStreamableHttp {
            mcpServer
        }
    }.start(wait = true)
}
