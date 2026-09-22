package com.example.knucklegame.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.knucklegame.bluetooth.GameLink
import com.example.knucklegame.game.GameHost
import com.example.knucklegame.game.GameMessages
import com.example.knucklegame.game.GameState
import com.example.knucklegame.game.KnucklebonesRules
import com.example.knucklegame.game.Phase
import com.example.knucklegame.game.PlayerId
import com.example.knucklegame.game.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "Knuckled"

class GameViewModel(
    private val link: GameLink,
    val myId: PlayerId,
    private val hostName: String? = null,
    private val clientName: String? = null,
    private val rollValue: () -> Int = { (1..6).random() },
    private val rollDelayMs: Long = 2000L,
    private val onPeerDisconnected: (() -> Unit)? = null,
) : ViewModel() {

    private val _state = MutableStateFlow<GameState?>(null)
    val state: StateFlow<GameState?> = _state.asStateFlow()

    private val _errorText = MutableStateFlow<String?>(null)
    val errorText: StateFlow<String?> = _errorText.asStateFlow()

    private val _peerDisconnected = MutableStateFlow(false)
    val peerDisconnected: StateFlow<Boolean> = _peerDisconnected.asStateFlow()

    private var host: GameHost? = null

    /** My turn and awaiting a roll. */
    val canRollNow: Boolean
        get() {
            val s = _state.value ?: return false
            return KnucklebonesRules.canRoll(s, myId)
        }

    /** My turn and the die has landed; I must place it. */
    val canPlaceNow: Boolean
        get() {
            val s = _state.value ?: return false
            return s.status == Status.IN_PROGRESS &&
                s.phase == Phase.AWAITING_PLACEMENT &&
                s.currentTurn == myId
        }

    init {
        if (myId == PlayerId.HOST) {
            val gameHost = GameHost(
                link = link,
                hostName = hostName ?: "Host",
                rollValue = rollValue,
                rollDelayMs = rollDelayMs,
                onState = { _state.value = it },
            )
            host = gameHost
            gameHost.connect()
            link.onClosed = { handlePeerClosed() }
        } else {
            try {
                link.send(GameMessages.encodeName(clientName ?: "Client"))
            } catch (e: Exception) {
                Log.e(TAG, "send NAME failed", e)
                _errorText.value = e.message ?: "Failed to send name"
            }
            link.onLine = { line ->
                val decoded = GameMessages.decodeState(line)
                if (decoded != null) _state.value = decoded
            }
            link.onClosed = { handlePeerClosed() }
        }
    }

    fun onDiceTap() {
        if (myId == PlayerId.HOST) {
            val h = host ?: return
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    h.hostRoll()
                } catch (e: Exception) {
                    Log.e(TAG, "hostRoll failed", e)
                    _errorText.value = e.message ?: "Roll failed"
                }
            }
        } else {
            try {
                link.send(GameMessages.encodeRoll())
            } catch (e: Exception) {
                Log.e(TAG, "send ROLL failed", e)
                _errorText.value = e.message ?: "Failed to send roll"
            }
        }
    }

    fun onPlaceColumn(column: Int) {
        if (myId == PlayerId.HOST) {
            val h = host ?: return
            viewModelScope.launch(Dispatchers.Default) {
                try {
                    h.hostPlace(column)
                } catch (e: Exception) {
                    Log.e(TAG, "hostPlace failed", e)
                    _errorText.value = e.message ?: "Place failed"
                }
            }
        } else {
            try {
                link.send(GameMessages.encodePlace(column))
            } catch (e: Exception) {
                Log.e(TAG, "send PLACE failed", e)
                _errorText.value = e.message ?: "Failed to send placement"
            }
        }
    }

    fun onPlayAgain() {
        if (myId == PlayerId.HOST) {
            val h = host ?: return
            viewModelScope.launch(Dispatchers.Default) {
                try {
                    h.restart()
                } catch (e: Exception) {
                    Log.e(TAG, "host restart failed", e)
                    _errorText.value = e.message ?: "Restart failed"
                }
            }
        } else {
            try {
                link.send(GameMessages.encodeRestart())
            } catch (e: Exception) {
                Log.e(TAG, "send RESTART failed", e)
                _errorText.value = e.message ?: "Failed to restart"
            }
        }
    }

    fun dismissError() {
        _errorText.value = null
    }

    fun disconnect() {
        try {
            link.onClosed = {}
            link.close()
        } catch (e: Exception) {
            Log.e(TAG, "disconnect close failed", e)
        }
    }

    private fun handlePeerClosed() {
        Log.e(TAG, "Peer disconnected")
        _peerDisconnected.value = true
        _errorText.value = "Peer disconnected"
        try {
            onPeerDisconnected?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "onPeerDisconnected callback failed", e)
        }
    }

    override fun onCleared() {
        try {
            link.close()
        } catch (_: Exception) {
        }
    }
}
