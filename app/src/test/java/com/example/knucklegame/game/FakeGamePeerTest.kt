package com.example.knucklegame.game

import com.example.knucklegame.FakeGameLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeGamePeerTest {

    @Test
    fun `fake client plays a full session to a draw or finish`() {
        val hostLink = FakeGameLink()
        val counter = java.util.concurrent.atomic.AtomicInteger(0)
        val gh = GameHost(
            link = hostLink,
            hostName = FakeGamePeer.HOST_NAME,
            rollValue = { (counter.getAndIncrement() % 6) + 1 },
            rollDelayMs = 0L,
            firstPlayer = { PlayerId.HOST },
            onState = {},
        )
        gh.connect()
        val clientLines = mutableListOf<String>()
        var forwarded = 0
        lateinit var botLink: com.example.knucklegame.bluetooth.GameLink
        fun pumpToBot() {
            while (forwarded < hostLink.sent.size) {
                val hostLine = hostLink.sent[forwarded++]
                if (GameMessages.decodeState(hostLine) != null) {
                    try { botLink.onLine(hostLine) } catch (_: Exception) {}
                }
            }
        }
        botLink = object : com.example.knucklegame.bluetooth.GameLink {
            override var onLine: (String) -> Unit = {}
            override var onClosed: () -> Unit = {}
            override fun send(line: String) {
                clientLines += line
                hostLink.receive(line)
                pumpToBot()
            }
            override fun close() { onClosed() }
        }
        runFakeClient(botLink)
        pumpToBot()
        var guard = 0
        while (gh.state.status == Status.IN_PROGRESS && guard < 900) {
            val s = gh.state
            if (KnucklebonesRules.canRoll(s, PlayerId.HOST)) {
                gh.hostRoll()
                pumpToBot()
            } else if (s.phase == Phase.AWAITING_PLACEMENT && s.currentTurn == PlayerId.HOST) {
                val col = FakeGamePeer.firstOpenColumn(s.grid[PlayerId.HOST]!!)
                gh.hostPlace(col)
                pumpToBot()
            } else {
                Thread.sleep(20)
                pumpToBot()
            }
            guard += 1
        }
        assertTrue("game should finish (steps=$guard, state=${gh.state})", gh.state.status != Status.IN_PROGRESS)
        if (gh.state.status == Status.FINISHED) assertNotNull(gh.state.winner)
        assertEquals("FakePeer", gh.state.clientName)
        assertTrue(clientLines.isNotEmpty())
    }
}
