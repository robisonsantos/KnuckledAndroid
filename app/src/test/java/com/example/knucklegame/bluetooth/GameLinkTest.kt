package com.example.knucklegame.bluetooth

import com.example.knucklegame.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

class GameLinkTest {

    @Test
    fun sendDeliversToPeerOnLine() {
        val (linkReader, peerWriter) = pipe()
        val received = mutableListOf<String>()
        val link = GameLinkImpl.create(linkReader, PipedOutputStream(), { received.add(it) })

        peerWriter.write("roll 5\n".toByteArray(Charsets.UTF_8))
        await { received == listOf("roll 5") }
        assertEquals(listOf("roll 5"), received)

        link.close()
    }

    @Test
    fun onLineReceivesUnicodeLine() {
        val (linkReader, peerWriter) = pipe()
        val received = mutableListOf<String>()
        val link = GameLinkImpl.create(linkReader, PipedOutputStream(), onLine = { received.add(it) })

        Protocol.writeLine(peerWriter, "héllo 世界")
        await { received.size == 1 }
        assertEquals(listOf("héllo 世界"), received)

        link.close()
    }

    @Test
    fun bidirectionalMessagesRoundTrip() {
        val (linkAReader, aToB) = pipe()
        val (linkBReader, bToA) = pipe()
        val a = GameLinkImpl.create(linkAReader, bToA, onLine = { _ -> })
        val b = GameLinkImpl.create(linkBReader, aToB, onLine = { _ -> })
        val aReceived = mutableListOf<String>()
        val bReceived = mutableListOf<String>()
        a.onLine = { aReceived.add(it) }
        b.onLine = { bReceived.add(it) }

        for (i in 1..50) {
            a.send("a->b $i")
            b.send("b->a $i")
        }
        await { bReceived.size == 50 }
        await { aReceived.size == 50 }
        assertEquals(aReceived, (1..50).map { "b->a $it" })
        assertEquals(bReceived, (1..50).map { "a->b $it" })

        a.close()
        b.close()
    }

    @Test
    fun onLineCanBeReassignedMidSession() {
        val (linkReader, peerWriter) = pipe()
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        val link = GameLinkImpl.create(linkReader, PipedOutputStream(), onLine = { first.add(it) })

        Protocol.writeLine(peerWriter, "one")
        await { first.contains("one") }

        link.onLine = { second.add(it) }
        Protocol.writeLine(peerWriter, "two")
        await { second.contains("two") }

        assertFalse(first.contains("two"))
        assertEquals(listOf("one"), first)
        assertEquals(listOf("two"), second)

        link.close()
    }

    @Test
    fun linesReceivedBeforeOnLineAttachedAreBuffered() {
        val (linkReader, peerWriter) = pipe()
        val received = mutableListOf<String>()
        val link = GameLinkImpl.create(linkReader, PipedOutputStream())

        Protocol.writeLine(peerWriter, "early 1")
        Thread.sleep(50)
        link.onLine = { received.add(it) }

        await { received == listOf("early 1") }
        assertEquals(listOf("early 1"), received)

        link.close()
    }

    @Test
    fun onClosedFiresWhenPeerReachesEof() {
        val (linkReader, linkWriter) = pipe()
        val link = GameLinkImpl.create(linkReader, PipedOutputStream(), onLine = { _ -> })
        val closed = booleanArrayOf(false)
        link.onClosed = { closed[0] = true }

        linkWriter.close()
        await { closed[0] }
        assertTrue(closed[0])

        link.close()
    }

    @Test
    fun closeIsIdempotent() {
        val (linkReader, linkWriter) = pipe()
        var closedCount = 0
        val link = GameLinkImpl.create(linkReader, linkWriter, {}, onClosed = { closedCount++ })

        link.close()
        link.close()
        link.close()

        assertEquals(1, closedCount)
    }

    @Test
    fun sendThrowsAfterClose() {
        val link = GameLinkImpl.create(PipedInputStream(), PipedOutputStream(), onLine = { _ -> })
        link.close()

        assertThrows(IllegalStateException::class.java) { link.send("hello") }
    }

    private fun pipe(): Pair<InputStream, OutputStream> {
        val input = PipedInputStream()
        return input to PipedOutputStream(input)
    }
}