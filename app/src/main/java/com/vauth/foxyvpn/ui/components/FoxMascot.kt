package com.vauth.foxyvpn.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.vauth.foxyvpn.ui.theme.FoxYellow
import com.vauth.foxyvpn.ui.theme.FurCream
import com.vauth.foxyvpn.ui.theme.FurNose
import com.vauth.foxyvpn.ui.theme.FurOrange
import com.vauth.foxyvpn.ui.theme.FurOrangeDark
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class FoxMood {
    /** Disconnected: asleep. */
    RESTING,
    /** Connecting: awake, looking around. */
    SEARCHING,
    /** Connected: content, keeping watch. */
    GUARDING,
}

private const val EARS_DROOP = 9f
private const val HEAD_DROP = 2.5f

private val SoftInOut = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/**
 * Everything that can move on the fox. Each value is driven independently so
 * gestures can overlap (an ear can flick mid-blink) instead of the whole face
 * running on one shared loop.
 */
private class FoxPose {
    val eyeOpen = Animatable(1f)
    val lookX = Animatable(0f)
    val tilt = Animatable(0f)
    val drop = Animatable(0f)
    val earLeft = Animatable(0f)
    val earRight = Animatable(0f)
    val squash = Animatable(0f)
    val joy = Animatable(0f)
    val squint = Animatable(0f)
    val asleep = Animatable(0f)

    suspend fun snapTo(mood: FoxMood) {
        val resting = mood == FoxMood.RESTING
        eyeOpen.snapTo(if (resting) 0f else 1f)
        lookX.snapTo(0f)
        tilt.snapTo(0f)
        drop.snapTo(if (resting) HEAD_DROP else 0f)
        earLeft.snapTo(if (resting) EARS_DROOP else 0f)
        earRight.snapTo(if (resting) EARS_DROOP else 0f)
        squash.snapTo(0f)
        joy.snapTo(if (mood == FoxMood.GUARDING) 1f else 0f)
        squint.snapTo(0f)
        asleep.snapTo(if (resting) 1f else 0f)
    }

    /** One-off acting when the mood changes. */
    suspend fun transitionTo(mood: FoxMood) {
        coroutineScope {
            when (mood) {
                FoxMood.RESTING -> {
                    launch { joy.animateTo(0f, tween(300)) }
                    launch { squint.animateTo(0f, tween(160)) }
                    launch { lookX.animateTo(0f, tween(300, easing = SoftInOut)) }
                    launch { tilt.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 120f)) }
                    launch {
                        // Fights sleep for a moment, then gives in.
                        eyeOpen.animateTo(0.35f, tween(260, easing = SoftInOut))
                        eyeOpen.animateTo(0.62f, tween(200, easing = SoftInOut))
                        eyeOpen.animateTo(0f, tween(440, easing = SoftInOut))
                    }
                    launch { drop.animateTo(HEAD_DROP, tween(900, easing = SoftInOut)) }
                    launch { earLeft.animateTo(EARS_DROOP, spring(dampingRatio = 0.6f, stiffness = 70f)) }
                    launch {
                        delay(90)
                        earRight.animateTo(EARS_DROOP, spring(dampingRatio = 0.6f, stiffness = 70f))
                    }
                    launch {
                        delay(900)
                        asleep.animateTo(1f, tween(700))
                    }
                }
                FoxMood.SEARCHING -> {
                    launch { asleep.animateTo(0f, tween(140)) }
                    launch { joy.animateTo(0f, tween(200)) }
                    launch { squint.animateTo(0f, tween(160)) }
                    launch { drop.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 220f)) }
                    launch { eyeOpen.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
                    launch { earLeft.animateTo(0f, spring(dampingRatio = 0.38f, stiffness = 260f)) }
                    launch {
                        delay(70)
                        earRight.animateTo(0f, spring(dampingRatio = 0.38f, stiffness = 260f))
                    }
                }
                FoxMood.GUARDING -> {
                    launch { asleep.animateTo(0f, tween(140)) }
                    launch { lookX.animateTo(0f, tween(220, easing = SoftInOut)) }
                    launch { tilt.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 200f)) }
                    launch { drop.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 220f)) }
                    launch { eyeOpen.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 380f)) }
                    launch { earLeft.animateTo(0f, spring(dampingRatio = 0.38f, stiffness = 260f)) }
                    launch { earRight.animateTo(0f, spring(dampingRatio = 0.38f, stiffness = 260f)) }
                    launch {
                        // Anticipation, then a springy pop.
                        squash.animateTo(0.08f, tween(110, easing = FastOutLinearInEasing))
                        squash.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 420f))
                    }
                    launch { joy.animateTo(1f, tween(350)) }
                    launch {
                        squint.animateTo(1f, tween(140))
                        delay(900)
                        squint.animateTo(0f, tween(260))
                    }
                }
            }
        }
    }

    /** Small unscripted-looking movements, at irregular intervals. */
    suspend fun idle(mood: FoxMood) {
        coroutineScope {
            when (mood) {
                FoxMood.RESTING ->
                    launch {
                        while (true) {
                            delay(Random.nextLong(5_000, 11_000))
                            flickEar(rest = EARS_DROOP)
                        }
                    }
                FoxMood.SEARCHING -> {
                    launch { blinkLoop() }
                    launch {
                        delay(350)
                        var side = if (Random.nextBoolean()) 1f else -1f
                        while (true) {
                            coroutineScope {
                                launch { lookX.animateTo(side, tween(360, easing = FastOutSlowInEasing)) }
                                launch {
                                    // The head follows the eyes a beat later.
                                    delay(70)
                                    tilt.animateTo(side * 5f, spring(dampingRatio = 0.55f, stiffness = 160f))
                                }
                                launch {
                                    delay(140)
                                    val ear = if (side < 0f) earLeft else earRight
                                    ear.animateTo(-7f, tween(110))
                                    ear.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 300f))
                                }
                            }
                            delay(Random.nextLong(420, 900))
                            side = -side
                        }
                    }
                }
                FoxMood.GUARDING -> {
                    launch { blinkLoop() }
                    launch {
                        while (true) {
                            delay(Random.nextLong(4_500, 9_500))
                            flickEar(rest = 0f)
                        }
                    }
                    launch {
                        while (true) {
                            delay(Random.nextLong(7_000, 14_000))
                            val side = if (Random.nextBoolean()) 0.7f else -0.7f
                            lookX.animateTo(side, tween(260, easing = SoftInOut))
                            delay(Random.nextLong(500, 1_100))
                            lookX.animateTo(0f, tween(320, easing = SoftInOut))
                        }
                    }
                }
            }
        }
    }

    private suspend fun blinkLoop() {
        while (true) {
            delay(Random.nextLong(2_400, 6_000))
            blink()
            if (Random.nextInt(5) == 0) {
                delay(120)
                blink()
            }
        }
    }

    private suspend fun blink() {
        eyeOpen.animateTo(0.05f, tween(70, easing = FastOutLinearInEasing))
        eyeOpen.animateTo(1f, tween(130, easing = FastOutSlowInEasing))
    }

    private suspend fun flickEar(rest: Float) {
        val ear = if (Random.nextBoolean()) earLeft else earRight
        ear.animateTo(rest - 8f, tween(80))
        ear.animateTo(rest, spring(dampingRatio = 0.35f, stiffness = 300f))
    }
}

/**
 * FoxyVPN mascot, drawn in a 100×100 virtual canvas so it scales to any size.
 * With [animated] off it holds the pose for [mood] and never moves (icons).
 */
@Composable
fun FoxMascot(mood: FoxMood, modifier: Modifier = Modifier, animated: Boolean = true) {
    val pose = remember { FoxPose() }
    val started = remember { mutableStateOf(false) }

    LaunchedEffect(mood, animated) {
        if (!animated) {
            pose.snapTo(mood)
            return@LaunchedEffect
        }
        coroutineScope {
            if (started.value) {
                launch { pose.transitionTo(mood) }
            } else {
                pose.snapTo(mood)
                started.value = true
            }
            launch {
                // Let the transition finish before idle gestures touch the same parts.
                delay(1_600)
                pose.idle(mood)
            }
        }
    }

    val loop = rememberInfiniteTransition(label = "fox")
    val breath by
        loop.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3_800, easing = LinearEasing)),
            label = "fox-breath",
        )
    val drift by
        loop.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3_200, easing = LinearEasing)),
            label = "fox-zz",
        )

    Canvas(modifier) {
        val unit = size.minDimension / 100f
        val asleep = pose.asleep.value
        val breathing = if (animated) sin(breath * 2 * PI).toFloat() * (0.5f + 0.7f * asleep) else 0f
        translate(
            left = (size.width - 100f * unit) / 2f,
            top = (size.height - 100f * unit) / 2f,
        ) {
            scale(unit, unit, pivot = Offset.Zero) {
                val squash = pose.squash.value
                translate(top = pose.drop.value + breathing) {
                    rotate(pose.tilt.value, pivot = Offset(50f, 78f)) {
                        scale(1f + squash * 0.6f, 1f - squash, pivot = Offset(50f, 92f)) {
                            drawFoxHead(
                                earLeft = pose.earLeft.value,
                                earRight = pose.earRight.value,
                                eyeOpen = pose.eyeOpen.value,
                                lookX = pose.lookX.value,
                                joy = pose.joy.value,
                                squint = pose.squint.value,
                            )
                        }
                    }
                }
                if (animated && asleep > 0.01f) drawSleepZ(drift, alpha = asleep)
            }
        }
    }
}

private fun DrawScope.drawFoxHead(
    earLeft: Float,
    earRight: Float,
    eyeOpen: Float,
    lookX: Float,
    joy: Float,
    squint: Float,
) {
    val furBrush =
        Brush.verticalGradient(
            listOf(FoxYellow, FurOrange, FurOrangeDark),
            startY = 4f,
            endY = 92f,
        )

    // Ears sit behind the head; positive angle folds them outwards.
    rotate(-earLeft, pivot = Offset(28f, 40f)) {
        drawPath(earPath(left = true), furBrush)
        drawPath(innerEarPath(left = true), FurNose.copy(alpha = 0.85f))
    }
    rotate(earRight, pivot = Offset(72f, 40f)) {
        drawPath(earPath(left = false), furBrush)
        drawPath(innerEarPath(left = false), FurNose.copy(alpha = 0.85f))
    }

    val head =
        Path().apply {
            moveTo(50f, 24f)
            quadraticTo(78f, 24f, 90f, 44f)
            quadraticTo(97f, 56f, 85f, 66f)
            quadraticTo(66f, 84f, 50f, 92f)
            quadraticTo(34f, 84f, 15f, 66f)
            quadraticTo(3f, 56f, 10f, 44f)
            quadraticTo(22f, 24f, 50f, 24f)
            close()
        }
    drawPath(head, furBrush)

    val mask =
        Path().apply {
            moveTo(9f, 57f)
            quadraticTo(30f, 52f, 50f, 64f)
            quadraticTo(70f, 52f, 91f, 57f)
            quadraticTo(90f, 63f, 85f, 66f)
            quadraticTo(66f, 84f, 50f, 92f)
            quadraticTo(34f, 84f, 15f, 66f)
            quadraticTo(10f, 63f, 9f, 57f)
            close()
        }
    drawPath(mask, FurCream)

    val flame =
        Path().apply {
            moveTo(50f, 30f)
            quadraticTo(55f, 38f, 50f, 46f)
            quadraticTo(45f, 38f, 50f, 30f)
            close()
        }
    drawPath(flame, FoxYellow.copy(alpha = 0.75f))

    if (joy > 0.01f) {
        val blush = Color(0xFFFF4F7B).copy(alpha = 0.35f * joy.coerceIn(0f, 1f))
        drawOval(blush, topLeft = Offset(20f, 64f), size = Size(10f, 6f))
        drawOval(blush, topLeft = Offset(70f, 64f), size = Size(10f, 6f))
    }

    val gaze = lookX * 2.4f
    drawEye(cx = 35f + gaze, cy = 55f, open = eyeOpen, squint = squint, gaze = lookX)
    drawEye(cx = 65f + gaze, cy = 55f, open = eyeOpen, squint = squint, gaze = lookX)

    // The muzzle trails the eyes slightly, which reads as the head turning.
    val muzzle = lookX * 1.1f
    drawOval(FurNose, topLeft = Offset(45f + muzzle, 73f), size = Size(10f, 7f))
    drawOval(Color.White.copy(alpha = 0.5f), topLeft = Offset(47.5f + muzzle, 74f), size = Size(3f, 2f))

    val smile = joy.coerceIn(0f, 1f)
    val mouth =
        Path().apply {
            moveTo(50f + muzzle, 80f)
            lineTo(50f + muzzle, 82f)
            quadraticTo(46f + muzzle, 84f + 2.5f * smile, 43f + muzzle - smile, 82f - 1.5f * smile)
            moveTo(50f + muzzle, 82f)
            quadraticTo(54f + muzzle, 84f + 2.5f * smile, 57f + muzzle + smile, 82f - 1.5f * smile)
        }
    drawPath(mouth, FurNose, style = Stroke(width = 1.6f, cap = StrokeCap.Round))
}

private fun DrawScope.drawEye(cx: Float, cy: Float, open: Float, squint: Float, gaze: Float) {
    if (squint > 0.5f) {
        // Happy, smiling eyes.
        val arc =
            Path().apply {
                moveTo(cx - 5f, cy + 1.5f)
                quadraticTo(cx, cy - 5f, cx + 5f, cy + 1.5f)
            }
        drawPath(arc, FurNose, style = Stroke(width = 2.4f, cap = StrokeCap.Round))
        return
    }
    if (open < 0.18f) {
        val arc =
            Path().apply {
                moveTo(cx - 5f, cy)
                quadraticTo(cx, cy + 4f, cx + 5f, cy)
            }
        drawPath(arc, FurNose, style = Stroke(width = 2.2f, cap = StrokeCap.Round))
        return
    }
    val h = 11f * open
    drawOval(FurNose, topLeft = Offset(cx - 4.5f, cy - h / 2f), size = Size(9f, h))
    if (open > 0.5f) {
        drawCircle(Color.White, radius = 1.8f, center = Offset(cx + 1.5f + gaze * 0.8f, cy - h / 4f))
    }
}

private fun earPath(left: Boolean): Path =
    Path().apply {
        if (left) {
            moveTo(12f, 48f)
            quadraticTo(12f, 18f, 20f, 6f)
            quadraticTo(34f, 14f, 44f, 30f)
        } else {
            moveTo(88f, 48f)
            quadraticTo(88f, 18f, 80f, 6f)
            quadraticTo(66f, 14f, 56f, 30f)
        }
        close()
    }

private fun innerEarPath(left: Boolean): Path =
    Path().apply {
        if (left) {
            moveTo(18f, 40f)
            quadraticTo(18f, 22f, 22f, 15f)
            quadraticTo(31f, 21f, 36f, 31f)
        } else {
            moveTo(82f, 40f)
            quadraticTo(82f, 22f, 78f, 15f)
            quadraticTo(69f, 21f, 64f, 31f)
        }
        close()
    }

/** Two "z"s drifting up from the sleeping fox. */
private fun DrawScope.drawSleepZ(progress: Float, alpha: Float) {
    for (i in 0 until 2) {
        val p = (progress + i * 0.5f) % 1f
        val zSize = 4.5f + 4f * p
        val x = 80f + 10f * p + sin(p * 2 * PI).toFloat() * 1.5f
        val y = 28f - 24f * p
        val fade = if (p < 0.25f) p / 0.25f else 1f - (p - 0.25f) / 0.75f
        val z =
            Path().apply {
                moveTo(x, y)
                lineTo(x + zSize, y)
                lineTo(x, y + zSize)
                lineTo(x + zSize, y + zSize)
            }
        drawPath(
            z,
            FurOrangeDark.copy(alpha = (alpha * fade).coerceIn(0f, 1f)),
            style = Stroke(width = 1.8f, cap = StrokeCap.Round),
        )
    }
}
