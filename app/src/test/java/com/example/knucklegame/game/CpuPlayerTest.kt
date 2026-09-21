package com.example.knucklegame.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CpuPlayerTest {

    private fun awaitingTurn(
        lastRoll: Int,
        mine: Grid,
        theirs: Grid,
        currentTurn: PlayerId = PlayerId.CLIENT,
    ): GameState =
        GameState(
            hostName = "Host",
            clientName = CpuPlayer.NAME,
            status = Status.IN_PROGRESS,
            currentTurn = currentTurn,
            phase = Phase.AWAITING_PLACEMENT,
            grid = mapOf(PlayerId.HOST to theirs, PlayerId.CLIENT to mine),
            lastRoll = lastRoll,
        )

    private fun openColumns(state: GameState, player: PlayerId): Set<Int> {
        val grid = state.grid[player]!!
        return grid.indices.filter { !KnucklebonesRules.columnFull(grid, it) }.toSet()
    }

    private fun emptyGrid(): Grid = List(3) { emptyList() }

    @Test
    fun `always returns a legal open column`() {
        val mine: Grid = listOf(listOf(2, 3), listOf(6), emptyList())
        val theirs: Grid = listOf(listOf(1), listOf(4, 4), listOf(5, 5, 5))
        val state = awaitingTurn(4, mine, theirs)
        val chosen = CpuPlayer.chooseColumn(state)
        assertTrue("chosen $chosen must be open", chosen in openColumns(state, PlayerId.CLIENT))
    }

    @Test
    fun `is deterministic for the same state`() {
        val mine: Grid = listOf(listOf(3, 5), listOf(2), emptyList())
        val theirs: Grid = listOf(listOf(6), emptyList(), listOf(1, 1))
        val state = awaitingTurn(2, mine, theirs)
        assertEquals(CpuPlayer.chooseColumn(state), CpuPlayer.chooseColumn(state))
    }

    @Test
    fun `destroys the opponents strong same-value pair`() {
        val mine = emptyGrid()
        val theirs: Grid = listOf(listOf(6, 6), emptyList(), emptyList())
        val state = awaitingTurn(6, mine, theirs)
        assertEquals(0, CpuPlayer.chooseColumn(state))
    }

    @Test
    fun `keeps its own pair together to complete a triple`() {
        val mine: Grid = listOf(listOf(5, 5), emptyList(), emptyList())
        val theirs = emptyGrid()
        val state = awaitingTurn(5, mine, theirs)
        assertEquals(0, CpuPlayer.chooseColumn(state))
    }

    @Test
    fun `never places into a full column`() {
        val mine: Grid = listOf(listOf(1, 2, 3), emptyList(), emptyList())
        val theirs = emptyGrid()
        val state = awaitingTurn(6, mine, theirs)
        val chosen = CpuPlayer.chooseColumn(state)
        assertTrue("chosen $chosen must not be the full column 0", chosen != 0)
    }

    @Test
    fun `fills the board to win when the endgame favours it`() {
        val mine: Grid = listOf(listOf(3, 3), listOf(4, 4, 4), listOf(2, 2))
        val theirs: Grid = listOf(listOf(2, 2), listOf(1), listOf(1))
        val state = awaitingTurn(3, mine, theirs)
        assertEquals(0, CpuPlayer.chooseColumn(state))
    }
}
