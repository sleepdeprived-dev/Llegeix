package com.david.llegeix.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.david.llegeix.data.settings.AccentColor

/**
 * The fixed colour schemes offered by Settings, one light and one dark each.
 *
 * Hand-written rather than generated from a seed colour: Material's tonal
 * palette generator lives in a separate artifact, and six pairs of schemes is
 * far less weight than pulling that in for a preference that will never grow
 * past a handful of choices. [AccentColor.SYSTEM] is absent on purpose — the
 * platform builds that one from the wallpaper.
 */
internal data class AccentSchemes(
    /** The dot shown in the Settings picker, legible against either background. */
    val swatch: Color,
    val light: ColorScheme,
    val dark: ColorScheme,
)

private val SenyeraSchemes = AccentSchemes(
    swatch = Color(0xFFDA121A),
    light = lightColorScheme(
        primary = Color(0xFFB3151B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDAD5),
        onPrimaryContainer = Color(0xFF410002),
        secondary = Color(0xFF775652),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFDAD5),
        onSecondaryContainer = Color(0xFF2C1512),
        tertiary = Color(0xFF7A5900),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFDEA6),
        onTertiaryContainer = Color(0xFF261A00),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFFFB4AB),
        onPrimary = Color(0xFF690004),
        primaryContainer = Color(0xFF93000A),
        onPrimaryContainer = Color(0xFFFFDAD5),
        secondary = Color(0xFFE7BDB7),
        onSecondary = Color(0xFF442926),
        secondaryContainer = Color(0xFF5D3F3B),
        onSecondaryContainer = Color(0xFFFFDAD5),
        tertiary = Color(0xFFF3C24B),
        onTertiary = Color(0xFF412D00),
        tertiaryContainer = Color(0xFF5D4200),
        onTertiaryContainer = Color(0xFFFFDEA6),
    ),
)

private val BlueSchemes = AccentSchemes(
    swatch = Color(0xFF1B6FB8),
    light = lightColorScheme(
        primary = Color(0xFF00639B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFCEE5FF),
        onPrimaryContainer = Color(0xFF001D33),
        secondary = Color(0xFF51606F),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD4E4F6),
        onSecondaryContainer = Color(0xFF0D1D2A),
        tertiary = Color(0xFF67587A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFEDDCFF),
        onTertiaryContainer = Color(0xFF221533),
    ),
    dark = darkColorScheme(
        primary = Color(0xFF97CBFF),
        onPrimary = Color(0xFF003353),
        primaryContainer = Color(0xFF004A77),
        onPrimaryContainer = Color(0xFFCEE5FF),
        secondary = Color(0xFFB9C8DA),
        onSecondary = Color(0xFF233240),
        secondaryContainer = Color(0xFF394857),
        onSecondaryContainer = Color(0xFFD4E4F6),
        tertiary = Color(0xFFD2BFE7),
        onTertiary = Color(0xFF382A49),
        tertiaryContainer = Color(0xFF4F4061),
        onTertiaryContainer = Color(0xFFEDDCFF),
    ),
)

private val GreenSchemes = AccentSchemes(
    swatch = Color(0xFF2E7D52),
    light = lightColorScheme(
        primary = Color(0xFF1F6B44),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFA8F2C1),
        onPrimaryContainer = Color(0xFF00210F),
        secondary = Color(0xFF4E6353),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD0E8D5),
        onSecondaryContainer = Color(0xFF0C1F14),
        tertiary = Color(0xFF3B6470),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFBFE9F8),
        onTertiaryContainer = Color(0xFF001F27),
    ),
    dark = darkColorScheme(
        primary = Color(0xFF8CD6A6),
        onPrimary = Color(0xFF00391F),
        primaryContainer = Color(0xFF005230),
        onPrimaryContainer = Color(0xFFA8F2C1),
        secondary = Color(0xFFB5CCBA),
        onSecondary = Color(0xFF203528),
        secondaryContainer = Color(0xFF364B3D),
        onSecondaryContainer = Color(0xFFD0E8D5),
        tertiary = Color(0xFFA3CDDA),
        onTertiary = Color(0xFF033640),
        tertiaryContainer = Color(0xFF224C57),
        onTertiaryContainer = Color(0xFFBFE9F8),
    ),
)

private val VioletSchemes = AccentSchemes(
    swatch = Color(0xFF6750A4),
    light = lightColorScheme(
        primary = Color(0xFF6750A4),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEADDFF),
        onPrimaryContainer = Color(0xFF21005D),
        secondary = Color(0xFF625B71),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE8DEF8),
        onSecondaryContainer = Color(0xFF1D192B),
        tertiary = Color(0xFF7D5260),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFD8E4),
        onTertiaryContainer = Color(0xFF31111D),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFD0BCFF),
        onPrimary = Color(0xFF381E72),
        primaryContainer = Color(0xFF4F378B),
        onPrimaryContainer = Color(0xFFEADDFF),
        secondary = Color(0xFFCCC2DC),
        onSecondary = Color(0xFF332D41),
        secondaryContainer = Color(0xFF4A4458),
        onSecondaryContainer = Color(0xFFE8DEF8),
        tertiary = Color(0xFFEFB8C8),
        onTertiary = Color(0xFF492532),
        tertiaryContainer = Color(0xFF633B48),
        onTertiaryContainer = Color(0xFFFFD8E4),
    ),
)

private val AmberSchemes = AccentSchemes(
    swatch = Color(0xFFC98A00),
    light = lightColorScheme(
        primary = Color(0xFF7C5800),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDEA6),
        onPrimaryContainer = Color(0xFF271900),
        secondary = Color(0xFF6C5C3F),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF5E0BB),
        onSecondaryContainer = Color(0xFF241A04),
        tertiary = Color(0xFF4B6547),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFCDEBC5),
        onTertiaryContainer = Color(0xFF092009),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFF6BE48),
        onPrimary = Color(0xFF412D00),
        primaryContainer = Color(0xFF5E4200),
        onPrimaryContainer = Color(0xFFFFDEA6),
        secondary = Color(0xFFD8C4A0),
        onSecondary = Color(0xFF3B2F15),
        secondaryContainer = Color(0xFF53452A),
        onSecondaryContainer = Color(0xFFF5E0BB),
        tertiary = Color(0xFFB1CFAA),
        onTertiary = Color(0xFF1E361D),
        tertiaryContainer = Color(0xFF344D32),
        onTertiaryContainer = Color(0xFFCDEBC5),
    ),
)

private val TealSchemes = AccentSchemes(
    swatch = Color(0xFF00897B),
    light = lightColorScheme(
        primary = Color(0xFF006A62),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF74F8E9),
        onPrimaryContainer = Color(0xFF00201D),
        secondary = Color(0xFF4A6360),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFCCE8E4),
        onSecondaryContainer = Color(0xFF051F1D),
        tertiary = Color(0xFF48607B),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD0E4FF),
        onTertiaryContainer = Color(0xFF011C34),
    ),
    dark = darkColorScheme(
        primary = Color(0xFF53DBCD),
        onPrimary = Color(0xFF003733),
        primaryContainer = Color(0xFF005049),
        onPrimaryContainer = Color(0xFF74F8E9),
        secondary = Color(0xFFB1CCC7),
        onSecondary = Color(0xFF1C3532),
        secondaryContainer = Color(0xFF324B48),
        onSecondaryContainer = Color(0xFFCCE8E4),
        tertiary = Color(0xFFB0C9E9),
        onTertiary = Color(0xFF19324B),
        tertiaryContainer = Color(0xFF304863),
        onTertiaryContainer = Color(0xFFD0E4FF),
    ),
)

/**
 * Null for [AccentColor.SYSTEM], which the platform supplies instead, and for
 * [AccentColor.CUSTOM], which is built from the reader's own colour.
 */
internal fun AccentColor.schemes(): AccentSchemes? = when (this) {
    AccentColor.SYSTEM, AccentColor.CUSTOM -> null
    AccentColor.SENYERA -> SenyeraSchemes
    AccentColor.BLUE -> BlueSchemes
    AccentColor.GREEN -> GreenSchemes
    AccentColor.VIOLET -> VioletSchemes
    AccentColor.AMBER -> AmberSchemes
    AccentColor.TEAL -> TealSchemes
}

/**
 * The dot for the Settings picker. [AccentColor.SYSTEM] has no fixed colour of
 * its own, so the caller substitutes the live dynamic primary.
 */
internal fun AccentColor.swatchOrNull(): Color? = schemes()?.swatch
