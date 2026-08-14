package com.blurt.app.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Blurt's haptic map — design/BLURT-DESIGN-STANDARD.md §6. Feedback is
 * multimodal: visual + haptic (+ sound) at the same instant. Effects are
 * short and precise, never decorative.
 *
 *   Mic press (record start)  → light tick
 *   Record stop               → double tick
 *   Analysis / confirm sheet  → medium pulse
 *   Blurt saved               → soft success tap
 *   Error / rejected key      → error buzz
 *   Tab switch                → none (quiet)
 */
class BlurtHaptics(private val view: View) {

    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun doubleTick() {
        tick()
        view.postDelayed({ tick() }, 90L)
    }

    fun pulse() {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun success() {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    fun error() {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }
}

@Composable
fun rememberBlurtHaptics(): BlurtHaptics {
    val view = LocalView.current
    return remember { BlurtHaptics(view) }
}
