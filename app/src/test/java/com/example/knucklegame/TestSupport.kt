package com.example.knucklegame

import com.example.knucklegame.bluetooth.GameLink
import org.junit.Assert.fail

fun await(timeoutMs: Long = 2000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
        if (System.currentTimeMillis() > deadline) fail("Timed out waiting for condition")
        Thread.sleep(20)
    }
}

/** In-memory [GameLink] recording sent lines; call [onLine] to simulate the peer. */
class FakeGameLink : GameLink {
    override var onLine: (String) -> Unit = {}
    override var onClosed: () -> Unit = {}
    val sent = mutableListOf<String>()

    override fun send(line: String) {
        sent += line
    }

    override fun close() {
        onClosed()
    }

    fun receive(line: String) {
        onLine(line)
    }

    val lastState: com.example.knucklegame.game.GameState?
        get() = sent.lastOrNull()
            ?.let { com.example.knucklegame.game.GameMessages.decodeState(it) }
}
