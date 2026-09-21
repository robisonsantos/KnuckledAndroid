package com.example.knucklegame.bluetooth

import com.example.knucklegame.game.CpuPacing
import com.example.knucklegame.game.runCpuClient

/** [BluetoothConnector] for single-player: hosts a local session with the CPU as the peer. */
class LocalConnector(
    private val preRollDelayMs: Long = CpuPacing.PRE_ROLL_MS,
    private val thinkDelay: () -> Long = CpuPacing::naturalThink,
) : BluetoothConnector {

    override fun listen(pin: String): GameLink {
        val (humanLink, cpuLink) = LocalPipe.gameLinkPair()
        runCpuClient(cpuLink, preRollDelayMs = preRollDelayMs, thinkDelay = thinkDelay)
        return humanLink
    }

    override fun connect(device: DeviceInfo, pin: String): GameLink =
        throw UnsupportedOperationException("Single-player does not connect to devices")

    override fun discover(): List<DeviceInfo> =
        throw UnsupportedOperationException("Single-player has nothing to discover")
}
