package ru.mcpserver.api

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import ru.mcpserver.common.RawSnapshot

fun main() {
    val api = WorldCupApi()

    val outputJson = Json {
        prettyPrint = true
    }

    val mcpServer = Server(
        serverInfo = Implementation(
            name = "worldcup-api-server",
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
        description = """
Returns ALL 12 World Cup 2026 groups (A through L) at once. 

RESPONSE FORMAT (JSON object with one key):
  {
    "groups": [
      {
        "_id": string (MongoDB ObjectId),
        "name": string (group letter, e.g. "A", "B", ..., "L"),
        "teams": [                                              // 4 teams per group, sorted by points descending
          {
            "team_id": string (MongoDB ObjectId of the team),
            "mp":       string (matches played),
            "w":        string (wins),
            "l":        string (losses),
            "d":        string (draws),
            "pts":      string (points),
            "gf":       string (goals for / scored),
            "ga":       string (goals against / conceded),
            "gd":       string (goal difference),
            "_id":      string (MongoDB ObjectId of this standing record)
          }
        ],
        "createdAt": string (ISO date) | null,
        "__v":       int | null,
        "updatedAt": string (ISO date) | null
      }
    ]
  }

USE WHEN: you need the COMPLETE tournament table (all groups at once) to compare standings across groups, determine qualification scenarios, or build a full leaderboard. For a SINGLE group, use get_group instead.
        """.trimIndent()
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
        description = """
Returns the full standings for ONE specific World Cup 2026 group.

PARAMETERS:
  - "name" (string, REQUIRED): group letter — one of A, B, C, D, E, F, G, H, I, J, K, L

RESPONSE FORMAT (JSON object with one key):
  {
    "group": {
      "_id":   string (MongoDB ObjectId),
      "name":  string (e.g. "A"),
      "teams": [                                              // 4 teams, sorted by points descending
        {
          "team_id": string (MongoDB ObjectId of the team),
          "mp":      string (matches played),
          "w":       string (wins),
          "l":       string (losses),
          "d":       string (draws),
          "pts":     string (points),
          "gf":      string (goals for),
          "ga":      string (goals against),
          "gd":      string (goal difference),
          "_id":     string (MongoDB ObjectId of this standing record)
        }
      ],
      "createdAt": string (ISO date) | null,
      "__v":       int | null,
      "updatedAt": string (ISO date) | null
    }
  }

USE WHEN: you need standings for a SPECIFIC group only (smaller response). For ALL groups use get_groups.
        """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("name", buildJsonObject {
                    put("type", "string")
                    put("description", "Group letter: one of A, B, C, D, E, F, G, H, I, J, K, L")
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
        description = """
Returns the FULL list of all 48 national teams participating in World Cup 2026. Optionally filter by group letter.

PARAMETERS:
  - "group" (string, OPTIONAL): group letter A-L to filter teams by their group assignment

RESPONSE FORMAT (JSON object with one key):
  {
    "teams": [
      {
        "_id":      string (MongoDB ObjectId, e.g. "679c9c6b5749c4077500ea09"),
        "name_en":  string (team name in English, e.g. "Brazil"),
        "name_fa":  string (team name in Persian) | null,
        "flag":     string (URL to flag image) | null,
        "fifa_code": string (3-letter FIFA code, e.g. "BRA") | null,
        "iso2":     string (2-letter country code, e.g. "BR") | null,
        "groups":   string (group letter, e.g. "F") | null,
        "id":       string (numeric ID) | null
      }
    ]
  }

USE WHEN: you need to list all teams, find a team's group, get FIFA codes or flag URLs, or get team MongoDB IDs to pass to get_team. For DETAILED info about ONE specific team, use get_team instead.
        """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("group", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "Optional filter — group letter (A-L) to return only teams in that group"
                    )
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
        description = """
Returns DETAILED information about ONE specific national team. Provide either the team's MongoDB ID or its English name.

PARAMETERS (at least one required):
  - "id"   (string, OPTIONAL): 24-character MongoDB ObjectId (e.g. "679c9c6b5749c4077500ea09")
  - "name" (string, OPTIONAL): team name in English (e.g. "Brazil")

RESPONSE FORMAT (JSON object with one key):
  {
    "team": {
      "_id":        string (MongoDB ObjectId),
      "name_en":    string (English name, e.g. "Brazil"),
      "name_fa":    string (Persian name) | null,
      "flag":       string (URL to PNG flag image) | null,
      "fifa_code":  string (3-letter FIFA code, e.g. "BRA") | null,
      "iso2":       string (2-letter country code, e.g. "BR") | null,
      "groups":     string (group letter, e.g. "F") | null,
      "id":         string (numeric ID) | null
    }
  }

USE WHEN: you need a single team's details — flag URL, Persian name, FIFA code, or country code. For a list of ALL teams (optionally filtered by group), use get_teams.
        """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("id", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "Team MongoDB ID (24-char hex, e.g. 679c9c6b5749c4077500ea09). Find this from get_teams output."
                    )
                })
                put("name", buildJsonObject {
                    put("type", "string")
                    put("description", "Team name in English (e.g. Brazil, Argentina, Germany)")
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
        description = """
Returns the COMPLETE schedule of ALL World Cup 2026 matches — group stage, round of 32, round of 16, quarter-finals, semi-finals, third place, and final.

NO PARAMETERS REQUIRED.

RESPONSE FORMAT (JSON object with one key):
  {
    "games": [
      {
        "_id":                string (MongoDB ObjectId, e.g. "679c9c8a5749c4077500e004"),
        "id":                 string (numeric ID) | null,
        "home_team_id":       string (MongoDB ObjectId of home team) | null,
        "away_team_id":       string (MongoDB ObjectId of away team) | null,
        "home_score":         string (goals scored by home team, e.g. "3") | null,
        "away_score":         string (goals scored by away team, e.g. "1") | null,
        "home_scorers":       string (JSON array of scorer strings for home team) | null,
        "away_scorers":       string (JSON array of scorer strings for away team) | null,
        "group":              string (group letter "A"-"L" OR knockout stage: "R32","R16","QF","SF","FINAL","3RD") | null,
        "matchday":           string (matchday number) | null,
        "local_date":         string (date in YYYY-MM-DD format) | null,
        "persian_date":       string (Persian calendar date) | null,
        "stadium_id":         string (MongoDB ObjectId of stadium) | null,
        "finished":           string ("TRUE" if match has been played, otherwise null) | null,
        "time_elapsed":       string (minutes elapsed if currently playing) | null,
        "type":               string (match type, e.g. "group") | null,
        "home_team_name_en":  string (home team English name) | null,
        "home_team_name_fa":  string (home team Persian name) | null,
        "away_team_name_en":  string (away team English name) | null,
        "away_team_name_fa":  string (away team Persian name) | null,
        "home_team_label":    string (short home team label) | null,
        "away_team_label":    string (short away team label) | null
      }
    ]
  }

USE WHEN: you need the full match schedule, find upcoming matches, check results of played matches, analyze scorers, or filter by group/knockout stage. For a SINGLE match by ID, use get_game.
        """.trimIndent()
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
        description = """
Returns DETAILED information about ONE specific World Cup 2026 match by its MongoDB ObjectId.

PARAMETERS:
  - "id" (string, REQUIRED): 24-character MongoDB ObjectId (e.g. "679c9c8a5749c4077500e004")

RESPONSE FORMAT (JSON object with one key):
  {
    "game": {
      "_id":                string (MongoDB ObjectId),
      "id":                 string (numeric ID) | null,
      "home_team_id":       string | null,
      "away_team_id":       string | null,
      "home_score":         string (goals by home team) | null,
      "away_score":         string (goals by away team) | null,
      "home_scorers":       string (JSON array of scorers) | null,
      "away_scorers":       string (JSON array of scorers) | null,
      "group":              string (group or knockout stage) | null,
      "matchday":           string | null,
      "local_date":         string (YYYY-MM-DD) | null,
      "persian_date":       string | null,
      "stadium_id":         string | null,
      "finished":           string ("TRUE" or null),
      "time_elapsed":       string | null,
      "type":               string | null,
      "home_team_name_en":  string | null,
      "home_team_name_fa":  string | null,
      "away_team_name_en":  string | null,
      "away_team_name_fa":  string | null,
      "home_team_label":    string | null,
      "away_team_label":    string | null
    }
  }

USE WHEN: you already know a match's MongoDB ID (obtained from get_games) and need full details for that specific match. For ALL matches, use get_games.
        """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("id", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "24-character MongoDB ObjectId of the match (e.g. 679c9c8a5749c4077500e004). Find from get_games output."
                    )
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
        description = """
Returns ALL 16 stadiums hosting World Cup 2026 matches across USA, Canada, and Mexico.

NO PARAMETERS REQUIRED.

RESPONSE FORMAT (JSON object with one key):
  {
    "stadiums": [
      {
        "_id":       string (MongoDB ObjectId),
        "id":        string (numeric stadium ID, e.g. "1") | null,
        "name_en":   string (English name, e.g. "Estadio Azteca") | null,
        "name_fa":   string (Persian name) | null,
        "fifa_name": string (official FIFA tournament name) | null,
        "city_en":   string (city in English, e.g. "Mexico City") | null,
        "city_fa":   string (city in Persian) | null,
        "country_en": string (country in English: "United States", "Canada", or "Mexico") | null,
        "country_fa": string (country in Persian) | null,
        "capacity":  int (seating capacity) | null,
        "region":    string ("USA", "Canada", or "Mexico") | null,
        "createdAt": string (ISO date) | null
      }
    ]
  }

USE WHEN: you need to list all venues, find stadium capacities, or get stadium IDs to pass to get_stadium. For ONE specific stadium, use get_stadium.
        """.trimIndent()
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
        description = """
Returns DETAILED information about ONE specific World Cup 2026 stadium by its numeric ID.

PARAMETERS:
  - "id" (string, REQUIRED): numeric stadium ID (e.g. "1" for Estadio Azteca)

RESPONSE FORMAT (JSON object with one key):
  {
    "stadium": {
      "_id":        string (MongoDB ObjectId),
      "id":         string (numeric ID, e.g. "1") | null,
      "name_en":    string (English name) | null,
      "name_fa":    string (Persian name) | null,
      "fifa_name":  string (official FIFA name) | null,
      "city_en":    string (city in English) | null,
      "city_fa":    string (city in Persian) | null,
      "country_en": string (country in English) | null,
      "country_fa": string (country in Persian) | null,
      "capacity":   int (seating capacity) | null,
      "region":     string ("USA", "Canada", or "Mexico") | null,
      "createdAt":  string (ISO date) | null
    }
  }

USE WHEN: you need details for a specific stadium (capacity, city, FIFA name). For ALL stadiums, use get_stadiums.
        """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("id", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "Stadium numeric ID (e.g. 1, 2, 3... up to 16). Find from get_stadiums output."
                    )
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

    mcpServer.addTool(
        name = "search_all",
        description = """
Fetches RAW data for ALL categories — games, teams, and groups — from the World Cup 2026 API.

NO PARAMETERS REQUIRED.

RESPONSE FORMAT (JSON object):
  {
    "games":  [ ... ]    // all matches (same format as get_games)
    "teams":  [ ... ]    // all 48 teams (same format as get_teams)
    "groups": [ ... ]    // all 12 groups (same format as get_groups)
  }

USE WHEN: you need a COMPLETE raw data dump for manual analysis or further processing. For individual categories use search_games, search_teams, or search_groups.
        """.trimIndent()
    ) { _ ->
        try {
            val snapshot = RawSnapshot(
                games = api.getGames().games,
                teams = api.getTeams().teams,
                groups = api.getGroups().groups
            )
            val text = outputJson.encodeToString(RawSnapshot.serializer(), snapshot)
            CallToolResult(content = listOf(TextContent(text)))
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    mcpServer.addTool(
        name = "search_games",
        description = """
Fetches RAW games data from the World Cup 2026 API — all matches and results.

NO PARAMETERS REQUIRED.

RESPONSE FORMAT (JSON object):
  {
    "games":  [ ... ]    // all matches (same format as get_games)
    "teams":  [ ]        // empty
    "groups": [ ]        // empty
  }

USE WHEN: you need raw/unprocessed match data for custom filtering or analysis.
        """.trimIndent()
    ) { _ ->
        try {
            val snapshot = RawSnapshot(games = api.getGames().games)
            val text = outputJson.encodeToString(RawSnapshot.serializer(), snapshot)
            CallToolResult(content = listOf(TextContent(text)))
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    mcpServer.addTool(
        name = "search_teams",
        description = """
Fetches RAW teams data from the World Cup 2026 API — all 48 participating teams.

NO PARAMETERS REQUIRED.

RESPONSE FORMAT (JSON object):
  {
    "games":  [ ]        // empty
    "teams":  [ ... ]    // all 48 teams (same format as get_teams)
    "groups": [ ]        // empty
  }

USE WHEN: you need raw/unprocessed team info for custom analysis.
        """.trimIndent()
    ) { _ ->
        try {
            val snapshot = RawSnapshot(teams = api.getTeams().teams)
            val text = outputJson.encodeToString(RawSnapshot.serializer(), snapshot)
            CallToolResult(content = listOf(TextContent(text)))
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    mcpServer.addTool(
        name = "search_groups",
        description = """
Fetches RAW group standings from the World Cup 2026 API — all 12 groups A-L.

NO PARAMETERS REQUIRED.

RESPONSE FORMAT (JSON object):
  {
    "games":  [ ]        // empty
    "teams":  [ ]        // empty
    "groups": [ ... ]    // all 12 groups (same format as get_groups)
  }

USE WHEN: you need raw/unprocessed group standings for custom analysis.
        """.trimIndent()
    ) { _ ->
        try {
            val snapshot = RawSnapshot(groups = api.getGroups().groups)
            val text = outputJson.encodeToString(RawSnapshot.serializer(), snapshot)
            CallToolResult(content = listOf(TextContent(text)))
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    println("Starting World Cup API Data Server on port 4453...")
    embeddedServer(CIO, host = "127.0.0.1", port = 4453) {
        mcpStreamableHttp {
            mcpServer
        }
    }.start(wait = true)
}
