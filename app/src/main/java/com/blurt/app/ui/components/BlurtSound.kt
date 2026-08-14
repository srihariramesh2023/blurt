package com.blurt.app.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.blurt.app.R

/**
 * Blurt's sound map — design/BLURT-DESIGN-STANDARD.md §6. Feedback, not
 * music: short, precise, never looped. Phase 1 ships generated ticks and a
 * soft chime; custom audio can replace these without touching call sites.
 */
object BlurtSound {

    private var pool: SoundPool? = null
    private var tickStart = 0
    private var tickStop = 0
    private var chimeSave = 0
    private var toneError = 0

    private const val VOLUME = 0.55f

    /** Load the sounds once, lazily, the first time they're needed. */
    @Synchronized
    fun init(context: Context) {
        if (pool != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(attributes)
            .build()
        tickStart = p.load(context, R.raw.tick_start, 1)
        tickStop = p.load(context, R.raw.tick_stop, 1)
        chimeSave = p.load(context, R.raw.chime_save, 1)
        toneError = p.load(context, R.raw.tone_error, 1)
        pool = p
    }

    fun playStart() = play(tickStart)
    fun playStop() = play(tickStop)
    fun playSave() = play(chimeSave)
    fun playError() = play(toneError)

    private fun play(soundId: Int) {
        if (soundId == 0) return
        pool?.play(soundId, VOLUME, VOLUME, 1, 0, 1f)
    }
}
