package ru.mcpserver

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(
    val name: String? = null,
    val email: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val message: String? = null,
    val user: UserInfo? = null,
    val token: String? = null,
    val error: String? = null
)

@Serializable
data class UserInfo(
    @SerialName("_id") val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val createdAt: String? = null
)

@Serializable
data class GroupsResponse(
    val groups: List<Group>? = null,
    val total: Int? = null,
    val error: String? = null
)

@Serializable
data class GroupWrapper(
    val name: String? = null,
    val teams: List<GroupTeam>? = null,
    @SerialName("match_table") val matchTable: MatchTable? = null,
    val error: String? = null
)

@Serializable
data class MatchTable(
    val standings: List<StandingRow>? = null
)

@Serializable
data class StandingRow(
    @SerialName("team_id") val teamId: String? = null,
    @SerialName("team_name") val teamName: String? = null,
    val mp: Int? = null,
    val w: Int? = null,
    val d: Int? = null,
    val l: Int? = null,
    val gf: Int? = null,
    val ga: Int? = null,
    val gd: Int? = null,
    val pts: Int? = null
)

@Serializable
data class Group(
    @SerialName("_id") val id: String? = null,
    val name: String? = null,
    val teams: List<GroupTeam>? = null
)

@Serializable
data class GroupTeam(
    @SerialName("_id") val id: String? = null,
    val name: String? = null,
    val flag: String? = null,
    @SerialName("fifa_rank") val fifaRank: Int? = null
)

@Serializable
data class TeamsResponse(
    val teams: List<Team>? = null,
    val total: Int? = null,
    val error: String? = null
)

@Serializable
data class TeamWrapper(
    val team: Team? = null,
    val error: String? = null
)

@Serializable
data class Team(
    @SerialName("_id") val id: String? = null,
    val name: String? = null,
    val flag: String? = null,
    @SerialName("fifa_rank") val fifaRank: Int? = null,
    val group: String? = null,
    val coach: String? = null,
    val captain: String? = null
)

@Serializable
data class GamesResponse(
    val games: List<Game>? = null,
    val total: Int? = null,
    val page: Int? = null,
    val limit: Int? = null,
    val error: String? = null
)

@Serializable
data class GameWrapper(
    val game: Game? = null,
    val error: String? = null
)

@Serializable
data class Game(
    @SerialName("_id") val id: String? = null,
    @SerialName("id") val numericId: String? = null,
    @SerialName("home_team_id") val homeTeamId: String? = null,
    @SerialName("away_team_id") val awayTeamId: String? = null,
    @SerialName("home_score") val homeScore: Int? = null,
    @SerialName("away_score") val awayScore: Int? = null,
    @SerialName("home_scorers") val homeScorers: List<String>? = null,
    @SerialName("away_scorers") val awayScorers: List<String>? = null,
    @SerialName("home_assists") val homeAssists: List<String>? = null,
    @SerialName("away_assists") val awayAssists: List<String>? = null,
    @SerialName("home_yellow_cards") val homeYellowCards: List<String>? = null,
    @SerialName("away_yellow_cards") val awayYellowCards: List<String>? = null,
    @SerialName("home_red_cards") val homeRedCards: List<String>? = null,
    @SerialName("away_red_cards") val awayRedCards: List<String>? = null,
    val group: String? = null,
    val matchday: Int? = null,
    @SerialName("local_date") val localDate: String? = null,
    @SerialName("persian_date") val persianDate: String? = null,
    @SerialName("stadium_id") val stadiumId: String? = null,
    val finished: Boolean? = null,
    @SerialName("time_elapsed") val timeElapsed: Int? = null,
    val type: String? = null,
    @SerialName("home_team_label") val homeTeamLabel: String? = null,
    @SerialName("away_team_label") val awayTeamLabel: String? = null,
    @SerialName("homeTeam") val homeTeam: TeamSummary? = null,
    @SerialName("visitingTeam") val visitingTeam: TeamSummary? = null,
    val date: String? = null,
    val stadium: StadiumDetail? = null,
    val referee: String? = null,
    val attendance: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class TeamSummary(
    val id: String? = null,
    val name: String? = null,
    val flag: String? = null,
    @SerialName("fifa_rank") val fifaRank: Int? = null,
    val coach: String? = null
)

@Serializable
data class StadiumDetail(
    val name: String? = null,
    val city: String? = null,
    val country: String? = null,
    val capacity: Int? = null
)

@Serializable
data class StadiumsResponse(
    val stadiums: List<Stadium>? = null,
    val error: String? = null
)

@Serializable
data class Stadium(
    @SerialName("_id") val id: String? = null,
    val name: String? = null,
    val city: String? = null,
    val country: String? = null,
    val capacity: Int? = null,
    val image: String? = null,
    @SerialName("games_count") val gamesCount: Int? = null
)
