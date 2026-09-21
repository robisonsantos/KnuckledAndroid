package com.example.knucklegame.game

import com.example.knucklegame.FakeGameLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CpuClientTest {

    private fun playFullGame(firstPlayer: PlayerId): GameState {
        val hostLink = FakeGameLink()
        val counter = java.util.concurrent.atomic.AtomicInteger(0)
        val gh = GameHost(
            link = hostLink,
            hostName = "Human",
            rollValue = { (counter.getAndIncrement() % 6) + 1 },
            rollDelayMs = 0L,
            firstPlayer = { firstPlayer },
            onState = {},
        )
        gh.connect()
        val forwarded = intArrayOf(0)
        lateinit var botLink: com.example.knucklegame.bluetooth.GameLink
        fun pumpToBot() {
            while (forwarded[0] < hostLink.sent.size) {
                val hostLine = hostLink.sent[forwarded[0]++]
                if (GameMessages.decodeState(hostLine) != null) {
                    try { botLink.onLine(hostLine) } catch (_: Exception) {}
                }
            }
        }
        botLink = object : com.example.knucklegame.bluetooth.GameLink {
            override var onLine: (String) -> Unit = {}
            override var onClosed: () -> Unit = {}
            override fun send(line: String) {
                hostLink.receive(line)
                pumpToBot()
            }
            override fun close() { onClosed() }
        }
        runCpuClient(botLink, preRollDelayMs = 0L, thinkDelay = { 0L })
        pumpToBot()
        var guard = 0
        while (gh.state.status == Status.IN_PROGRESS && guard < 2000) {
            val s = gh.state
            if (KnucklebonesRules.canRoll(s, PlayerId.HOST)) {
                gh.hostRoll()
                pumpToBot()
            } else if (s.phase == Phase.AWAITING_PLACEMENT && s.currentTurn == PlayerId.HOST) {
                gh.hostPlace(FakeGamePeer.firstOpenColumn(s.grid[PlayerId.HOST]!!))
                pumpToBot()
            } else {
                Thread.sleep(20)
                pumpToBot()
            }
            guard += 1
        }
        return gh.state
    }

    @Test
    fun `cpu plays a full session and fills its own board`() {
        // CPU goes first, so it is the one who fills the board and ends the game.
        val state = playFullGame(PlayerId.CLIENT)
        assertTrue(
            "game should finish (state=$state)",
            state.status == Status.FINISHED || state.status == Status.DRAW,
        )
        if (state.status == Status.FINISHED) assertNotNull(state.winner)
        assertEquals(CpuPlayer.NAME, state.clientName)
        assertEquals(9, state.grid[PlayerId.CLIENT]!!.flatten().size)
    }

    @Test
    fun `cpu keeps playing after a restart`() {
        val hostLink = FakeGameLink()
        val counter = java.util.concurrent.atomic.AtomicInteger(0)
        val gh = GameHost(
            link = hostLink,
            hostName = "Human",
            rollValue = { (counter.getAndIncrement() % 6) + 1 },
            rollDelayMs = 0L,
            firstPlayer = { PlayerId.CLIENT },
            onState = {},
        )
        gh.connect()
        val forwarded = intArrayOf(0)
        lateinit var botLink: com.example.knucklegame.bluetooth.GameLink
        fun pumpToBot() {
            while (forwarded[0] < hostLink.sent.size) {
                val hostLine = hostLink.sent[forwarded[0]++]
                if (GameMessages.decodeState(hostLine) != null) {
                    try { botLink.onLine(hostLine) } catch (_: Exception) {}
                }
            }
        }
        botLink = object : com.example.knucklegame.bluetooth.GameLink {
            override var onLine: (String) -> Unit = {}
            override var onClosed: () -> Unit = {}
            override fun send(line: String) {
                hostLink.receive(line)
                pumpToBot()
            }
            override fun close() { onClosed() }
        }
        runCpuClient(botLink, preRollDelayMs = 0L, thinkDelay = { 0L })
        pumpToBot()

        fun playWhileInProgress() {
            var guard = 0
            while (gh.state.status == Status.IN_PROGRESS && guard < 2000) {
                val s = gh.state
                if (KnucklebonesRules.canRoll(s, PlayerId.HOST)) {
                    gh.hostRoll()
                    pumpToBot()
                } else if (s.phase == Phase.AWAITING_PLACEMENT && s.currentTurn == PlayerId.HOST) {
                    gh.hostPlace(FakeGamePeer.firstOpenColumn(s.grid[PlayerId.HOST]!!))
                    pumpToBot()
                } else {
                    Thread.sleep(20)
                    pumpToBot()
                }
                guard += 1
            }
        }

        playWhileInProgress()
        assertTrue("first game should finish", gh.state.status != Status.IN_PROGRESS)
        gh.restart()
        pumpToBot()
        assertTrue("restart should be in progress", gh.state.status == Status.IN_PROGRESS)
        assertEquals(CpuPlayer.NAME, gh.state.clientName)
        playWhileInProgress()
        assertTrue("second game should finish", gh.state.status != Status.IN_PROGRESS)
        assertEquals(9, gh.state.grid[PlayerId.CLIENT]!!.flatten().size)
    }
}
