package ru.mcpserver

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawSnapshot(
    val games: List<Game> = emptyList(),
    val teams: List<Team> = emptyList(),
    val groups: List<Group> = emptyList()
)

@Serializable
data class MatchSummary(
    @SerialName("generated_at") val generatedAt: Long,
    @SerialName("total_matches") val totalMatches: Int,
    @SerialName("finished_matches") val finishedMatches: Int,
    @SerialName("upcoming_matches") val upcomingMatches: Int,
    @SerialName("group_standings") val groupStandings: List<Group> = emptyList(),
    @SerialName("recent_results") val recentResults: List<GameResult> = emptyList(),
    @SerialName("top_scorers") val topScorers: List<ScorerEntry> = emptyList(),
    @SerialName("knockout_matches") val knockoutMatches: List<GameResult> = emptyList()
)

@Serializable
data class GameResult(
    @SerialName("home_team") val homeTeam: String,
    @SerialName("away_team") val awayTeam: String,
    @SerialName("home_score") val homeScore: String,
    @SerialName("away_score") val awayScore: String,
    val group: String? = null,
    val date: String? = null,
    val scorers: String = ""
)

@Serializable
data class ScorerEntry(
    val player: String,
    val goals: Int,
    val team: String
)
