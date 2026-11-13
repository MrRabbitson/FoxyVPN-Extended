package com.vauth.foxyvpn.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

private val ControlSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 420f)

/**
 * Glass switch: a glass track that fills with the accent colour, and a bead
 * that stretches as it slides, the way a drop does when it is dragged.
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val position by animateFloatAsState(if (checked) 1f else 0f, ControlSpring, label = "switch-position")
    val fill by
        animateColorAsState(
            targetValue =
                if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.78f) else Color.Transparent,
            animationSpec = tween(220),
            label = "switch-fill",
        )
    GlassSurface(
        modifier =
            modifier
                .size(width = 52.dp, height = 32.dp)
                .alpha(if (enabled) 1f else 0.45f)
                .then(
                    if (onCheckedChange != null) {
                        Modifier.toggleable(
                            value = checked,
                            enabled = enabled,
                            role = Role.Switch,
                            onValueChange = onCheckedChange,
                        )
                    } else {
                        Modifier
                    }
                ),
        shape = RoundedCornerShape(50),
        rim = GlassRim.Vertical,
        tint = fill,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 3.dp.toPx()
            val bead = size.height - inset * 2
            val t = position.coerceIn(0f, 1f)
            // Widest in the middle of the travel.
            val stretch = bead * 0.30f * sin(t * PI).toFloat()
            val width = bead + stretch
            val travel = size.width - inset * 2 - bead
            val left = inset + travel * t - stretch / 2f
            val corner = CornerRadius(bead / 2f)
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.18f),
                topLeft = Offset(left, inset + 1.dp.toPx()),
                size = Size(width, bead),
                cornerRadius = corner,
            )
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFE4E4EA)), startY = inset, endY = inset + bead),
                topLeft = Offset(left, inset),
                size = Size(width, bead),
                cornerRadius = corner,
            )
            // Highlight along the top of the bead.
            drawRoundRect(
                brush =
                    Brush.verticalGradient(
                        listOf(Color.White, Color.White.copy(alpha = 0f)),
                        startY = inset,
                        endY = inset + bead * 0.5f,
                    ),
                topLeft = Offset(left + bead * 0.18f, inset + bead * 0.08f),
                size = Size(width - bead * 0.36f, bead * 0.38f),
                cornerRadius = CornerRadius(bead * 0.2f),
            )
        }
    }
}

/** Glass radio button: a glass ring that fills with the accent colour around a white bead. */
@Composable
fun GlassRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dot by animateFloatAsState(if (selected) 1f else 0f, ControlSpring, label = "radio-dot")
    val fill by
        animateColorAsState(
            targetValue =
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.82f) else Color.Transparent,
            animationSpec = tween(200),
            label = "radio-fill",
        )
    GlassSurface(
        modifier =
            modifier
                .size(24.dp)
                .alpha(if (enabled) 1f else 0.45f)
                .then(
                    if (onClick != null) {
                        Modifier.selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    }
                ),
        shape = CircleShape,
        rim = GlassRim.Vertical,
        tint = fill,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.21f * dot.coerceAtLeast(0f)
            if (radius > 0f) {
                drawCircle(Color.Black.copy(alpha = 0.16f), radius, center.copy(y = center.y + 1.dp.toPx()))
                drawCircle(Color.White, radius)
            }
        }
    }
}

/** Glass checkbox: a glass tile that fills with the accent colour and draws its tick. */
@Composable
fun GlassCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tick by animateFloatAsState(if (checked) 1f else 0f, tween(220), label = "checkbox-tick")
    val fill by
        animateColorAsState(
            targetValue =
                if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.82f) else Color.Transparent,
            animationSpec = tween(200),
            label = "checkbox-fill",
        )
    GlassSurface(
        modifier =
            modifier
                .size(24.dp)
                .alpha(if (enabled) 1f else 0.45f)
                .then(
                    if (onCheckedChange != null) {
                        Modifier.toggleable(
                            value = checked,
                            enabled = enabled,
                            role = Role.Checkbox,
                            onValueChange = onCheckedChange,
                        )
                    } else {
                        Modifier
                    }
                ),
        shape = RoundedCornerShape(8.dp),
        rim = GlassRim.Vertical,
        tint = fill,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (tick <= 0f) return@Canvas
            val u = size.minDimension / 24f
            // The tick is drawn in two strokes, the short one first.
            val start = Offset(6.5f * u, 12.5f * u)
            val corner = Offset(10.5f * u, 16.5f * u)
            val end = Offset(17.5f * u, 8.5f * u)
            val first = (tick / 0.4f).coerceIn(0f, 1f)
            val second = ((tick - 0.4f) / 0.6f).coerceIn(0f, 1f)
            val path =
                Path().apply {
                    moveTo(start.x, start.y)
                    lineTo(start.x + (corner.x - start.x) * first, start.y + (corner.y - start.y) * first)
                    if (second > 0f) {
                        lineTo(corner.x + (end.x - corner.x) * second, corner.y + (end.y - corner.y) * second)
                    }
                }
            drawPath(
                path,
                Color.White,
                style = Stroke(width = 2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
