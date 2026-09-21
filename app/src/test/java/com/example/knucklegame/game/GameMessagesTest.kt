package com.example.knucklegame.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameMessagesTest {

    @Test
    fun `sanitizeName trims and strips control chars`() {
        assertEquals("Alice", GameMessages.sanitizeName("  Alice\n "))
        assertEquals("A b", GameMessages.sanitizeName("A\u0000 b"))
    }

    @Test
    fun `encodeName and decodeName round-trip`() {
        val encoded = GameMessages.encodeName("  Zulo  ")
        assertEquals("NAME:Zulo", encoded)
        assertEquals("Zulo", GameMessages.decodeName(encoded))
        assertNull(GameMessages.decodeName("STATE:{}"))
    }

    @Test
    fun `roll and restart markers match exactly`() {
        assertTrue(GameMessages.isRoll(GameMessages.encodeRoll()))
        assertTrue(GameMessages.isRestart(GameMessages.encodeRestart()))
        assertTrue(!GameMessages.isRoll("ROLL:0"))
        assertTrue(!GameMessages.isRestart("RESTARTING"))
    }

    @Test
    fun `encodePlace round-trips valid columns`() {
        for (col in 0..2) {
            val encoded = GameMessages.encodePlace(col)
            assertEquals(col, GameMessages.decodePlace(encoded))
        }
    }

    @Test
    fun `decodePlace rejects malformed lines`() {
        assertNull(GameMessages.decodePlace("PLACE"))
        assertNull(GameMessages.decodePlace("PLACE:abc"))
        assertNull(GameMessages.decodePlace("PLACE:3"))
        assertNull(GameMessages.decodePlace("ROLL"))
    }

    @Test
    fun `state round-trips all fields including destroyed refs`() {
        val state = GameState(
            hostName = "Host",
            clientName = "Client",
            status = Status.IN_PROGRESS,
            currentTurn = PlayerId.CLIENT,
            phase = Phase.AWAITING_PLACEMENT,
            grid = mapOf(
                PlayerId.HOST to listOf(listOf(4, 1, 4), emptyList(), emptyList()),
                PlayerId.CLIENT to listOf(emptyList(), listOf(6), emptyList()),
            ),
            lastRoll = 6,
            destroyed = listOf(DieRef(PlayerId.CLIENT, 0, 4), DieRef(PlayerId.CLIENT, 0, 4)),
        )
        val encoded = GameMessages.encodeState(state)
        val decoded = GameMessages.decodeState(encoded)
        assertEquals(state, decoded)
        assertNull(GameMessages.decodeState("STATE:not-json"))
        assertNull(GameMessages.decodeState("NAME:Host"))
    }
}
