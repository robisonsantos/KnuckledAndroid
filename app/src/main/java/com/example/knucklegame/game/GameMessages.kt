package com.example.knucklegame.game

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object GameMessages {
    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    fun sanitizeName(name: String): String =
        name.trim().filterNot { it.isISOControl() }

    fun encodeName(name: String): String = "NAME:${sanitizeName(name)}"
    fun encodeRoll(): String = "ROLL"
    fun encodePlace(column: Int): String = "PLACE:$column"
    fun encodeRestart(): String = "RESTART"
    fun encodeState(state: GameState): String = "STATE:${json.encodeToString(state)}"

    fun decodeName(line: String): String? = line.let {
        if (it.startsWith("NAME:")) it.removePrefix("NAME:") else null
    }

    fun isRoll(line: String): Boolean = line == "ROLL"
    fun isRestart(line: String): Boolean = line == "RESTART"

    /** Decodes `PLACE:<col>` to the column index, or null for anything invalid. */
    fun decodePlace(line: String): Int? {
        if (!line.startsWith("PLACE:")) return null
        val col = line.removePrefix("PLACE:").toIntOrNull() ?: return null
        return if (col in 0..KnucklebonesRules.COLUMNS - 1) col else null
    }

    fun decodeState(line: String): GameState? {
        if (!line.startsWith("STATE:")) return null
        return try {
            val text = line.removePrefix("STATE:")
            json.decodeFromString<GameState>(text)
        } catch (_: Exception) {
            null
        }
    }
}
