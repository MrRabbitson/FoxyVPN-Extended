package com.vauth.foxyvpn.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vauth.foxyvpn.R
import com.vauth.foxyvpn.data.FxaAuthRepository
import com.vauth.foxyvpn.data.SessionStatus
import com.vauth.foxyvpn.ui.components.EmphasizedEasing
import com.vauth.foxyvpn.ui.components.FlameBackdrop
import com.vauth.foxyvpn.ui.components.FoxMascot
import com.vauth.foxyvpn.ui.components.FoxMood
import com.vauth.foxyvpn.ui.components.GradientText
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
    // The fox settles in first; the name follows it up a moment later.
    val fox = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    var slow by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch { fox.animateTo(1f, tween(520, easing = EmphasizedEasing)) }
        launch {
            delay(140)
            title.animateTo(1f, tween(520, easing = EmphasizedEasing))
        }
        // Only admit to loading if restoring the session really takes a while.
        delay(1_400)
        slow = true
    }

    LaunchedEffect(Unit) {
        delay(650)
        val status =
            withTimeoutOrNull(SESSION_RESTORE_TIMEOUT_MS) { authRepository.restoreSession() }
                ?: SessionStatus.UNREACHABLE

        when (status) {
            SessionStatus.ACTIVE -> onSignedIn()
            SessionStatus.UNREACHABLE -> onSignedIn()
            SessionStatus.NEEDS_LOGIN -> onNeedsLogin()
        }
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        FlameBackdrop(Modifier.fillMaxSize(), intensity = 1f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FoxMascot(
                mood = FoxMood.GUARDING,
                modifier =
                    Modifier.size(112.dp).graphicsLayer {
                        val t = fox.value
                        alpha = t
                        scaleX = 0.86f + 0.14f * t
                        scaleY = 0.86f + 0.14f * t
                        translationY = (1f - t) * 16.dp.toPx()
                    },
            )
            Spacer(Modifier.height(18.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier.graphicsLayer {
                        val t = title.value
                        alpha = t
                        translationY = (1f - t) * 12.dp.toPx()
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
            Box(Modifier.height(40.dp), contentAlignment = Alignment.BottomCenter) {
                androidx.compose.animation.AnimatedVisibility(visible = slow, enter = fadeIn(tween(400))) {
                    LinearProgressIndicator(
                        modifier = Modifier.width(96.dp).height(3.dp).clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
        }
    }
}
