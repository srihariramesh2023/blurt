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

object BlurtRoutes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    /** The composer has no type anymore — Blurt decides what a blurt is. */
    const val CAPTURE = "capture"
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
                onCapture = { navController.navigate(BlurtRoutes.CAPTURE) },
                onOpenCapture = { navController.navigate(BlurtRoutes.detail(it)) },
                onOpenLibrary = { navController.navigate(BlurtRoutes.LIBRARY) },
                onSearch = { navController.navigate(BlurtRoutes.SEARCH) },
            )
        }
        composable(BlurtRoutes.LIBRARY) {
            LibraryScreen(
                onOpenCapture = { navController.navigate(BlurtRoutes.detail(it)) },
                onCaptureNew = { navController.navigate(BlurtRoutes.CAPTURE) },
            )
        }
        composable(BlurtRoutes.SEARCH) {
            SearchScreen(
                onOpenCapture = { navController.navigate(BlurtRoutes.detail(it)) },
            )
        }
        composable(BlurtRoutes.CAPTURE) {
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
