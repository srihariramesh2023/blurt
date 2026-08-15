package com.blurt.app

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.blurt.app.auth.AuthState
import com.blurt.app.auth.AuthUser
import com.blurt.app.ui.components.LocalBlurtListState
import com.blurt.app.ui.components.BlurtLogo
import com.blurt.app.ui.login.LoginScreen
import com.blurt.app.ui.navigation.BlurtNavHost
import com.blurt.app.ui.navigation.BlurtRoutes
import com.blurt.app.ui.theme.BlurtMotion
import com.blurt.app.ui.theme.BlurtTheme
import com.blurt.app.ui.theme.ThemeMode
import com.blurt.app.ui.theme.rememberReduceMotion
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * Set when the app is opened from a reminder notification; the scaffold
     * navigates straight to that blurt and clears it. Survives onNewIntent
     * (warm start from the notification shade) via [setIntent].
     */
    val openCaptureId = androidx.compose.runtime.mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openCaptureId.value = intent.getLongExtra(EXTRA_OPEN_CAPTURE_ID, -1L).takeIf { it > 0 }
        setContent {
            BlurtAppRoot(openCaptureId = openCaptureId)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openCaptureId.value = intent.getLongExtra(EXTRA_OPEN_CAPTURE_ID, -1L).takeIf { it > 0 }
    }

    companion object {
        const val EXTRA_OPEN_CAPTURE_ID = "blurt.open.captureId"
    }
}

/**
 * The root of the UI, gated by authentication. The auth state is an ongoing
 * listener, so a returning signed-in user goes straight into the app — the
 * login screen never flashes while the session is being restored.
 */
@Composable
private fun BlurtAppRoot(
    openCaptureId: androidx.compose.runtime.MutableState<Long?>,
) {
    val app = LocalContext.current.applicationContext as BlurtApp
    val authState by app.container.authRepository.authState.collectAsStateWithLifecycle()
    val themeMode by app.container.themeMode.collectAsStateWithLifecycle()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scope = rememberCoroutineScope()

    // Status/navigation bar icon contrast follows the active theme.
    val view = LocalView.current
    val context = LocalContext.current
    LaunchedEffect(darkTheme) {
        val window = (context as? ComponentActivity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }

    // Legacy captures created before authentication belong to nobody; assign
    // them to the first user who signs in on this device. Idempotent.
    LaunchedEffect(authState) {
        val uid = (authState as? AuthState.SignedIn)?.user?.uid
        if (uid != null) app.container.captureRepository.claimUnowned(uid)
    }

    BlurtTheme(darkTheme = darkTheme) {
        // The auth gate is a full-screen state change: a calm crossfade,
        // spring-physics by default, plain fade when the OS reduces motion.
        val reduceMotion = rememberReduceMotion()
        AnimatedContent(
            targetState = authState,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                } else {
                    fadeIn(BlurtMotion.standard()) togetherWith fadeOut(BlurtMotion.micro())
                }
            },
            label = "authGate",
        ) { state ->
            when (state) {
                is AuthState.Loading -> BlurtSplash()
                is AuthState.SignedOut -> LoginScreen()
                is AuthState.SignedIn -> BlurtMainScaffold(
                    user = state.user,
                    themeMode = themeMode,
                    onThemeChange = { app.container.themePreferences.setThemeMode(it) },
                    onSignOut = { scope.launch { app.container.authRepository.signOut() } },
                    openCaptureId = openCaptureId,
                )
            }
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
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onSignOut: () -> Unit,
    openCaptureId: androidx.compose.runtime.MutableState<Long?>,
) {
    // A fresh nav controller per session, so signing out and back in never
    // resurfaces the previous user's navigation state.
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in TAB_ROUTES

    // Opened from a reminder notification → jump straight to that blurt.
    val pendingId by openCaptureId
    LaunchedEffect(pendingId) {
        val id = pendingId ?: return@LaunchedEffect
        navController.navigate(BlurtRoutes.detail(id)) { launchSingleTop = true }
        openCaptureId.value = null
    }

    // One list state per tab screen, shared by the sharp content and the
    // frosted backdrop copy so both scroll in lockstep. The explicit Saver
    // is required — autoSaver only handles Bundle types.
    val listState = rememberSaveable(currentRoute, saver = LazyListState.Saver) { LazyListState() }

    // Real backdrop frost: RenderEffect blur on a duplicate layer behind the
    // bar. Only on API 31+ and when the OS isn't asking for reduced motion /
    // transparency (Android has no public "reduce transparency" toggle, so
    // the animator-scale setting is the honest proxy — the standard's
    // fallback is a solid surface).
    val context = LocalContext.current
    val canBlur = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) != 0f
    }

    // The frosted region's height: the bar (80dp) + the system nav inset,
    // refined to the exact measured height after first layout.
    val density = LocalDensity.current
    val navInsetPx = with(density) {
        WindowInsets.navigationBars.asPaddingValues(this).calculateBottomPadding().toPx()
    }
    val estimatedBarPx = with(density) { 80.dp.toPx() } + navInsetPx
    var barHeightPx by remember { mutableStateOf(estimatedBarPx) }

    // The screen content, composed once for the sharp layer and once for the
    // frosted backdrop. Both copies use the identical modifier chain so their
    // pixels align exactly.
    val content: @Composable () -> Unit = {
        BlurtNavHost(
            navController = navController,
            user = user,
            themeMode = themeMode,
            onThemeChange = onThemeChange,
            onSignOut = onSignOut,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                // On tabbed screens the list scrolls under the glass bar and
                // the system nav area; everywhere else content stops above the
                // nav bar so nothing hides behind the gesture pill.
                .then(if (showBottomBar) Modifier else Modifier.navigationBarsPadding()),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        CompositionLocalProvider(LocalBlurtListState provides listState) {
            // Sharp content — the real, interactive layer.
            content()

            if (showBottomBar) {
                // Frosted backdrop: the same content, blurred by a RenderEffect
                // on this layer and clipped to the bar region. Whatever scrolls
                // beneath visibly frosts — the point of the glass.
                if (canBlur) {
                    val blurRadius = with(density) { 24.dp.toPx() }
                    val blurEffect = remember(blurRadius) {
                        BlurEffect(blurRadius, blurRadius, TileMode.Mirror)
                    }
                    val frostShape = remember(barHeightPx) { BottomBarShape(barHeightPx) }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                clip = true
                                shape = frostShape
                                renderEffect = blurEffect
                            },
                    ) {
                        content()
                    }
                }

                // The sharp bar on top: a translucent tint over the frost
                // (solid elevated surface when the OS requests reduced
                // transparency, translucent when blur isn't available).
                Box(Modifier.align(Alignment.BottomCenter)) {
                    BlurtBottomBar(
                        navController = navController,
                        currentRoute = currentRoute,
                        frosted = canBlur,
                        onMeasured = { barHeightPx = it },
                    )
                }
            }
        }
    }
}

/**
 * Minimal three-tab bar, resting on a hairline: the active tab turns system blue,
 * the rest stay muted. No indicator pill, no badges — quiet, like the rest
 * of Blurt. The bar appears only on the three tabbed screens (never login,
 * detail, or capture).
 *
 * Glass: when [frosted] the RenderEffect backdrop does the frosting, so the
 * bar's own tint stays light and the content behind visibly blurs through
 * it. When reduced transparency is requested the bar turns into a solid
 * elevated surface; below API 31 it's a translucent frost.
 */
@Composable
private fun BlurtBottomBar(
    navController: NavHostController,
    currentRoute: String?,
    frosted: Boolean,
    onMeasured: (Float) -> Unit,
) {
    val items = listOf(
        BottomItem(BlurtRoutes.HOME, Icons.Filled.Home, stringResource(com.blurt.app.R.string.nav_home)),
        BottomItem(BlurtRoutes.LIBRARY, Icons.AutoMirrored.Filled.List, stringResource(com.blurt.app.R.string.nav_library)),
        BottomItem(BlurtRoutes.SEARCH, Icons.Filled.Search, stringResource(com.blurt.app.R.string.nav_search)),
    )
    // Solid elevated surface when the OS requests reduced transparency.
    val reducedTransparency =
        Settings.Global.getFloat(
            LocalContext.current.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f

    Column(
        modifier = Modifier.onSizeChanged { onMeasured(it.height.toFloat()) },
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            thickness = 0.5.dp,
        )
        NavigationBar(
            containerColor = when {
                reducedTransparency -> MaterialTheme.colorScheme.surfaceVariant
                frosted -> MaterialTheme.colorScheme.background.copy(alpha = 0.38f)
                else -> MaterialTheme.colorScheme.background.copy(alpha = 0.82f)
            },
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        navController.navigate(item.route) {
                            popUpTo(BlurtRoutes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(22.dp))
                    },
                    label = { Text(item.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

private data class BottomItem(
    val route: String,
    val icon: ImageVector,
    val label: String,
)

private val TAB_ROUTES = listOf(BlurtRoutes.HOME, BlurtRoutes.LIBRARY, BlurtRoutes.SEARCH)

/**
 * A shape covering only the bottom [barHeightPx] of its bounds — used to clip
 * the frosted backdrop to exactly the glass bar's region, so the frost never
 * bleeds above the hairline.
 */
private class BottomBarShape(private val barHeightPx: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): Outline {
        val top = size.height - barHeightPx
        return Outline.Generic(
            Path().apply {
                moveTo(0f, top)
                lineTo(size.width, top)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            },
        )
    }
}
