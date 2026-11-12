package com.vauth.foxyvpn.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vauth.foxyvpn.data.model.ConnectionState
import com.vauth.foxyvpn.ui.theme.FoxAmber
import com.vauth.foxyvpn.ui.theme.FoxMagenta
import com.vauth.foxyvpn.ui.theme.FoxOrange
import com.vauth.foxyvpn.ui.theme.FoxRed
import com.vauth.foxyvpn.ui.theme.FoxViolet
import com.vauth.foxyvpn.ui.theme.FoxYellow
import com.vauth.foxyvpn.ui.theme.LocalFoxyStatusColors
import com.vauth.foxyvpn.ui.theme.firefoxSweepBrush
import kotlinx.coroutines.delay

fun ConnectionState.toFoxMood(): FoxMood =
    when (this) {
        ConnectionState.CONNECTED -> FoxMood.GUARDING
        ConnectionState.CONNECTING -> FoxMood.SEARCHING
        ConnectionState.DISCONNECTED -> FoxMood.RESTING
    }

/**
 * The big round connect button: a fox inside a Firefox-flame ring.
 *  - disconnected: muted ring, sleeping fox
 *  - connecting: spinning flame arc, fox looking around
 *  - connected: full glowing flame ring with radar pulses, happy fox
 */
@Composable
fun FoxConnectButton(
    state: ConnectionState,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by
        animateFloatAsState(
            targetValue = if (pressed) 0.93f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "press-scale",
        )
    val connectedScale by
        animateFloatAsState(
            targetValue = if (state == ConnectionState.CONNECTED) 1f else 0.94f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "connected-scale",
        )

    val transition = rememberInfiniteTransition(label = "connect-button")
    val spin by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                infiniteRepeatable(
                    tween(if (state == ConnectionState.CONNECTING) 1100 else 9000, easing = LinearEasing)
                ),
            label = "ring-spin",
        )
    val pulse by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing)),
            label = "ring-pulse",
        )
    val sweepLength by
        transition.animateFloat(
            initialValue = 60f,
            targetValue = 280f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "ring-sweep",
        )

    val ringAlpha by
        animateFloatAsState(
            targetValue = if (state == ConnectionState.DISCONNECTED) 0f else 1f,
            animationSpec = tween(500),
            label = "ring-alpha",
        )
    val fullRing by
        animateFloatAsState(
            targetValue = if (state == ConnectionState.CONNECTED) 1f else 0f,
            animationSpec = tween(600, easing = FastOutSlowInEasing),
            label = "ring-full",
        )

    val outline = MaterialTheme.colorScheme.outlineVariant
    val discColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val statusColors = LocalFoxyStatusColors.current
    val badgeColor by
        animateColorAsState(
            targetValue =
                when (state) {
                    ConnectionState.CONNECTED -> statusColors.connected
                    ConnectionState.CONNECTING -> FoxAmber
                    ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline
                },
            label = "badge-color",
        )
    val sweep = remember { firefoxSweepBrush() }

    Box(
        modifier =
            modifier
                .size(size)
                .scale(pressScale)
                .semantics { this.contentDescription = contentDescription }
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Radar pulses + ring
        Canvas(Modifier.fillMaxSize()) {
            val mid = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.minDimension / 2f
            val stroke = radius * 0.075f

            if (fullRing > 0f) {
                for (i in 0 until 2) {
                    val p = (pulse + i * 0.5f) % 1f
                    drawCircle(
                        brush = Brush.radialGradient(listOf(FoxOrange.copy(alpha = 0f), FoxOrange.copy(alpha = 0.22f)), mid, radius * (0.72f + 0.28f * p)),
                        radius = radius * (0.72f + 0.28f * p),
                        center = mid,
                        alpha = fullRing * (1f - p),
                    )
                }
            }

            val ringRadius = radius * 0.80f
            // base track
            drawCircle(outline, radius = ringRadius, center = mid, style = Stroke(stroke))
            if (ringAlpha > 0f) {
                val arc = sweepLength + (360f - sweepLength) * fullRing
                rotate(spin, mid) {
                    drawArc(
                        brush = sweep,
                        startAngle = -90f,
                        sweepAngle = arc,
                        useCenter = false,
                        topLeft = Offset(mid.x - ringRadius, mid.y - ringRadius),
                        size = androidx.compose.ui.geometry.Size(ringRadius * 2, ringRadius * 2),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                        alpha = ringAlpha,
                    )
                }
            }
        }

        // Disc with the fox
        Box(
            modifier =
                Modifier.size(size * 0.66f)
                    .scale(connectedScale)
                    .shadow(elevation = 10.dp, shape = CircleShape, ambientColor = FoxOrange, spotColor = FoxOrange)
                    .clip(CircleShape)
                    .background(discColor),
            contentAlignment = Alignment.Center,
        ) {
            FoxMascot(mood = state.toFoxMood(), modifier = Modifier.size(size * 0.5f).offset(y = 2.dp))
        }

        // Power badge
        Box(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = size * 0.07f)
                    .size(size * 0.17f)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(badgeColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PowerSettingsNew,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.1f),
            )
        }
    }
}

/**
 * Slowly drifting Firefox-gradient blobs behind the content.
 * [intensity] fades the glow in (e.g. when connected).
 */
@Composable
fun FlameBackdrop(modifier: Modifier = Modifier, intensity: Float = 1f) {
    val dark = MaterialTheme.colorScheme.background.luminanceApprox() < 0.3f
    val base = if (dark) 0.28f else 0.18f
    val glow by animateFloatAsState(targetValue = intensity, animationSpec = tween(900), label = "backdrop-glow")
    val transition = rememberInfiniteTransition(label = "backdrop")
    val t by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse),
            label = "backdrop-t",
        )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun blob(color: Color, cx: Float, cy: Float, r: Float, strength: Float) {
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = strength), color.copy(alpha = 0f)), Offset(cx, cy), r),
                radius = r,
                center = Offset(cx, cy),
            )
        }
        val a = base * (0.55f + 0.45f * glow)
        blob(FoxOrange, w * (0.2f + 0.25f * t), h * 0.12f, w * 0.75f, a)
        blob(FoxMagenta, w * (0.95f - 0.2f * t), h * (0.05f + 0.1f * t), w * 0.6f, a * 0.8f)
        blob(FoxYellow, w * (0.6f + 0.2f * t), h * (0.32f - 0.08f * t), w * 0.5f, a * 0.6f)
        blob(FoxViolet, w * (0.05f + 0.1f * t), h * 0.42f, w * 0.45f, a * 0.35f * glow)
        blob(FoxRed, w * 0.5f, h * (0.9f - 0.05f * t), w * 0.6f, a * 0.25f)
    }
}

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** Staggered fade + slide-up entrance, played once per screen visit. */
@Composable
fun StaggeredEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!visible) {
            delay(80L + index * 70L)
            visible = true
        }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter =
            fadeIn(tween(420)) +
                slideInVertically(tween(480, easing = FastOutSlowInEasing)) { it / 3 },
    ) {
        content()
    }
}

/** Gradient "FoxyVPN" wordmark-style brush for titles. */
val FoxyTitleBrush: Brush = Brush.linearGradient(listOf(FoxAmber, FoxOrange, FoxRed, FoxMagenta))

/** Text painted with the Firefox flame gradient (uses only stable drawing APIs). */
@Composable
fun GradientText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    brush: Brush = FoxyTitleBrush,
) {
    Text(
        text = text,
        style = style,
        modifier =
            modifier
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush = brush, blendMode = BlendMode.SrcAtop)
                    }
                },
    )
}

/** Pops a value from 0 → 1 once (for count-up / reveal effects). */
@Composable
fun rememberRevealProgress(key: Any?, durationMs: Int = 700): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(key) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
    }
    return anim.value
}

@Composable
fun Modifier.wiggleOnChange(key: Any?): Modifier {
    val rot = remember { Animatable(0f) }
    LaunchedEffect(key) {
        rot.snapTo(0f)
        for (d in listOf(-6f, 5f, -3f, 0f)) rot.animateTo(d, tween(70))
    }
    return this.graphicsLayer { rotationZ = rot.value }
}

/** Primary call-to-action button filled with the Firefox flame gradient. */
@Composable
fun FlameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            targetValue = if (pressed) 0.96f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "flame-button-scale",
        )
    val transition = rememberInfiniteTransition(label = "flame-button")
    val shimmer by
        transition.animateFloat(
            initialValue = -0.4f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
            label = "flame-button-shimmer",
        )
    Box(
        modifier =
            modifier
                .scale(scale)
                .heightIn(min = 54.dp)
                .shadow(if (enabled) 8.dp else 0.dp, RoundedCornerShape(50), ambientColor = FoxOrange, spotColor = FoxOrange)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(listOf(FoxAmber, FoxOrange, FoxRed)),
                    alpha = if (enabled) 1f else 0.45f,
                )
                .drawWithCache {
                    val band = size.width * 0.35f
                    onDrawWithContent {
                        drawContent()
                        if (enabled) {
                            val x = size.width * shimmer
                            drawRect(
                                Brush.linearGradient(
                                    listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0f)),
                                    start = Offset(x - band, 0f),
                                    end = Offset(x, size.height),
                                )
                            )
                        }
                    }
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
        )
    }
}
