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
        description = "Create a new user account on the World Cup 2026 platform. Requires a display name, a valid email address, and a password. Returns the user profile with ID, name, email, registration timestamp, and a JWT token for subsequent authenticated requests.",
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
        description = "Authenticate with an existing account using email and password. On success, the returned JWT token is automatically stored in the session and will be attached to all subsequent API requests that require authorization. The token is kept in memory until cleared with clear_token.",
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
        description = "Manually set a JWT authentication token obtained from an external source (e.g. from a previous login session or another client). The token is stored in memory and will be sent as a Bearer token in the Authorization header of all subsequent API requests to the World Cup 2026 backend.",
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
        description = "Remove the currently stored JWT authentication token from memory. After calling this tool, subsequent API requests will be sent without any Authorization header, effectively logging out the current user session."
    ) { _ ->
        api.clearToken()
        CallToolResult(content = listOf(TextContent("Token cleared.")))
    }

    mcpServer.addTool(
        name = "get_groups",
        description = "List all 12 World Cup 2026 groups (A through L) with the teams assigned to each group. For each team, displays the team name, flag emoji, and FIFA ranking. This is a lightweight overview without standings data — use get_group with a specific letter to see the full standings table with points, matches played, wins, draws, losses, goals for/against, and goal difference."
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
                            val flag = t.flag ?: ""
                            appendLine("  $flag ${t.name} (FIFA Rank: ${t.fifaRank ?: "?"})")
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
        description = "Retrieve the full standings table for a specific World Cup 2026 group by supplying its letter (A through L). Returns each team's position, name, matches played (MP), wins (W), draws (D), losses (L), goals for (GF), goals against (GA), goal difference (GD), and points (PTS). Also lists all teams in the group with their flags and FIFA rankings.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("name", buildJsonObject {
                    put("type", "string")
                    put("description", "Single letter from A to L identifying the World Cup 2026 group (e.g. A, B, C, ..., L)")
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
                val standings = response.matchTable?.standings
                if (standings.isNullOrEmpty()) {
                    CallToolResult(content = listOf(TextContent("Group not found.")))
                } else {
                    val text = buildString {
                        appendLine("Group ${response.name}:")
                        standings.forEachIndexed { index, row ->
                            appendLine("  ${index + 1}. ${row.teamName} | MP:${row.mp} W:${row.w} D:${row.d} L:${row.l} GF:${row.gf} GA:${row.ga} GD:${row.gd} PTS:${row.pts}")
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
        description = "Retrieve the full list of all 48 national teams participating in the World Cup 2026 tournament. For each team, displays the name, flag emoji, FIFA world ranking, and the group they are assigned to (A-L). Use get_team with a specific team name for detailed information including coach, captain, and players.",
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
                        val flag = team.flag ?: ""
                        appendLine("$flag ${team.name} (FIFA Rank: ${team.fifaRank ?: "?"}) Group: ${team.group ?: "?"}")
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
        description = "Get comprehensive information about a specific national team by its full name (e.g. Brazil, Argentina, Germany). Returns the team's official name, FIFA ranking, flag emoji, group assignment, head coach, and team captain. For the full list of available teams, use get_teams first.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("name", buildJsonObject {
                    put("type", "string")
                    put("description", "Full name of the national team in English, for example Brazil, Argentina, Germany, France, Spain, England, Portugal, Netherlands")
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
                        appendLine(team.name ?: "?")
                        appendLine("FIFA Rank: ${team.fifaRank ?: "?"}")
                        appendLine("Flag: ${team.flag ?: "?"}")
                        appendLine("Group: ${team.group ?: "?"}")
                        append("Coach: ${team.coach ?: "?"} | Captain: ${team.captain ?: "?"}")
                    })))
                }
            }
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    mcpServer.addTool(
        name = "get_games",
        description = "Retrieve the full schedule of all World Cup 2026 matches. Returns each match with its unique ID, home and away team names (with flag emojis), the score if the match has been played, the group or stage, the local date and time, and the match status. Use get_game with a specific match ID for detailed match statistics including scorers, assists, cards, and stadium information.",
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
                        val home = game.homeTeam?.name ?: game.homeTeamLabel ?: "?"
                        val away = game.visitingTeam?.name ?: game.awayTeamLabel ?: "?"
                        val score = if (game.finished == true) " ${game.homeScore}:${game.awayScore}" else " vs "
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
        description = "Get comprehensive details about a specific World Cup 2026 match using its MongoDB ObjectId (e.g. 679c9c8a5749c4077500e001). Returns the full match report including: home and away teams with flags, final score, goal scorers with minute markers, assists, yellow and red cards for each side, match status (finished/live/scheduled), elapsed time, group or stage, matchday number, date, stadium details (name, city, country, capacity), referee, and attendance. Use get_games first to obtain match IDs.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("id", buildJsonObject {
                    put("type", "string")
                    put("description", "24-character MongoDB ObjectId of the match (e.g. 679c9c8a5749c4077500e001). Can be obtained from the output of get_games where each match is listed with its unique ID.")
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
                    val home = game.homeTeam?.name ?: game.homeTeamLabel ?: "Team #${game.homeTeamId}"
                    val away = game.visitingTeam?.name ?: game.awayTeamLabel ?: "Team #${game.awayTeamId}"
                    val status = when (game.finished) {
                        true -> "Finished"
                        false -> {
                            val elapsed = game.timeElapsed
                            if (elapsed != null && elapsed > 0) "Live ${elapsed}'" else "Not started"
                        }
                        else -> "Scheduled"
                    }
                    CallToolResult(content = listOf(TextContent(buildString {
                        appendLine("Match #${game.numericId} [$status]")
                        appendLine("$home ${game.homeScore} : ${game.awayScore} $away")
                        appendLine("Group: ${game.group} | Matchday: ${game.matchday}")
                        appendLine("Date: ${game.localDate}")
                        appendLine("Type: ${game.type}")
                        if (!game.homeScorers.isNullOrEmpty()) {
                            appendLine("Scorers ($home): ${game.homeScorers.joinToString(", ")}")
                        }
                        if (!game.awayScorers.isNullOrEmpty()) {
                            append("Scorers ($away): ${game.awayScorers.joinToString(", ")}")
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
        description = "List all stadiums hosting World Cup 2026 matches. Returns each venue with its full name, city and country location, seating capacity, and number of matches scheduled. Use this to find stadium details like the Maracanã in Rio de Janeiro, MetLife Stadium in New Jersey, or the Azteca in Mexico City.",
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
                        appendLine("${s.name} (${s.city}, ${s.country}) - ${s.capacity} seats")
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
        description = "Make a direct HTTP GET request to any path or full URL on the World Cup 2026 backend server. Useful for accessing undocumented endpoints, debugging, or fetching raw JSON data that is not covered by the other dedicated tools. The path can be relative (e.g. /get/teams) or an absolute URL. The response is returned as raw text without any formatting or parsing.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("path", buildJsonObject {
                    put("type", "string")
                    put("description", "API endpoint path starting with / (e.g. /get/teams, /get/groups) or a full absolute URL to the World Cup 2026 server. The base URL (https://worldcup26.ir) is automatically prepended for relative paths.")
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
