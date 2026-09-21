package com.example.knucklegame.bluetooth

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

interface GameLink {
    var onLine: (String) -> Unit
    var onClosed: () -> Unit
    fun send(line: String)
    fun close()
}

/**
 * Concrete reader/writer over an [InputStream]/[OutputStream] pair using [Protocol] framing.
 *
 * A reader thread continuously reads framed lines and dispatches them to [onLine]; when the peer
 * reaches EOF (or the reader hits a fatal I/O error) it fires [onClosed] and closes the link.
 * Lines received before [onLine] is attached are buffered and delivered once a handler is assigned
 * (register-after-start), so no line is ever dropped.
 */
class GameLinkImpl private constructor(
    private val input: InputStream,
    private val output: OutputStream,
) : GameLink {

    @Volatile
    override var onLine: (String) -> Unit = initialNoop
        set(value) {
            field = value
            synchronized(this) {
                drainPending(value)
            }
        }

    @Volatile
    override var onClosed: () -> Unit = {}

    private val pending = ArrayDeque<String>()
    private val running = AtomicBoolean(true)
    @Volatile
    private var closed = false

    @Synchronized
    fun attach(onLine: (String) -> Unit, onClosed: () -> Unit = {}) {
        this.onLine = onLine
        this.onClosed = onClosed
        drainPending(onLine)
    }

    @Synchronized
    override fun send(line: String) {
        check(!closed) { "GameLink is closed" }
        Protocol.writeLine(output, line)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        onClosed()
        running.set(false)
        try {
            input.close()
        } catch (_: IOException) {
        }
        try {
            output.close()
        } catch (_: IOException) {
        }
    }

    fun startReading() {
        Thread {
            while (running.get()) {
                val line = Protocol.readLine(input) ?: break
                val handler: (String) -> Unit = synchronized(this) {
                    val current = onLine
                    if (current === initialNoop) {
                        pending.addLast(line)
                        continue
                    }
                    current
                }
                handler(line)
            }
            closeSafelyFromReader()
        }.apply {
            name = "knucklegame-link-reader"
            isDaemon = true
            start()
        }
    }

    private fun closeSafelyFromReader() {
        close()
    }

    private fun drainPending(handler: (String) -> Unit) {
        while (pending.isNotEmpty()) {
            handler(pending.removeFirst())
        }
    }

    companion object {
        private val initialNoop: (String) -> Unit = {}

        fun create(
            input: InputStream,
            output: OutputStream,
            onLine: (String) -> Unit = initialNoop,
            onClosed: () -> Unit = {},
        ): GameLink {
            val link = GameLinkImpl(input, output)
            link.attach(onLine, onClosed)
            link.startReading()
            return link
        }
    }
}
