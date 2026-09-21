package com.example.knucklegame.game

import com.example.knucklegame.bluetooth.GameLink
import kotlin.random.Random

/** Pacing knobs that make the CPU feel human. All injectable in tests. */
object CpuPacing {
    const val PRE_ROLL_MS = 600L
    fun naturalThink(): Long = Random.nextLong(700L, 1101L)
}

fun delayed(delayMs: Long, block: () -> Unit) {
    Thread {
        try {
            Thread.sleep(delayMs)
        } catch (_: InterruptedException) {
        }
        try {
            block()
        } catch (_: Exception) {
            // link may be closed
        }
    }.apply {
        name = "cpu-ai"
        isDaemon = true
        start()
    }
}

/** CPU playing the client side: sends NAME, rolls on its turn, places via [CpuPlayer]. */
fun runCpuClient(
    link: GameLink,
    preRollDelayMs: Long = CpuPacing.PRE_ROLL_MS,
    thinkDelay: () -> Long = CpuPacing::naturalThink,
    choose: (GameState) -> Int = { CpuPlayer.chooseColumn(it) },
) {
    link.onLine = cpu@{ line ->
        val state = GameMessages.decodeState(line) ?: return@cpu
        if (KnucklebonesRules.canRoll(state, PlayerId.CLIENT)) {
            delayed(preRollDelayMs) { link.send(GameMessages.encodeRoll()) }
        }
        if (state.phase == Phase.AWAITING_PLACEMENT && state.currentTurn == PlayerId.CLIENT) {
            val col = choose(state)
            delayed(thinkDelay()) { link.send(GameMessages.encodePlace(col)) }
        }
    }
    link.send(GameMessages.encodeName(CpuPlayer.NAME))
}
