package com.example.knucklegame.game

import com.example.knucklegame.bluetooth.GameLink

object FakeGamePeer {
    const val NAME = "FakePeer"
    const val PIN = "1234"
    const val HOST_NAME = "FakeHost"
    const val REACT_DELAY_MS = 150L

    fun firstOpenColumn(grid: Grid): Int =
        grid.indices.first { !KnucklebonesRules.columnFull(grid, it) }
}

private fun delayed(delayMs: Long, block: () -> Unit) {
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
        name = "fake-peer"
        isDaemon = true
        start()
    }
}

/** Bot playing the client side: sends NAME, rolls on its turn, places in the first open column. */
fun runFakeClient(link: GameLink) {
    link.send(GameMessages.encodeName(FakeGamePeer.NAME))
    link.onLine = client@{ line ->
        val state = GameMessages.decodeState(line) ?: return@client
        if (KnucklebonesRules.canRoll(state, PlayerId.CLIENT)) {
            delayed(FakeGamePeer.REACT_DELAY_MS) { link.send(GameMessages.encodeRoll()) }
        }
        if (state.phase == Phase.AWAITING_PLACEMENT && state.currentTurn == PlayerId.CLIENT) {
            val col = FakeGamePeer.firstOpenColumn(state.grid[PlayerId.CLIENT]!!)
            delayed(FakeGamePeer.REACT_DELAY_MS) { link.send(GameMessages.encodePlace(col)) }
        }
    }
}

/** Bot playing the host side via a [GameHost], for when the app connects as a client in fake mode. */
fun runFakeHost(link: GameLink): GameHost {
    val counter = java.util.concurrent.atomic.AtomicInteger(0)
    val rollValue: () -> Int = { (counter.getAndIncrement() % 6) + 1 }
    lateinit var host: GameHost
    host = GameHost(
        link = link,
        hostName = FakeGamePeer.HOST_NAME,
        rollValue = rollValue,
        rollDelayMs = 50L,
        onState = { state ->
            if (KnucklebonesRules.canRoll(state, PlayerId.HOST)) {
                delayed(50L) { host.hostRoll() }
            }
            if (state.phase == Phase.AWAITING_PLACEMENT && state.currentTurn == PlayerId.HOST) {
                val col = FakeGamePeer.firstOpenColumn(state.grid[PlayerId.HOST]!!)
                delayed(50L) { host.hostPlace(col) }
            }
        },
    )
    host.connect()
    return host
}
