package com.example.knucklegame.bluetooth

import com.example.knucklegame.game.FakeGamePeer
import com.example.knucklegame.game.runFakeClient
import com.example.knucklegame.game.runFakeHost

class FakeBluetoothConnector : BluetoothConnector {

    private val peers = mutableListOf<GameLink>()

    override fun listen(pin: String): GameLink {
        require(pin == FakeGamePeer.PIN) { "Invalid PIN" }
        val (hostLink, clientLink) = LocalPipe.gameLinkPair()
        peers.add(clientLink)
        runFakeClient(clientLink)
        return hostLink
    }

    override fun connect(device: DeviceInfo, pin: String): GameLink {
        require(pin == FakeGamePeer.PIN) { "Invalid PIN" }
        val (clientLink, hostLink) = LocalPipe.gameLinkPair()
        peers.add(hostLink)
        runFakeHost(hostLink)
        return clientLink
    }

    override fun discover(): List<DeviceInfo> =
        listOf(DeviceInfo("Fake Peer", "00:11:22:33:44:55"))
}
