package com.example.knucklegame.game

import kotlinx.serialization.Serializable

enum class PlayerId { HOST, CLIENT }

enum class Status { IN_PROGRESS, FINISHED, DRAW }

enum class Phase { IDLE, ROLLING, AWAITING_PLACEMENT }

/** A single column of a board: each die is its face value 1..6, bottom-most die first. */
typealias Column = List<Int>

/** A 3x3 board: three columns, each holding up to [KnucklebonesRules.COLUMN_SIZE] dice. */
typealias Grid = List<Column>

/** One die that was just destroyed, so the UI can animate it. */
@Serializable
data class DieRef(
    val player: PlayerId,
    val column: Int,
    val value: Int,
)

@Serializable
data class GameState(
    val hostName: String,
    val clientName: String,
    val status: Status,
    val currentTurn: PlayerId,
    val phase: Phase,
    val grid: Map<PlayerId, Grid>,
    val winner: PlayerId? = null,
    val lastRoll: Int? = null,
    val destroyed: List<DieRef> = emptyList(),
) {
    fun playerName(player: PlayerId): String = when (player) {
        PlayerId.HOST -> hostName
        PlayerId.CLIENT -> clientName
    }

    fun opponentOf(player: PlayerId): PlayerId =
        if (player == PlayerId.HOST) PlayerId.CLIENT else PlayerId.HOST
}
