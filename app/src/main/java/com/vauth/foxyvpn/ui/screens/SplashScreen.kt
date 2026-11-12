package com.vauth.foxyvpn.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vauth.foxyvpn.R
import com.vauth.foxyvpn.data.FxaAuthRepository
import com.vauth.foxyvpn.data.SessionStatus
import com.vauth.foxyvpn.ui.components.FlameBackdrop
import com.vauth.foxyvpn.ui.components.FoxMascot
import com.vauth.foxyvpn.ui.components.FoxMood
import com.vauth.foxyvpn.ui.components.GradientText
import com.vauth.foxyvpn.ui.theme.firefoxSweepBrush
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val SESSION_RESTORE_TIMEOUT_MS = 8_000L

@Composable
fun SplashScreen(
    authRepository: FxaAuthRepository,
    onSignedIn: () -> Unit,
    onNeedsLogin: () -> Unit,
) {
    val foxScale = remember { Animatable(0.3f) }
    val foxAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffset = remember { Animatable(24f) }

    LaunchedEffect(Unit) {
        launch {
            foxAlpha.animateTo(1f, tween(300))
        }
        launch {
            foxScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            )
        }
        launch {
            delay(220)
            launch { textAlpha.animateTo(1f, tween(450)) }
            textOffset.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(Unit) {
        delay(700)
        val status =
            withTimeoutOrNull(SESSION_RESTORE_TIMEOUT_MS) { authRepository.restoreSession() }
                ?: SessionStatus.UNREACHABLE

        when (status) {
            SessionStatus.ACTIVE -> onSignedIn()
            SessionStatus.UNREACHABLE -> onSignedIn()
            SessionStatus.NEEDS_LOGIN -> onNeedsLogin()
        }
    }

    val transition = rememberInfiniteTransition(label = "splash")
    val spin by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
            label = "splash-spin",
        )
    val sweep = remember { firefoxSweepBrush() }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        FlameBackdrop(Modifier.fillMaxSize(), intensity = 1f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(150.dp).graphicsLayer { alpha = foxAlpha.value }) {
                    val stroke = size.minDimension * 0.05f
                    rotate(spin) {
                        drawArc(
                            brush = sweep,
                            startAngle = 0f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = Offset(stroke, stroke),
                            size = Size(size.width - stroke * 2, size.height - stroke * 2),
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                }
                FoxMascot(
                    mood = FoxMood.GUARDING,
                    modifier =
                        Modifier.size(104.dp).graphicsLayer {
                            scaleX = foxScale.value
                            scaleY = foxScale.value
                            alpha = foxAlpha.value
                        },
                )
            }
            Spacer(Modifier.height(20.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier.graphicsLayer {
                        alpha = textAlpha.value
                        translationY = textOffset.value * density
                    },
            ) {
                GradientText(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.splash_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
