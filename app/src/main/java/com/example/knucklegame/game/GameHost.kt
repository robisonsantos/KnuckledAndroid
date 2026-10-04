package com.example.knucklegame.game

import com.example.knucklegame.bluetooth.GameLink
import kotlin.random.Random

class GameHost(
    private val link: GameLink,
    private val hostName: String,
    private val rollValue: () -> Int = { (1..6).random() },
    private val rollDelayMs: Long = 2000,
    private val firstPlayer: () -> PlayerId = { if (Random.nextBoolean()) PlayerId.HOST else PlayerId.CLIENT },
    private val onState: (GameState) -> Unit = {},
) {
    private var first: PlayerId? = null

    var state: GameState = KnucklebonesRules.reset(hostName, "?", PlayerId.HOST)
        private set

    fun connect() {
        link.onLine = { line -> onLine(line) }
    }

    private fun onLine(line: String) {
        val name = GameMessages.decodeName(line)
        when {
            name != null -> {
                first = firstPlayer()
                state = KnucklebonesRules.reset(hostName, GameMessages.sanitizeName(name), first!!)
                publish()
            }

            GameMessages.isRoll(line) -> rollFor(PlayerId.CLIENT)
            GameMessages.decodePlace(line) != null -> placeFor(PlayerId.CLIENT, GameMessages.decodePlace(line)!!)
            GameMessages.isRestart(line) -> restart()
        }
    }

    fun hostRoll() {
        rollFor(PlayerId.HOST)
    }

    fun hostPlace(column: Int) {
        placeFor(PlayerId.HOST, column)
    }

    private fun rollFor(player: PlayerId) {
        if (!KnucklebonesRules.canRoll(state, player)) return
        state = KnucklebonesRules.beginRoll(state, player)
        publish()
        Thread.sleep(rollDelayMs)
        state = KnucklebonesRules.completeRoll(state, player, rollValue())
        publish()
    }

    private fun placeFor(player: PlayerId, column: Int) {
        if (!KnucklebonesRules.canPlace(state, player, column)) return
        state = KnucklebonesRules.place(state, player, column)
        publish()
    }

    fun restart() {
        if (!KnucklebonesRules.canRestart(state)) return
        val firstPlayerNow = if (first == PlayerId.HOST) PlayerId.CLIENT else PlayerId.HOST
        first = firstPlayerNow
        state = KnucklebonesRules.reset(state.hostName, state.clientName, firstPlayerNow)
        publish()
    }

    private fun publish() {
        state.let {
            link.send(GameMessages.encodeState(it))
            onState(it)
        }
    }
}
