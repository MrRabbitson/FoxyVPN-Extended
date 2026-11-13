package com.vauth.foxyvpn.ui.components

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import android.graphics.Bitmap
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import com.vauth.foxyvpn.data.AppLogger
import kotlin.math.hypot
import kotlin.math.max

/**
 * Theme switch in the manner of Telegram: the screen is photographed in the
 * old theme, the theme changes underneath, and the photograph is cut away by
 * a circle growing from the button that was pressed.
 */
class ThemeReveal {
    internal var snapshot by mutableStateOf<ImageBitmap?>(null)
        private set
    internal var center = Offset.Zero
        private set

    /** [view] is the app's root view; [center] the button centre in root coordinates. */
    fun start(view: View, center: Offset, change: () -> Unit) {
        if (snapshot != null || view.width <= 0 || view.height <= 0) {
            change()
            return
        }
        val photo =
            try {
                // Half resolution: a quarter of the pixels to draw and to keep on
                // screen, and it is only visible for half a second.
                val w = max(1, view.width / 2)
                val h = max(1, view.height / 2)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.scale(w / view.width.toFloat(), h / view.height.toFloat())
                withGlassPaused { view.draw(canvas) }
                bitmap.asImageBitmap()
            } catch (error: Exception) {
                AppLogger.w("ThemeReveal", "could not photograph the screen; switching without animation", error)
                null
            }
        this.center = center
        snapshot = photo
        if (photo != null) setGlassPaused(true)
        change()
    }

    internal fun finish() {
        snapshot = null
        setGlassPaused(false)
        GlassClock.keepLive(300L)
    }
}

val LocalThemeReveal = staticCompositionLocalOf<ThemeReveal?> { null }

/** Place above everything else in the app. */
@Composable
fun ThemeRevealOverlay(reveal: ThemeReveal) {
    val photo = reveal.snapshot ?: return
    val progress = androidx.compose.runtime.remember(photo) { Animatable(0f) }
    LaunchedEffect(photo) {
        try {
            // Let the new theme compose and draw underneath before uncovering it,
            // so that work does not land in the middle of the animation.
            repeat(3) { withFrameNanos {} }
            progress.animateTo(1f, tween(480, easing = EmphasizedEasing))
        } finally {
            reveal.finish()
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val c = reveal.center
        val reach =
            max(
                max(hypot(c.x, c.y), hypot(size.width - c.x, c.y)),
                max(hypot(c.x, size.height - c.y), hypot(size.width - c.x, size.height - c.y)),
            )
        val radius = reach * progress.value
        val hole = Path().apply { addOval(Rect(center = c, radius = radius)) }
        clipPath(hole, clipOp = ClipOp.Difference) {
            drawImage(
                image = photo,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Low,
            )
        }
    }
}
