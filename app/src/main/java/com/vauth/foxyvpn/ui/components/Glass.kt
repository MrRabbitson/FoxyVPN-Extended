package com.vauth.foxyvpn.ui.components

import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.HandlerThread
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import java.util.concurrent.atomic.AtomicInteger
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.ceil
import android.os.Handler
import android.os.Looper
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.positionOnScreen
import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.vauth.foxyvpn.data.AppLogger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.toSize
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vauth.foxyvpn.R
import com.vauth.foxyvpn.ui.theme.FoxMagenta
import com.vauth.foxyvpn.ui.theme.FoxOrange
import com.vauth.foxyvpn.ui.theme.FoxViolet

private const val CAPTURE_WIDTH = 96


/**
 * Decides when the picture behind the glass is retaken. Scrolling moves
 * content without redrawing it, so nothing would tell a pane that what is
 * under it changed; while the screen is being touched (and briefly after) the
 * picture is retaken every frame, otherwise a couple of times a second.
 */
object GlassClock {
    private var tick by mutableIntStateOf(0)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var busyUntil = 0L

    internal fun observe(): Int = tick

    /** Keep the glass live for [millis] from now. */
    fun keepLive(millis: Long) {
        busyUntil = SystemClock.uptimeMillis() + millis
        wake.trySend(Unit)
    }

    suspend fun run() {
        while (true) {
            if (SystemClock.uptimeMillis() < busyUntil) {
                withFrameNanos { tick++ }
            } else {
                withTimeoutOrNull(500L) { wake.receive() }
                tick++
            }
        }
    }
}

/** Marks the whole app as "being touched" for [GlassClock]; consumes nothing. */
fun Modifier.glassTouchWatch(): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            GlassClock.keepLive(60_000L)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
            // Flings and settle animations outlast the finger.
            GlassClock.keepLive(1_600L)
        }
    }

/** Set if rendering the picture ever fails on this device; glass then shows the backdrop only. */
@Volatile private var captureBroken = false
/** True while something else needs every frame (the theme reveal); no pictures are taken then. */
private var capturePaused = false

internal fun setGlassPaused(paused: Boolean) {
    capturePaused = paused
}

private const val BUFFERS = 3
/** Pictures that may be on their way at once; more would make the UI wait for the reader. */
private const val MAX_IN_FLIGHT = 2

/**
 * Something glass can sit on top of. The node carrying [glassSource] draws
 * into a layer. Every frame the GPU renders a thumbnail of that layer (on top
 * of the backdrop) into a small off-screen surface; a worker thread reads it
 * back, blurs it and hands it over, and panes above show the piece that lies
 * behind them. This is what CSS calls backdrop-filter: blur, done by hand so
 * that it works on every Android version.
 *
 * Requests are not serialised: a new picture is asked for every frame while
 * earlier ones are still on their way, so the rate is the frame rate and only
 * the delay (two to three frames) depends on the device.
 *
 * Nothing that uses the picture may live inside the source, or it would end up
 * in its own background.
 */
class GlassSource
internal constructor(
    private val backdrop: GlassBackdrop?,
    internal val layer: GraphicsLayer,
) {
    internal var origin by mutableStateOf(Offset.Zero)
    internal var area = Size.Zero
        private set
    internal var blurred: ImageBitmap? = null
        private set

    private var frame by mutableIntStateOf(0)
    private val inFlight = AtomicInteger(0)
    private var lastRequestAt = 0L
    @Volatile private var released = false
    @Volatile private var pendingArea = Size.Zero
    private var reader: ImageReader? = null
    private val main = Handler(Looper.getMainLooper())
    private val drawScope = CanvasDrawScope()

    // Three bitmaps: the worker never writes to the one on screen, nor to the
    // one it has just handed over.
    private val buffers = arrayOfNulls<Bitmap>(BUFFERS)
    private val images = arrayOfNulls<ImageBitmap>(BUFFERS)
    @Volatile private var showing = -1
    private var handedOver = -1
    private var pixels = IntArray(0)
    private var scratch = IntArray(0)

    // Diagnostics, written to the log now and then.
    private var arrivals = 0
    private var delaySum = 0L
    private var windowStart = 0L

    /** Subscribes the caller to new pictures of this source. */
    internal fun observe(): Int = frame

    /** Called right after [layer] was recorded. */
    internal fun snap(scope: DrawScope) {
        if (captureBroken || capturePaused || released) return
        val now = SystemClock.uptimeMillis()
        // Pictures that never arrived must not block new ones for ever.
        if (inFlight.get() >= MAX_IN_FLIGHT && now - lastRequestAt > 1_000L) inFlight.set(0)
        if (inFlight.get() >= MAX_IN_FLIGHT) return
        val full = scope.size
        if (full.width < 1f || full.height < 1f) return
        val scale = CAPTURE_WIDTH / full.width
        val w = CAPTURE_WIDTH
        val h = max(1, (full.height * scale).roundToInt())
        try {
            var target = reader
            if (target == null || target.width != w || target.height != h) {
                target?.close()
                target = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, MAX_IN_FLIGHT + 2)
                target.setOnImageAvailableListener({ onPicture(it) }, ReadbackThread.handler)
                reader = target
                inFlight.set(0)
            }
            val behind = backdrop
            val at = origin
            val surface = target.surface
            val canvas = surface.lockHardwareCanvas()
            try {
                canvas.drawColor(android.graphics.Color.BLACK)
                drawScope.draw(scope, scope.layoutDirection, ComposeCanvas(canvas), Size(w.toFloat(), h.toFloat())) {
                    scale(scale, scale, pivot = Offset.Zero) {
                        if (behind != null && behind.area.width > 0f) {
                            translate(behind.origin.x - at.x, behind.origin.y - at.y) {
                                drawFlameField(behind.area, behind.glow, behind.dark, behind.base)
                            }
                        }
                        drawLayer(layer)
                    }
                }
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }
            // The image carries its own request time, so the delay can be measured.
            inFlight.incrementAndGet()
            lastRequestAt = now
            pendingArea = full
        } catch (error: Exception) {
            captureBroken = true
            AppLogger.w("Glass", "off-screen rendering failed; glass shows the backdrop only", error)
        }
    }

    /** Runs on the read-back thread, so the pixel work never costs the UI a frame. */
    private fun onPicture(source: ImageReader) {
        inFlight.updateAndGet { if (it > 0) it - 1 else 0 }
        try {
            // Older pictures still queued are dropped; only the newest matters.
            val image = source.acquireLatestImage() ?: return
            val w = image.width
            val h = image.height
            val requestedAt = image.timestamp / 1_000_000L
            try {
                val plane = image.planes[0]
                val bytes = plane.buffer
                val rowStride = plane.rowStride
                val pixelStride = plane.pixelStride
                if (pixels.size != w * h) {
                    pixels = IntArray(w * h)
                    scratch = IntArray(w * h)
                }
                for (y in 0 until h) {
                    var at = y * rowStride
                    val row = y * w
                    for (x in 0 until w) {
                        val r = bytes.get(at).toInt() and 0xFF
                        val g = bytes.get(at + 1).toInt() and 0xFF
                        val b = bytes.get(at + 2).toInt() and 0xFF
                        pixels[row + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        at += pixelStride
                    }
                }
            } finally {
                image.close()
            }
            boxBlur(pixels, scratch, w, h, radius = 3)
            boxBlur(pixels, scratch, w, h, radius = 3)
            var slot = 0
            while (slot == showing || slot == handedOver) slot++
            var out = buffers[slot]
            if (out == null || out.width != w || out.height != h) {
                out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                buffers[slot] = out
                images[slot] = out.asImageBitmap()
            }
            out.setPixels(pixels, 0, w, 0, 0, w, h)
            handedOver = slot
            val shownArea = pendingArea
            main.post {
                showing = slot
                blurred = images[slot]
                area = shownArea
                frame++
                logDelay(requestedAt)
            }
        } catch (error: Exception) {
            if (!released) {
                captureBroken = true
                AppLogger.w("Glass", "reading the picture back failed; glass shows the backdrop only", error)
            }
        }
    }

    private fun logDelay(requestedAt: Long) {
        val now = SystemClock.uptimeMillis()
        // Image timestamps are on the same clock on most devices; ignore them where they are not.
        val delay = now - requestedAt
        if (delay in 0L..2_000L) delaySum += delay
        arrivals++
        if (windowStart == 0L) windowStart = now
        val span = now - windowStart
        if (span > 15_000L || (span > 2_000L && arrivals >= 100)) {
            AppLogger.i(
                "Glass",
                "pictures: %.0f per second while live, %d ms behind".format(
                    java.util.Locale.US,
                    arrivals * 1000f / span,
                    delaySum / arrivals,
                ),
            )
            arrivals = 0
            delaySum = 0L
            windowStart = 0L
        }
    }

    internal fun release() {
        released = true
        reader?.close()
        reader = null
    }
}

/** One background thread shared by every source for reading pictures back. */
private object ReadbackThread {
    val handler: Handler by lazy {
        val thread = HandlerThread("glass-readback")
        thread.start()
        Handler(thread.looper)
    }
}

@Composable
fun rememberGlassSource(): GlassSource {
    val backdrop = LocalGlassBackdrop.current
    val layer = rememberGraphicsLayer()
    val source = remember(backdrop, layer) { GlassSource(backdrop, layer) }
    DisposableEffect(source) { onDispose { source.release() } }
    return source
}

fun Modifier.glassSource(source: GlassSource): Modifier =
    this.onGloballyPositioned { source.origin = it.positionInRoot() }
        .drawWithContent {
            GlassClock.observe()
            source.layer.record { this@drawWithContent.drawContent() }
            drawLayer(source.layer)
            source.snap(this)
        }

/**
 * The colour backdrop shared by every screen. Panes that have no content
 * under them show the matching piece of its blurred picture.
 */
class GlassBackdrop {
    internal var origin by mutableStateOf(Offset.Zero)
    internal var area by mutableStateOf(Size.Zero)
    internal var blurred by mutableStateOf<ImageBitmap?>(null)
    internal var glow = 1f
    internal var dark = true
    internal var base = Color.Black
    /** Where the app's root sits on the physical screen; dialogs need it. */
    internal var onScreen = Offset.Zero
}

/** Null inside dialogs, which live in their own window. */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** The picture of the current screen's content, for bars that float above it. */
val LocalGlassContent = staticCompositionLocalOf<GlassSource?> { null }

enum class GlassRim {
    /** Light catches opposite corners. For large panes. */
    Diagonal,
    /** Light catches the top and bottom edges. For buttons and bars. */
    Vertical,
}

private fun Color.luma(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
private fun isDarkGlass(): Boolean = MaterialTheme.colorScheme.onSurface.luma() > 0.5f

/**
 * A pane of glass. It shows what is really behind it (the backdrop, plus
 * [over] when the pane floats above scrolling content) through a blur, then
 * adds a faint body tint, a sheen and a lit rim. Pressing it flexes the pane.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    onClick: (() -> Unit)? = null,
    rim: GlassRim = GlassRim.Diagonal,
    tint: Color = Color.Unspecified,
    over: GlassSource? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = isDarkGlass()
    val backdrop = LocalGlassBackdrop.current

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press =
        animateFloatAsState(
            targetValue = if (pressed) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
            label = "glass-press",
        )

    var position by remember { mutableStateOf(Offset.Zero) }

    // Only a whisper of white over the blurred backdrop: the colour comes from
    // what is behind the pane, not from a fill.
    val bodyTop = Color.White.copy(alpha = if (dark) 0.07f else 0.24f)
    val bodyBottom = Color.White.copy(alpha = if (dark) 0.00f else 0.06f)
    val sheenTop = Color.White.copy(alpha = if (dark) 0.06f else 0.20f)
    val rimBright = Color.White.copy(alpha = if (dark) 0.50f else 0.95f)
    val rimDim = Color.White.copy(alpha = if (dark) 0.06f else 0.35f)

    Box(
        modifier =
            modifier
                .onGloballyPositioned { position = it.positionInRoot() }
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
                    val edge =
                        when (rim) {
                            GlassRim.Diagonal ->
                                Brush.linearGradient(
                                    0f to rimBright,
                                    0.30f to rimDim,
                                    0.70f to rimDim,
                                    1f to rimBright.copy(alpha = rimBright.alpha * 0.55f),
                                    start = Offset.Zero,
                                    end = Offset(size.width, size.height),
                                )
                            GlassRim.Vertical ->
                                Brush.verticalGradient(
                                    0f to rimBright,
                                    0.32f to rimDim,
                                    0.68f to rimDim,
                                    1f to rimBright.copy(alpha = rimBright.alpha * 0.7f),
                                )
                        }
                    // Half of the stroke is clipped away, leaving a 1dp inner rim.
                    val edgeStroke = Stroke(width = 2.dp.toPx())
                    onDrawWithContent {
                        if (backdrop != null) {
                            val at = position
                            // What is behind this pane, blurred: the content it floats
                            // over if there is any, otherwise the backdrop.
                            over?.observe()
                            val picture = over?.blurred
                            if (over != null && picture != null) {
                                val area = over.area
                                translate(over.origin.x - at.x, over.origin.y - at.y) {
                                    drawImage(
                                        image = picture,
                                        dstSize = IntSize(area.width.roundToInt(), area.height.roundToInt()),
                                        colorFilter = Vivid,
                                        filterQuality = FilterQuality.Low,
                                    )
                                }
                            } else {
                                val image = backdrop.blurred
                                val area = backdrop.area
                                if (image != null && area.width > 0f) {
                                    translate(backdrop.origin.x - at.x, backdrop.origin.y - at.y) {
                                        drawImage(
                                            image = image,
                                            dstSize = IntSize(area.width.roundToInt(), area.height.roundToInt()),
                                            colorFilter = Vivid,
                                            filterQuality = FilterQuality.Low,
                                        )
                                    }
                                }
                            }
                        }
                        if (tint.isSpecified) drawOutline(outline, tint)
                        drawOutline(outline, body)
                        drawOutline(outline, sheen)
                        val lit = press.value
                        if (lit > 0f) drawOutline(outline, Color.White, alpha = 0.10f * lit)
                        drawContent()
                        drawOutline(outline, edge, style = edgeStroke)
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

/** A card for content (grouped rows, lists): a glass pane with a calmer rim. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(modifier = modifier, shape = RoundedCornerShape(22.dp), onClick = onClick, content = content)
}

/** Hairline between rows of a [SectionCard], inset like the row text. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
    )
}

/** A group of rows on one card, as used for settings sections. */
@Composable
fun GlassGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    SectionCard(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

class GlassTab(val key: String, val icon: Int, val label: String)

/**
 * Floating tab bar. The selected tab sits in a lighter capsule that slides
 * between tabs; [over] is the content scrolling underneath.
 */
@Composable
fun GlassTabBar(
    tabs: List<GlassTab>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    over: GlassSource? = null,
) {
    val index = tabs.indexOfFirst { it.key == selected }.coerceAtLeast(0)
    val slide by
        animateFloatAsState(
            targetValue = index.toFloat(),
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
            label = "tab-capsule",
        )
    val capsule = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    GlassSurface(
        modifier = modifier.height(64.dp).widthIn(max = 360.dp).fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        rim = GlassRim.Vertical,
        over = over,
    ) {
        Row(
            Modifier.fillMaxSize()
                .padding(5.dp)
                .drawBehind {
                    val slot = size.width / tabs.size
                    drawRoundRect(
                        color = capsule,
                        topLeft = Offset(slot * slide, 0f),
                        size = Size(slot, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                }
        ) {
            for (tab in tabs) {
                val active = tab.key == selected
                val tint by
                    animateColorAsState(
                        targetValue =
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(220),
                        label = "tab-tint",
                    )
                Column(
                    modifier =
                        Modifier.weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(27.dp))
                            .selectable(
                                selected = active,
                                role = Role.Tab,
                                interactionSource = null,
                                indication = null,
                                onClick = { onSelect(tab.key) },
                            ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(painterResource(tab.icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    Text(tab.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
                }
            }
        }
    }
}

/** Pill button made of glass, lit along its top and bottom edges. */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable RowScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier.heightIn(min = 40.dp).alpha(if (enabled) 1f else 0.45f),
        shape = RoundedCornerShape(50),
        onClick = if (enabled) onClick else null,
        rim = GlassRim.Vertical,
        tint = tint,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                Row(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
    }
}

/** Drop-in replacement for Material's text button. */
@Composable
fun GlassTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentColor = MaterialTheme.colorScheme.primary,
        content = content,
    )
}

/** Round glass button holding a single icon. */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    over: GlassSource? = null,
    content: @Composable () -> Unit,
) {
    GlassSurface(
        modifier = modifier.size(44.dp).alpha(if (enabled) 1f else 0.45f),
        shape = CircleShape,
        onClick = if (enabled) onClick else null,
        rim = GlassRim.Vertical,
        over = over,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Box(Modifier.align(Alignment.Center).size(22.dp), contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}

private class TopBarSpec(
    val owner: Any,
    val title: String,
    val onBack: (() -> Unit)?,
    val leading: (@Composable () -> Unit)?,
    val actions: @Composable RowScope.() -> Unit,
)

/**
 * Holds the top bar the current screen asked for. The bar itself is drawn by
 * [GlassTopBarHost], above and outside the screen, because it shows a picture
 * of the screen and so cannot be part of it.
 */
class TopBarHost {
    private var specs by mutableStateOf(listOf<TopBarSpec>())

    private fun put(spec: TopBarSpec) {
        val index = specs.indexOfFirst { it.owner === spec.owner }
        specs = if (index < 0) specs + spec else specs.toMutableList().also { it[index] = spec }
    }

    private fun drop(owner: Any) {
        specs = specs.filterNot { it.owner === owner }
    }

    internal fun show(owner: Any, title: String, onBack: (() -> Unit)?, leading: (@Composable () -> Unit)?, actions: @Composable RowScope.() -> Unit) =
        put(TopBarSpec(owner, title, onBack, leading, actions))

    internal fun hide(owner: Any) = drop(owner)

    /** The most recently opened screen wins while two overlap in a transition. */
    @Composable
    internal fun Current(over: GlassSource?) {
        val spec = specs.lastOrNull() ?: return
        Box(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            GlassSurface(
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                rim = GlassRim.Vertical,
                over = over,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val back = spec.onBack
                    if (back != null) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = back),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.lucide_arrow_left),
                                contentDescription = stringResource(R.string.action_back),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    } else {
                        Spacer(Modifier.width(if (spec.leading == null) 10.dp else 6.dp))
                    }
                    spec.leading?.invoke()
                    Text(
                        spec.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    spec.actions(this)
                }
            }
        }
    }
}

val LocalTopBarHost = staticCompositionLocalOf<TopBarHost?> { null }

/** Draws the bar the current screen registered. Place above the screens, outside their source. */
@Composable
fun GlassTopBarHost(host: TopBarHost, over: GlassSource?) {
    host.Current(over)
}

/**
 * Declares the screen's top bar. Takes up the bar's room where it is placed;
 * the bar itself is drawn by [GlassTopBarHost] so that the screen can show
 * through it.
 */
@Composable
fun GlassTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val host = LocalTopBarHost.current
    val owner = remember { Any() }
    SideEffect { host?.show(owner, title, onBack, leading, actions) }
    DisposableEffect(host, owner) { onDispose { host?.hide(owner) } }
    Spacer(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(72.dp))
}

/**
 * Dialog on glass. A dialog lives in its own window, so it cannot see the app
 * behind it; instead it paints the piece of the screen's blurred picture that
 * it covers, found by comparing where both sit on the physical screen.
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
    val dark = isDarkGlass()
    val backdrop = LocalGlassBackdrop.current
    val screen = LocalGlassContent.current
    val solid = MaterialTheme.colorScheme.surfaceContainerHigh
    val veil = if (dark) Color(0xFF16151B).copy(alpha = 0.24f) else Color.White.copy(alpha = 0.32f)
    val sheenTop = Color.White.copy(alpha = if (dark) 0.07f else 0.22f)
    val rimBright = Color.White.copy(alpha = if (dark) 0.45f else 0.95f)
    val rimDim = Color.White.copy(alpha = if (dark) 0.06f else 0.35f)
    val shape = RoundedCornerShape(28.dp)

    // Panes inside the dialog cannot sample the app's backdrop either.
    CompositionLocalProvider(LocalGlassBackdrop provides null) {
        var onScreen by remember { mutableStateOf(Offset.Zero) }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier =
                modifier
                    .onGloballyPositioned {
                        onScreen = it.positionOnScreen()
                    }
                    .clip(shape)
                    .drawBehind {
                        val picture = screen?.blurred
                        if (screen != null && backdrop != null && picture != null) {
                            screen.observe()
                            val area = screen.area
                            val left = backdrop.onScreen.x + screen.origin.x - onScreen.x
                            val top = backdrop.onScreen.y + screen.origin.y - onScreen.y
                            translate(left, top) {
                                drawImage(
                                    image = picture,
                                    dstSize = IntSize(area.width.roundToInt(), area.height.roundToInt()),
                                    colorFilter = Vivid,
                                    filterQuality = FilterQuality.Low,
                                )
                            }
                            drawRect(veil)
                        } else {
                            drawRect(solid)
                        }
                        drawRect(
                            Brush.verticalGradient(0f to sheenTop, 1f to Color.Transparent, endY = size.height * 0.35f)
                        )
                    }
                    .border(
                        1.dp,
                        Brush.linearGradient(0f to rimBright, 0.3f to rimDim, 0.7f to rimDim, 1f to rimBright),
                        shape,
                    ),
            dismissButton = dismissButton,
            title = {
                LighterDialogDim()
                title?.invoke()
            },
            text = text,
            shape = shape,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        )
    }
}

/** The default dimming hides most of the screen the dialog is supposed to show through. */
@Composable
private fun LighterDialogDim() {
    val view = LocalView.current
    DisposableEffect(view) {
        (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0.30f)
        onDispose {}
    }
}

private class Disc(
    val color: Color,
    val x: Float,
    val y: Float,
    val radius: Float,
    val warm: Boolean,
    val weight: Float,
)

/** Positions are fractions of the screen; radii are fractions of its width. */
private val Discs =
    listOf(
        Disc(FoxOrange, 0.50f, 0.20f, 0.95f, warm = true, weight = 1f),
        Disc(FoxMagenta, 1.05f, 0.02f, 0.70f, warm = true, weight = 0.45f),
        Disc(FoxViolet, -0.05f, 0.52f, 0.80f, warm = false, weight = 1f),
        Disc(FoxMagenta, 1.00f, 0.80f, 0.75f, warm = false, weight = 0.8f),
        Disc(FoxOrange, 0.20f, 1.02f, 0.70f, warm = false, weight = 0.7f),
    )

/**
 * Paints the backdrop: the theme background with a soft glow behind the top of
 * the screen. Quiet on purpose; the interface, not the wallpaper, is the subject.
 */
private fun DrawScope.drawFlameField(area: Size, glow: Float, dark: Boolean, base: Color) {
    drawRect(base, size = area)
    val warm = (if (dark) 0.30f else 0.22f) * (0.30f + 0.70f * glow)
    val cool = if (dark) 0.20f else 0.12f
    for (disc in Discs) {
        val strength = (if (disc.warm) warm else cool) * disc.weight
        val at = Offset(area.width * disc.x, area.height * disc.y)
        val radius = area.width * disc.radius
        drawCircle(
            brush =
                Brush.radialGradient(
                    0f to disc.color.copy(alpha = strength),
                    0.5f to disc.color.copy(alpha = strength * 0.42f),
                    1f to disc.color.copy(alpha = 0f),
                    center = at,
                    radius = radius,
                ),
            radius = radius,
            center = at,
        )
    }
}

/** Slight saturation lift, as glass does to what is seen through it. */
private val Vivid = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.35f) })

/**
 * A blurred picture of the whole backdrop. It is painted small and blurred in
 * software, which works on every Android version; stretched back to full size
 * it is a wide, even blur with the colours at full strength.
 */
private fun renderBlurredField(area: Size, glow: Float, dark: Boolean, base: Color, density: Density): ImageBitmap? {
    if (area.width < 1f || area.height < 1f) return null
    val w = CAPTURE_WIDTH
    val h = max(1, (CAPTURE_WIDTH * area.height / area.width).roundToInt())
    val bitmap = ImageBitmap(w, h)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, ComposeCanvas(bitmap), Size(w.toFloat(), h.toFloat())) {
        drawFlameField(size, glow, dark, base)
    }
    val native = bitmap.asAndroidBitmap()
    val pixels = IntArray(w * h)
    val scratch = IntArray(w * h)
    native.getPixels(pixels, 0, w, 0, 0, w, h)
    repeat(3) { boxBlur(pixels, scratch, w, h, radius = 3) }
    native.setPixels(pixels, 0, w, 0, 0, w, h)
    return bitmap
}

/**
 * One box-blur pass over opaque pixels, in place, with running sums so the
 * cost does not depend on the radius. Two passes give a soft, even blur.
 */
private fun boxBlur(pixels: IntArray, scratch: IntArray, w: Int, h: Int, radius: Int) {
    val window = radius * 2 + 1
    // Rows: pixels -> scratch
    for (y in 0 until h) {
        val row = y * w
        var r = 0
        var g = 0
        var b = 0
        for (k in -radius..radius) {
            val p = pixels[row + k.coerceIn(0, w - 1)]
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
        }
        for (x in 0 until w) {
            scratch[row + x] = (0xFF shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
            val enter = pixels[row + minOf(x + radius + 1, w - 1)]
            val leave = pixels[row + maxOf(x - radius, 0)]
            r += ((enter shr 16) and 0xFF) - ((leave shr 16) and 0xFF)
            g += ((enter shr 8) and 0xFF) - ((leave shr 8) and 0xFF)
            b += (enter and 0xFF) - (leave and 0xFF)
        }
    }
    // Columns: scratch -> pixels
    for (x in 0 until w) {
        var r = 0
        var g = 0
        var b = 0
        for (k in -radius..radius) {
            val p = scratch[k.coerceIn(0, h - 1) * w + x]
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
        }
        for (y in 0 until h) {
            pixels[y * w + x] = (0xFF shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
            val enter = scratch[minOf(y + radius + 1, h - 1) * w + x]
            val leave = scratch[maxOf(y - radius, 0) * w + x]
            r += ((enter shr 16) and 0xFF) - ((leave shr 16) and 0xFF)
            g += ((enter shr 8) and 0xFF) - ((leave shr 8) and 0xFF)
            b += (enter and 0xFF) - (leave and 0xFF)
        }
    }
}

/**
 * The backdrop every screen sits on: still discs of flame colour with crisp
 * edges. Nothing moves on its own; the warm discs brighten or dim with
 * [intensity]. It also keeps [state] supplied with its blurred twin.
 */
@Composable
fun FlameBackdrop(state: GlassBackdrop, modifier: Modifier = Modifier, intensity: Float = 1f) {
    val base = MaterialTheme.colorScheme.background
    val dark = base.luma() < 0.3f
    val glow by
        animateFloatAsState(
            targetValue = intensity,
            animationSpec = tween(1_400, easing = EmphasizedEasing),
            label = "backdrop-glow",
        )
    val density = LocalDensity.current
    // Re-blur in a few steps while the glow animates rather than every frame.
    val glowStep = (glow * 16f).roundToInt()
    val area = state.area
    val blurred =
        remember(area, dark, base, glowStep) {
            renderBlurredField(area, glowStep / 16f, dark, base, density)
        }
    state.glow = glowStep / 16f
    state.dark = dark
    state.base = base
    SideEffect { state.blurred = blurred }
    val rootView = LocalView.current
    Canvas(
        modifier.onGloballyPositioned {
            state.origin = it.positionInRoot()
            state.area = it.size.toSize()
            val corner = IntArray(2)
            rootView.getLocationOnScreen(corner)
            state.onScreen = Offset(corner[0].toFloat(), corner[1].toFloat())
        }
    ) {
        drawFlameField(area = size, glow = glowStep / 16f, dark = dark, base = base)
    }
}

/**
 * Makes Material surfaces inside see-through, so lists and bars of a screen
 * sit directly on the shared backdrop.
 */
@Composable
fun GlassScreen(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    MaterialTheme(
        colorScheme = scheme.copy(surface = Color.Transparent, background = Color.Transparent),
        content = content,
    )
}
