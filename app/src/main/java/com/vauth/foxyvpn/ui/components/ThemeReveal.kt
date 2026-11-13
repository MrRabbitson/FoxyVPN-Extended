package com.vauth.foxyvpn.ui.components

import kotlinx.coroutines.withTimeoutOrNull
import android.app.Activity
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
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
import androidx.compose.ui.geometry.Rect as ComposeRect
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
        val window = (view.context as? Activity)?.window
        if (snapshot != null || window == null || view.width <= 0 || view.height <= 0) {
            change()
            return
        }
        // Copy what is on screen straight from the GPU, at half resolution.
        val bitmap =
            Bitmap.createBitmap(max(1, view.width / 2), max(1, view.height / 2), Bitmap.Config.ARGB_8888)
        val corner = IntArray(2)
        view.getLocationInWindow(corner)
        val bounds = Rect(corner[0], corner[1], corner[0] + view.width, corner[1] + view.height)
        try {
            PixelCopy.request(
                window,
                bounds,
                bitmap,
                { result ->
                    if (result == PixelCopy.SUCCESS && snapshot == null) {
                        this.center = center
                        snapshot = bitmap.asImageBitmap()
                    }
                    change()
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (error: Exception) {
            AppLogger.w("ThemeReveal", "could not copy the screen; switching without animation", error)
            change()
        }
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
    val screen = LocalGlassContent.current
    LaunchedEffect(photo) {
        try {
            // Let the new theme compose and draw underneath before uncovering it,
            // and wait for the glass to pick it up, so neither lands mid-animation.
            GlassClock.keepLive(600L)
            repeat(2) { withFrameNanos {} }
            if (screen != null) {
                // A picture asked for from now on is guaranteed to be of the new theme.
                val themed = SystemClock.uptimeMillis()
                withTimeoutOrNull(500L) { while (!screen.isFreshSince(themed)) withFrameNanos {} }
                // One more frame so the bars have drawn it.
                withFrameNanos {}
            }
            // Nothing but the circle runs from here on.
            setGlassPaused(true)
            progress.animateTo(1f, tween(460, easing = EmphasizedEasing))
        } finally {
            setGlassPaused(false)
            reveal.finish()
            GlassClock.keepLive(300L)
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
        val hole = Path().apply { addOval(ComposeRect(center = c, radius = radius)) }
        clipPath(hole, clipOp = ClipOp.Difference) {
            drawImage(
                image = photo,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Low,
            )
        }
    }
}
