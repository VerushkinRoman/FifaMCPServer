package ru.mcpserver.common

import kotlinx.serialization.Serializable

@Serializable
data class RawSnapshot(
    val games: List<Game> = emptyList(),
    val teams: List<Team> = emptyList(),
    val groups: List<Group> = emptyList()
)
