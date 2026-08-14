package com.blurt.app.ui.login

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blurt.app.R
import com.blurt.app.ui.components.BlurtLogo
import com.blurt.app.ui.components.blurtPressScale
import com.blurt.app.ui.components.rememberBlurtInteractionSource

/**
 * The Blurt sign-in screen. Shown whenever there is no authenticated session:
 * a calm, minimal welcome with a single primary action — Continue with Google.
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    val isSigningIn by viewModel.isSigningIn.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onSignInResult(result.resultCode, result.data)
    }

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Entrance(tick = entered, delayMs = 0) {
                BlurtLogo(size = 72.dp)
            }
            Spacer(Modifier.height(28.dp))
            Entrance(tick = entered, delayMs = 90) {
                Text(
                    text = "Blurt",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.height(12.dp))
            Entrance(tick = entered, delayMs = 180) {
                Text(
                    text = "A quiet home for the things on your mind.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(56.dp))
            Entrance(tick = entered, delayMs = 260) {
                ContinueWithGoogleButton(
                    isSigningIn = isSigningIn,
                    onClick = {
                        viewModel.signInIntent()?.let { launcher.launch(it) }
                    },
                )
            }
            AnimatedVisibility(
                visible = error != null,
                enter = fadeIn(tween(250)),
            ) {
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Your blurts stay private to your Google account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Google's primary sign-in button, per the locked standard: a white capsule
 * pill (54pt) with dark label in both themes, hairline-edged on light so it
 * still reads against the white canvas. The G mark stays full-color.
 */
@Composable
private fun ContinueWithGoogleButton(
    isSigningIn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = rememberBlurtInteractionSource()
    // The spec's white pill — with a hairline edge in light mode so it
    // still reads against the white canvas.
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Surface(
        onClick = onClick,
        enabled = !isSigningIn,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(com.blurt.app.ui.theme.BlurtRadii.pill),
        color = Color.White,
        border = if (isDark) null
        else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .blurtPressScale(interactionSource),
    ) {
        val labelColor = Color(0xFF1F1F1F)
        Box(contentAlignment = Alignment.Center) {
            if (isSigningIn) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = labelColor,
                    strokeWidth = 2.dp,
                )
            } else {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_google_logo),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Continue with Google",
                        style = MaterialTheme.typography.labelLarge,
                        color = labelColor,
                    )
                }
            }
        }
    }
}

/** Staggered, gentle rise+fade entrance for the sign-in hero elements. */
@Composable
private fun Entrance(
    tick: Boolean,
    delayMs: Int,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = tick,
        enter = fadeIn(tween(420, delayMillis = delayMs)) +
            slideInVertically(tween(420, delayMillis = delayMs)) { it / 5 },
    ) {
        content()
    }
}
