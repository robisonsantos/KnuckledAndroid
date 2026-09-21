package com.example.knucklegame.bluetooth

import com.example.knucklegame.game.FakeGamePeer
import com.example.knucklegame.game.runFakeClient
import com.example.knucklegame.game.runFakeHost
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.LinkedBlockingQueue

class FakeBluetoothConnector : BluetoothConnector {

    private val peers = mutableListOf<GameLink>()

    override fun listen(pin: String): GameLink {
        require(pin == FakeGamePeer.PIN) { "Invalid PIN" }
        val (server, client) = pipe()
        val hostLink = GameLinkImpl.create(server.first, server.second)
        val clientLink = GameLinkImpl.create(client.first, client.second)
        peers.add(clientLink)
        runFakeClient(clientLink)
        return hostLink
    }

    override fun connect(device: DeviceInfo, pin: String): GameLink {
        require(pin == FakeGamePeer.PIN) { "Invalid PIN" }
        val (server, client) = pipe()
        val clientLink = GameLinkImpl.create(server.first, server.second)
        val hostLink = GameLinkImpl.create(client.first, client.second)
        peers.add(hostLink)
        runFakeHost(hostLink)
        return clientLink
    }

    override fun discover(): List<DeviceInfo> =
        listOf(DeviceInfo("Fake Peer", "00:11:22:33:44:55"))

    private fun pipe(): Pair<Pair<InputStream, OutputStream>, Pair<InputStream, OutputStream>> {
        val hostInput = PipedInputStream(4096)
        val rawClientOut = PipedOutputStream(hostInput)
        val clientInput = PipedInputStream(4096)
        val rawHostOut = PipedOutputStream(clientInput)
        // Wrap outputs so that all Piped writes happen on a long-lived thread,
        // avoiding Piped's "Write end dead" when short-lived FakeGamePeer threads die.
        val clientOutput = AsyncOutputStream(rawClientOut)
        val hostOutput = AsyncOutputStream(rawHostOut)
        val server: Pair<InputStream, OutputStream> = Pair(hostInput, hostOutput)
        val client: Pair<InputStream, OutputStream> = Pair(clientInput, clientOutput)
        return Pair(server, client)
    }

    private class AsyncOutputStream(private val delegate: OutputStream) : OutputStream() {
        private val queue = LinkedBlockingQueue<ByteArray>()
        @Volatile
        private var closed = false
        private val writer = Thread {
            try {
                while (true) {
                    val data = queue.take()
                    if (data.isEmpty() && closed) break
                    if (data.isNotEmpty()) {
                        delegate.write(data)
                        delegate.flush()
                    }
                    if (data.isEmpty() && closed) break
                }
            } catch (_: InterruptedException) {
            } catch (_: IOException) {
            } finally {
                try { delegate.close() } catch (_: IOException) {}
            }
        }.apply {
            name = "fake-pipe-writer-${System.identityHashCode(this)}"
            isDaemon = true
            start()
        }

        override fun write(b: Int) {
            if (closed) return
            queue.put(byteArrayOf(b.toByte()))
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (closed) return
            val copy = b.copyOfRange(off, off + len)
            queue.put(copy)
        }

        override fun flush() {
            // delegate flush happens on writer thread after each chunk
        }

        override fun close() {
            if (closed) return
            closed = true
            queue.put(ByteArray(0))
        }
    }
}
