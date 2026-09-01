package com.david.llegeix.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.david.llegeix.data.settings.AccentColor

/**
 * A fixed accent: the dot shown in Settings, and the two schemes built from it.
 */
internal data class AccentSchemes(
    /** The dot in the picker, legible against either background. */
    val swatch: Color,
    val light: ColorScheme,
    val dark: ColorScheme,
)

/**
 * The six named colours, each built from its own seed.
 *
 * Generated through [schemesFromSeed] rather than hand-written tone by tone.
 * The earlier version spelled out roughly twenty colours per accent, which was
 * both tedious and quietly unsafe: yellow needs a much darker text tone than
 * blue to stay readable, and picking those by eye is how a yellow accent ends
 * up failing contrast while the others pass. The generator chooses text tones
 * by measured contrast, so adding a colour here cannot introduce that bug.
 *
 * The seeds are the colours the dots actually show, so what you tap is what you
 * get. Yellow is pulled slightly towards amber: a pure yellow dot on a white
 * settings card is almost invisible.
 */
private val Seeds: Map<AccentColor, Color> = mapOf(
    AccentColor.RED to Color(0xFFD32F2F),
    AccentColor.ORANGE to Color(0xFFF57C00),
    AccentColor.YELLOW to Color(0xFFF2B705),
    AccentColor.GREEN to Color(0xFF2E7D32),
    AccentColor.BLUE to Color(0xFF1976D2),
    AccentColor.PURPLE to Color(0xFF7B3FBF),
)

private val schemeCache: Map<AccentColor, AccentSchemes> =
    Seeds.mapValues { (_, seed) -> schemesFromSeed(seed) }

/**
 * Null for the two entries that are not fixed colours: [AccentColor.SYSTEM],
 * which the platform supplies from the wallpaper, and [AccentColor.CUSTOM],
 * which is built from whatever the reader mixed.
 */
internal fun AccentColor.schemes(): AccentSchemes? = schemeCache[this]

/**
 * The dot for the Settings picker. The two non-colours have none of their own,
 * so the caller substitutes something live for them.
 */
internal fun AccentColor.swatchOrNull(): Color? = Seeds[this]
