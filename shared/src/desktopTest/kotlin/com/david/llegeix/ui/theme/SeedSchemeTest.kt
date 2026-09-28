package com.david.llegeix.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * A reader can pick any colour at all, including the ones that are hardest to
 * read — a bright yellow-green, a pale cyan. The scheme has to stay legible
 * whatever they choose, so this checks the whole hue circle rather than one
 * convenient example.
 */
class SeedSchemeTest {

    private fun luminance(c: Color): Float {
        fun ch(v: Float) = if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * ch(c.red) + 0.7152f * ch(c.green) + 0.0722f * ch(c.blue)
    }

    private fun contrast(a: Color, b: Color): Float {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    @Test
    fun `primary stays readable on light surfaces for every hue`() {
        for (hue in 0 until 360 step 15) {
            val seed = hslColor(hue.toFloat(), 0.9f, 0.6f)
            val scheme = schemesFromSeed(seed).light
            val ratio = contrast(scheme.primary, Color.White)
            assertTrue(
                "hue $hue gave only ${"%.2f".format(ratio)}:1 on white",
                ratio >= 4.4f,
            )
        }
    }

    @Test
    fun `primary stays readable on dark surfaces for every hue`() {
        val darkSurface = Color(0xFF141218)
        for (hue in 0 until 360 step 15) {
            val seed = hslColor(hue.toFloat(), 0.9f, 0.4f)
            val scheme = schemesFromSeed(seed).dark
            val ratio = contrast(scheme.primary, darkSurface)
            assertTrue(
                "hue $hue gave only ${"%.2f".format(ratio)}:1 on the dark surface",
                ratio >= 4.4f,
            )
        }
    }

    @Test
    fun `the scheme keeps the hue that was chosen`() {
        val seed = Color(0xFF2E7D32)
        val primary = schemesFromSeed(seed).light.primary
        val seedHue = seed.toHsl()[0]
        val primaryHue = primary.toHsl()[0]
        assertTrue(
            "expected a green primary, got hue $primaryHue",
            kotlin.math.abs(seedHue - primaryHue) < 12f,
        )
    }

    @Test
    fun `hex round trips`() {
        val parsed = parseHex("#7A5AF8")
        assertTrue(parsed != null)
        assertTrue(parsed!!.toHex().equals("#7A5AF8", ignoreCase = true))
        assertTrue(parseHex("not a colour") == null)
        assertTrue(parseHex("7A5AF8") != null)
    }
}
