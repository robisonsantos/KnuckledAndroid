package com.example.knucklegame.bluetooth

import com.example.knucklegame.game.CpuPlayer
import com.example.knucklegame.game.GameHost
import com.example.knucklegame.game.KnucklebonesRules
import com.example.knucklegame.game.Phase
import com.example.knucklegame.game.PlayerId
import com.example.knucklegame.game.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalConnectorTest {

    @Test
    fun `a host game over the local connector finishes with the CPU on the other side`() {
        val connector = LocalConnector(preRollDelayMs = 0L, thinkDelay = { 0L })
        val appLink = connector.listen("0000")

        val counter = java.util.concurrent.atomic.AtomicInteger(0)
        val host = GameHost(
            link = appLink,
            hostName = "Human",
            rollValue = { (counter.getAndIncrement() % 6) + 1 },
            rollDelayMs = 0L,
            firstPlayer = { PlayerId.HOST },
            onState = {},
        )
        host.connect()

        // Drive the human side (a plain first-open-column player) until the game ends.
        var guard = 0
        while (host.state.status == Status.IN_PROGRESS && guard < 2000) {
            val s = host.state
            if (KnucklebonesRules.canRoll(s, PlayerId.HOST)) {
                host.hostRoll()
            } else if (s.phase == Phase.AWAITING_PLACEMENT && s.currentTurn == PlayerId.HOST) {
                val open = s.grid[PlayerId.HOST]!!
                    .indices.first { !KnucklebonesRules.columnFull(s.grid[PlayerId.HOST]!!, it) }
                host.hostPlace(open)
            } else {
                Thread.sleep(20)
            }
            guard += 1
        }
        assertTrue(
            "game should finish (steps=$guard, state=${host.state})",
            host.state.status != Status.IN_PROGRESS,
        )
        assertEquals(CpuPlayer.NAME, host.state.clientName)
    }
}
