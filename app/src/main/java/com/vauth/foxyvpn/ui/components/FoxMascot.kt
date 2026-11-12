package com.vauth.foxyvpn.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

enum class FoxMood {
    /** Disconnected: eyes closed, floating "z"s. */
    RESTING,
    /** Connecting: looks around, head tilts. */
    SEARCHING,
    /** Connected: awake, smiling, blinking. */
    GUARDING,
}

/**
 * FoxyVPN mascot, drawn in a 100×100 virtual canvas so it scales to any size.
 * Every mood has its own idle animation; transitions between moods are smooth.
 */
@Composable
fun FoxMascot(mood: FoxMood, modifier: Modifier = Modifier, animated: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "fox")

    val breath by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    tween(if (mood == FoxMood.RESTING) 3200 else 2200, easing = LinearEasing)
                ),
            label = "fox-breath",
        )
    val blink by
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    keyframes {
                        durationMillis = 4200
                        1f at 0
                        1f at 3900
                        0.08f at 3990
                        1f at 4100
                        1f at 4200
                    }
                ),
            label = "fox-blink",
        )
    val earTwitch by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec =
                infiniteRepeatable(
                    keyframes {
                        durationMillis = 5600
                        0f at 0
                        0f at 2600
                        -12f at 2720
                        4f at 2840
                        -6f at 2950
                        0f at 3100
                        0f at 5600
                    }
                ),
            label = "fox-ear",
        )
    val look by
        transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "fox-look",
        )
    val zz by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
            label = "fox-zz",
        )

    // Smoothly blend between moods.
    val awake by
        animateFloatAsState(
            targetValue = if (mood == FoxMood.RESTING) 0f else 1f,
            animationSpec = tween(450),
            label = "fox-awake",
        )
    val searching by
        animateFloatAsState(
            targetValue = if (mood == FoxMood.SEARCHING) 1f else 0f,
            animationSpec = tween(400),
            label = "fox-searching",
        )
    val happy by
        animateFloatAsState(
            targetValue = if (mood == FoxMood.GUARDING) 1f else 0f,
            animationSpec = tween(500),
            label = "fox-happy",
        )

    val b = if (animated) breath else 0f
    val wave = sin(b * 2 * PI).toFloat()

    Canvas(modifier) {
        val unit = size.minDimension / 100f
        translate(
            left = (size.width - 100f * unit) / 2f,
            top = (size.height - 100f * unit) / 2f,
        ) {
            scale(unit, unit, pivot = Offset.Zero) {
                val bob = wave * (1.2f + (1f - awake) * 0.6f)
                val tilt = if (animated) look * 7f * searching else 0f
                translate(top = bob) {
                    rotate(tilt, pivot = Offset(50f, 70f)) {
                        drawFoxHead(
                            earAngle = if (animated) earTwitch * awake else 0f,
                            eyeOpen = awake * (if (animated) blink else 1f),
                            lookX = if (animated) look * 3f * searching else 0f,
                            happy = happy,
                        )
                    }
                }
                if (awake < 0.99f && animated) drawSleepZ(zz, alpha = 1f - awake)
            }
        }
    }
}

private fun DrawScope.drawFoxHead(earAngle: Float, eyeOpen: Float, lookX: Float, happy: Float) {
    val furBrush =
        Brush.verticalGradient(
            listOf(FoxYellow, FurOrange, FurOrangeDark),
            startY = 4f,
            endY = 92f,
        )

    // Ears (behind the head)
    rotate(-earAngle, pivot = Offset(30f, 36f)) {
        drawPath(earPath(left = true), furBrush)
        drawPath(innerEarPath(left = true), FurNose.copy(alpha = 0.85f))
    }
    rotate(earAngle * 0.6f, pivot = Offset(70f, 36f)) {
        drawPath(earPath(left = false), furBrush)
        drawPath(innerEarPath(left = false), FurNose.copy(alpha = 0.85f))
    }

    // Head
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

    // Cream mask
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

    // Forehead flame mark
    val flame =
        Path().apply {
            moveTo(50f, 30f)
            quadraticTo(55f, 38f, 50f, 46f)
            quadraticTo(45f, 38f, 50f, 30f)
            close()
        }
    drawPath(flame, FoxYellow.copy(alpha = 0.75f))

    // Blush (fades in when happy)
    if (happy > 0f) {
        val blush = Color(0xFFFF4F7B).copy(alpha = 0.35f * happy)
        drawOval(blush, topLeft = Offset(20f, 64f), size = Size(10f, 6f))
        drawOval(blush, topLeft = Offset(70f, 64f), size = Size(10f, 6f))
    }

    // Eyes
    drawEye(cx = 35f + lookX, cy = 55f, open = eyeOpen)
    drawEye(cx = 65f + lookX, cy = 55f, open = eyeOpen)

    // Nose
    drawOval(FurNose, topLeft = Offset(45f, 73f), size = Size(10f, 7f))
    drawOval(Color.White.copy(alpha = 0.5f), topLeft = Offset(47.5f, 74f), size = Size(3f, 2f))

    // Mouth: neutral line → smile as happiness grows
    val mouth =
        Path().apply {
            moveTo(50f, 80f)
            lineTo(50f, 82f)
            moveTo(50f, 82f)
            quadraticTo(46f, 84f + 2f * happy, 43f, 82f - happy)
            moveTo(50f, 82f)
            quadraticTo(54f, 84f + 2f * happy, 57f, 82f - happy)
        }
    drawPath(mouth, FurNose, style = Stroke(width = 1.6f, cap = StrokeCap.Round))
}

private fun DrawScope.drawEye(cx: Float, cy: Float, open: Float) {
    if (open < 0.18f) {
        // Closed, sleepy arc
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
        drawCircle(Color.White, radius = 1.8f, center = Offset(cx + 1.5f, cy - h / 4f))
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

/** Three little "z"s drifting up and to the right while the fox sleeps. */
private fun DrawScope.drawSleepZ(progress: Float, alpha: Float) {
    for (i in 0 until 3) {
        val p = (progress + i / 3f) % 1f
        val zSize = 5f + 4f * p
        val x = 80f + 12f * p
        val y = 28f - 26f * p
        val a = alpha * (if (p < 0.2f) p / 0.2f else 1f - (p - 0.2f) / 0.8f)
        val z =
            Path().apply {
                moveTo(x, y)
                lineTo(x + zSize, y)
                lineTo(x, y + zSize)
                lineTo(x + zSize, y + zSize)
            }
        drawPath(
            z,
            FurOrangeDark.copy(alpha = a.coerceIn(0f, 1f)),
            style = Stroke(width = 1.8f, cap = StrokeCap.Round),
        )
    }
}
