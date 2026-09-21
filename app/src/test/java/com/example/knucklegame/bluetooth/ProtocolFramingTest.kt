package com.example.knucklegame.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ProtocolFramingTest {

    @Test
    fun encodeAppendsNewlineInUtf8() {
        assertArrayEquals(
            "hi\n".toByteArray(Charsets.UTF_8),
            Protocol.encode("hi")
        )
    }

    @Test
    fun encodeHandlesUnicode() {
        assertArrayEquals(
            "héllo 世界\n".toByteArray(Charsets.UTF_8),
            Protocol.encode("héllo 世界")
        )
    }

    @Test
    fun readLineReadsUtf8Lines() {
        val input = ByteArrayInputStream("one\ntwo\n".toByteArray(Charsets.UTF_8))
        assertEquals("one", Protocol.readLine(input))
        assertEquals("two", Protocol.readLine(input))
    }

    @Test
    fun readLineReturnsNullOnEof() {
        val input = ByteArrayInputStream("".toByteArray())
        assertNull(Protocol.readLine(input))
    }

    @Test
    fun readLineStripsTrailingCarriageReturn() {
        val input = ByteArrayInputStream("one\r\n".toByteArray(Charsets.UTF_8))
        assertEquals("one", Protocol.readLine(input))
    }

    @Test
    fun writeLineRoundTripsThroughStream() {
        val output = ByteArrayOutputStream()
        Protocol.writeLine(output, "hello")
        assertEquals("hello\n", output.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun readLineRejectsOverlongFrameWithoutThrowing() {
        val bytes = ("x".repeat(Protocol.MAX_FRAME_BYTES + 1) + "\n").toByteArray(Charsets.UTF_8)
        val input = ByteArrayInputStream(bytes)
        assertNull(Protocol.readLine(input))
    }
}