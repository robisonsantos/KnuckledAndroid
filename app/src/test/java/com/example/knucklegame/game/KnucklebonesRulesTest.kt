package com.example.knucklegame.game

import com.example.knucklegame.game.KnucklebonesRules.columnFull
import com.example.knucklegame.game.KnucklebonesRules.columnScore
import com.example.knucklegame.game.KnucklebonesRules.completeRoll
import com.example.knucklegame.game.KnucklebonesRules.totalScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KnucklebonesRulesTest {

    private fun hostTurn(): GameState =
        KnucklebonesRules.reset("Host", "Client", PlayerId.HOST)

    private fun rolled(state: GameState, value: Int = 3): GameState =
        KnucklebonesRules.beginRoll(state, state.currentTurn).let { completeRoll(it, it.currentTurn, value) }

    @Test
    fun `reset starts an empty in-progress game with given first player`() {
        val s = KnucklebonesRules.reset("Host", "Client", PlayerId.CLIENT)
        assertEquals(Status.IN_PROGRESS, s.status)
        assertEquals(PlayerId.CLIENT, s.currentTurn)
        assertEquals(Phase.IDLE, s.phase)
        assertNull(s.winner)
        assertNull(s.lastRoll)
        assertTrue(s.destroyed.isEmpty())
        for (p in PlayerId.entries) {
            assertEquals(3, s.grid[p]!!.size)
            for (col in s.grid[p]!!) {
                assertTrue(col.isEmpty())
            }
        }
    }

    @Test
    fun `single die in a column scores its value`() {
        assertEquals(1, columnScore(listOf(1)))
        assertEquals(6, columnScore(listOf(6)))
    }

    @Test
    fun `pair of same value multiplies by two`() {
        // README table: 2 dice of value v → v*2*2
        assertEquals(12, columnScore(listOf(3, 3)))
        assertEquals(16, columnScore(listOf(4, 4)))
    }

    @Test
    fun `triple of same value multiplies by three`() {
        // README table: 3 dice of value v → v*3*3
        assertEquals(9, columnScore(listOf(1, 1, 1)))
        assertEquals(54, columnScore(listOf(6, 6, 6)))
    }

    @Test
    fun `mixed column scores each value by its own count, order irrelevant`() {
        // README example: 4-1-4 → (4+4)*2 + 1 = 17
        assertEquals(17, columnScore(listOf(4, 1, 4)))
        assertEquals(17, columnScore(listOf(1, 4, 4)))
    }

    @Test
    fun `multi column grid sums column scores`() {
        val grid: Grid = listOf(listOf(4, 1, 4), listOf(3, 3), emptyList())
        assertEquals(17 + 12, totalScore(grid))
    }

    @Test
    fun `columnFull reflects capacity`() {
        assertFalse(columnFull(listOf(listOf(1, 2), emptyList(), emptyList()), 0))
        assertTrue(columnFull(listOf(listOf(1, 2, 3), emptyList(), emptyList()), 0))
        assertFalse(columnFull(listOf(emptyList(), emptyList(), emptyList()), 0))
    }

    @Test
    fun `canRoll only during idle on senders turn`() {
        val s = hostTurn()
        assertTrue(KnucklebonesRules.canRoll(s, PlayerId.HOST))
        assertFalse(KnucklebonesRules.canRoll(s, PlayerId.CLIENT))
        val playing = KnucklebonesRules.beginRoll(s, PlayerId.HOST)
        assertFalse(KnucklebonesRules.canRoll(playing, PlayerId.HOST))
    }

    @Test
    fun `completeRoll moves to awaiting placement and records the value`() {
        val after = rolled(hostTurn(), value = 5)
        assertEquals(Phase.AWAITING_PLACEMENT, after.phase)
        assertEquals(5, after.lastRoll)
        assertTrue(after.grid[PlayerId.HOST]!!.flatten().isEmpty())
    }

    @Test
    fun `canPlace restricted to the roller awaiting placement and non-full column`() {
        val s = rolled(hostTurn(), value = 4)
        assertTrue(KnucklebonesRules.canPlace(s, PlayerId.HOST, 0))
        assertFalse(KnucklebonesRules.canPlace(s, PlayerId.CLIENT, 0))
        assertFalse(KnucklebonesRules.canPlace(s, PlayerId.HOST, 2 + 1))
        val idle = KnucklebonesRules.reset("Host", "Client", PlayerId.HOST)
        assertFalse(KnucklebonesRules.canPlace(idle, PlayerId.HOST, 0))
    }

    @Test
    fun `placing adds die to chosen column and ends the turn`() {
        val s = rolled(hostTurn(), value = 4)
        val after = KnucklebonesRules.place(s, PlayerId.HOST, 1)
        assertEquals(listOf(4), after.grid[PlayerId.HOST]!![1])
        assertEquals(PlayerId.CLIENT, after.currentTurn)
        assertEquals(Phase.IDLE, after.phase)
        assertNull(after.lastRoll)
    }

    @Test
    fun `placed die destroys matching dice in opponents same column only`() {
        val mine = KnucklebonesRules.reset("Host", "Client", PlayerId.HOST)
        // Give Host a 3 in column 0; Client three dice; opponent has 3 in column 0 and 3 in column 2.
        var s = rolled(mine, value = 1)
        s = KnucklebonesRules.place(s, PlayerId.HOST, 0) // host 1
        s = s.copy(currentTurn = PlayerId.HOST)          // force host turn for test simplicity
        // place two more host dice in col 0 (1s) directly via place flow: use a fresh helper
        // Build state by hand to isolate the rule:
        val grid = mapOf(
            PlayerId.HOST to listOf(
                listOf(4, 1),
                emptyList(),
                emptyList(),
            ),
            PlayerId.CLIENT to listOf(
                listOf(3, 3),
                emptyList(),
                listOf(3),
            ),
        )
        val built = GameState(
            hostName = "Host", clientName = "Client",
            status = Status.IN_PROGRESS, currentTurn = PlayerId.HOST,
            phase = Phase.AWAITING_PLACEMENT, grid = grid, lastRoll = 3,
        )
        val after = KnucklebonesRules.place(built, PlayerId.HOST, 0)
        assertEquals(listOf(4, 1, 3), after.grid[PlayerId.HOST]!![0])
        // opponent col 0: both 3s destroyed
        assertEquals(emptyList<Int>(), after.grid[PlayerId.CLIENT]!![0])
        // opponent col 2: untouched
        assertEquals(listOf(3), after.grid[PlayerId.CLIENT]!![2])
        assertEquals(listOf(DieRef(PlayerId.CLIENT, 0, 3), DieRef(PlayerId.CLIENT, 0, 3)), after.destroyed)
    }

    @Test
    fun `opponent dice of other values survive`() {
        val grid = mapOf(
            PlayerId.HOST to listOf(listOf(), listOf(), listOf()),
            PlayerId.CLIENT to listOf(listOf(2, 3, 4), listOf(), listOf()),
        )
        val built = GameState(
            hostName = "Host", clientName = "Client",
            status = Status.IN_PROGRESS, currentTurn = PlayerId.HOST,
            phase = Phase.AWAITING_PLACEMENT, grid = grid, lastRoll = 3,
        )
        val after = KnucklebonesRules.place(built, PlayerId.HOST, 0)
        assertEquals(listOf(2, 4), after.grid[PlayerId.CLIENT]!![0])
    }

    @Test
    fun `game ends when a player fills their board and higher score wins`() {
        // Host: cols (3,3,3),(2,2,2),(1,1,1) = 27+12+9 = 48; Client: 3 empty columns.
        val grid = mapOf(
            PlayerId.HOST to listOf(listOf(3, 3, 3), listOf(2, 2, 2), listOf(1, 1, 1)),
            PlayerId.CLIENT to listOf(listOf(), listOf(), listOf()),
        )
        val built = GameState(
            hostName = "Host", clientName = "Client",
            status = Status.IN_PROGRESS, currentTurn = PlayerId.HOST,
            phase = Phase.AWAITING_PLACEMENT, grid = grid, lastRoll = 5,
        )
        // col0 is full, so choose col 1 for the winning die: rebuild with room in col 2
        val grid2 = mapOf(
            PlayerId.HOST to listOf(listOf(3, 3, 3), listOf(2, 2), listOf(1, 1, 1)),
            PlayerId.CLIENT to listOf(listOf(), listOf(), listOf()),
        )
        val built2 = GameState(
            hostName = "Host", clientName = "Client",
            status = Status.IN_PROGRESS, currentTurn = PlayerId.HOST,
            phase = Phase.AWAITING_PLACEMENT, grid = grid2, lastRoll = 2,
        )
        val after2 = KnucklebonesRules.place(built2, PlayerId.HOST, 1)
        assertEquals(Status.FINISHED, after2.status)
        assertEquals(PlayerId.HOST, after2.winner)
    }

    @Test
    fun `filling player can still lose on score`() {
        // Host fills board but trails Client massively.
        val grid = mapOf(
            PlayerId.HOST to listOf(listOf(1, 1), listOf(1, 1, 1), listOf(1, 1, 1)),
            PlayerId.CLIENT to listOf(listOf(6, 6, 6), listOf(6, 6, 6), listOf(6, 6, 6)),
        )
        val built = GameState(
            hostName = "Host", clientName = "Client",
            status = Status.IN_PROGRESS, currentTurn = PlayerId.HOST,
            phase = Phase.AWAITING_PLACEMENT, grid = grid, lastRoll = 1,
        )
        val after = KnucklebonesRules.place(built, PlayerId.HOST, 0)
        assertEquals(Status.FINISHED, after.status)
        assertEquals(PlayerId.CLIENT, after.winner)
    }

    @Test
    fun `equal scores at full board produce a draw`() {
        // Host col0 [2,2] + lastRoll 2 → [2,2,2]=18; col1 [1,1,1]=9; col2 [1,1,1]=9; total 36.
        // Client col0 [3,3,3]=27; col1 [1,1,1]=9; col2 []=0; total 36. Opponent col0 has no 2s so no destruction.
        val grid = mapOf(
            PlayerId.HOST to listOf(listOf(2, 2), listOf(1, 1, 1), listOf(1, 1, 1)),
            PlayerId.CLIENT to listOf(listOf(3, 3, 3), listOf(1, 1, 1), listOf()),
        )
        val built = GameState(
            hostName = "Host", clientName = "Client",
            status = Status.IN_PROGRESS, currentTurn = PlayerId.HOST,
            phase = Phase.AWAITING_PLACEMENT, grid = grid, lastRoll = 2,
        )
        val after = KnucklebonesRules.place(built, PlayerId.HOST, 0)
        assertEquals(Status.DRAW, after.status)
        assertNull(after.winner)
    }

    @Test
    fun `canRestart only after finished or draw`() {
        assertFalse(KnucklebonesRules.canRestart(hostTurn()))
        val finished = hostTurn().copy(status = Status.FINISHED, winner = PlayerId.HOST)
        assertTrue(KnucklebonesRules.canRestart(finished))
        val draw = hostTurn().copy(status = Status.DRAW)
        assertTrue(KnucklebonesRules.canRestart(draw))
    }
}
