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
        name = "register",
        description = "Register a new user on the World Cup 2026 platform",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("name", buildJsonObject {
                    put("type", "string")
                    put("description", "User display name")
                })
                put("email", buildJsonObject {
                    put("type", "string")
                    put("description", "User email address")
                })
                put("password", buildJsonObject {
                    put("type", "string")
                    put("description", "Account password")
                })
            },
            required = listOf("name", "email", "password")
        )
    ) { request ->
        val name = request.arguments?.get("name")?.jsonPrimitive?.content ?: ""
        val email = request.arguments?.get("email")?.jsonPrimitive?.content ?: ""
        val password = request.arguments?.get("password")?.jsonPrimitive?.content ?: ""
        try {
            val response = api.register(name, email, password)
            if (response.error != null) {
                CallToolResult(content = listOf(TextContent("Error: ${response.error}")))
            } else {
                CallToolResult(content = listOf(TextContent(buildString {
                    appendLine("Registration successful!")
                    appendLine("User: ${response.user?.name ?: ""}")
                    appendLine("Email: ${response.user?.email ?: ""}")
                    response.token?.let { appendLine("Token: $it") }
                }.trimEnd())))
            }
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    mcpServer.addTool(
        name = "login",
        description = "Login to the World Cup 2026 platform and save the auth token",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("email", buildJsonObject {
                    put("type", "string")
                    put("description", "User email address")
                })
                put("password", buildJsonObject {
                    put("type", "string")
                    put("description", "Account password")
                })
            },
            required = listOf("email", "password")
        )
    ) { request ->
        val email = request.arguments?.get("email")?.jsonPrimitive?.content ?: ""
        val password = request.arguments?.get("password")?.jsonPrimitive?.content ?: ""
        try {
            val response = api.login(email, password)
            if (response.error != null) {
                CallToolResult(content = listOf(TextContent("Error: ${response.error}")))
            } else {
                response.token?.let { api.setToken(it) }
                CallToolResult(content = listOf(TextContent(buildString {
                    appendLine("Login successful!")
                    response.token?.let { appendLine("Token: ${it.take(50)}...") }
                }.trimEnd())))
            }
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    mcpServer.addTool(
        name = "set_token",
        description = "Set JWT token manually for authenticated requests",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("token", buildJsonObject {
                    put("type", "string")
                    put("description", "JWT token string")
                })
            },
            required = listOf("token")
        )
    ) { request ->
        val token = request.arguments?.get("token")?.jsonPrimitive?.content ?: ""
        api.setToken(token)
        CallToolResult(content = listOf(TextContent("Token saved.")))
    }

    mcpServer.addTool(
        name = "clear_token",
        description = "Clear the stored JWT token"
    ) { _ ->
        api.clearToken()
        CallToolResult(content = listOf(TextContent("Token cleared.")))
    }

    mcpServer.addTool(
        name = "get_groups",
        description = "List all World Cup 2026 groups with standings table"
    ) { _ ->
        try {
            val response = api.getGroups()
            if (response.error != null) {
                CallToolResult(content = listOf(TextContent("Error: ${response.error}")))
            } else if (response.groups.isNullOrEmpty()) {
                CallToolResult(content = listOf(TextContent("No groups found.")))
            } else {
                val text = buildString {
                    response.groups.forEach { group ->
                        appendLine("Group ${group.name}:")
                        group.teams?.forEach { t ->
                            appendLine("  Team #${t.teamId} | P:${t.mp} W:${t.w} D:${t.d} L:${t.l} GF:${t.gf} GA:${t.ga} GD:${t.gd} PTS:${t.pts}")
                        }
                        appendLine()
                    }
                    append("Total groups: ${response.groups.size}")
                }
                CallToolResult(content = listOf(TextContent(text)))
            }
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    mcpServer.addTool(
        name = "get_group",
        description = "Get a specific World Cup 2026 group by name (A-L)",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("name", buildJsonObject {
                    put("type", "string")
                    put("description", "Group name letter (A-L)")
                })
            },
            required = listOf("name")
        )
    ) { request ->
        val name = request.arguments?.get("name")?.jsonPrimitive?.content ?: ""
        try {
            val response = api.getGroupByName(name)
            if (response.error != null) {
                CallToolResult(content = listOf(TextContent("Error: ${response.error}")))
            } else {
                val group = response.group
                if (group == null) {
                    CallToolResult(content = listOf(TextContent("Group not found.")))
                } else {
                    val text = buildString {
                        appendLine("Group ${group.name}:")
                        group.teams?.forEach { t ->
                            appendLine("  Team #${t.teamId} | P:${t.mp} W:${t.w} D:${t.d} L:${t.l} GF:${t.gf} GA:${t.ga} GD:${t.gd} PTS:${t.pts}")
                        }
                    }
                    CallToolResult(content = listOf(TextContent(text.trimEnd())))
                }
            }
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    mcpServer.addTool(
        name = "get_teams",
        description = "List all teams participating in World Cup 2026",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
            },
            required = listOf()
        )
    ) { _ ->
        try {
            val response = api.getTeams()
            if (response.error != null) {
                CallToolResult(content = listOf(TextContent("Error: ${response.error}")))
            } else if (response.teams.isNullOrEmpty()) {
                CallToolResult(content = listOf(TextContent("No teams found.")))
            } else {
                val text = buildString {
                    response.teams.forEach { team ->
                        val name = team.nameEn ?: team.nameFa ?: "?"
                        val group = team.group ?: "?"
                        appendLine("#${team.numericId} $name (FIFA: ${team.fifaCode ?: "?"}) Group: $group")
                    }
                    append("Total teams: ${response.teams.size}")
                }
                CallToolResult(content = listOf(TextContent(text)))
            }
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    mcpServer.addTool(
        name = "get_team",
        description = "Get detailed information about a specific team by name",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("name", buildJsonObject {
                    put("type", "string")
                    put("description", "Team name (e.g. Brazil, Argentina, Germany)")
                })
            },
            required = listOf("name")
        )
    ) { request ->
        val name = request.arguments?.get("name")?.jsonPrimitive?.content ?: ""
        try {
            val response = api.getTeamByName(name)
            if (response.error != null) {
                CallToolResult(content = listOf(TextContent("Error: ${response.error}")))
            } else {
                val team = response.team
                if (team == null) {
                    CallToolResult(content = listOf(TextContent("Team not found.")))
                } else {
                    CallToolResult(content = listOf(TextContent(buildString {
                        appendLine("${team.nameEn} / ${team.nameFa}")
                        appendLine("ID: #${team.numericId}")
                        appendLine("FIFA Code: ${team.fifaCode ?: "?"}")
                        appendLine("Flag: ${team.flag ?: "?"}")
                        append("Group: ${team.group ?: "?"}")
                    })))
                }
            }
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    mcpServer.addTool(
        name = "get_games",
        description = "List all World Cup 2026 matches",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
            },
            required = listOf()
        )
    ) { _ ->
        try {
            val response = api.getGames()
            if (response.error != null) {
                CallToolResult(content = listOf(TextContent("Error: ${response.error}")))
            } else if (response.games.isNullOrEmpty()) {
                CallToolResult(content = listOf(TextContent("No games found.")))
            } else {
                val text = buildString {
                    response.games.forEach { game ->
                        val home = game.homeTeamNameEn ?: game.homeTeamLabel ?: "?"
                        val away = game.awayTeamNameEn ?: game.awayTeamLabel ?: "?"
                        val score = if (game.finished == "TRUE") " ${game.homeScore}:${game.awayScore}" else " vs "
                        appendLine("${game.numericId}. $home$score$away [${game.group}] ${game.localDate}")
                    }
                    append("Total games: ${response.games.size}")
                }
                CallToolResult(content = listOf(TextContent(text)))
            }
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    mcpServer.addTool(
        name = "get_game",
        description = "Get detailed information about a specific match by MongoDB ID",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("id", buildJsonObject {
                    put("type", "string")
                    put("description", "MongoDB ID of the game (e.g. 679c9c8a5749c4077500e001)")
                })
            },
            required = listOf("id")
        )
    ) { request ->
        val id = request.arguments?.get("id")?.jsonPrimitive?.content ?: ""
        try {
            val response = api.getGameById(id)
            if (response.error != null) {
                CallToolResult(content = listOf(TextContent("Error: ${response.error}")))
            } else {
                val game = response.game
                if (game == null) {
                    CallToolResult(content = listOf(TextContent("Game not found.")))
                } else {
                    val home = game.homeTeamNameEn ?: game.homeTeamLabel ?: "Team #${game.homeTeamId}"
                    val away = game.awayTeamNameEn ?: game.awayTeamLabel ?: "Team #${game.awayTeamId}"
                    val status = when (game.finished) {
                        "TRUE" -> "Finished"
                        "FALSE" -> game.timeElapsed?.let {
                            when (it.lowercase()) {
                                "notstarted" -> "Not started"
                                "finished" -> "Finished"
                                else -> "Live $it'"
                            }
                        } ?: "Scheduled"
                        else -> "Scheduled"
                    }
                    CallToolResult(content = listOf(TextContent(buildString {
                        appendLine("Match #${game.numericId} [$status]")
                        appendLine("$home ${game.homeScore} : ${game.awayScore} $away")
                        appendLine("Group: ${game.group} | Matchday: ${game.matchday}")
                        appendLine("Date: ${game.localDate}")
                        appendLine("Type: ${game.type}")
                        if (game.homeScorers != null && game.homeScorers != "null") {
                            appendLine("Scorers ($home): ${game.homeScorers}")
                        }
                        if (game.awayScorers != null && game.awayScorers != "null") {
                            append("Scorers ($away): ${game.awayScorers}")
                        }
                    })))
                }
            }
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    mcpServer.addTool(
        name = "get_stadiums",
        description = "List all World Cup 2026 stadiums with location and capacity",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
            },
            required = listOf()
        )
    ) { _ ->
        try {
            val response = api.getStadiums()
            if (response.error != null) {
                CallToolResult(content = listOf(TextContent("Error: ${response.error}")))
            } else if (response.stadiums.isNullOrEmpty()) {
                CallToolResult(content = listOf(TextContent("No stadiums found.")))
            } else {
                val text = buildString {
                    response.stadiums.forEach { s ->
                        appendLine("${s.nameEn} (${s.cityEn}, ${s.countryEn}) - ${s.capacity} seats")
                    }
                    append("Total stadiums: ${response.stadiums.size}")
                }
                CallToolResult(content = listOf(TextContent(text)))
            }
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    mcpServer.addTool(
        name = "raw_get",
        description = "Make a raw GET request to any API endpoint on the World Cup 2026 server",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("path", buildJsonObject {
                    put("type", "string")
                    put("description", "API path (e.g. /get/teams) or full URL")
                })
            },
            required = listOf("path")
        )
    ) { request ->
        val path = request.arguments?.get("path")?.jsonPrimitive?.content ?: ""
        try {
            val raw = api.rawGet(path)
            CallToolResult(content = listOf(TextContent(raw)))
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
