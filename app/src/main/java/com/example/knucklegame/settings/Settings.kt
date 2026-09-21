package com.example.knucklegame.settings

import androidx.core.content.edit

interface Prefs {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}

class AndroidPrefs(private val prefs: android.content.SharedPreferences) : Prefs {
    override fun getBoolean(key: String, default: Boolean): Boolean =
        prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
    }
}

class Settings(private val prefs: Prefs) {
    var muted: Boolean
        get() = prefs.getBoolean("muted", false)
        set(value) = prefs.putBoolean("muted", value)

    var onboardingSeen: Boolean
        get() = prefs.getBoolean("onboarding_seen", false)
        set(value) = prefs.putBoolean("onboarding_seen", value)
}