package com.blurt.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import com.blurt.app.ui.search.SearchScreen
import com.blurt.app.ui.theme.ThemeMode
import com.blurt.app.ui.voice.VoiceScreen

object BlurtRoutes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SEARCH = "search"

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
    NavHost(
        navController = navController,
        startDestination = BlurtRoutes.HOME,
        modifier = modifier,
    ) {
        composable(BlurtRoutes.HOME) {
            HomeScreen(
                user = user,
                themeMode = themeMode,
                onThemeChange = onThemeChange,
                onSignOut = onSignOut,
                onVoice = { navController.navigate(BlurtRoutes.VOICE) },
                onCapture = { navController.navigate(BlurtRoutes.capture()) },
                onOpenCapture = { navController.navigate(BlurtRoutes.detail(it)) },
                onOpenLibrary = { navController.navigate(BlurtRoutes.LIBRARY) },
                onSearch = { navController.navigate(BlurtRoutes.SEARCH) },
            )
        }
        composable(BlurtRoutes.LIBRARY) {
            LibraryScreen(
                onOpenCapture = { navController.navigate(BlurtRoutes.detail(it)) },
                onCaptureNew = { navController.navigate(BlurtRoutes.VOICE) },
            )
        }
        composable(BlurtRoutes.SEARCH) {
            SearchScreen(
                onOpenCapture = { navController.navigate(BlurtRoutes.detail(it)) },
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
