package com.example.knucklegame.bluetooth

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object Protocol {
    const val MAX_FRAME_BYTES = 1024
    const val DEFAULT_ENCODING = "UTF-8"
    const val RFCOMM_UUID = "00001101-0000-1000-8000-00805f9b34fb"
    const val BT_SERVICE_NAME = "KnuckleGame"

    fun encode(line: String): ByteArray = "$line\n".toByteArray(Charsets.UTF_8)

    fun writeLine(output: OutputStream, line: String) {
        output.write(encode(line))
        output.flush()
    }

    /** Reads one line (without the trailing '\n'); null on EOF, IOException, or overlong line. */
    fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val byte = try {
                input.read()
            } catch (_: IOException) {
                return null
            }
            if (byte == -1) return null
            if (byte == '\n'.code) {
                val text = buffer.toByteArray().toString(Charsets.UTF_8)
                return if (text.endsWith('\r')) text.dropLast(1) else text
            }
            buffer.write(byte)
            if (buffer.size() > MAX_FRAME_BYTES) return null
        }
    }
}

object PinGenerator {
    fun generate(): String =
        String.format(java.util.Locale.US, "%04d", kotlin.random.Random.nextInt(0, 10_000))
}