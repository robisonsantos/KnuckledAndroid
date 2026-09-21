package com.example.knucklegame.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {

    private class FakePrefs : Prefs {
        val map = mutableMapOf<String, Any>()
        override fun getBoolean(key: String, default: Boolean): Boolean =
            map[key] as? Boolean ?: default
        override fun putBoolean(key: String, value: Boolean) {
            map[key] = value
        }
    }

    @Test
    fun defaultsAreUnmutedAndOnboardingUnseen() {
        val s = Settings(FakePrefs())
        assertEquals(false, s.muted)
        assertEquals(false, s.onboardingSeen)
    }

    @Test
    fun mutePersists() {
        val prefs = FakePrefs()
        Settings(prefs).muted = true
        assertEquals(true, Settings(prefs).muted)
    }

    @Test
    fun onboardingSeenPersists() {
        val prefs = FakePrefs()
        Settings(prefs).onboardingSeen = true
        assertEquals(true, Settings(prefs).onboardingSeen)
    }
}