package ru.mcpserver.pipeline

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import ru.mcpserver.common.Game
import ru.mcpserver.common.GameResult
import ru.mcpserver.common.MatchSummary
import ru.mcpserver.common.RawSnapshot
import ru.mcpserver.common.ScorerEntry
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PipelineTools {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    fun summarize(snapshot: RawSnapshot): MatchSummary {
        val finished = snapshot.games.filter { it.finished == "TRUE" }

        val teamNameById = snapshot.teams.associate { it.mongoId to it.nameEn }

        fun resolveTeamName(game: Game): Pair<String, String> {
            val home = game.homeTeamNameEn
                ?: teamNameById[game.homeTeamId]
                ?: game.homeTeamId
                ?: "?"
            val away = game.awayTeamNameEn
                ?: teamNameById[game.awayTeamId]
                ?: game.awayTeamId
                ?: "?"
            return home to away
        }

        val recentResults = finished
            .sortedByDescending { it.localDate ?: "" }
            .take(20)
            .map { game ->
                val (home, away) = resolveTeamName(game)
                GameResult(
                    homeTeam = home,
                    awayTeam = away,
                    homeScore = game.homeScore ?: "?",
                    awayScore = game.awayScore ?: "?",
                    group = game.group,
                    date = game.localDate,
                    scorers = formatScorers(game, teamNameById)
                )
            }

        val topScorers = extractTopScorers(finished, teamNameById)

        val knockoutMatches = snapshot.games
            .filter { game ->
                val g = game.group ?: ""
                g in listOf("R32", "R16", "QF", "SF", "FINAL", "3RD")
            }
            .map { game ->
                val (home, away) = resolveTeamName(game)
                GameResult(
                    homeTeam = home,
                    awayTeam = away,
                    homeScore = game.homeScore ?: "?",
                    awayScore = game.awayScore ?: "?",
                    group = game.group,
                    date = game.localDate,
                    scorers = formatScorers(game, teamNameById)
                )
            }

        return MatchSummary(
            generatedAt = System.currentTimeMillis(),
            totalMatches = snapshot.games.size,
            finishedMatches = finished.size,
            upcomingMatches = snapshot.games.size - finished.size,
            groupStandings = snapshot.groups,
            recentResults = recentResults,
            topScorers = topScorers,
            knockoutMatches = knockoutMatches
        )
    }

    fun saveRaw(text: String, extension: String = "txt"): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val dir = File("data")
        dir.mkdirs()
        val file = File(dir, "data_$timestamp.$extension")
        file.writeText(text)
        return file.absolutePath
    }

    private fun parseScorers(raw: String?): List<RawScorer> {
        if (raw.isNullOrBlank() || raw.trim() == "null") return emptyList()
        val trimmed = raw.trim()

        val entries: List<String> = tryParseAsJsonArray(trimmed)
            ?: tryParseAsJsonObject(trimmed)
            ?: tryParseAsBareArray(trimmed)
            ?: smartSplit(stripOuterBraces(trimmed))
            ?: return emptyList()

        return entries.mapNotNull { entry ->
            val cleaned = entry.trim()
                .removeSurrounding("\"")
                .removeSurrounding("'")
                .trim()
            if (cleaned.isBlank() || cleaned == "null") null
            else parseOneScorer(cleaned)
        }
    }

    private fun tryParseAsJsonArray(text: String): List<String>? {
        if (!text.startsWith("[")) return null
        return try {
            val element = json.parseToJsonElement(text)
            if (element is JsonArray) element.map { it.jsonPrimitive.content }
            else null
        } catch (_: Exception) {
            null
        }
    }

    private fun tryParseAsJsonObject(text: String): List<String>? {
        if (!text.startsWith("{")) return null
        return try {
            val element = json.parseToJsonElement(text)
            if (element is JsonObject) element.values.map { it.jsonPrimitive.content }
            else null
        } catch (_: Exception) {
            null
        }
    }

    private fun tryParseAsBareArray(text: String): List<String>? {
        if (!text.startsWith("\"") || !text.endsWith("\"")) return null
        return try {
            val element = json.parseToJsonElement(text)
            if (element is JsonPrimitive) {
                val inner = element.content.trim()
                when {
                    inner.startsWith("[") -> {
                        val arr = json.parseToJsonElement(inner)
                        if (arr is JsonArray) arr.map { it.jsonPrimitive.content } else null
                    }

                    inner.startsWith("{") -> {
                        val obj = json.parseToJsonElement(inner)
                        if (obj is JsonObject) obj.values.map { it.jsonPrimitive.content } else null
                    }

                    else -> listOf(inner)
                }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun stripOuterBraces(text: String): String {
        var s = text
        if (s.startsWith("{") && s.endsWith("}")) s = s.substring(1, s.length - 1).trim()
        if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length - 1).trim()
        return s
    }

    private fun smartSplit(text: String): List<String>? {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var inQuote = false
        for (ch in text) {
            when (ch) {
                '"' -> inQuote = !inQuote
                '(', '[', '{' -> {
                    depth++; current.append(ch)
                }

                ')', ']', '}' -> {
                    depth--; current.append(ch)
                }

                ',' if depth == 0 && !inQuote -> {
                    result.add(current.toString())
                    current.clear()
                }

                else -> current.append(ch)
            }
        }
        if (current.isNotBlank()) result.add(current.toString())
        return result.takeIf { it.isNotEmpty() }
    }

    private fun parseOneScorer(entry: String): RawScorer? {
        if (entry.isBlank()) return null

        val parenIdx = entry.indexOf('(')
        if (parenIdx > 0) {
            val namePart = entry.substring(0, parenIdx).trim()
            val rest = entry.substring(parenIdx + 1)
            val closeIdx = rest.lastIndexOf(')')
            val inside = if (closeIdx >= 0) rest.substring(0, closeIdx) else rest
            val parts = inside.split(",").map { it.trim() }
            val minute = if (parts.size >= 2) {
                parts.last().removeSuffix("'").trim().removeSuffix("(p)").removeSuffix("(OG)")
                    .trim()
            } else ""
            val player = namePart.ifBlank { parts.firstOrNull() ?: "" }
            return RawScorer(player, minute)
        }

        val tokens = entry.split(" ")
        if (tokens.size >= 2) {
            val minute = tokens.last()
                .removeSuffix("'")
                .removeSuffix("(p)")
                .removeSuffix("(OG)")
                .trim()
            val player = tokens.dropLast(1).joinToString(" ")
            return RawScorer(player, minute)
        }

        return null
    }

    private fun formatScorers(game: Game, teamNames: Map<String, String>): String {
        val parts = mutableListOf<String>()
        val homeTeamName =
            game.homeTeamNameEn ?: teamNames[game.homeTeamId] ?: game.homeTeamId ?: "?"
        val awayTeamName =
            game.awayTeamNameEn ?: teamNames[game.awayTeamId] ?: game.awayTeamId ?: "?"
        val home = parseScorers(game.homeScorers)
        val away = parseScorers(game.awayScorers)
        home.forEach { parts.add("${it.player} ($homeTeamName, ${it.minute}')") }
        away.forEach { parts.add("${it.player} ($awayTeamName, ${it.minute}')") }
        return parts.joinToString("; ")
    }

    private fun extractTopScorers(
        finishedGames: List<Game>,
        teamNames: Map<String, String>
    ): List<ScorerEntry> {
        val goalCounts = mutableMapOf<String, MutableMap<String, Int>>()
        val teamOfPlayer = mutableMapOf<String, String>()

        for (game in finishedGames) {
            val homeTeamName = game.homeTeamNameEn
                ?: teamNames[game.homeTeamId]
                ?: game.homeTeamId
                ?: ""
            val awayTeamName = game.awayTeamNameEn
                ?: teamNames[game.awayTeamId]
                ?: game.awayTeamId
                ?: ""
            val home = parseScorers(game.homeScorers)
            val away = parseScorers(game.awayScorers)
            home.forEach { scorer ->
                val counts = goalCounts.getOrPut(scorer.player) { mutableMapOf() }
                counts[homeTeamName] = (counts[homeTeamName] ?: 0) + 1
                teamOfPlayer[scorer.player] = homeTeamName
            }
            away.forEach { scorer ->
                val counts = goalCounts.getOrPut(scorer.player) { mutableMapOf() }
                counts[awayTeamName] = (counts[awayTeamName] ?: 0) + 1
                teamOfPlayer[scorer.player] = awayTeamName
            }
        }

        return goalCounts.entries
            .filter { it.key.isNotBlank() && it.key != "null" }
            .map { (player, teamGoals) ->
                val team = teamGoals.maxByOrNull { it.value }?.key
                    ?: teamOfPlayer[player]
                    ?: ""
                val total = teamGoals.values.sum()
                ScorerEntry(player, total, team)
            }
            .sortedByDescending { it.goals }
    }

    private data class RawScorer(val player: String, val minute: String)
}
