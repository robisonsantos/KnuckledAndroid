package com.example.knucklegame.bluetooth

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.LinkedBlockingQueue

/** Builds a pair of in-process [GameLink]s connected through pipes, so a bot can play both
 * sides of a session with no transport. */
object LocalPipe {

    /** Returns two [GameLink]s whose streams form a loopback pair. */
    fun gameLinkPair(): Pair<GameLink, GameLink> {
        val (first, second) = pipe()
        return Pair(
            GameLinkImpl.create(first.first, first.second),
            GameLinkImpl.create(second.first, second.second),
        )
    }

    private fun pipe(): Pair<Pair<InputStream, OutputStream>, Pair<InputStream, OutputStream>> {
        val firstInput = PipedInputStream(4096)
        val rawSecondOut = PipedOutputStream(firstInput)
        val secondInput = PipedInputStream(4096)
        val rawFirstOut = PipedOutputStream(secondInput)
        // Wrap outputs so all Piped writes happen on a long-lived thread, avoiding
        // Piped's "Write end dead" when short-lived bot threads die.
        return Pair(
            Pair(firstInput, AsyncOutputStream(rawFirstOut)),
            Pair(secondInput, AsyncOutputStream(rawSecondOut)),
        )
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
            name = "local-pipe-writer-${System.identityHashCode(this)}"
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
            // delegate flush happens on the writer thread after each chunk
        }

        override fun close() {
            if (closed) return
            closed = true
            queue.put(ByteArray(0))
        }
    }
}
