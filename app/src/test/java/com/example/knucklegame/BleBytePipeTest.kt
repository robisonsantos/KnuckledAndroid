package com.example.knucklegame

import com.example.knucklegame.bluetooth.BleBytePipe
import com.example.knucklegame.bluetooth.Protocol
import org.junit.Assert.*
import org.junit.Test
import kotlin.concurrent.thread

class BleBytePipeTest {

    @Test
    fun chunkSizeDefaults() {
        val pipe = BleBytePipe(mtu = 23) {}
        assertEquals(20, pipe.chunkSize())
        assertEquals(182, BleBytePipe(mtu = 185) {}.chunkSize())
        assertEquals(20, BleBytePipe(mtu = 0) {}.chunkSize())
    }

    @Test
    fun writeSplitsAndReadLineRejoins() {
        val sent = mutableListOf<ByteArray>()
        val pipe = BleBytePipe(mtu = 23, onChunk = { sent.add(it) })
        pipe.outputStream().use { out ->
            out.write("ROLL\n".toByteArray(Charsets.UTF_8))
            out.flush()
        }
        assertEquals(1, sent.size)
        sent.forEach { pipe.feed(it) }
        assertEquals("ROLL", Protocol.readLine(pipe.inputStream()))
    }

    @Test
    fun longLineSplitsToMtuChunks() {
        val sent = mutableListOf<ByteArray>()
        val pipe = BleBytePipe(mtu = 23, onChunk = { sent.add(it) })
        val line = "x".repeat(50).toByteArray(Charsets.UTF_8)
        pipe.outputStream().use { it.write(line) }
        assertEquals(3, sent.size)
        assertTrue(sent.all { it.size <= 20 })
        assertArrayEquals(line, sent.reduce { a, b -> a + b })
    }

    @Test
    fun closeUnblocksReaderAndDropsWrites() {
        var writes = 0
        val pipe = BleBytePipe(onChunk = { writes++ })
        pipe.close()
        pipe.outputStream().use { it.write("x\n".toByteArray()) }
        assertEquals(0, writes)
        assertEquals(-1, pipe.inputStream().read())
        pipe.close()
    }

    @Test
    fun overlongFrameIsFatalLikeProtocol() {
        val pipe = BleBytePipe(onChunk = {})
        pipe.feed(ByteArray(1025) { 'x'.code.toByte() })
        pipe.feed("\n".toByteArray())
        assertNull(Protocol.readLine(pipe.inputStream()))
    }

    @Test
    fun blockedReadWakesOnFeedFromAnotherThread() {
        val pipe = BleBytePipe(onChunk = {})
        var got: Int? = null
        val t = thread(isDaemon = true) { got = pipe.inputStream().read() }
        Thread.sleep(100)
        pipe.feed(byteArrayOf('A'.code.toByte()))
        t.join(2000)
        assertEquals('A'.code, got)
    }
}
