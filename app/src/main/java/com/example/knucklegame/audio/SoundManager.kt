package com.example.knucklegame.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SoundEvent { TAP, RATTLE, LAND, WIN, LOSE }

interface SoundManager {
    val muted: StateFlow<Boolean>
    fun setMuted(muted: Boolean)
    fun play(event: SoundEvent)
    fun startLoop(event: SoundEvent)
    fun stopLoop()
    fun release()
}

object NoopSoundManager : SoundManager {
    private val state = MutableStateFlow(false)
    override val muted: StateFlow<Boolean> = state.asStateFlow()
    override fun setMuted(muted: Boolean) {}
    override fun play(event: SoundEvent) {}
    override fun startLoop(event: SoundEvent) {}
    override fun stopLoop() {}
    override fun release() {}
}

class FakeSoundManager : SoundManager {
    private val state = MutableStateFlow(false)
    override val muted: StateFlow<Boolean> = state.asStateFlow()
    val played = mutableListOf<SoundEvent>()
    var looping: SoundEvent? = null
    override fun setMuted(muted: Boolean) { state.value = muted }
    override fun play(event: SoundEvent) { played += event }
    override fun startLoop(event: SoundEvent) { looping = event }
    override fun stopLoop() { looping = null }
    override fun release() {}
}