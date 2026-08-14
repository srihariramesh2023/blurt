package com.blurt.app.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.compositionLocalOf

/**
 * The [LazyListState] shared between the sharp and frosted copies of a tab
 * screen (Home / Library / Search). The frosted bottom bar renders a blurred
 * duplicate of the screen content; both copies must scroll in perfect sync,
 * so the shell owns one state and hands it down through this local. Screens
 * outside the shell fall back to their own remembered state.
 */
val LocalBlurtListState = compositionLocalOf<LazyListState?> { null }
