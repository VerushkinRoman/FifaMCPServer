package ru.mcpserver.pipeline

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
import ru.mcpserver.common.MatchSummary
import ru.mcpserver.common.RawSnapshot

fun main() {
    val pipe = PipelineTools()

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val mcpServer = Server(
        serverInfo = Implementation(
            name = "worldcup-pipeline-server",
            version = "1.0.0"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
            ),
        )
    )

    mcpServer.addTool(
        name = "summarize_data",
        description = """
Aggregates World Cup 2026 data from a RawSnapshot and returns a structured summary with group standings, recent results, top scorers, and knockout matches.

━━━ INPUT: "raw_data" (string, REQUIRED) ━━━
A JSON string matching the RawSnapshot structure below.
Typically obtained from the data-api server's search_all / search_games / search_teams / search_groups tools.

RawSnapshot JSON structure:
{
  "games": [                                          // array of game objects
    {
      "_id":              string,                     // MongoDB ID
      "id":               string | null,              // numeric ID
      "home_team_id":     string | null,              // references Team._id
      "away_team_id":     string | null,
      "home_score":       string | null,              // e.g. "3"
      "away_score":       string | null,
      "home_scorers":     string | null,              // JSON array/object of scorers, e.g. '["Player (Team, 45\')"]' or "null"
      "away_scorers":     string | null,
      "group":            string | null,              // group letter (A-L) or knockout stage: "R32","R16","QF","SF","FINAL","3RD"
      "matchday":         string | null,
      "local_date":       string | null,              // "2026-06-14"
      "persian_date":     string | null,
      "stadium_id":       string | null,
      "finished":         string | null,              // "TRUE" or null
      "time_elapsed":     string | null,
      "type":             string | null,
      "home_team_name_en":  string | null,            // team display name (optional)
      "home_team_name_fa":  string | null,
      "away_team_name_en":  string | null,
      "away_team_name_fa":  string | null,
      "home_team_label":    string | null,
      "away_team_label":    string | null
    }
  ],
  "teams": [                                          // array of team objects
    {
      "_id":              string,                     // MongoDB ID (referenced by game.home_team_id / away_team_id)
      "name_en":          string,                     // e.g. "Brazil"
      "name_fa":          string | null,
      "flag":             string | null,              // emoji or image URL
      "fifa_code":        string | null,              // e.g. "BRA"
      "iso2":             string | null,
      "groups":           string | null,
      "id":               string | null               // numeric ID
    }
  ],
  "groups": [                                         // array of group standings
    {
      "_id":              string,
      "name":             string,                     // group letter: "A", "B", ..., "L"
      "teams": [                                      // 4 teams per group, sorted by pts desc
        {
          "team_id":      string,                     // references Team._id
          "mp":           string,                     // matches played
          "w":            string,                     // wins
          "l":            string,                     // losses
          "d":            string,                     // draws
          "pts":          string,                     // points
          "gf":           string,                     // goals for
          "ga":           string,                     // goals against
          "gd":           string,                     // goal difference
          "_id":          string
        }
      ],
      "createdAt":        string | null,
      "updatedAt":        string | null,
      "__v":              int | null
    }
  ]
}

━━━ OUTPUT (JSON object) ━━━
{
  "generated_at":       int,                          // Unix timestamp in milliseconds
  "total_matches":      int,                          // total games in snapshot
  "finished_matches":   int,                          // games with finished="TRUE"
  "upcoming_matches":   int,                          // games not yet finished
  "group_standings": [                                // all groups A-L
    {
      "_id":    string,
      "name":   string,                               // group letter
      "teams": [                                      // sorted by pts descending
        { "team_id", "mp", "w", "l", "d", "pts", "gf", "ga", "gd", "_id" }
      ]
    }
  ],
  "recent_results": [                                 // last 20 finished games, sorted by date desc
    { "home_team", "away_team", "home_score", "away_score", "group", "date", "scorers" }
  ],
  "top_scorers": [                                    // aggregated from all finished games
    { "player": string, "goals": int, "team": string }
  ],
  "knockout_matches": [                               // R32 / R16 / QF / SF / FINAL / 3RD
    { "home_team", "away_team", "home_score", "away_score", "group", "date", "scorers" }
  ]
}

TIP: Pipe the output JSON into save_data's "content" parameter to persist to disk.
        """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("raw_data", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "JSON string from search_all / search_games / search_teams / search_groups output — the RawSnapshot containing games, teams, and groups data."
                    )
                })
            },
            required = listOf("raw_data")
        )
    ) { request ->
        try {
            val rawJson = request.arguments?.get("raw_data")?.jsonPrimitive?.contentOrNull
                ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'raw_data' parameter is required.")))
            val snapshot = json.decodeFromString(RawSnapshot.serializer(), rawJson)
            val summary = pipe.summarize(snapshot)
            val text = json.encodeToString(MatchSummary.serializer(), summary)
            CallToolResult(content = listOf(TextContent(text)))
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    mcpServer.addTool(
        name = "save_data",
        description = """
Saves any raw text content to a file on the server's local filesystem.
Useful for persisting JSON, CSV, markdown, logs, or any text output to disk.

━━━ INPUT PARAMETERS ━━━
  "content" (string, REQUIRED):
    The raw text to write to the file. Accepts any string — JSON, plain text, markdown, CSV, etc.
    Pass the full output of summarize_data here to persist the summary JSON.

  "format" (string, OPTIONAL, default "txt"):
    File extension (without dot). Determines the output file name:
      "txt"   → data/data_YYYYMMDD_HHmmss.txt
      "json"  → data/data_YYYYMMDD_HHmmss.json
      "md"    → data/data_YYYYMMDD_HHmmss.md
      "csv"   → data/data_YYYYMMDD_HHmmss.csv
      (any extension works)

━━━ OUTPUT ━━━
  Plain text response: "Saved to: /absolute/path/to/data/data_YYYYMMDD_HHmmss.<format>"

━━━ EXAMPLE ━━━
  1. Call summarize_data with raw_data
  2. Take the JSON output
  3. Call save_data with content=<that JSON> and format="json"
  → File saved as data/data_20260626_143022.json
        """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("content", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "Raw text content to save to a file."
                    )
                })
                put("format", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "File extension (default: 'txt'). Examples: 'txt', 'json', 'md', 'csv', 'html'."
                    )
                })
            },
            required = listOf("content")
        )
    ) { request ->
        try {
            val content = request.arguments?.get("content")?.jsonPrimitive?.contentOrNull
                ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'content' parameter is required.")))
            val format = request.arguments?.get("format")?.jsonPrimitive?.contentOrNull ?: "txt"
            val filePath = pipe.saveRaw(content, format)
            CallToolResult(content = listOf(TextContent("Saved to: $filePath")))
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")))
        }
    }

    println("Starting World Cup Pipeline Server on port 4455...")
    embeddedServer(CIO, host = "127.0.0.1", port = 4455) {
        mcpStreamableHttp {
            mcpServer
        }
    }.start(wait = true)
}
