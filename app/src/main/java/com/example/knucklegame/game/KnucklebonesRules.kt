package com.example.knucklegame.game

object KnucklebonesRules {
    const val COLUMNS = 3
    const val COLUMN_SIZE = 3

    fun emptyGrid(): Grid = List(COLUMNS) { emptyList() }

    fun reset(hostName: String, clientName: String, firstPlayer: PlayerId): GameState =
        GameState(
            hostName = hostName,
            clientName = clientName,
            status = Status.IN_PROGRESS,
            currentTurn = firstPlayer,
            phase = Phase.IDLE,
            grid = mapOf(
                PlayerId.HOST to emptyGrid(),
                PlayerId.CLIENT to emptyGrid(),
            ),
            winner = null,
            lastRoll = null,
            destroyed = emptyList(),
        )

    /** value × count²: count dice, each worth value×count. */
    fun columnScore(column: Column): Int =
        column.groupingBy { it }.eachCount()
            .entries.sumOf { (value, count) -> value * count * count }

    fun totalScore(grid: Grid): Int = grid.sumOf { columnScore(it) }

    fun columnFull(grid: Grid, column: Int): Boolean =
        grid.getOrElse(column) { emptyList() }.size >= COLUMN_SIZE

    fun canRoll(state: GameState, player: PlayerId): Boolean =
        state.status == Status.IN_PROGRESS &&
            state.phase == Phase.IDLE &&
            state.currentTurn == player

    fun beginRoll(state: GameState, player: PlayerId): GameState {
        require(canRoll(state, player)) { "cannot begin roll for $player" }
        return state.copy(phase = Phase.ROLLING)
    }

    fun completeRoll(state: GameState, player: PlayerId, value: Int): GameState {
        require(state.phase == Phase.ROLLING && state.currentTurn == player) { "invalid completeRoll" }
        require(value in 1..6) { "die out of range: $value" }
        return state.copy(phase = Phase.AWAITING_PLACEMENT, lastRoll = value)
    }

    fun canPlace(state: GameState, player: PlayerId, column: Int): Boolean {
        val grid = state.grid[player] ?: return false
        return state.phase == Phase.AWAITING_PLACEMENT &&
            state.currentTurn == player &&
            column in grid.indices &&
            !columnFull(grid, column)
    }

    fun place(state: GameState, player: PlayerId, column: Int): GameState {
        require(canPlace(state, player, column)) { "cannot place for $player in $column" }
        val value = state.lastRoll ?: error("no roll to place")

        val opponent = state.opponentOf(player)
        val grid = mutableMapOf<PlayerId, Grid>()
        for (p in PlayerId.entries) {
            grid[p] = state.grid[p] !!  // safe: reset always sets both
        }

        val mine = grid[player]!!.toMutableList()
        mine[column] = mine[column] + value
        grid[player] = mine

        val destroyed = mutableListOf<DieRef>()
        val theirs = grid[opponent]!!.toMutableList()
        val theirColumn = theirs[column]
        if (value in theirColumn) {
            theirs[column] = theirColumn.filterNot { it == value }
            repeat(theirColumn.count { it == value }) {
                destroyed += DieRef(opponent, column, value)
            }
            grid[opponent] = theirs
        }

        val boardFull = grid[player]!!.all { it.size >= COLUMN_SIZE }
        val base = state.copy(
            grid = grid,
            lastRoll = null,
            destroyed = destroyed,
            phase = Phase.IDLE,
        )
        if (!boardFull) {
            return base.copy(currentTurn = opponent)
        }

        val mineScore = totalScore(grid[player]!!)
        val theirsScore = totalScore(grid[opponent]!!)
        return when {
            mineScore == theirsScore -> base.copy(status = Status.DRAW)
            mineScore > theirsScore -> base.copy(status = Status.FINISHED, winner = player)
            else -> base.copy(status = Status.FINISHED, winner = opponent)
        }
    }

    fun canRestart(state: GameState): Boolean = state.status != Status.IN_PROGRESS
}
