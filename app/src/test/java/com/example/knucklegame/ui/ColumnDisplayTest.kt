package com.example.knucklegame.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Top (peer) grid must stack from the bottom up:
 * first placed die stays at the bottom, newer dice stack on top.
 * Remaining dice "go down" in the same order after a removal.
 */
class ColumnDisplayTest {

    @Test
    fun `top column shows first die at bottom`() {
        assertEquals(
            listOf(null, null, 2),
            ColumnDisplay.topColumnTopToBottom(listOf(2)),
        )
    }

    @Test
    fun `top column stacks new die on top of existing`() {
        assertEquals(
            listOf(null, 3, 2),
            ColumnDisplay.topColumnTopToBottom(listOf(2, 3)),
        )
    }

    @Test
    fun `top column full keeps oldest at bottom newest on top`() {
        assertEquals(
            listOf(5, 3, 2),
            ColumnDisplay.topColumnTopToBottom(listOf(2, 3, 5)),
        )
    }

    @Test
    fun `top column remaining dice go down preserving order after removal`() {
        // Was [2, 3, 5] (bottom=2), opponent destroys 3s -> [2, 5] must sit at bottom.
        assertEquals(
            listOf(null, 5, 2),
            ColumnDisplay.topColumnTopToBottom(listOf(2, 5)),
        )
    }

    @Test
    fun `own column keeps existing top-anchored behaviour`() {
        assertEquals(
            listOf(2, null, null),
            ColumnDisplay.ownColumnTopToBottom(listOf(2)),
        )
        assertEquals(
            listOf(2, 3, null),
            ColumnDisplay.ownColumnTopToBottom(listOf(2, 3)),
        )
    }
}
