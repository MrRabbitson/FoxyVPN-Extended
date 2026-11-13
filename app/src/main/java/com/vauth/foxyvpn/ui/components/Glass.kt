package com.vauth.foxyvpn.ui.components

import android.os.Build
import android.view.WindowManager
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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.vauth.foxyvpn.R
import com.vauth.foxyvpn.ui.theme.FoxMagenta
import com.vauth.foxyvpn.ui.theme.FoxOrange
import com.vauth.foxyvpn.ui.theme.FoxRed
import com.vauth.foxyvpn.ui.theme.FoxViolet
import com.vauth.foxyvpn.ui.theme.FoxYellow

/** Real backdrop blur needs RenderEffect, which exists from Android 12. */
private val BlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Something glass can sit on top of. Whatever is drawn by the node carrying
 * [glassSource] is also kept in [layer], so panes above can redraw the part
 * that lies behind them, blurred.
 */
class GlassSource internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
    private var frame by mutableIntStateOf(0)

    /** Subscribes the caller to redraws of this source. */
    internal fun observe(): Int = frame

    /** Called after the source has drawn; never subscribes the caller. */
    internal fun markDrawn() {
        Snapshot.withoutReadObservation { frame++ }
    }
}

@Composable
fun rememberGlassSource(): GlassSource {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassSource(layer) }
}

fun Modifier.glassSource(source: GlassSource): Modifier =
    this.onGloballyPositioned { source.origin = it.positionInRoot() }
        .drawWithContent {
            source.layer.record { this@drawWithContent.drawContent() }
            drawLayer(source.layer)
            // Tell panes above that what is behind them has changed.
            source.markDrawn()
        }

/** The colour backdrop shared by every screen; null inside dialogs. */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassSource?> { null }

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
    val behind = if (BlurSupported) backdrop else null
    // Without blur the pane must hide the detail behind it some other way.
    val solid = !BlurSupported && backdrop != null

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press =
        animateFloatAsState(
            targetValue = if (pressed) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
            label = "glass-press",
        )

    val blurRadius = with(LocalDensity.current) { 22.dp.toPx() }
    val pane = rememberGraphicsLayer()
    if (behind != null) {
        remember(pane, blurRadius) {
            pane.renderEffect = BlurEffect(blurRadius, blurRadius, TileMode.Clamp)
            true
        }
    }
    var position by remember { mutableStateOf(Offset.Zero) }

    val scrim =
        when {
            solid -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
            dark -> Color.Black.copy(alpha = 0.16f)
            else -> Color.White.copy(alpha = 0.22f)
        }
    val bodyTop = Color.White.copy(alpha = if (dark) 0.11f else 0.46f)
    val bodyBottom = Color.White.copy(alpha = if (dark) 0.03f else 0.24f)
    val sheenTop = Color.White.copy(alpha = if (dark) 0.08f else 0.26f)
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
                        if (behind != null) {
                            // Reading the frame counters redraws the pane whenever
                            // what is behind it was redrawn.
                            behind.observe()
                            over?.observe()
                            val at = position
                            pane.record {
                                translate(behind.origin.x - at.x, behind.origin.y - at.y) {
                                    drawLayer(behind.layer)
                                }
                                if (over != null) {
                                    translate(over.origin.x - at.x, over.origin.y - at.y) {
                                        drawLayer(over.layer)
                                    }
                                }
                            }
                            drawLayer(pane)
                        }
                        drawOutline(outline, scrim)
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

/** A group of rows on one pane, as used for settings sections. */
@Composable
fun GlassGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
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

/**
 * Floating glass bar at the top of a screen. Pass the screen's scrolling
 * content as [over] so it shows through the bar as it scrolls underneath.
 */
@Composable
fun GlassTopBar(
    title: String,
    onBack: (() -> Unit)?,
    over: GlassSource? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            rim = GlassRim.Vertical,
            over = over,
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (onBack != null) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.lucide_arrow_left),
                            contentDescription = stringResource(R.string.action_back),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = if (onBack == null) 14.dp else 0.dp),
                )
                actions()
            }
        }
    }
}

/**
 * Dialog on glass. The system blurs the whole screen behind the dialog window
 * (Android 12+, when the device allows it), so the translucent sheet reads as
 * frosted. Where that blur is unavailable the sheet is made nearly opaque.
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
    val windowBlur = rememberWindowBlurEnabled()
    val scheme = MaterialTheme.colorScheme
    val sheet =
        if (windowBlur) {
            if (dark) Color(0xFF2B2A33).copy(alpha = 0.62f) else Color.White.copy(alpha = 0.68f)
        } else {
            scheme.surfaceContainerHigh
        }
    val rimBright = Color.White.copy(alpha = if (dark) 0.45f else 0.95f)
    val rimDim = Color.White.copy(alpha = if (dark) 0.06f else 0.35f)
    val shape = RoundedCornerShape(28.dp)

    // Panes inside the dialog live in another window, so they cannot sample
    // the app's backdrop; they fall back to plain translucent glass.
    CompositionLocalProvider(LocalGlassBackdrop provides null) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier =
                modifier.border(
                    1.dp,
                    Brush.linearGradient(0f to rimBright, 0.3f to rimDim, 0.7f to rimDim, 1f to rimBright),
                    shape,
                ),
            dismissButton = dismissButton,
            title = {
                if (windowBlur) BlurBehindDialog()
                title?.invoke()
            },
            text = text,
            shape = shape,
            containerColor = sheet,
            tonalElevation = 0.dp,
        )
    }
}

@Composable
private fun rememberWindowBlurEnabled(): Boolean {
    val view = LocalView.current
    return remember(view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
        } else {
            false
        }
    }
}

/** Asks the system to blur everything behind the dialog this is placed in. */
@Composable
private fun BlurBehindDialog() {
    val view = LocalView.current
    val radius = with(LocalDensity.current) { 28.dp.roundToPx() }
    DisposableEffect(view, radius) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = radius }
            window.setDimAmount(0.28f)
        }
        onDispose {}
    }
}

/**
 * The backdrop every screen sits on: a few still discs of flame colour. They
 * have real edges on purpose, so the blur under the glass is visible. Nothing
 * moves on its own; the warm discs brighten or dim with [intensity].
 */
@Composable
fun FlameBackdrop(modifier: Modifier = Modifier, intensity: Float = 1f) {
    val dark = MaterialTheme.colorScheme.background.luma() < 0.3f
    val glow by
        animateFloatAsState(
            targetValue = intensity,
            animationSpec = tween(1_400, easing = EmphasizedEasing),
            label = "backdrop-glow",
        )
    val base = MaterialTheme.colorScheme.background
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // Opaque, so a blurred copy of this layer fully hides the sharp one beneath it.
        drawRect(base)
        val warm = (if (dark) 0.62f else 0.50f) * (0.40f + 0.60f * glow)
        val cool = if (dark) 0.50f else 0.30f
        fun disc(color: Color, x: Float, y: Float, radius: Float, strength: Float) {
            val at = Offset(w * x, h * y)
            val r = w * radius
            drawCircle(
                brush =
                    Brush.radialGradient(
                        0f to color.copy(alpha = strength),
                        0.62f to color.copy(alpha = strength * 0.85f),
                        0.90f to color.copy(alpha = strength * 0.30f),
                        1f to color.copy(alpha = 0f),
                        center = at,
                        radius = r,
                    ),
                radius = r,
                center = at,
            )
        }
        disc(FoxViolet, 0.02f, 0.72f, 0.42f, cool)
        disc(FoxRed, 0.90f, 0.95f, 0.36f, cool * 0.8f)
        disc(FoxMagenta, 0.98f, 0.46f, 0.34f, warm * 0.75f)
        disc(FoxOrange, 0.50f, 0.25f, 0.46f, warm)
        disc(FoxYellow, 0.10f, 0.08f, 0.24f, warm * 0.85f)
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
