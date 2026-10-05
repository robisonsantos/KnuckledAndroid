package com.example.knucklegame.bluetooth

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/** Blocking duplex byte pipe fed by GATT callbacks (both directions share one
 *  pipe, mirroring the iOS BlePipe). Write path chunks by MTU (20-byte
 *  fallback); read path is framed downstream by GameLinkImpl/Protocol
 *  (1024 cap, overlong fatal). Observable behavior matches iOS. */
class BleBytePipe(
    @Volatile var mtu: Int = 23,
    private val onChunk: (ByteArray) -> Unit,
) {
    private val queue = LinkedBlockingQueue<ByteArray>()
    private val closed = AtomicBoolean(false)
    private var current: ByteArray? = null
    private var offset = 0

    fun chunkSize(): Int = if (mtu <= 0) 20 else maxOf(mtu - 3, 1)

    fun feed(chunk: ByteArray) {
        if (!closed.get()) queue.put(chunk.copyOf())
    }

    fun readByte(): Int {
        while (true) {
            val cur = current
            if (cur != null && offset < cur.size) {
                val b = cur[offset++].toInt() and 0xFF
                if (offset >= cur.size) {
                    current = null
                    offset = 0
                }
                return b
            }
            if (closed.get()) return -1
            val next = queue.take()
            if (next.isEmpty()) return -1
            current = next
            offset = 0
        }
    }

    fun close() {
        if (closed.compareAndSet(false, true)) queue.put(ByteArray(0))
    }

    fun inputStream(): InputStream = object : InputStream() {
        override fun read(): Int = this@BleBytePipe.readByte()
    }

    fun outputStream(): OutputStream = object : OutputStream() {
        private val pending = ByteArrayOutputStream()

        override fun write(b: Int) {
            val toSend: ByteArray? = synchronized(this) {
                pending.write(b)
                if (b == '\n'.code) takePending() else null
            }
            if (toSend != null) sendChunks(toSend)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            val toSend: ByteArray? = synchronized(this) {
                pending.write(b, off, len)
                takePending()
            }
            if (toSend != null) sendChunks(toSend)
        }

        override fun flush() {
            val toSend: ByteArray? = synchronized(this) { takePending() }
            if (toSend != null) sendChunks(toSend)
        }

        private fun takePending(): ByteArray? {
            if (pending.size() == 0) return null
            val bytes = pending.toByteArray()
            pending.reset()
            return bytes
        }

        private fun sendChunks(bytes: ByteArray) {
            if (closed.get()) return
            val size = chunkSize()
            var i = 0
            while (i < bytes.size) {
                val j = minOf(i + size, bytes.size)
                onChunk(bytes.copyOfRange(i, j))
                i = j
            }
        }
    }
}
