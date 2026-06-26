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

PARAMETERS:
  - "raw_data" (string, REQUIRED): JSON string of a RawSnapshot object containing games, teams, and groups data.

RESPONSE FORMAT (JSON object):
  {
    "generated_at":      int (Unix timestamp in milliseconds),
    "total_matches":     int (total number of matches),
    "finished_matches":  int (matches with finished="TRUE"),
    "upcoming_matches":  int (matches not yet finished),
    "group_standings": [                                        // ALL groups A-L, 12 items
      {
        "_id":      string,
        "name":     string (group letter A-L),
        "teams": [                                              // 4 teams each, sorted by pts
          { "team_id", "mp", "w", "l", "d", "pts", "gf", "ga", "gd", "_id" }
        ]
      }
    ],
    "recent_results": [                                         // last 20 finished, sorted by date
      { "home_team", "away_team", "home_score", "away_score", "group", "date", "scorers" }
    ],
    "top_scorers": [                                            // sorted by goals descending
      { "player", "goals": int, "team" }
    ],
    "knockout_matches": [                                       // R32, R16, QF, SF, FINAL, 3RD
      { "home_team", "away_team", "home_score", "away_score", "group", "date", "scorers" }
    ]
  }

Use the output of this tool as input for save_data to persist results to disk.
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
Saves a MatchSummary to a file on the server's local filesystem.

PARAMETERS:
  - "summary_data" (string, REQUIRED): JSON string of a MatchSummary object obtained from summarize_data.
  - "format" (string, OPTIONAL, default "json"): output file format:
      "json" → saves as pretty-printed JSON (data/summary_YYYYMMDD_HHmmss.json)
      "txt"  → saves as human-readable formatted table (data/summary_YYYYMMDD_HHmmss.txt)

RESPONSE:
  Plain text: "Saved to: <absolute-file-path>"

When format="txt", the text file contains:
  - Tournament overview (total/played/remaining matches)
  - Group standings table for each group (A-L, with Team, P, W, D, L, GF, GA, GD, Pts)
  - Recent results (last 10 finished matches with scorers)
  - Top scorers leaderboard (top 15 players)
  - Knockout rounds results with scorers

Pass the JSON output from summarize_data as the summary_data parameter.
        """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("summary_data", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "JSON string of a MatchSummary object obtained from summarize_data."
                    )
                })
                put("format", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "Output format: 'json' (default, structured data) or 'txt' (human-readable formatted report)"
                    )
                })
            },
            required = listOf("summary_data")
        )
    ) { request ->
        try {
            val summaryJson = request.arguments?.get("summary_data")?.jsonPrimitive?.contentOrNull
                ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'summary_data' parameter is required.")))
            val summary = json.decodeFromString(MatchSummary.serializer(), summaryJson)
            val format = request.arguments?.get("format")?.jsonPrimitive?.contentOrNull ?: "json"
            val filePath = pipe.save(summary, format)
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
