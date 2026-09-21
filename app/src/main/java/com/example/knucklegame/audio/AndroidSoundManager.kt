package com.example.knucklegame.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.example.knucklegame.R
import com.example.knucklegame.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidSoundManager(
    context: Context,
    private val settings: Settings,
) : SoundManager {
    private val appContext = context.applicationContext
    private val state = MutableStateFlow(settings.muted)
    override val muted = state.asStateFlow()

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids: Map<SoundEvent, Int> = mapOf(
        SoundEvent.TAP to pool.load(appContext, R.raw.tap, 1),
        SoundEvent.RATTLE to pool.load(appContext, R.raw.rattle, 1),
        SoundEvent.LAND to pool.load(appContext, R.raw.land, 1),
        SoundEvent.WIN to pool.load(appContext, R.raw.win, 1),
        SoundEvent.LOSE to pool.load(appContext, R.raw.lose, 1),
    )
    private var loopStreamId: Int? = null

    override fun setMuted(muted: Boolean) {
        settings.muted = muted
        state.value = muted
        if (muted) stopLoop()
    }

    override fun play(event: SoundEvent) {
        if (state.value) return
        ids[event]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    override fun startLoop(event: SoundEvent) {
        if (state.value) return
        stopLoop()
        ids[event]?.let { loopStreamId = pool.play(it, 1f, 1f, 1, -1, 1f) }
    }

    override fun stopLoop() {
        loopStreamId?.let { pool.stop(it) }
        loopStreamId = null
    }

    override fun release() {
        stopLoop()
        pool.release()
    }
}