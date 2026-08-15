package com.blurt.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The [LazyListState] shared between the sharp and frosted copies of a tab
 * screen (Home / Library / Search). The frosted bottom bar renders a blurred
 * duplicate of the screen content; both copies must scroll in perfect sync,
 * so the shell owns one state and hands it down through this local. Screens
 * outside the shell fall back to their own remembered state.
 */
val LocalBlurtListState = compositionLocalOf<LazyListState?> { null }

/**
 * The [ScrollState] shared between the sharp and frosted copies of the
 * vertically-scrolling tab screens (Home, Profile/Settings). Same contract
 * as [LocalBlurtListState] — one state, two synchronized copies.
 */
val LocalBlurtScrollState = compositionLocalOf<ScrollState?> { null }

/**
 * The bottom inset the floating tab bar reserves, in dp: the system gesture
 * inset + the pill's float gap + the pill height + a little air. Tab screens
 * read this so the last row of a list — or the bottom of a hero — always
 * clears the bar instead of hiding underneath it.
 */
val LocalTabBarInset = compositionLocalOf<Dp> { 0.dp }
