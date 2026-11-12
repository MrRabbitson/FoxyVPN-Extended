package com.vauth.foxyvpn.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Material "emphasized" curve: quick to start, long gentle settle. */
val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private const val ARC_SPAN = 280f
private const val ARC_MIN = 14f
private const val ARC_CYCLE_SECONDS = 1.35f

fun ConnectionState.toFoxMood(): FoxMood =
    when (this) {
        ConnectionState.CONNECTED -> FoxMood.GUARDING
        ConnectionState.CONNECTING -> FoxMood.SEARCHING
        ConnectionState.DISCONNECTED -> FoxMood.RESTING
    }

/**
 * The connect button: the fox inside a flame-coloured ring.
 *
 * The ring is one continuous object across all states rather than three
 * separate drawings: while connecting an arc chases its own tail, on success
 * that same arc closes into a full ring from wherever it happens to be and a
 * single ripple leaves it, and on disconnect the ring unwinds.
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
            targetValue = if (pressed) 0.96f else 1f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
            label = "press-scale",
        )

    val active = state != ConnectionState.DISCONNECTED
    val presence =
        animateFloatAsState(
            targetValue = if (active) 1f else 0f,
            animationSpec = tween(if (active) 260 else 520, easing = EmphasizedEasing),
            label = "ring-presence",
        )
    val closed =
        animateFloatAsState(
            targetValue = if (state == ConnectionState.CONNECTED) 1f else 0f,
            animationSpec = tween(640, easing = EmphasizedEasing),
            label = "ring-closed",
        )

    // Free-running clock for the arc, advanced per frame so its speed can ease
    // down as the ring closes instead of jumping between two animations.
    val angle = remember { mutableFloatStateOf(0f) }
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                last = now
                val settle = closed.value
                angle.floatValue = (angle.floatValue + dt * (150f - 142f * settle)) % 360f
                phase.floatValue = (phase.floatValue + dt / ARC_CYCLE_SECONDS * (1f - settle)) % 1024f
            }
        }
    }

    // One ripple when the connection lands.
    val ripple = remember { Animatable(1f) }
    val previous = remember { mutableStateOf<ConnectionState?>(null) }
    LaunchedEffect(state) {
        val before = previous.value
        previous.value = state
        if (state == ConnectionState.CONNECTED && before != null && before != ConnectionState.CONNECTED) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(820, easing = LinearOutSlowInEasing))
        }
    }

    val track = MaterialTheme.colorScheme.outlineVariant
    val statusColors = LocalFoxyStatusColors.current
    val badge by
        animateColorAsState(
            targetValue =
                when (state) {
                    ConnectionState.CONNECTED -> statusColors.connected
                    ConnectionState.CONNECTING -> FoxAmber
                    ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline
                },
            animationSpec = tween(400),
            label = "badge-color",
        )
    val flame = remember { firefoxSweepBrush() }

    Box(
        modifier =
            modifier
                .size(size)
                .scale(pressScale)
                .semantics { this.contentDescription = contentDescription }
                .clip(CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val mid = Offset(this.size.width / 2f, this.size.height / 2f)
            val half = this.size.minDimension / 2f
            val ringRadius = half * 0.76f
            val stroke = half * 0.07f
            val settle = closed.value.coerceIn(0f, 1f)
            val shown = presence.value.coerceIn(0f, 1f)

            // Warm halo that only exists once connected.
            if (settle > 0f) {
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            0.55f to FoxOrange.copy(alpha = 0f),
                            0.78f to FoxOrange.copy(alpha = 0.20f * settle),
                            1f to FoxMagenta.copy(alpha = 0f),
                            center = mid,
                            radius = half,
                        ),
                    radius = half,
                    center = mid,
                )
            }

            drawCircle(track, radius = ringRadius, center = mid, style = Stroke(stroke))

            if (shown > 0f) {
                val cycle = phase.floatValue
                val turn = floor(cycle)
                val p = cycle - turn
                val head = FastOutSlowInEasing.transform(min(1f, p * 2f))
                val tail = FastOutSlowInEasing.transform(max(0f, p * 2f - 1f))
                val chasing = max(ARC_MIN, (head - tail) * ARC_SPAN)
                val sweep = (chasing + (360f - chasing) * settle) * shown
                val start = -90f + angle.floatValue + ((turn + tail) * ARC_SPAN) % 360f
                rotate(start, mid) {
                    drawArc(
                        brush = flame,
                        startAngle = 0f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(mid.x - ringRadius, mid.y - ringRadius),
                        size = Size(ringRadius * 2f, ringRadius * 2f),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                        alpha = min(1f, shown * 1.6f),
                    )
                }
            }

            val r = ripple.value
            if (r < 1f) {
                drawCircle(
                    color = FoxOrange.copy(alpha = 0.55f * (1f - r)),
                    radius = ringRadius * (1f + 0.3f * r),
                    center = mid,
                    style = Stroke(width = stroke * (1f - r) + 1f),
                )
            }
        }

        GlassSurface(modifier = Modifier.size(size * 0.62f), shape = CircleShape) {
            FoxMascot(
                mood = state.toFoxMood(),
                modifier = Modifier.align(Alignment.Center).size(size * 0.47f).offset(y = 2.dp),
            )
        }

        Box(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = size * 0.09f)
                    .size(size * 0.16f)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(badge),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PowerSettingsNew,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.095f),
            )
        }
    }
}

/**
 * Still colour fields behind the glass. Nothing here moves on its own; the
 * warm fields only brighten or dim with [intensity] (the connection state).
 */
@Composable
fun FlameBackdrop(modifier: Modifier = Modifier, intensity: Float = 1f) {
    val dark = MaterialTheme.colorScheme.background.luminanceApprox() < 0.3f
    val glow by
        animateFloatAsState(
            targetValue = intensity,
            animationSpec = tween(1_400, easing = EmphasizedEasing),
            label = "backdrop-glow",
        )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val warm = (if (dark) 0.42f else 0.30f) * (0.35f + 0.65f * glow)
        val cool = if (dark) 0.30f else 0.16f
        fun field(color: Color, x: Float, y: Float, radius: Float, strength: Float) {
            val at = Offset(w * x, h * y)
            drawRect(
                Brush.radialGradient(
                    0f to color.copy(alpha = strength),
                    1f to color.copy(alpha = 0f),
                    center = at,
                    radius = w * radius,
                )
            )
        }
        field(FoxOrange, 0.50f, 0.26f, 0.95f, warm)
        field(FoxMagenta, 1.00f, 0.52f, 0.80f, warm * 0.55f)
        field(FoxYellow, 0.02f, 0.02f, 0.70f, warm * 0.50f)
        field(FoxViolet, 0.00f, 0.80f, 0.90f, cool)
        field(FoxRed, 0.85f, 1.00f, 0.70f, cool * 0.6f)
    }
}

@Composable
private fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.onSurface.luminanceApprox() > 0.5f

/**
 * A pane of glass: translucent body, a sheen falling off from the top edge and
 * a rim that catches light at opposite corners. Pressing it flexes the pane.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = isDarkSurface()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press =
        animateFloatAsState(
            targetValue = if (pressed) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
            label = "glass-press",
        )
    val bodyTop = Color.White.copy(alpha = if (dark) 0.13f else 0.74f)
    val bodyBottom = Color.White.copy(alpha = if (dark) 0.05f else 0.48f)
    val sheenTop = Color.White.copy(alpha = if (dark) 0.09f else 0.30f)
    val rimBright = Color.White.copy(alpha = if (dark) 0.44f else 0.95f)
    val rimDim = Color.White.copy(alpha = if (dark) 0.07f else 0.40f)

    Box(
        modifier =
            modifier
                .graphicsLayer {
                    val flex = 1f - 0.025f * press.value
                    scaleX = flex
                    scaleY = flex
                }
                .clip(shape)
                .drawWithCache {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    val body = Brush.verticalGradient(listOf(bodyTop, bodyBottom))
                    val sheen =
                        Brush.verticalGradient(
                            0f to sheenTop,
                            1f to Color.Transparent,
                            endY = size.height * 0.45f,
                        )
                    val rim =
                        Brush.linearGradient(
                            0f to rimBright,
                            0.30f to rimDim,
                            0.70f to rimDim,
                            1f to rimBright.copy(alpha = rimBright.alpha * 0.55f),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        )
                    // Half of the stroke is clipped away, leaving a 1dp inner rim.
                    val rimStroke = Stroke(width = 2.dp.toPx())
                    onDrawWithContent {
                        drawOutline(outline, body)
                        drawOutline(outline, sheen)
                        val lit = press.value
                        if (lit > 0f) drawOutline(outline, Color.White, alpha = 0.10f * lit)
                        drawContent()
                        drawOutline(outline, rim, style = rimStroke)
                    }
                }
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    }
                ),
        content = content,
    )
}

/**
 * Puts a screen on the glass backdrop: Material surfaces inside become
 * see-through so lists and bars sit directly on the colour fields.
 */
@Composable
fun GlassScreen(intensity: Float = 0.45f, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(scheme.background)) {
        FlameBackdrop(Modifier.fillMaxSize(), intensity = intensity)
        MaterialTheme(
            colorScheme = scheme.copy(surface = Color.Transparent, background = Color.Transparent),
            content = content,
        )
    }
}

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

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

/**
 * Text whose digits roll like an odometer: only the digits that actually
 * changed move. When the text changes shape (a number becomes a word, or gains
 * a digit) the whole value cross-fades instead, so letters never shuffle.
 */
@Composable
fun RollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val tabular = style.copy(fontFeatureSettings = "tnum")
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(200, delayMillis = 70)) togetherWith fadeOut(tween(90)))
                .using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.CenterStart,
        contentKey = { value -> value.map { if (it.isDigit()) '0' else it }.joinToString("") },
        label = "rolling-text",
    ) { value ->
        Row {
            value.forEachIndexed { index, char ->
                if (!char.isDigit()) {
                    Text(char.toString(), style = tabular, color = color, softWrap = false)
                } else {
                    key(value.length - index) {
                        AnimatedContent(
                            targetState = char,
                            transitionSpec = {
                                (slideInVertically(tween(260, easing = EmphasizedEasing)) { it } +
                                    fadeIn(tween(180))) togetherWith
                                    (slideOutVertically(tween(260, easing = EmphasizedEasing)) { -it } +
                                        fadeOut(tween(120)))
                            },
                            label = "rolling-digit",
                        ) { digit ->
                            Text(digit.toString(), style = tabular, color = color, softWrap = false)
                        }
                    }
                }
            }
        }
    }
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
            targetValue = if (pressed) 0.97f else 1f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
            label = "flame-button-scale",
        )
    val lift by
        animateFloatAsState(
            targetValue = if (pressed || !enabled) 2f else 8f,
            animationSpec = tween(160),
            label = "flame-button-lift",
        )
    Box(
        modifier =
            modifier
                .scale(scale)
                .heightIn(min = 54.dp)
                .shadow(lift.dp, RoundedCornerShape(50), ambientColor = FoxOrange, spotColor = FoxOrange)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(listOf(FoxAmber, FoxOrange, FoxRed)),
                    alpha = if (enabled) 1f else 0.45f,
                )
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
