package com.example.knucklegame.bluetooth

import org.junit.Assert.assertTrue
import org.junit.Test

class PinGeneratorTest {

    @Test
    fun generatesFourDigitPinsAcrossRange() {
        repeat(1000) {
            val pin = PinGenerator.generate()
            assertTrue("pin should be 4 digits, was '$pin'", pin.length == 4 && pin.all { it.isDigit() })
            val value = pin.toInt()
            assertTrue("pin out of numeric range, was '$pin'", value in 0..9999)
        }
    }
}