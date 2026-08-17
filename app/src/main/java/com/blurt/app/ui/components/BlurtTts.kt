package com.blurt.app.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Blurt's spoken voice — the companion reply.
 *
 * Two-tier by design: when a **Fish Audio** key is saved (BYOK, free tier),
 * the reply is spoken by Fish's natural neural voice (model s2.1-pro-free),
 * returned as raw 24 kHz 16-bit PCM and played straight through AudioTrack.
 * Without a Fish key, or when the provider fails, it falls back to the
 * device's built-in TextToSpeech engine so the conversation never stalls.
 *
 * Every path reports completion exactly once ([onDone] fires when the
 * utterance finishes or when nothing could be spoken) — the flow advances
 * either way.
 */
class BlurtTts(
    context: Context,
    private val fishKeyProvider: () -> String? = { null },
) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var engine: TextToSpeech? = null
    private var ready = false
    private var initFailed = false
    private var pending: PendingSpeak? = null
    private var utteranceId = 0
    private var audioTrack: AudioTrack? = null

    /** Bumped on stop/shutdown so in-flight synthesis never plays stale audio. */
    private var generation = 0

    private data class PendingSpeak(val text: String, val onDone: () -> Unit)

    /**
     * Speaks [text]; [onDone] fires exactly once — when the utterance
     * finishes, or immediately when nothing could be spoken.
     */
    fun speak(text: String, onDone: () -> Unit) {
        if (text.isBlank()) {
            onDone()
            return
        }
        generation++
        val gen = generation
        val apiKey = fishKeyProvider()
        if (!apiKey.isNullOrBlank()) {
            android.util.Log.d(TAG, "speak: fish voice (key present)")
            scope.launch {
                if (gen != generation) return@launch
                val synthResult = runCatching { synthFish(text, apiKey) }
                if (gen != generation) return@launch
                val pcm = synthResult.getOrNull()
                if (pcm == null || pcm.isEmpty()) {
                    synthResult.exceptionOrNull()?.let {
                        android.util.Log.w(TAG, "fish synth failed: ${it::class.simpleName}: ${it.message}")
                    }
                    android.util.Log.w(TAG, "fish synth empty/absent — device fallback")
                    mainHandler.post { if (gen == generation) speakWithDevice(text, onDone) }
                } else {
                    android.util.Log.d(TAG, "fish synth ok: ${pcm.size} bytes — playing")
                    runCatching { playPcm(pcm, gen, onDone) }
                        .onFailure {
                            android.util.Log.w(TAG, "fish playback failed — device fallback: ${it.message}")
                            mainHandler.post { if (gen == generation) speakWithDevice(text, onDone) }
                        }
                }
            }
        } else {
            android.util.Log.d(TAG, "speak: device fallback (no fish key)")
            speakWithDevice(text, onDone)
        }
    }

    /**
     * Initializes the device engine early (when capture starts) so a fallback
     * reply doesn't pay the engine's cold-start cost on top of everything.
     */
    fun warmUp() {
        if (fishKeyProvider().isNullOrBlank()) ensureInit()
    }

    /** Stops the current utterance and drops any queued one. */
    @Synchronized
    fun stop() {
        generation++
        pending = null
        engine?.stop()
        audioTrack?.stop()
    }

    /** Releases everything — call when the capture screen goes away. */
    @Synchronized
    fun shutdown() {
        generation++
        mainHandler.removeCallbacksAndMessages(null)
        pending = null
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
        initFailed = false
        audioTrack?.release()
        audioTrack = null
        scope.cancel()
    }

    // --- Fish Audio TTS (the primary voice) ---------------------------------

    /**
     * One-shot synthesis via Fish Audio's free tier. Returns raw 24 kHz
     * 16-bit mono PCM — exactly what the AudioTrack player expects.
     */
    private fun synthFish(text: String, apiKey: String): ByteArray {
        val body = JSONObject()
            .put("text", text)
            .put("reference_id", FISH_VOICE_ID)
            .put("format", "pcm")
            .put("sample_rate", PCM_RATE)
            .put("latency", "low")
        val connection = URL(FISH_ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("model", FISH_MODEL)
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val err = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                throw IllegalStateException("Fish TTS HTTP $code: ${err.take(200)}")
            }
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun buildTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            PCM_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(PCM_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf.coerceAtLeast(minBuf * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    /** Plays a full PCM clip and reports completion (duration math + air). */
    private fun playPcm(pcm: ByteArray, gen: Int, onDone: () -> Unit) {
        val track = buildTrack()
        audioTrack = track
        track.play()
        track.write(pcm, 0, pcm.size)
        val durationMs = pcm.size.toLong() * 1000 / (PCM_RATE * 2) + 250
        mainHandler.postDelayed(
            {
                runCatching { track.release() }
                if (audioTrack === track) audioTrack = null
                if (gen == generation) onDone()
            },
            durationMs,
        )
    }

    // --- Device TTS (the safety net) ---------------------------------------

    @Synchronized
    private fun ensureInit() {
        if (engine != null) return
        engine = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.getDefault()
                ready = true
                pending?.let { p ->
                    pending = null
                    speakNow(p.text, p.onDone)
                }
            } else {
                // No usable engine (not installed / disabled) — never hang.
                android.util.Log.w(TAG, "device TTS init failed: $status")
                initFailed = true
                pending?.let { p ->
                    pending = null
                    mainHandler.postDelayed(p.onDone, NO_ENGINE_PAUSE_MS)
                }
            }
        }
    }

    private fun speakWithDevice(text: String, onDone: () -> Unit) {
        ensureInit()
        if (initFailed) {
            // No engine at all — complete after a beat so the reply still
            // shows on screen and the flow advances.
            mainHandler.postDelayed(onDone, NO_ENGINE_PAUSE_MS)
            return
        }
        if (!ready) {
            pending = PendingSpeak(text, onDone)
            return
        }
        speakNow(text, onDone)
    }

    private fun speakNow(text: String, onDone: () -> Unit) {
        val tts = engine
        if (tts == null) {
            onDone()
            return
        }
        val id = "blurt_${utteranceId++}"
        val finished = AtomicBoolean(false)
        val finish = {
            if (finished.compareAndSet(false, true)) mainHandler.post { onDone() }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finish()
            override fun onError(utteranceId: String?) = finish()
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish()
        })
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        // Safety net: never let the conversation stall on a silent engine.
        mainHandler.postDelayed(finish, FALLBACK_COMPLETE_MS)
    }

    private companion object {
        const val TAG = "BlurtTts"
        const val FALLBACK_COMPLETE_MS = 8_000L
        const val NO_ENGINE_PAUSE_MS = 900L
        const val FISH_ENDPOINT = "https://api.fish.audio/v1/tts"
        const val FISH_MODEL = "s2.1-pro-free"
        const val FISH_VOICE_ID = "bf322df2096a46f18c579d0baa36f41d"
        const val PCM_RATE = 24_000
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 20_000
    }
}