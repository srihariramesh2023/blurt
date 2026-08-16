package com.blurt.app.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blurt.app.ui.components.AiKeySection
import com.blurt.app.ui.components.AiKeyViewModel
import com.blurt.app.ui.components.BlurtIcons
import com.blurt.app.ui.components.BlurtOrb
import com.blurt.app.ui.components.BlurtWordmark
import com.blurt.app.ui.components.OrbState
import com.blurt.app.ui.components.blurtPressScale
import com.blurt.app.ui.components.rememberBlurtInteractionSource
import com.blurt.app.ui.theme.BlurtMotion
import com.blurt.app.ui.theme.BlurtRadii
import com.blurt.app.ui.theme.BlurtSpacing
import com.blurt.app.ui.theme.rememberReduceMotion

/**
 * The V2 onboarding — three quiet value pages first, then the BYOK screen,
 * then the mic permission. Shown only on first run (never again once
 * completed), so the bring-your-own-key screen appears exactly once, after
 * the value pitch: 00 Just talk · 01 How it works · 02 Privacy · 03 Bring
 * your own keys, then "Let Blurt listen". The simple pages come first so a
 * brand-new user is never bombarded with the complex key step. Skip is
 * never offered — the pages are short and the value is the product (keys
 * stay optional: without them blurts save unclassified and search falls
 * back to keywords).
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    aiKeyViewModel: AiKeyViewModel = viewModel(factory = AiKeyViewModel.Factory),
) {
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    var page by rememberSaveable { mutableIntStateOf(0) }
    val lastPage = 4 // 0..3 content, 4 = permissions

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        // Mic is the only hard requirement; notification (13+) is a nice-to-have.
        if (granted[Manifest.permission.RECORD_AUDIO] == true || !needsMic(context)) {
            onComplete()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = BlurtSpacing.screen),
    ) {
        Spacer(Modifier.height(BlurtSpacing.l))
        BlurtWordmark(markSize = 36.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(BlurtSpacing.xl))
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (reduceMotion) {
                    (fadeIn(tween(BlurtMotion.FADE_MS)) togetherWith fadeOut(tween(BlurtMotion.FADE_MS / 2)))
                } else {
                    val forward = targetState > initialState
                    val enter = if (forward) slideInHorizontally(BlurtMotion.standard()) { it / 3 } else slideInHorizontally(BlurtMotion.standard()) { -it / 3 }
                    val exit = if (forward) slideOutHorizontally(BlurtMotion.micro()) { -it / 4 } else slideOutHorizontally(BlurtMotion.micro()) { it / 4 }
                    (fadeIn(BlurtMotion.standard()) + enter) togetherWith (fadeOut(BlurtMotion.micro()) + exit)
                }
            },
            label = "onboardingPage",
            modifier = Modifier.weight(1f),
        ) { current ->
            when (current) {
                0 -> OnboardingPage(
                    title = "Just talk.",
                    subtitle = "We'll organize it.",
                    body = "Blurt turns your messy thoughts into tasks, reminders, and notes automatically.",
                    icon = BlurtIcons.BlurtMark,
                )
                1 -> HowItWorksPage()
                2 -> PrivacyPage()
                3 -> ByokPage(aiKeyViewModel = aiKeyViewModel)
                else -> PermissionPage(
                    onAllow = {
                        if (needsMic(context)) {
                            permissionLauncher.launch(
                                buildList {
                                    add(Manifest.permission.RECORD_AUDIO)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }.toTypedArray(),
                            )
                        } else {
                            onComplete()
                        }
                    },
                    onNotNow = onComplete,
                )
            }
        }
        Spacer(Modifier.height(BlurtSpacing.l))
        // Continue / Get Started + the quiet page dots.
        Row(verticalAlignment = Alignment.CenterVertically) {
            PageDots(count = 5, current = page)
            Spacer(Modifier.weight(1f))
            val source = rememberBlurtInteractionSource()
            Button(
                onClick = {
                    if (page < lastPage) page += 1 else onComplete()
                },
                interactionSource = source,
                modifier = Modifier
                    .height(52.dp)
                    .blurtPressScale(source),
                shape = RoundedCornerShape(BlurtRadii.l),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = if (page == lastPage) "Get Started" else "Continue",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Spacer(Modifier.height(BlurtSpacing.xxxl))
    }
}

/**
 * The key step — bring your own keys, the only key path. Blurt ships zero
 * AI keys, so this is the one place a first-run user can paste their own
 * free Groq / Gemini keys before anything else. It sits after the value
 * pages so a brand-new user meets the product before the setup. Keys are
 * optional: Continue works either way, and the Settings → AI keys sheet
 * stays available later. Centered when it fits, scrollable on small screens
 * (Box alignment, not Arrangement.Center, so tall content never clips at
 * the top).
 */
@Composable
private fun ByokPage(
    aiKeyViewModel: AiKeyViewModel,
) {
    val draftKey by aiKeyViewModel.draftKey.collectAsStateWithLifecycle()
    val status by aiKeyViewModel.status.collectAsStateWithLifecycle()
    val geminiDraftKey by aiKeyViewModel.geminiDraftKey.collectAsStateWithLifecycle()
    val geminiStatus by aiKeyViewModel.geminiStatus.collectAsStateWithLifecycle()
    val savedTail = aiKeyViewModel.savedKeyTail()
    val geminiSavedTail = aiKeyViewModel.savedGeminiKeyTail()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = BlurtSpacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BlurtOrb(
                state = OrbState.IDLE,
                size = 120.dp,
                icon = BlurtIcons.Key,
            )
            Spacer(Modifier.height(BlurtSpacing.l))
            Text(
                text = "Bring your own keys",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(BlurtSpacing.s))
            Text(
                text = "Blurt ships with zero AI keys. Paste your own free keys to power it — stored encrypted on this device, never uploaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = BlurtSpacing.l),
            )
            Spacer(Modifier.height(BlurtSpacing.xl))
            AiKeySection(
                title = "Groq — classification",
                icon = BlurtIcons.Bolt,
                description = "Reads every blurt to pick its type, category, and any reminder time.",
                noKeyText = "No key — blurts save unclassified",
                draftKey = draftKey,
                savedTail = savedTail,
                status = status,
                providerName = "Groq",
                placeholder = "gsk_…",
                onDraftChange = aiKeyViewModel::onDraftChange,
                onSaveAndCheck = aiKeyViewModel::saveAndCheck,
                onRemove = aiKeyViewModel::removeKey,
            )
            Spacer(Modifier.height(BlurtSpacing.m))
            AiKeySection(
                title = "Gemini — semantic search",
                icon = BlurtIcons.Sparkle,
                description = "Backup classifier, plus meaning-based search across your library.",
                noKeyText = "No key — search falls back to keywords",
                draftKey = geminiDraftKey,
                savedTail = geminiSavedTail,
                status = geminiStatus,
                providerName = "Gemini",
                placeholder = "AIza… or AQ.…",
                onDraftChange = aiKeyViewModel::onGeminiDraftChange,
                onSaveAndCheck = aiKeyViewModel::saveAndCheckGemini,
                onRemove = aiKeyViewModel::removeGeminiKey,
            )
            Spacer(Modifier.height(BlurtSpacing.s))
        }
    }
}

/** The value pages: a small orb, a headline, a quiet paragraph. */
@Composable
private fun OnboardingPage(
    title: String,
    subtitle: String,
    body: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BlurtOrb(
            state = OrbState.IDLE,
            size = 172.dp,
            icon = icon,
        )
        Spacer(Modifier.height(BlurtSpacing.xl))
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(BlurtSpacing.m))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = BlurtSpacing.l),
        )
    }
}

/** Page two — the three-step "How it works" list. */
@Composable
private fun HowItWorksPage() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "How it works",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(BlurtSpacing.xl))
        StepRow(number = "01", icon = BlurtIcons.Mic, title = "You talk", body = "Say anything, exactly how you'd say it.")
        Spacer(Modifier.height(BlurtSpacing.l))
        StepRow(number = "02", icon = BlurtIcons.Sparkle, title = "We understand", body = "Blurt figures out what you meant.")
        Spacer(Modifier.height(BlurtSpacing.l))
        StepRow(number = "03", icon = BlurtIcons.Archive, title = "We organize", body = "Tasks, reminders, and notes appear automatically.")
    }
}

@Composable
private fun StepRow(
    number: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BlurtSpacing.l),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(BlurtSpacing.m))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Page three — privacy, with the three promises. */
@Composable
private fun PrivacyPage() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BlurtOrb(
            state = OrbState.IDLE,
            size = 140.dp,
            icon = BlurtIcons.BlurtMark,
        )
        Spacer(Modifier.height(BlurtSpacing.l))
        Text(
            text = "Your thoughts.",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Your privacy.",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(BlurtSpacing.m))
        Text(
            text = "Your data stays private and under your control.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(BlurtSpacing.xl))
        PrivacyBullet("We only listen when you choose to.")
        Spacer(Modifier.height(BlurtSpacing.m))
        PrivacyBullet("We process your voice to understand.")
        Spacer(Modifier.height(BlurtSpacing.m))
        PrivacyBullet("You're in control of what gets stored.")
    }
}

@Composable
private fun PrivacyBullet(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BlurtSpacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = BlurtIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp),
            )
        }
        Spacer(Modifier.width(BlurtSpacing.m))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The final page — the mic permission, with the quiet security note. */
@Composable
private fun PermissionPage(
    onAllow: () -> Unit,
    onNotNow: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BlurtOrb(
            state = OrbState.IDLE,
            size = 172.dp,
            icon = BlurtIcons.Mic,
        )
        Spacer(Modifier.height(BlurtSpacing.xl))
        Text(
            text = "Let Blurt listen",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(BlurtSpacing.s))
        Text(
            text = "Blurt needs microphone access so you can talk instead of typing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = BlurtSpacing.l),
        )
        Spacer(Modifier.height(BlurtSpacing.l))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = BlurtIcons.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "We only listen when you tap the orb.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(BlurtSpacing.xl))
        val source = rememberBlurtInteractionSource()
        Button(
            onClick = onAllow,
            interactionSource = source,
            modifier = Modifier
                .height(52.dp)
                .blurtPressScale(source),
            shape = RoundedCornerShape(BlurtRadii.l),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text("Allow Microphone", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(BlurtSpacing.s))
        val notNowSource = rememberBlurtInteractionSource()
        Text(
            text = "Not Now",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(BlurtRadii.s))
                .clickable(onClick = onNotNow, indication = null, interactionSource = notNowSource)
                .blurtPressScale(notNowSource)
                .padding(horizontal = BlurtSpacing.m, vertical = 10.dp),
        )
    }
}

/** The quiet page dots — the current page filled, the rest dimmed. */
@Composable
private fun PageDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            val active = index == current
            Box(
                modifier = Modifier
                    .size(if (active) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    ),
            )
        }
    }
}

private fun needsMic(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
        PackageManager.PERMISSION_GRANTED
