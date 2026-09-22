package com.example.knucklegame.ui

/**
 * Pure layout helpers for the 3-high die columns.
 *
 * Game state stores each column in placement order: index 0 is the oldest die.
 * Both boards grow outward from the middle (oldest die stays closest to the
 * middle roll area):
 * - own (bottom) board: top row is closest to the middle → top-anchored,
 *   [oldest, ..., null].
 * - peer (top) board: bottom row is closest to the middle → bottom-anchored
 *   with oldest at the bottom, [null, ..., newest, ..., oldest].
 */
object ColumnDisplay {
    fun ownColumnTopToBottom(dice: List<Int>): List<Int?> {
        val rows = MutableList<Int?>(3) { null }
        for (row in 0 until 3) {
            rows[row] = if (row < dice.size) dice[row] else null
        }
        return rows
    }

    fun topColumnTopToBottom(dice: List<Int>): List<Int?> {
        val rows = MutableList<Int?>(3) { null }
        val emptyRows = 3 - dice.size
        for (row in 0 until 3) {
            rows[row] = if (row < emptyRows) {
                null
            } else {
                dice[dice.size - 1 - (row - emptyRows)]
            }
        }
        return rows
    }
}
