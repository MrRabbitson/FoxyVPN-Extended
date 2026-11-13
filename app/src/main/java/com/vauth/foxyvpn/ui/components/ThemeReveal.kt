package com.vauth.foxyvpn.ui.components

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
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                bitmap.asImageBitmap()
            } catch (error: Exception) {
                AppLogger.w("ThemeReveal", "could not photograph the screen; switching without animation", error)
                null
            }
        this.center = center
        snapshot = photo
        change()
    }

    internal fun finish() {
        snapshot = null
    }
}

val LocalThemeReveal = staticCompositionLocalOf<ThemeReveal?> { null }

/** Place above everything else in the app. */
@Composable
fun ThemeRevealOverlay(reveal: ThemeReveal) {
    val photo = reveal.snapshot ?: return
    val progress = androidx.compose.runtime.remember(photo) { Animatable(0f) }
    LaunchedEffect(photo) {
        progress.animateTo(1f, tween(560, easing = EmphasizedEasing))
        reveal.finish()
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
        clipPath(hole, clipOp = ClipOp.Difference) { drawImage(photo) }
    }
}
