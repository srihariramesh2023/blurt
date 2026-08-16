package com.blurt.app.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Blurt's spoken voice — the companion reply.
 *
 * Two-tier by design: when a **Gemini key** is saved (BYOK, free tier), the
 * reply is spoken by Google's own TTS model — a natural, warm voice, the same
 * family as ChatGPT voice mode. Audio is **streamed** so the voice starts
 * while the model is still generating, instead of after the whole clip is
 * ready. Without a key, or when the network / provider fails, it falls back
 * to the device's built-in TextToSpeech engine so the conversation never
 * stalls.
 *
 * Every path reports completion exactly once ([onDone] fires when the
 * utterance finishes or when nothing could be spoken) — the flow advances
 * either way.
 */
class BlurtTts(
    context: Context,
    private val geminiKeyProvider: () -> String? = { null },
    private val packageName: String = "",
    private val certSha1: String = "",
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
        val apiKey = geminiKeyProvider()
        if (!apiKey.isNullOrBlank()) {
            android.util.Log.d(TAG, "speak: gemini voice (key present)")
            scope.launch {
                if (gen != generation) return@launch
                // Streaming first — audio plays while the model generates,
                // which is what makes the reply feel conversational.
                val streamResult = runCatching { synthStream(text, apiKey, gen, onDone) }
                if (gen != generation) return@launch
                if (streamResult.getOrDefault(false)) return@launch
                val streamError = streamResult.exceptionOrNull()
                streamError?.let {
                    android.util.Log.w(TAG, "gemini stream failed: ${it::class.simpleName}: ${it.message}")
                }
                // The free tier caps TTS hard — when quota is hit, reply as
                // text and move on instead of stalling on a robot voice.
                if (streamError?.isQuota() == true) {
                    android.util.Log.w(TAG, "gemini TTS quota — text-only reply")
                    mainHandler.postDelayed({ if (gen == generation) onDone() }, TEXT_ONLY_PAUSE_MS)
                    return@launch
                }
                // One-shot fallback for transient failures (not quota).
                val synthResult = runCatching { synth(text, apiKey) }
                if (gen != generation) return@launch
                if (synthResult.exceptionOrNull()?.isQuota() == true) {
                    android.util.Log.w(TAG, "gemini TTS quota — text-only reply")
                    mainHandler.postDelayed({ if (gen == generation) onDone() }, TEXT_ONLY_PAUSE_MS)
                    return@launch
                }
                synthResult.exceptionOrNull()?.let {
                    android.util.Log.w(TAG, "gemini synth failed: ${it::class.simpleName}: ${it.message}")
                }
                val pcm = synthResult.getOrNull()
                if (pcm == null || pcm.isEmpty()) {
                    android.util.Log.w(TAG, "gemini synth empty/absent — device fallback")
                    mainHandler.post { if (gen == generation) speakWithDevice(text, onDone) }
                } else {
                    android.util.Log.d(TAG, "gemini synth ok: ${pcm.size} bytes — playing")
                    runCatching { playPcm(pcm, gen, onDone) }
                        .onFailure {
                            android.util.Log.w(TAG, "gemini playback failed — device fallback: ${it.message}")
                            mainHandler.post { if (gen == generation) speakWithDevice(text, onDone) }
                        }
                }
            }
        } else {
            android.util.Log.d(TAG, "speak: device fallback (no gemini key)")
            speakWithDevice(text, onDone)
        }
    }

    /**
     * Initializes the device engine early (when capture starts) so a fallback
     * reply doesn't pay the engine's cold-start cost on top of everything.
     */
    fun warmUp() {
        if (geminiKeyProvider().isNullOrBlank()) ensureInit()
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

    // --- Gemini TTS (the primary voice) -------------------------------------

    /**
     * Streaming synthesis: plays audio chunks as they arrive, so the voice
     * starts within a beat of the reply text instead of after the whole clip
     * has been generated. Returns true once audio was played (completion was
     * scheduled); false when nothing could be played.
     */
    private fun synthStream(text: String, apiKey: String, gen: Int, onDone: () -> Unit): Boolean {
        val body = JSONObject()
            .put("model", MODEL)
            .put("input", text)
            .put("response_format", JSONObject().put("type", "audio"))
            .put(
                "generation_config",
                JSONObject().put(
                    "speech_config",
                    JSONArray().put(JSONObject().put("voice", VOICE)),
                ),
            )
            .put("stream", true)
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            applyAuth(connection, apiKey)
            connection.connectTimeout = 8_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val err = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                throw IllegalStateException("Gemini TTS stream HTTP $code: ${err.take(200)}")
            }
            val track = buildTrack()
            audioTrack = track
            var playedBytes = 0L
            try {
                track.play()
                val reader = connection.inputStream.bufferedReader(Charsets.UTF_8)
                while (true) {
                    val line = reader.readLine() ?: break
                    val payload = line.trim().removePrefix("data:").trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue
                    val delta = runCatching { JSONObject(payload).optJSONObject("delta") }.getOrNull() ?: continue
                    if (delta.optString("type") != "audio") continue
                    val chunk = Base64.decode(delta.optString("data"), Base64.DEFAULT)
                    if (chunk.isNotEmpty()) {
                        track.write(chunk, 0, chunk.size)
                        playedBytes += chunk.size
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "gemini stream read failed: ${e.message}")
            }
            if (playedBytes == 0L) {
                runCatching { track.release() }
                if (audioTrack === track) audioTrack = null
                return false
            }
            // The model streams faster than real time, so the track buffers a
            // fraction of the clip — give it a beat to drain, then finish.
            val drainMs = (playedBytes * 1000 / (PCM_RATE * 2)).coerceAtMost(2_000) + 300
            mainHandler.postDelayed(
                {
                    runCatching { track.release() }
                    if (audioTrack === track) audioTrack = null
                    if (gen == generation) onDone()
                },
                drainMs,
            )
            return true
        } finally {
            connection.disconnect()
        }
    }

    /**
     * One-shot synthesis (non-streaming fallback). Returns the raw 24 kHz
     * 16-bit mono PCM.
     */
    private fun synth(text: String, apiKey: String): ByteArray {
        val body = JSONObject()
            .put("model", MODEL)
            .put("input", text)
            .put("response_format", JSONObject().put("type", "audio"))
            .put(
                "generation_config",
                JSONObject().put(
                    "speech_config",
                    JSONArray().put(JSONObject().put("voice", VOICE)),
                ),
            )

        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            applyAuth(connection, apiKey)
            connection.connectTimeout = 8_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("Gemini TTS HTTP $code: ${raw.take(200)}")
            }
            val root = JSONObject(raw)
            val data = findAudio(root)
                ?: throw IllegalStateException("Gemini TTS: no audio in response: ${raw.take(600)}")
            return Base64.decode(data, Base64.DEFAULT)
        } finally {
            connection.disconnect()
        }
    }

    private fun applyAuth(connection: HttpURLConnection, apiKey: String) {
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("x-goog-api-key", apiKey)
        // Android-app key restriction headers — the same ones the analyzers
        // send, so a key limited to this app validates here too.
        if (packageName.isNotBlank() && certSha1.isNotBlank()) {
            connection.setRequestProperty("X-Android-Package", packageName)
            connection.setRequestProperty("X-Android-Cert", certSha1)
        }
    }

    /**
     * Finds the base64 audio in a non-streaming Interactions response. The
     * audio can live in a few places depending on the API version: a
     * top-level `output_audio` block, `steps[i].output_audio`, the current
     * shape `steps[i].content` (audio-only parts, each with a `data` field),
     * or the older `steps[i].output` typed-part array (`type == "audio"`).
     */
    private fun findAudio(root: JSONObject): String? {
        root.optJSONObject("output_audio")?.optString("data")?.takeIf { it.isNotBlank() }?.let { return it }
        val steps = root.optJSONArray("steps") ?: return null
        for (i in 0 until steps.length()) {
            val step = steps.optJSONObject(i) ?: continue
            step.optJSONObject("output_audio")?.optString("data")?.takeIf { it.isNotBlank() }?.let { return it }
            step.optJSONArray("content")?.let { parts ->
                for (j in 0 until parts.length()) {
                    val data = parts.optJSONObject(j)?.optString("data")
                    if (!data.isNullOrBlank()) return data
                }
            }
            val output = step.optJSONArray("output") ?: continue
            for (j in 0 until output.length()) {
                val part = output.optJSONObject(j) ?: continue
                if (part.optString("type") == "audio") {
                    val data = part.optString("data")
                    if (data.isNotBlank()) return data
                }
            }
        }
        return null
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
        const val TEXT_ONLY_PAUSE_MS = 1_600L
        const val MODEL = "gemini-3.1-flash-tts-preview"
        const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions"
        const val VOICE = "Kore"
        const val PCM_RATE = 24_000

        fun Throwable.isQuota(): Boolean = message?.contains("HTTP 429") == true
    }
}
