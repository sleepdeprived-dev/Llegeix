package com.david.llegeix.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Builds a usable colour scheme from a single colour the reader picked.
 *
 * Material's own generator lives in a separate artifact and pulls in a whole
 * colour-science library to do it properly. For one preference that is a lot of
 * weight, so this works in HSL instead: the hue is kept, and the lightness is
 * moved to the tones a scheme needs. It is not Material You's algorithm and does
 * not claim to be — but it does guarantee the two things that actually matter,
 * which are that text on each surface stays readable and that the result looks
 * like the colour that was chosen.
 */
internal fun schemesFromSeed(seed: Color): AccentSchemes {
    val hsl = seed.toHsl()
    val hue = hsl[0]
    // A very grey or very bright pick still has to produce a usable accent.
    val saturation = hsl[1].coerceIn(0.35f, 0.95f)

    fun tone(lightness: Float, sat: Float = saturation) =
        hslColor(hue, sat, lightness)

    // Text tones are chosen by contrast, not by a fixed lightness. A green at
    // lightness 0.38 and a blue at lightness 0.38 are nowhere near equally
    // readable — green is far brighter — so picking a number would leave some
    // hues failing against the surface behind them.
    val primaryOnLight = readableTone(hue, saturation, against = Color.White, goDarker = true)
    val primaryOnDark = readableTone(hue, saturation * 0.85f, against = DarkSurface, goDarker = false)

    return AccentSchemes(
        swatch = seed,
        light = lightColorScheme(
            primary = primaryOnLight,
            onPrimary = Color.White,
            primaryContainer = tone(0.90f, saturation * 0.7f),
            onPrimaryContainer = tone(0.14f),
            secondary = readableTone(hue, saturation * 0.45f, Color.White, true),
            onSecondary = Color.White,
            secondaryContainer = tone(0.90f, saturation * 0.35f),
            onSecondaryContainer = tone(0.15f, saturation * 0.5f),
            // A neighbouring hue, so the tertiary role is distinguishable
            // without belonging to a different palette.
            tertiary = readableTone(hue + 40f, saturation, Color.White, true),
            onTertiary = Color.White,
            tertiaryContainer = tone(0.90f, saturation * 0.6f).shiftHue(40f),
            onTertiaryContainer = tone(0.15f).shiftHue(40f),
        ),
        dark = darkColorScheme(
            primary = primaryOnDark,
            onPrimary = tone(0.18f),
            primaryContainer = tone(0.30f),
            onPrimaryContainer = tone(0.90f, saturation * 0.7f),
            secondary = readableTone(hue, saturation * 0.35f, DarkSurface, false),
            onSecondary = tone(0.20f, saturation * 0.4f),
            secondaryContainer = tone(0.32f, saturation * 0.35f),
            onSecondaryContainer = tone(0.90f, saturation * 0.3f),
            tertiary = readableTone(hue + 40f, saturation * 0.7f, DarkSurface, false),
            onTertiary = tone(0.20f).shiftHue(40f),
            tertiaryContainer = tone(0.32f).shiftHue(40f),
            onTertiaryContainer = tone(0.90f, saturation * 0.6f).shiftHue(40f),
        ),
    )
}

/** Material's default dark surface, which is what dark-scheme text sits on. */
private val DarkSurface = Color(0xFF141218)

/** The WCAG contrast ratio between two opaque colours. */
private fun contrastRatio(a: Color, b: Color): Float {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    val lighter = max(la, lb)
    val darker = min(la, lb)
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun Color.relativeLuminance(): Float {
    fun channel(value: Float): Float =
        if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}

/**
 * The nearest tone of this hue that is readable against [against].
 *
 * Walks the lightness away from mid-grey until the contrast clears 4.5:1, the
 * threshold for body text, and gives up at the extreme rather than returning
 * something unreadable. Keeps the hue the reader picked in every case.
 */
private fun readableTone(
    hue: Float,
    saturation: Float,
    against: Color,
    goDarker: Boolean,
): Color {
    val step = if (goDarker) -0.02f else 0.02f
    var lightness = 0.5f
    repeat(24) {
        val candidate = hslColor(hue, saturation, lightness)
        if (contrastRatio(candidate, against) >= 4.5f) return candidate
        lightness += step
    }
    return hslColor(hue, saturation, if (goDarker) 0.08f else 0.95f)
}

/** Hue in degrees, saturation and lightness in 0..1. */
internal fun Color.toHsl(): FloatArray {
    val r = red
    val g = green
    val b = blue
    val maximum = max(r, max(g, b))
    val minimum = min(r, min(g, b))
    val delta = maximum - minimum
    val lightness = (maximum + minimum) / 2f

    if (delta == 0f) return floatArrayOf(0f, 0f, lightness)

    val saturation = delta / (1f - abs(2f * lightness - 1f))
    val hue = when (maximum) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return floatArrayOf((hue + 360f) % 360f, saturation.coerceIn(0f, 1f), lightness)
}

internal fun hslColor(hue: Float, saturation: Float, lightness: Float): Color {
    val c = (1f - abs(2f * lightness - 1f)) * saturation
    val h = ((hue % 360f) + 360f) % 360f / 60f
    val x = c * (1f - abs(h % 2f - 1f))
    val (r1, g1, b1) = when {
        h < 1f -> Triple(c, x, 0f)
        h < 2f -> Triple(x, c, 0f)
        h < 3f -> Triple(0f, c, x)
        h < 4f -> Triple(0f, x, c)
        h < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = lightness - c / 2f
    return Color(
        (r1 + m).coerceIn(0f, 1f),
        (g1 + m).coerceIn(0f, 1f),
        (b1 + m).coerceIn(0f, 1f),
    )
}

private fun Color.shiftHue(degrees: Float): Color {
    val hsl = toHsl()
    return hslColor(hsl[0] + degrees, hsl[1], hsl[2])
}

/** "#RRGGBB", the form the picker shows and accepts. */
internal fun Color.toHex(): String = String.format(
    "#%02X%02X%02X",
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)

/** Parses "#RRGGBB" or "RRGGBB"; null when it is not a colour yet. */
internal fun parseHex(text: String): Color? {
    val cleaned = text.trim().removePrefix("#")
    if (cleaned.length != 6 || !cleaned.all { it.isDigit() || it.lowercaseChar() in "abcdef" }) {
        return null
    }
    return runCatching { Color(cleaned.toLong(16) or 0xFF000000L) }.getOrNull()
}
