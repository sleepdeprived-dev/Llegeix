package com.david.llegeix.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.david.llegeix.resources.Res
import com.david.llegeix.resources.figtree
import org.jetbrains.compose.resources.Font

/**
 * Figtree, bundled: a clean geometric sans with open, friendly letterforms and
 * generous x-height, which reads well at small sizes and has character at large
 * ones. One variable file (SIL Open Font License — see tools/fonts) carries
 * every weight, and it has the whole of Catalan and Romanian: à ç l·l, ș ț ă î.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun figtree(weight: FontWeight) = Font(
    resource = Res.font.figtree,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/**
 * A composable rather than a value: a font in Compose resources is read through
 * the composition, which is what lets the phone and the Mac share the one file.
 */
@Composable
fun figtreeFamily(): FontFamily = FontFamily(
    figtree(FontWeight.Normal),
    figtree(FontWeight.Medium),
    figtree(FontWeight.SemiBold),
    figtree(FontWeight.Bold),
    figtree(FontWeight.ExtraBold),
)

/**
 * The font for IPA. Figtree has no phonetic symbols, and letting each one fall
 * back to the system font on its own would set half of every transcription in
 * a different typeface from the other half. The system font has them all.
 */
val IpaFont: FontFamily = FontFamily.Default

/**
 * The type scale.
 *
 * Titles are heavier and tighter than Material's — a clear top level to every
 * screen is most of what makes it feel modern and easy to scan — while body
 * text stays regular, a touch larger than the default and generously leaded:
 * long Catalan sentences wrap often, and tight leading is what turns a wrapped
 * paragraph into a wall.
 */
@Composable
fun llegeixTypography(): Typography {
    val Figtree = figtreeFamily()
    return remember(Figtree) { typographyIn(Figtree) }
}

private fun typographyIn(Figtree: FontFamily) = Typography(
    displayLarge = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight.Bold,
        fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-1.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight.Bold,
        fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = (-1).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight.Bold,
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.75).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight.Normal,
        fontSize = 17.sp, lineHeight = 26.sp, letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp,
    ),
)
