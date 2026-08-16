package com.blurt.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.blurt.app.auth.AuthUser
import com.blurt.app.ui.capture.CaptureScreen
import com.blurt.app.ui.detail.DetailScreen
import com.blurt.app.ui.home.HomeScreen
import com.blurt.app.ui.library.LibraryScreen
import com.blurt.app.ui.profile.ProfileScreen
import com.blurt.app.ui.search.SearchScreen
import com.blurt.app.ui.theme.BlurtMotion
import com.blurt.app.ui.theme.ThemeMode
import com.blurt.app.ui.theme.rememberReduceMotion
import com.blurt.app.ui.voice.VoiceScreen

/** The four bottom-tab destinations. */
private val TAB_ROUTES = setOf(
    BlurtRoutes.HOME,
    BlurtRoutes.SEARCH,
    BlurtRoutes.LIBRARY,
    BlurtRoutes.SETTINGS,
)

/** Voice, capture and detail are pushed on top of the tabs (iOS stack). */
private fun isPushRoute(route: String?): Boolean = route != null && (
    route.startsWith(BlurtRoutes.VOICE) ||
        route.startsWith("capture") ||
        route.startsWith("detail")
    )

private fun isTabRoute(route: String?): Boolean = route in TAB_ROUTES

object BlurtRoutes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SEARCH = "search"

    /** The account surface — avatar → settings page (a bottom tab now). */
    const val SETTINGS = "settings"

    /** Voice-first capture — the primary way in. */
    const val VOICE = "voice"

    /** The typed composer; optional pre-filled text (e.g. Edit after voice). */
    const val CAPTURE = "capture?text={text}"
    fun capture(text: String? = null) =
        if (text.isNullOrBlank()) "capture" else "capture?text=${android.net.Uri.encode(text)}"

    const val DETAIL = "detail/{id}"

    fun detail(id: Long) = "detail/$id"
}

@Composable
fun BlurtNavHost(
    navController: NavHostController,
    user: AuthUser,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Navigation motion (design standard §7): tab switches crossfade, pushed
    // screens slide in from the right like an iOS stack. These are timed
    // curves, not springs — iOS screen transitions are timed (≈220ms fades,
    // ≈320ms pushes), and a timed curve always reaches its end value, so a
    // quick follow-up tap can never leave an incoming screen stuck mid-fade.
    // Springs stay reserved for direct manipulation inside a screen (the
    // tab capsule); the OS reduce-motion setting swaps everything for short
    // plain fades.
    val reduceMotion = rememberReduceMotion()
    val fadeSpec: androidx.compose.animation.core.FiniteAnimationSpec<Float> =
        if (reduceMotion) tween(BlurtMotion.FADE_MS)
        else tween(220, easing = FastOutSlowInEasing)
    val slideSpec: androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.unit.IntOffset> =
        if (reduceMotion) tween(BlurtMotion.FADE_MS)
        else tween(320, easing = FastOutSlowInEasing)

    NavHost(
        navController = navController,
        startDestination = BlurtRoutes.HOME,
        modifier = modifier,
        enterTransition = {
            val to = targetState.destination.route
            val from = initialState.destination.route
            when {
                isTabRoute(to) && isTabRoute(from) -> fadeIn(fadeSpec)
                isPushRoute(to) -> slideInHorizontally(slideSpec) { it } + fadeIn(fadeSpec)
                else -> fadeIn(fadeSpec)
            }
        },
        exitTransition = {
            val to = targetState.destination.route
            val from = initialState.destination.route
            when {
                isTabRoute(to) && isTabRoute(from) -> fadeOut(fadeSpec)
                else -> fadeOut(fadeSpec)
            }
        },
        popEnterTransition = {
            val to = targetState.destination.route
            when {
                isTabRoute(to) -> fadeIn(fadeSpec)
                else -> slideInHorizontally(slideSpec) { -it / 4 } + fadeIn(fadeSpec)
            }
        },
        popExitTransition = {
            val from = initialState.destination.route
            when {
                isPushRoute(from) -> slideOutHorizontally(slideSpec) { it } + fadeOut(fadeSpec)
                else -> fadeOut(fadeSpec)
            }
        },
    ) {
        composable(BlurtRoutes.HOME) {
            HomeScreen(
                user = user,
                onCapture = { text -> navController.navigate(BlurtRoutes.capture(text)) },
                onSearch = { navController.navigate(BlurtRoutes.SEARCH) },
                onOpenProfile = { navController.navigate(BlurtRoutes.SETTINGS) },
            )
        }
        composable(BlurtRoutes.SETTINGS) {
            ProfileScreen(
                user = user,
                themeMode = themeMode,
                onThemeChange = onThemeChange,
                onSignOut = onSignOut,
            )
        }
        composable(BlurtRoutes.LIBRARY) {
            LibraryScreen(
                user = user,
                onOpenCapture = { navController.navigate(BlurtRoutes.detail(it)) },
                onCaptureNew = { navController.navigate(BlurtRoutes.VOICE) },
                onOpenProfile = { navController.navigate(BlurtRoutes.SETTINGS) },
            )
        }
        composable(BlurtRoutes.SEARCH) {
            SearchScreen(
                user = user,
                onOpenCapture = { navController.navigate(BlurtRoutes.detail(it)) },
                onOpenProfile = { navController.navigate(BlurtRoutes.SETTINGS) },
            )
        }
        composable(BlurtRoutes.VOICE) {
            VoiceScreen(
                onBack = { navController.popBackStack() },
                onEdit = { text ->
                    navController.navigate(BlurtRoutes.capture(text)) {
                        popUpTo(BlurtRoutes.VOICE) { inclusive = true }
                    }
                },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(
            route = BlurtRoutes.CAPTURE,
            arguments = listOf(
                navArgument("text") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            CaptureScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(
            route = BlurtRoutes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) {
            DetailScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
