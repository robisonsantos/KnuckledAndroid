package com.example.knucklegame.ui

import androidx.compose.runtime.compositionLocalOf
import com.example.knucklegame.audio.NoopSoundManager
import com.example.knucklegame.audio.SoundManager

val LocalSoundManager = compositionLocalOf<SoundManager> { NoopSoundManager }
val LocalAnimationsEnabled = compositionLocalOf { true }