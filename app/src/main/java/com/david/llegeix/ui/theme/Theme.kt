package com.david.llegeix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.david.llegeix.data.settings.AccentColor
import com.david.llegeix.data.settings.DEFAULT_CUSTOM_ACCENT
import com.david.llegeix.data.settings.ThemeMode

@Composable
fun LlegeixTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentColor = AccentColor.SYSTEM,
    customAccent: Int = DEFAULT_CUSTOM_ACCENT,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.isDark()

    // minSdk is 31, so dynamic colour is always available; no SDK_INT guard needed.
    val context = LocalContext.current
    val schemes = if (accent == AccentColor.CUSTOM) {
        remember(customAccent) { schemesFromSeed(Color(customAccent)) }
    } else {
        accent.schemes()
    }
    val base = when {
        schemes != null && darkTheme -> schemes.dark
        schemes != null -> schemes.light
        darkTheme -> dynamicDarkColorScheme(context)
        else -> dynamicLightColorScheme(context)
    }

    val colorScheme = when {
        themeMode == ThemeMode.AMOLED -> base.toTrueBlack()
        darkTheme -> base
        // Every fixed accent leaves the neutrals to Material's defaults, which
        // are tinted and low-contrast for body text. Reading is the whole job
        // here, so light mode gets neutrals chosen for legibility instead.
        schemes != null -> base.withReadableLightNeutrals()
        else -> base
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}

/**
 * Whether this mode renders dark right now, resolving SYSTEM against the device.
 */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.AMOLED -> true
}

/**
 * Collapses every background tone to true black.
 *
 * Only the surfaces move: the accent, the text and the outlines keep the values
 * the dark scheme already chose, because those are what make it readable. The
 * elevated containers go to near-black rather than black so a sheet or a card
 * still has an edge you can see against the page behind it.
 */
internal fun ColorScheme.toTrueBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF1A1A1A),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
    surfaceVariant = Color(0xFF161616),
    // Pure white on pure black is harsh over a long read; a touch under it
    // still clears 18:1.
    onBackground = Color(0xFFEDEDED),
    onSurface = Color(0xFFEDEDED),
    onSurfaceVariant = Color(0xFFC2C2C2),
    outline = Color(0xFF5C5C5C),
    outlineVariant = Color(0xFF2E2E2E),
)

/**
 * Light-mode neutrals picked for contrast rather than for tint.
 *
 * Material's defaults are already accessible — measured, they give 16.7:1 for
 * body text and 9.1:1 for the secondary line. These are a modest improvement on
 * that (17.8:1 and 9.6:1) and, more usefully, they are untinted: the defaults
 * pull the surfaces towards the accent, so the page took on a colour cast that
 * changed with the accent while the text stayed put. Paper should look like
 * paper whichever colour the reader picked.
 */
internal fun ColorScheme.withReadableLightNeutrals(): ColorScheme = copy(
    background = Color(0xFFFCFCFC),
    onBackground = Color(0xFF14151A),
    surface = Color(0xFFFCFCFC),
    onSurface = Color(0xFF14151A),
    surfaceDim = Color(0xFFEDEDF0),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F6F8),
    surfaceContainer = Color(0xFFF1F1F4),
    surfaceContainerHigh = Color(0xFFEAEAEF),
    surfaceContainerHighest = Color(0xFFE3E3E9),
    surfaceVariant = Color(0xFFE6E6EB),
    onSurfaceVariant = Color(0xFF41434A),
    outline = Color(0xFF74767E),
    outlineVariant = Color(0xFFCACBD2),
)
