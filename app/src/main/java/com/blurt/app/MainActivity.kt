package com.blurt.app

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.blurt.app.auth.AuthState
import com.blurt.app.auth.AuthUser
import com.blurt.app.ui.components.BlurtLogo
import com.blurt.app.ui.components.LocalBlurtListState
import com.blurt.app.ui.components.LocalBlurtScrollState
import com.blurt.app.ui.components.LocalTabBarInset
import com.blurt.app.ui.components.blurtPressScale
import com.blurt.app.ui.components.rememberBlurtInteractionSource
import com.blurt.app.ui.login.LoginScreen
import com.blurt.app.ui.navigation.BlurtNavHost
import com.blurt.app.ui.navigation.BlurtRoutes
import com.blurt.app.ui.onboarding.OnboardingScreen
import com.blurt.app.ui.theme.BlurtMotion
import com.blurt.app.ui.theme.BlurtSpacing
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
    val onboardingComplete by app.container.themePreferences.onboardingComplete.collectAsStateWithLifecycle()
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
        // The V2 flow: onboarding first (once), then the auth gate. Both are
        // full-screen state changes with a calm crossfade — spring by
        // default, plain fade when the OS reduces motion.
        val reduceMotion = rememberReduceMotion()
        AnimatedContent(
            targetState = if (onboardingComplete) authState else "onboarding",
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
                "onboarding" -> OnboardingScreen(
                    onComplete = { app.container.themePreferences.completeOnboarding() },
                )
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

    // The floating pill's geometry — measured once the bar lays out, with a
    // sane first estimate so the frost renders correctly on the first frame.
    val density = LocalDensity.current
    val navInsetPx = with(density) {
        WindowInsets.navigationBars.asPaddingValues(this).calculateBottomPadding().toPx()
    }
    val floatGapPx = with(density) { 8.dp.toPx() }
    val pillMarginPx = with(density) { 16.dp.toPx() }
    val pillCornerPx = with(density) { 30.dp.toPx() }
    val estimatedPillPx = with(density) { 60.dp.toPx() }
    var barMetrics by remember {
        mutableStateOf(FloatingBarMetrics(estimatedPillPx, navInsetPx, floatGapPx, pillMarginPx, pillCornerPx))
    }

    // The bottom inset tab screens reserve: gesture inset + float gap + pill
    // + a little air, so the last row of every list — or the bottom of a
    // hero — clears the glass instead of hiding underneath it.
    val tabBarInset = with(density) {
        (barMetrics.navInsetPx + barMetrics.floatGapPx + barMetrics.pillHeightPx + 12.dp.toPx()).toDp()
    }

    // One scroll state per tab screen, shared by the sharp content and the
    // frosted backdrop copy so both scroll in lockstep (verticalScroll
    // screens: Home, Profile).
    val scrollState = rememberSaveable(currentRoute, saver = ScrollState.Saver) { ScrollState(0) }

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
        CompositionLocalProvider(
            LocalBlurtListState provides listState,
            LocalBlurtScrollState provides scrollState,
            LocalTabBarInset provides tabBarInset,
        ) {
            // Sharp content — the real, interactive layer.
            content()

            if (showBottomBar) {
                // Frosted backdrop: the same content, blurred by a RenderEffect
                // on this layer and clipped to exactly the floating pill's
                // rounded region. Whatever scrolls beneath visibly frosts.
                if (canBlur) {
                    val blurRadius = with(density) { 24.dp.toPx() }
                    val blurEffect = remember(blurRadius) {
                        BlurEffect(blurRadius, blurRadius, TileMode.Mirror)
                    }
                    val frostShape = remember(barMetrics) { FloatingBarShape(barMetrics) }
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

                // The sharp pill on top: a translucent glass tint over the
                // frost (solid elevated surface when the OS requests reduced
                // transparency, translucent when blur isn't available).
                Box(Modifier.align(Alignment.BottomCenter)) {
                    BlurtBottomBar(
                        navController = navController,
                        currentRoute = currentRoute,
                        frosted = canBlur,
                        onMeasured = { barMetrics = barMetrics.copy(pillHeightPx = it) },
                    )
                }
            }
        }
    }
}

/**
 * The floating tab bar — the iOS 26 Liquid Glass pattern, in Blurt's violet:
 * a rounded glass pill floating above the content, inset from the screen
 * edges, with the content visibly frosting beneath it. No hairline, no
 * full-width chrome — just a quiet capsule of tabs. The active tab gets a
 * soft violet highlight capsule; the rest stay muted. The bar appears only
 * on the four tabbed screens (never login, detail, voice, or capture).
 *
 * Glass: when [frosted] the RenderEffect backdrop does the frosting, so the
 * pill's own tint stays translucent and the content behind visibly blurs
 * through it. When reduced transparency is requested the pill turns into a
 * solid elevated surface; below API 31 it's a more opaque translucent frost.
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
        BottomItem(BlurtRoutes.SEARCH, Icons.Filled.Search, stringResource(com.blurt.app.R.string.nav_search)),
        BottomItem(BlurtRoutes.LIBRARY, Icons.AutoMirrored.Filled.List, stringResource(com.blurt.app.R.string.nav_library)),
        BottomItem(BlurtRoutes.SETTINGS, com.blurt.app.ui.components.BlurtIcons.Settings, stringResource(com.blurt.app.R.string.nav_settings)),
    )
    // Solid elevated surface when the OS requests reduced transparency.
    val reducedTransparency =
        Settings.Global.getFloat(
            LocalContext.current.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // The glass recipe: a translucent tint that lets the blurred content show
    // through, a hairline edge that catches the light, and a soft shadow that
    // lifts the pill off the content.
    val glassColor = when {
        reducedTransparency -> MaterialTheme.colorScheme.surface
        frosted -> if (isDark) Color(0xFF1C1C1E).copy(alpha = 0.55f)
        else Color(0xFFF2F2F7).copy(alpha = 0.6f)
        else -> if (isDark) Color(0xFF1C1C1E).copy(alpha = 0.94f)
        else Color(0xFFF2F2F7).copy(alpha = 0.94f)
    }
    val edgeColor = if (isDark) Color.White.copy(alpha = 0.16f)
    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val reduceMotion = rememberReduceMotion()

    // The pill's entrance — a quiet opacity fade. Never a slide: the frosted
    // backdrop is clipped to the pill's final bounds, so motion that changes
    // position would expose an un-frosted edge while it travels.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val pillAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = when {
            reducedTransparency -> tween(0)
            reduceMotion -> tween(BlurtMotion.FADE_MS)
            else -> spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
        },
        label = "pillEntrance",
    )

    // The pill floats above the gesture area — 8dp up, 16dp in from the
    // edges — the same rhythm as iOS's Liquid Glass tab bar.
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .graphicsLayer { alpha = pillAlpha },
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = glassColor,
            border = BorderStroke(1.dp, edgeColor),
            shadowElevation = if (reducedTransparency) 0.dp else 12.dp,
            modifier = Modifier.onSizeChanged { onMeasured(it.height.toFloat()) },
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // The active tab's highlight capsule glides between tabs,
                // like iOS 26's Liquid Glass active item. Four equal cells;
                // the capsule is one cell wide, offset to the selection.
                val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
                var rowWidthPx by remember { mutableStateOf(0) }
                val density = LocalDensity.current
                val cellWidth = with(density) { (rowWidthPx / items.size.toFloat()).toDp() }
                val capsuleX by animateDpAsState(
                    targetValue = cellWidth * selectedIndex + 4.dp,
                    animationSpec = if (reduceMotion) tween(0)
                    else spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
                    label = "tabCapsule",
                )
                // Aligned to the row's content box: the row pads 5dp top and
                // bottom, so the capsule needs the same 5dp top offset to sit
                // exactly under the icon + label, not 5dp high.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = capsuleX, y = 5.dp)
                        .size(
                            width = (cellWidth - 8.dp).coerceAtLeast(0.dp),
                            height = 50.dp,
                        )
                        .clip(RoundedCornerShape(BlurtSpacing.l))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(vertical = 5.dp)
                        .onSizeChanged { rowWidthPx = it.width },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEach { item ->
                        val selected = currentRoute == item.route
                        val source = rememberBlurtInteractionSource()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    onClick = {
                                        // Read the freshest route at tap time
                                        // and never re-navigate to the tab
                                        // we're already on: a redundant
                                        // navigate is what interrupts a
                                        // just-started transition and makes
                                        // a tab switch look like it didn't
                                        // happen.
                                        val current =
                                            navController.currentBackStackEntry?.destination?.route
                                        if (item.route != current) {
                                            navController.navigate(item.route) {
                                                popUpTo(BlurtRoutes.HOME) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    interactionSource = source,
                                    indication = null,
                                )
                                .blurtPressScale(source),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(23.dp),
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    ),
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class BottomItem(
    val route: String,
    val icon: ImageVector,
    val label: String,
)

private val TAB_ROUTES = listOf(
    BlurtRoutes.HOME,
    BlurtRoutes.SEARCH,
    BlurtRoutes.LIBRARY,
    BlurtRoutes.SETTINGS,
)

/** The floating pill's measured geometry, shared by the frost clip. */
private data class FloatingBarMetrics(
    val pillHeightPx: Float,
    val navInsetPx: Float,
    val floatGapPx: Float,
    val marginPx: Float,
    val cornerPx: Float,
)

/**
 * A rounded-rect outline matching the floating pill's exact bounds — used to
 * clip the frosted backdrop so the blur never bleeds outside the glass.
 */
private class FloatingBarShape(private val m: FloatingBarMetrics) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): Outline {
        val bottom = size.height - m.navInsetPx - m.floatGapPx
        return Outline.Generic(
            Path().apply {
                addRoundRect(
                    RoundRect(
                        left = m.marginPx,
                        top = bottom - m.pillHeightPx,
                        right = size.width - m.marginPx,
                        bottom = bottom,
                        radiusX = m.cornerPx,
                        radiusY = m.cornerPx,
                    ),
                )
            },
        )
    }
}
