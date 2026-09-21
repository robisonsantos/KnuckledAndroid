package com.example.knucklegame.game

object CpuPlayer {
    const val NAME = "CPU"

    private const val WIN = 1_000_000.0
    private const val LOCK_BONUS = 0.10
    private const val RISK_WEIGHT = 0.05

    /** Pick the column for the already-rolled die that maximises expected final score. */
    fun chooseColumn(state: GameState, me: PlayerId = PlayerId.CLIENT): Int {
        val grid = state.grid[me] ?: return 0
        val open = grid.indices.filter { !KnucklebonesRules.columnFull(grid, it) }
        if (open.isEmpty()) return 0
        val search = Search(me)
        var best = open.first()
        var bestValue = Double.NEGATIVE_INFINITY
        for (c in open) {
            val after = KnucklebonesRules.place(state, me, c)
            val v = search.value(after, depthLimit(state))
            if (v > bestValue) {
                bestValue = v
                best = c
            }
        }
        return best
    }

    /** Deeper search the closer the boards are to full. */
    private fun depthLimit(state: GameState): Int {
        val placed = state.grid.values.sumOf { g -> g.sumOf { it.size } }
        return when {
            placed >= 12 -> 6
            placed >= 6 -> 4
            else -> 3
        }
    }

    /** Expectimax: max nodes are the CPU's placements, min nodes the human's, chance nodes the die. */
    private class Search(private val me: PlayerId) {
        private val memo = HashMap<String, Double>()

        fun value(state: GameState, depth: Int): Double =
            when (state.status) {
                Status.FINISHED -> if (state.winner == me) WIN else -WIN
                Status.DRAW -> 0.0
                Status.IN_PROGRESS -> searchValue(state, depth)
            }

        private fun searchValue(state: GameState, depth: Int): Double {
            if (depth <= 0) return evaluate(state)
            val key = stateKey(state, depth)
            memo[key]?.let { return it }
            val result = when (state.phase) {
                Phase.IDLE -> {
                    val roller = state.currentTurn
                    var sum = 0.0
                    for (v in 1..6) {
                        val rolled = KnucklebonesRules.completeRoll(
                            KnucklebonesRules.beginRoll(state, roller),
                            roller,
                            v,
                        )
                        sum += value(rolled, depth)
                    }
                    sum / 6.0
                }
                Phase.AWAITING_PLACEMENT -> {
                    val mover = state.currentTurn
                    val moverGrid = state.grid[mover]!!
                    val open = moverGrid.indices.filter { !KnucklebonesRules.columnFull(moverGrid, it) }
                    val children = open.map { value(KnucklebonesRules.place(state, mover, it), depth - 1) }
                    if (mover == me) children.max() else children.min()
                }
                Phase.ROLLING -> evaluate(state)
            }
            memo[key] = result
            return result
        }

        private fun evaluate(state: GameState): Double {
            val mine = state.grid[me]!!
            val theirs = state.grid[state.opponentOf(me)]!!
            var e = (KnucklebonesRules.totalScore(mine) - KnucklebonesRules.totalScore(theirs)).toDouble()
            for (i in mine.indices) {
                if (KnucklebonesRules.columnFull(mine, i)) {
                    e += KnucklebonesRules.columnScore(mine[i]) * LOCK_BONUS
                }
                if (!KnucklebonesRules.columnFull(theirs, i)) {
                    for ((value, count) in mine[i].groupingBy { it }.eachCount()) {
                        if (count >= 2) e -= value * count * count * RISK_WEIGHT
                    }
                }
            }
            return e
        }

        private fun stateKey(state: GameState, depth: Int): String {
            val host = state.grid[PlayerId.HOST]!!
            val client = state.grid[PlayerId.CLIENT]!!
            return "$depth|${state.currentTurn}|${state.phase}|${state.lastRoll}|" +
                host.joinToString(";") { it.joinToString(",") } + "|" +
                client.joinToString(";") { it.joinToString(",") }
        }
    }
}
