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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

fun main() {
    val pipe = PipelineTools()

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
