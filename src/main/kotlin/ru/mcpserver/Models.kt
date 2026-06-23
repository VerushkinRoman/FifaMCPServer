package ru.mcpserver

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GroupsResponse(
    val groups: List<Group>
)

@Serializable
data class GroupWrapper(
    val group: Group
)

@Serializable
data class Group(
    @SerialName("_id") val mongoId: String,
    val name: String,
    val teams: List<TeamStanding>,
    val createdAt: String? = null,
    @SerialName("__v") val version: Int? = null,
    val updatedAt: String? = null
)

@Serializable
data class TeamStanding(
    @SerialName("team_id") val teamId: String,
    val mp: String,
    val w: String,
    val l: String,
    val d: String,
    val pts: String,
    val gf: String,
    val ga: String,
    val gd: String,
    @SerialName("_id") val mongoId: String
)

@Serializable
data class TeamsResponse(
    val teams: List<Team>
)

@Serializable
data class TeamWrapper(
    val team: Team
)

@Serializable
data class Team(
    @SerialName("_id") val mongoId: String,
    @SerialName("name_en") val nameEn: String,
    @SerialName("name_fa") val nameFa: String? = null,
    val flag: String? = null,
    @SerialName("fifa_code") val fifaCode: String? = null,
    val iso2: String? = null,
    val groups: String? = null,
    @SerialName("id") val numericId: String? = null
)

@Serializable
data class GamesResponse(
    val games: List<Game>
)

@Serializable
data class GameWrapper(
    val game: Game
)

@Serializable
data class Game(
    @SerialName("_id") val mongoId: String,
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
    val stadiums: List<Stadium>
)

@Serializable
data class StadiumWrapper(
    val stadium: Stadium
)

@Serializable
data class Stadium(
    @SerialName("_id") val mongoId: String,
    @SerialName("id") val numericId: String? = null,
    @SerialName("name_en") val nameEn: String? = null,
    @SerialName("name_fa") val nameFa: String? = null,
    @SerialName("fifa_name") val fifaName: String? = null,
    @SerialName("city_en") val cityEn: String? = null,
    @SerialName("city_fa") val cityFa: String? = null,
    @SerialName("country_en") val countryEn: String? = null,
    @SerialName("country_fa") val countryFa: String? = null,
    val capacity: Int? = null,
    val region: String? = null,
    val createdAt: String? = null
)
