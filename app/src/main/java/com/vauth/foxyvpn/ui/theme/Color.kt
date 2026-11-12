package com.vauth.foxyvpn.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/*
 * Firefox-inspired palette (Photon / Acorn): warm orange flame, ink-violet
 * night surfaces and the signature yellow → orange → red → magenta gradient.
 */

// Brand flame
val FoxYellow = Color(0xFFFFBD4F)
val FoxAmber = Color(0xFFFF9640)
val FoxOrange = Color(0xFFFF7139)
val FoxOrangeDeep = Color(0xFFE25920)
val FoxRed = Color(0xFFFF3750)
val FoxMagenta = Color(0xFFF5156C)
val FoxViolet = Color(0xFF9059FF)
val FoxInk = Color(0xFF20123A)

// Mascot fur
val FurOrange = Color(0xFFFF7A2F)
val FurOrangeDark = Color(0xFFE2541C)
val FurCream = Color(0xFFFFF4E6)
val FurNose = Color(0xFF2B1A12)

val FirefoxFlameColors = listOf(FoxYellow, FoxAmber, FoxOrange, FoxRed, FoxMagenta)
val FirefoxFlameBrush: Brush get() = Brush.linearGradient(FirefoxFlameColors)
fun firefoxSweepBrush(): Brush =
    Brush.sweepGradient(listOf(FoxYellow, FoxAmber, FoxOrange, FoxRed, FoxMagenta, FoxYellow))

// ---- Light ----
val md_theme_light_primary = FoxOrangeDeep
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFFFE2D1)
val md_theme_light_onPrimaryContainer = Color(0xFF3A1200)
val md_theme_light_inversePrimary = FoxAmber

val md_theme_light_secondary = Color(0xFFB04A00)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFFFEBD9)
val md_theme_light_onSecondaryContainer = Color(0xFF3A1A00)

val md_theme_light_tertiary = Color(0xFF7542E5)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFEDE3FF)
val md_theme_light_onTertiaryContainer = FoxInk

val md_theme_light_error = Color(0xFFD7264C)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_errorContainer = Color(0xFFFFDDE3)
val md_theme_light_onErrorContainer = Color(0xFF41000F)

val md_theme_light_background = Color(0xFFF9F9FB)
val md_theme_light_onBackground = Color(0xFF15141A)
val md_theme_light_surface = Color(0xFFF9F9FB)
val md_theme_light_onSurface = Color(0xFF15141A)
val md_theme_light_surfaceVariant = Color(0xFFF0F0F4)
val md_theme_light_onSurfaceVariant = Color(0xFF5B5B66)
val md_theme_light_surfaceTint = md_theme_light_primary

val md_theme_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerLow = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainer = Color(0xFFF5F4F7)
val md_theme_light_surfaceContainerHigh = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerHighest = Color(0xFFE8E7EC)
val md_theme_light_surfaceBright = Color(0xFFFFFFFF)
val md_theme_light_surfaceDim = Color(0xFFE0E0E6)

val md_theme_light_outline = Color(0xFF8F8F9D)
val md_theme_light_outlineVariant = Color(0xFFE0E0E6)
val md_theme_light_inverseSurface = Color(0xFF2B2A33)
val md_theme_light_inverseOnSurface = Color(0xFFFBFBFE)
val md_theme_light_scrim = Color(0xFF000000)

// ---- Dark (Firefox night) ----
val md_theme_dark_primary = FoxAmber
val md_theme_dark_onPrimary = FoxInk
val md_theme_dark_primaryContainer = Color(0xFF6B2A0A)
val md_theme_dark_onPrimaryContainer = Color(0xFFFFDCC7)
val md_theme_dark_inversePrimary = FoxOrangeDeep

val md_theme_dark_secondary = FoxYellow
val md_theme_dark_onSecondary = Color(0xFF3A2200)
val md_theme_dark_secondaryContainer = Color(0xFF52361A)
val md_theme_dark_onSecondaryContainer = Color(0xFFFFE3B8)

val md_theme_dark_tertiary = Color(0xFFAB71FF)
val md_theme_dark_onTertiary = FoxInk
val md_theme_dark_tertiaryContainer = Color(0xFF3B2470)
val md_theme_dark_onTertiaryContainer = Color(0xFFEDE3FF)

val md_theme_dark_error = Color(0xFFFF6A84)
val md_theme_dark_onError = Color(0xFF4A0012)
val md_theme_dark_errorContainer = Color(0xFF7A1530)
val md_theme_dark_onErrorContainer = Color(0xFFFFDDE3)

val md_theme_dark_background = Color(0xFF1C1B22)
val md_theme_dark_onBackground = Color(0xFFFBFBFE)
val md_theme_dark_surface = Color(0xFF1C1B22)
val md_theme_dark_onSurface = Color(0xFFFBFBFE)
val md_theme_dark_surfaceVariant = Color(0xFF42414D)
val md_theme_dark_onSurfaceVariant = Color(0xFFBFBFC9)
val md_theme_dark_surfaceTint = md_theme_dark_primary

val md_theme_dark_surfaceContainerLowest = Color(0xFF15141A)
val md_theme_dark_surfaceContainerLow = Color(0xFF23222B)
val md_theme_dark_surfaceContainer = Color(0xFF2B2A33)
val md_theme_dark_surfaceContainerHigh = Color(0xFF2B2A33)
val md_theme_dark_surfaceContainerHighest = Color(0xFF42414D)
val md_theme_dark_surfaceBright = Color(0xFF52525E)
val md_theme_dark_surfaceDim = Color(0xFF15141A)

val md_theme_dark_outline = Color(0xFF8F8F9D)
val md_theme_dark_outlineVariant = Color(0xFF42414D)
val md_theme_dark_inverseSurface = Color(0xFFFBFBFE)
val md_theme_dark_inverseOnSurface = Color(0xFF2B2A33)
val md_theme_dark_scrim = Color(0xFF000000)

data class FoxyStatusColors(val connected: Color, val connecting: Color)

val LightStatusColors =
    FoxyStatusColors(connected = Color(0xFF00856F), connecting = Color(0xFFE27F00))

val DarkStatusColors =
    FoxyStatusColors(connected = Color(0xFF54FFBD), connecting = FoxYellow)

val LocalFoxyStatusColors = staticCompositionLocalOf { LightStatusColors }
