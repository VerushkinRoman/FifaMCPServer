package ru.mcpserver

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AuthRequest(
    val name: String? = null,
    val email: String,
    val password: String
)

@Serializable
data class AuthResponse(
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
    val group: Group? = null,
    val teams: List<GroupTeam>? = null,
    val error: String? = null
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
    @SerialName("team_id") val teamId: String? = null,
    val mp: String? = null,
    val w: String? = null,
    val d: String? = null,
    val l: String? = null,
    val pts: String? = null,
    val gf: String? = null,
    val ga: String? = null,
    val gd: String? = null
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
    @SerialName("id") val numericId: String? = null,
    @SerialName("name_en") val nameEn: String? = null,
    @SerialName("name_fa") val nameFa: String? = null,
    val flag: String? = null,
    @SerialName("fifa_code") val fifaCode: String? = null,
    val iso2: String? = null,
    @SerialName("groups") val group: String? = null
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
    @SerialName("home_score") val homeScore: String? = null,
    @SerialName("away_score") val awayScore: String? = null,
    @SerialName("home_scorers") val homeScorers: String? = null,
    @SerialName("away_scorers") val awayScorers: String? = null,
    val group: String? = null,
    val matchday: String? = null,
    @SerialName("local_date") val localDate: String? = null,
    @SerialName("persian_date") val persianDate: String? = null,
    @SerialName("stadium_id") val stadiumId: String? = null,
    val finished: String? = null,
    @SerialName("time_elapsed") val timeElapsed: String? = null,
    val type: String? = null,
    @SerialName("home_team_name_en") val homeTeamNameEn: String? = null,
    @SerialName("home_team_name_fa") val homeTeamNameFa: String? = null,
    @SerialName("away_team_name_en") val awayTeamNameEn: String? = null,
    @SerialName("away_team_name_fa") val awayTeamNameFa: String? = null,
    @SerialName("home_team_label") val homeTeamLabel: String? = null,
    @SerialName("away_team_label") val awayTeamLabel: String? = null
)

@Serializable
data class StadiumsResponse(
    val stadiums: List<Stadium>? = null,
    val error: String? = null
)

@Serializable
data class Stadium(
    @SerialName("_id") val id: String? = null,
    @SerialName("id") val numericId: String? = null,
    @SerialName("name_en") val nameEn: String? = null,
    @SerialName("name_fa") val nameFa: String? = null,
    @SerialName("fifa_name") val fifaName: String? = null,
    @SerialName("city_en") val cityEn: String? = null,
    @SerialName("city_fa") val cityFa: String? = null,
    @SerialName("country_en") val countryEn: String? = null,
    @SerialName("country_fa") val countryFa: String? = null,
    val capacity: Int? = null,
    val region: String? = null
)
