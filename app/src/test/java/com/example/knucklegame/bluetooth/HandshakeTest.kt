package com.example.knucklegame.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.atomic.AtomicReference

class HandshakeTest {

    @Test
    fun correctPinSucceedsOnBothEnds() {
        val (aToBReader, aToBWriter) = pipe()
        val (bToAReader, bToAWriter) = pipe()
        val pin = "4321"
        val hostResult = AtomicReference<Boolean?>()
        val clientResult = AtomicReference<Boolean?>()
        val host = Thread { hostResult.set(Handshake.accept(bToAReader, aToBWriter, pin)) }
        val client = Thread { clientResult.set(Handshake.initiate(aToBReader, bToAWriter, pin)) }
        host.start(); client.start()
        host.join(2000); client.join(2000)
        assertFalse(host.isAlive)
        assertFalse(client.isAlive)
        assertEquals(true, hostResult.get())
        assertEquals(true, clientResult.get())
    }

    @Test
    fun wrongPinFailsOnBothEnds() {
        val (aToBReader, aToBWriter) = pipe()
        val (bToAReader, bToAWriter) = pipe()
        val hostResult = AtomicReference<Boolean?>()
        val clientResult = AtomicReference<Boolean?>()
        val host = Thread { hostResult.set(Handshake.accept(bToAReader, aToBWriter, "4321")) }
        val client = Thread { clientResult.set(Handshake.initiate(aToBReader, bToAWriter, "0000")) }
        host.start(); client.start()
        host.join(2000); client.join(2000)
        assertFalse(host.isAlive)
        assertFalse(client.isAlive)
        assertEquals(false, hostResult.get())
        assertEquals(false, clientResult.get())
    }

    @Test
    fun acceptReturnsFalseWhenPeerIsEof() {
        val result = Handshake.accept(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream(), "1234")
        assertFalse(result)
    }

    @Test
    fun initiateReturnsFalseWhenPeerSendsNoReply() {
        val result = Handshake.initiate(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream(), "1234")
        assertFalse(result)
    }

    @Test
    fun initiateReturnsFalseWhenPeerSendsInvalid() {
        val result = Handshake.initiate(ByteArrayInputStream("INVALID\n".toByteArray()), ByteArrayOutputStream(), "1234")
        assertFalse(result)
    }

    private fun pipe(): Pair<InputStream, OutputStream> {
        val input = PipedInputStream()
        return input to PipedOutputStream(input)
    }
}