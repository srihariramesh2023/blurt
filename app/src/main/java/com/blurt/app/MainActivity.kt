package com.blurt.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.blurt.app.auth.AuthState
import com.blurt.app.auth.AuthUser
import com.blurt.app.ui.components.BlurtLogo
import com.blurt.app.ui.login.LoginScreen
import com.blurt.app.ui.navigation.BlurtNavHost
import com.blurt.app.ui.navigation.BlurtRoutes
import com.blurt.app.ui.theme.BlurtTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlurtTheme {
                BlurtAppRoot()
            }
        }
    }
}

/**
 * The root of the UI, gated by authentication. The auth state is an ongoing
 * listener, so a returning signed-in user goes straight into the app — the
 * login screen never flashes while the session is being restored.
 */
@Composable
private fun BlurtAppRoot() {
    val app = LocalContext.current.applicationContext as BlurtApp
    val authState by app.container.authRepository.authState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Legacy captures created before authentication belong to nobody; assign
    // them to the first user who signs in on this device. Idempotent.
    LaunchedEffect(authState) {
        val uid = (authState as? AuthState.SignedIn)?.user?.uid
        if (uid != null) app.container.captureRepository.claimUnowned(uid)
    }

    AnimatedContent(
        targetState = authState,
        transitionSpec = {
            fadeIn(tween(350)) togetherWith fadeOut(tween(200))
        },
        label = "authGate",
    ) { state ->
        when (state) {
            is AuthState.Loading -> BlurtSplash()
            is AuthState.SignedOut -> LoginScreen()
            is AuthState.SignedIn -> BlurtMainScaffold(
                user = state.user,
                onSignOut = { scope.launch { app.container.authRepository.signOut() } },
            )
        }
    }
}

/** Brief, calm splash shown only while the persisted session is restored. */
@Composable
private fun BlurtSplash() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        BlurtLogo(size = 64.dp)
    }
}

/** The signed-in app: bottom navigation + the existing Blurt screens. */
@Composable
private fun BlurtMainScaffold(
    user: AuthUser,
    onSignOut: () -> Unit,
) {
    // A fresh nav controller per session, so signing out and back in never
    // resurfaces the previous user's navigation state.
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute in listOf(BlurtRoutes.HOME, BlurtRoutes.LIBRARY, BlurtRoutes.SEARCH)) {
                BlurtBottomBar(navController = navController, currentRoute = currentRoute)
            }
        },
    ) { innerPadding ->
        BlurtNavHost(
            navController = navController,
            user = user,
            onSignOut = onSignOut,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun BlurtBottomBar(navController: NavHostController, currentRoute: String?) {
    val items = listOf(
        BottomItem(BlurtRoutes.HOME, Icons.Filled.Home, stringResource(com.blurt.app.R.string.nav_home)),
        BottomItem(BlurtRoutes.LIBRARY, Icons.AutoMirrored.Filled.List, stringResource(com.blurt.app.R.string.nav_library)),
        BottomItem(BlurtRoutes.SEARCH, Icons.Filled.Search, stringResource(com.blurt.app.R.string.nav_search)),
    )

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(BlurtRoutes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private data class BottomItem(
    val route: String,
    val icon: ImageVector,
    val label: String,
)
