package com.blurt.app.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the app resolves its appearance. */
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

/**
 * Persists the user's theme choice in a tiny preferences file and exposes it
 * as a StateFlow, so a change applies immediately and survives restarts.
 */
class ThemePreferences(context: Context) {

    private val prefs = context.getSharedPreferences("blurt_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _onboardingComplete = MutableStateFlow(prefs.getBoolean(ONBOARDING_KEY, false))
    val onboardingComplete: StateFlow<Boolean> = _onboardingComplete.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY, mode.name).apply()
    }

    fun completeOnboarding() {
        _onboardingComplete.value = true
        prefs.edit().putBoolean(ONBOARDING_KEY, true).apply()
    }

    private companion object {
        const val KEY = "theme_mode"
        const val ONBOARDING_KEY = "onboarding_complete"
    }
}
