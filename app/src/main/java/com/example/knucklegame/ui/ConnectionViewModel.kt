package com.example.knucklegame.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.knucklegame.BuildConfig
import com.example.knucklegame.bluetooth.AndroidBluetoothConnector
import com.example.knucklegame.bluetooth.BluetoothConnector
import com.example.knucklegame.bluetooth.DeviceInfo
import com.example.knucklegame.bluetooth.FakeBluetoothConnector
import com.example.knucklegame.game.FakeGamePeer
import com.example.knucklegame.bluetooth.GameLink
import com.example.knucklegame.bluetooth.LocalConnector
import com.example.knucklegame.bluetooth.PinGenerator
import com.example.knucklegame.game.CpuPacing
import com.example.knucklegame.game.GameMessages

private const val TAG = "Knuckled"

sealed interface ConnectionState {
    data object Start : ConnectionState
    data class Hosting(val pin: String) : ConnectionState
    data object Discovering : ConnectionState
    data class EnterPin(val device: DeviceInfo) : ConnectionState
    data class Connected(val link: GameLink, val peer: DeviceInfo?, val isHost: Boolean) : ConnectionState
}

class ConnectionViewModel(application: Application) : AndroidViewModel(application) {

    private val main = Handler(Looper.getMainLooper())

    private val androidConnector = AndroidBluetoothConnector(application)
    private val fakeConnector = FakeBluetoothConnector()

    var inFakeMode by mutableStateOf(false)
        private set
    var state by mutableStateOf<ConnectionState>(ConnectionState.Start)
        private set
    var statusText by mutableStateOf("")
        private set
    var hostPin by mutableStateOf("")
        private set
    var playerName by mutableStateOf("")
        private set
    var sanitizedPlayerName by mutableStateOf("")
        private set
    var isHost by mutableStateOf(false)
        private set
    var foundDevices by mutableStateOf<List<DeviceInfo>>(emptyList())
        private set
    var selectedDevice by mutableStateOf<DeviceInfo?>(null)
        private set
    /** CPU pacing knobs (debug/test overrides, defaults feel human). */
    var cpuPreRollDelayMs: Long = CpuPacing.PRE_ROLL_MS
    var cpuThinkDelay: () -> Long = CpuPacing::naturalThink
    var errorText by mutableStateOf<String?>(null)
        private set
    var currentLink by mutableStateOf<GameLink?>(null)
        private set
    var currentPeer by mutableStateOf<DeviceInfo?>(null)
        private set

    private var activeConnector: BluetoothConnector = androidConnector

    fun onPlayerNameChange(name: String) {
        playerName = name
    }

    fun toggleFakeMode() {
        if (!BuildConfig.DEBUG) return
        inFakeMode = !inFakeMode
        activeConnector = if (inFakeMode) fakeConnector else androidConnector
        reset()
    }

    fun setFakeMode(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        if (inFakeMode == enabled) return
        toggleFakeMode()
    }

    fun onHostClicked() {
        val sanitized = GameMessages.sanitizeName(playerName)
        if (sanitized.isBlank()) {
            showError("Enter your name")
            return
        }
        sanitizedPlayerName = sanitized
        isHost = true
        errorText = null
        hostPin = if (inFakeMode) FakeGamePeer.PIN else PinGenerator.generate()
        statusText = "Waiting for device..."
        state = ConnectionState.Hosting(hostPin)
        Thread {
            try {
                val link = activeConnector.listen(hostPin)
                main.post { onConnected(link, peer = null, isHost = true) }
            } catch (t: Throwable) {
                Log.e(TAG, "listen failed", t)
                main.post {
                    val msg = t.message ?: "Could not start listening."
                    showError(msg)
                    resetForError()
                }
            }
        }.apply {
            name = "dicegame-listen"
            isDaemon = true
            start()
        }
    }

    fun startSinglePlayer() {
        val sanitized = GameMessages.sanitizeName(playerName)
        if (sanitized.isBlank()) {
            showError("Enter your name")
            return
        }
        sanitizedPlayerName = sanitized
        isHost = true
        errorText = null
        Thread {
            try {
                val connector = LocalConnector(preRollDelayMs = cpuPreRollDelayMs, thinkDelay = cpuThinkDelay)
                val link = connector.listen("single")
                main.post { onConnected(link, peer = null, isHost = true) }
            } catch (t: Throwable) {
                Log.e(TAG, "single-player start failed", t)
                main.post {
                    resetForError()
                    showError(t.message ?: "Could not start single-player game.")
                }
            }
        }.apply {
            name = "dicegame-single"
            isDaemon = true
            start()
        }
    }

    fun onDiscoverClicked() {
        val sanitized = GameMessages.sanitizeName(playerName)
        if (sanitized.isBlank()) {
            showError("Enter your name")
            return
        }
        sanitizedPlayerName = sanitized
        isHost = false
        errorText = null
        foundDevices = emptyList()
        statusText = "Searching for devices..."
        state = ConnectionState.Discovering
        Thread {
            try {
                val devices = activeConnector.discover()
                main.post {
                    foundDevices = devices
                    statusText = if (devices.isEmpty()) "No devices found — tap Host first (visible ~2 min)" else ""
                }
            } catch (t: Throwable) {
                Log.e(TAG, "discover failed", t)
                main.post {
                    showError(t.message ?: "Discovery failed.")
                    resetForError()
                }
            }
        }.apply {
            name = "dicegame-discover"
            isDaemon = true
            start()
        }
    }

    fun onDeviceSelected(device: DeviceInfo) {
        selectedDevice = device
        statusText = ""
        errorText = null
        state = ConnectionState.EnterPin(device)
    }

    fun onPinEntered(pin: String) {
        val device = selectedDevice ?: return
        errorText = null
        statusText = "Connecting..."
        Thread {
            try {
                val link = activeConnector.connect(device, pin)
                main.post { onConnected(link, peer = device, isHost = false) }
            } catch (t: Throwable) {
                Log.e(TAG, "connect failed", t)
                main.post {
                    val raw = t.message ?: "Could not connect."
                    val msg = if (raw.contains("PIN", ignoreCase = true) || raw.contains("handshake", ignoreCase = true)) {
                        "Wrong code. Try again."
                    } else raw
                    showError(msg)
                    resetForError()
                }
            }
        }.apply {
            name = "dicegame-connect"
            isDaemon = true
            start()
        }
    }

    fun onBack() {
        cancelCurrent()
    }

    fun cancelCurrent() {
        currentLink?.let { link ->
            try { link.close() } catch (_: Throwable) {}
        }
        resetForError()
    }

    fun disconnect() {
        currentLink?.let { link ->
            try {
                link.onClosed = {}
                link.close()
            } catch (_: Throwable) {}
        }
        currentLink = null
        currentPeer = null
        reset()
        statusText = "Disconnected"
    }

    fun onPeerDisconnected() {
        currentLink?.let { link ->
            try { link.close() } catch (_: Throwable) {}
        }
        currentLink = null
        currentPeer = null
        reset()
        statusText = "Peer disconnected"
    }

    fun dismissError() {
        errorText = null
    }

    fun showError(message: String) {
        errorText = message
    }

    private fun onConnected(link: GameLink, peer: DeviceInfo?, isHost: Boolean) {
        currentLink?.let { try { it.close() } catch (_: Throwable) {} }
        currentLink = link
        currentPeer = peer
        this.isHost = isHost
        statusText = ""
        errorText = null
        state = ConnectionState.Connected(link, peer, isHost)
    }

    private fun resetForError() {
        state = ConnectionState.Start
        statusText = ""
        hostPin = ""
        foundDevices = emptyList()
        selectedDevice = null
    }

    private fun reset() {
        currentLink?.let { try { it.close() } catch (_: Throwable) {} }
        state = ConnectionState.Start
        statusText = ""
        hostPin = ""
        foundDevices = emptyList()
        selectedDevice = null
        isHost = false
    }

    override fun onCleared() {
        try { currentLink?.close() } catch (_: Throwable) {}
    }
}
