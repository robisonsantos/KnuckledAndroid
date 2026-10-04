package com.example.knucklegame.game

import com.example.knucklegame.FakeGameLink
import com.example.knucklegame.game.KnucklebonesRules.emptyGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class GameHostTest {

    private fun host(link: FakeGameLink, first: PlayerId, body: (GameHost) -> Unit) {
        var current: GameHost? = null
        current = GameHost(
            link = link,
            hostName = "Host",
            rollValue = { 4 },
            rollDelayMs = 0L,
            firstPlayer = { first },
            onState = {},
        )
        body(current)
    }

    private fun lastStateSent(link: FakeGameLink): GameState =
        link.sent.asReversed().firstNotNullOf { GameMessages.decodeState(it) }

    @Test
    fun `client name triggers reset with both players and chosen first player`() {
        val link = FakeGameLink()
        host(link, PlayerId.CLIENT) { gameHost ->
            gameHost.connect()
            link.receive(GameMessages.encodeName("  Bob  "))
            val state = lastStateSent(link)
            assertEquals("Bob", state.clientName)
            assertEquals("Host", state.hostName)
            assertEquals(PlayerId.CLIENT, state.currentTurn)
            assertEquals(Status.IN_PROGRESS, state.status)
        }
    }

    @Test
    fun `hostRoll rolls and goes to awaiting placement on the host`() {
        val link = FakeGameLink()
        host(link, PlayerId.HOST) { gameHost ->
            gameHost.connect()
            link.receive(GameMessages.encodeName("Bob"))
            gameHost.hostRoll()
            val state = lastStateSent(link)
            assertEquals(Phase.AWAITING_PLACEMENT, state.phase)
            assertEquals(4, state.lastRoll)
            assertEquals(PlayerId.HOST, state.currentTurn)
        }
    }

    @Test
    fun `hostPlace places the die and hands the turn to the client`() {
        val link = FakeGameLink()
        host(link, PlayerId.HOST) { gameHost ->
            gameHost.connect()
            link.receive(GameMessages.encodeName("Bob"))
            gameHost.hostRoll()
            gameHost.hostPlace(2)
            val state = lastStateSent(link)
            assertEquals(PlayerId.CLIENT, state.currentTurn)
            assertEquals(Phase.IDLE, state.phase)
            assertEquals(listOf(4), state.grid[PlayerId.HOST]!![2])
        }
    }

    @Test
    fun `client roll and place messages drive the game`() {
        val link = FakeGameLink()
        host(link, PlayerId.HOST) { gameHost ->
            gameHost.connect()
            link.receive(GameMessages.encodeName("Bob"))
            gameHost.hostRoll()
            gameHost.hostPlace(0)
            // client's turn → client asks to roll
            link.receive(GameMessages.encodeRoll())
            assertTrue(
                lastStateSent(link).phase == Phase.AWAITING_PLACEMENT &&
                        lastStateSent(link).currentTurn == PlayerId.CLIENT
            )
            link.receive(GameMessages.encodePlace(1))
            val state = lastStateSent(link)
            assertEquals(PlayerId.HOST, state.currentTurn)
            assertEquals(listOf(4), state.grid[PlayerId.CLIENT]!![1])
        }
    }

    @Test
    fun `invalid messages are ignored`() {
        val link = FakeGameLink()
        host(link, PlayerId.CLIENT) { gameHost ->
            gameHost.connect()
            link.receive(GameMessages.encodeName("Bob"))
            // Client tries to roll on host's turn; also invalid place, also junk.
            gameHost.hostRoll() // host rolls on their turn (client's turn first!) → ignored
            assertEquals(1, link.sent.count { GameMessages.decodeState(it) != null })
            link.receive(GameMessages.encodeRoll())
            assertTrue(lastStateSent(link).currentTurn == PlayerId.CLIENT) // pollutes roll → wait, client turn
            link.receive("GARBAGE")
            link.receive("PLACE:9")
            assertEquals(3, link.sent.count { GameMessages.decodeState(it) != null })
        }
    }

    @Test
    fun `restart keeps names and switch first player and refreshes boards`() {
        val link = FakeGameLink()
        host(link, PlayerId.HOST) { gameHost ->
            gameHost.connect()
            link.receive(GameMessages.encodeName("Bob"))
            // force finished
            val finishedState = lastStateSent(link).copy(status = Status.FINISHED, winner = PlayerId.HOST)
            val gameState = GameHost::class.memberProperties.find { it.name == "state" } as? KMutableProperty<*>
            gameState?.isAccessible = true
            gameState?.setter?.call(gameHost, finishedState)

            gameHost.restart()
            assertEquals(PlayerId.CLIENT, gameHost.state.currentTurn)
            assertEquals("Host", gameHost.state.hostName)
            assertEquals("Bob", gameHost.state.clientName)

            val resetGrid = mapOf(
                PlayerId.HOST to emptyGrid(),
                PlayerId.CLIENT to emptyGrid(),
            )
            assertEquals(resetGrid, gameHost.state.grid)
        }
    }

    @Test
    fun `full game reaches a terminal state`() {
        val link = FakeGameLink()
        var finished: GameState? = null
        val counter = AtomicInteger(0)
        val gh = GameHost(
            link = link,
            hostName = "Host",
            rollValue = { (counter.getAndIncrement() % 6) + 1 },
            rollDelayMs = 0L,
            firstPlayer = { PlayerId.HOST },
            onState = { finished = it },
        )
        gh.connect()
        link.receive(GameMessages.encodeName("Bob"))
        var guard = 0
        while (finished?.status == Status.IN_PROGRESS && guard < 300) {
            val s: GameState = finished
            if (KnucklebonesRules.canRoll(s, s.currentTurn)) {
                if (s.currentTurn == PlayerId.HOST) gh.hostRoll() else link.receive(GameMessages.encodeRoll())
            } else {
                val col =
                    s.grid[s.currentTurn]!!.indices.first { !KnucklebonesRules.columnFull(s.grid[s.currentTurn]!!, it) }
                if (s.currentTurn == PlayerId.HOST) gh.hostPlace(col) else link.receive(GameMessages.encodePlace(col))
            }
            guard += 1
        }
        assertTrue("game should finish (steps=$guard)", finished != null && finished.status != Status.IN_PROGRESS)
        assertEquals(finished!!.status, if (finished.status == Status.DRAW) Status.DRAW else Status.FINISHED)
    }
}
