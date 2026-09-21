package com.example.knucklegame.bluetooth

import java.io.InputStream
import java.io.OutputStream

object Handshake {
    const val PIN_PREFIX = "PIN:"
    const val OK = "OK"
    const val INVALID = "INVALID"

    /** Host side: expect a PIN line, verify against [expectedPin], reply OK/INVALID. */
    fun accept(input: InputStream, output: OutputStream, expectedPin: String): Boolean {
        val line = Protocol.readLine(input) ?: return false
        val matches = line.removePrefix(PIN_PREFIX) == expectedPin
        Protocol.writeLine(output, if (matches) OK else INVALID)
        return matches
    }

    /** Client side: send [pin], wait for OK/INVALID. */
    fun initiate(input: InputStream, output: OutputStream, pin: String): Boolean {
        Protocol.writeLine(output, "$PIN_PREFIX$pin")
        return Protocol.readLine(input) == OK
    }
}