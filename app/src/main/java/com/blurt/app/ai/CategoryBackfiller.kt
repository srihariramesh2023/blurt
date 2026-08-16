package com.blurt.app.ai

import com.blurt.app.auth.AuthState
import com.blurt.app.data.CaptureRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Blurts saved before the analyzer existed — or saved while offline — have no
 * category. On every sign-in this quietly reads them, one at a time with a
 * small pause to respect the free tier's per-minute limit, and writes the
 * category back. New blurts are categorized at save time; this only catches
 * up the stragglers, in the background, never blocking the UI.
 */
class CategoryBackfiller(
    private val scope: CoroutineScope,
    private val repository: CaptureRepository,
    private val analyzer: CaptureAnalyzer?,
    private val authState: StateFlow<AuthState>,
) {
    private var job: Job? = null

    fun start() {
        job = scope.launch {
            authState.collect { state ->
                val uid = (state as? AuthState.SignedIn)?.user?.uid
                if (uid != null && analyzer != null) backfill(uid)
            }
        }
    }

    private suspend fun backfill(uid: String) {
        val analyzer = analyzer ?: return
        val unanalyzed = repository.getUnanalyzed(uid)
        android.util.Log.d(TAG, "backfill: ${unanalyzed.size} unanalyzed blurts")
        unanalyzed.forEach { capture ->
            delay(DELAY_BETWEEN_CALLS_MS)
            val analysis = runCatching {
                analyzer.analyze(capture.content, System.currentTimeMillis())
            }.getOrNull()?.firstOrNull()
            if (analysis == null) {
                android.util.Log.w(TAG, "backfill: analyzer returned null for id=${capture.id}")
                return@forEach
            }
            android.util.Log.d(TAG, "backfill: id=${capture.id} -> ${analysis.intent}/${analysis.category}")
            repository.setAnalysis(capture.id, uid, analysis.category, analysis.intent)
        }
        android.util.Log.d(TAG, "backfill: done")
    }

    private companion object {
        const val DELAY_BETWEEN_CALLS_MS = 2_000L
        const val TAG = "BlurtBackfill"
    }

}
