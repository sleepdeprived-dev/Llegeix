package com.david.llegeix.ui.theme

import androidx.compose.ui.graphics.Color
import com.david.llegeix.data.settings.AccentColor
import com.david.llegeix.ui.common.HighlightColors
import com.david.llegeix.ui.common.contrastAgainst
import com.david.llegeix.ui.common.tagChipBackground
import com.david.llegeix.ui.common.tagLabelColor
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Every fixed accent has to be readable, not just the one that was looked at.
 *
 * Light mode is where this bites: Material's default neutrals are tinted
 * towards the accent, which looks cohesive and reads worse. These thresholds
 * are the WCAG ones — 4.5:1 for body text, 3:1 for the larger and lighter
 * things — checked across every scheme the app can produce.
 */
class ContrastTest {

    private fun luminance(c: Color): Float {
        fun ch(v: Float) = if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * ch(c.red) + 0.7152f * ch(c.green) + 0.0722f * ch(c.blue)
    }

    private fun contrast(a: Color, b: Color): Float {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    private val fixedAccents = AccentColor.entries.filter { it.schemes() != null }

    @Test
    fun `body text is readable on every light surface`() {
        for (accent in fixedAccents) {
            val scheme = accent.schemes()!!.light.withReadableLightNeutrals()
            val ratio = contrast(scheme.onSurface, scheme.surface)
            assertTrue("$accent: onSurface was only ${"%.1f".format(ratio)}:1", ratio >= 4.5f)
        }
    }

    @Test
    fun `secondary text is readable on every light surface`() {
        // The detail line under a document's title uses this pair, and it is
        // the smallest text in the app.
        for (accent in fixedAccents) {
            val scheme = accent.schemes()!!.light.withReadableLightNeutrals()
            val ratio = contrast(scheme.onSurfaceVariant, scheme.surface)
            assertTrue(
                "$accent: onSurfaceVariant was only ${"%.1f".format(ratio)}:1",
                ratio >= 4.5f,
            )
        }
    }

    @Test
    fun `text on a filled button is readable in both themes`() {
        for (accent in fixedAccents) {
            val schemes = accent.schemes()!!
            val light = contrast(schemes.light.onPrimary, schemes.light.primary)
            val dark = contrast(schemes.dark.onPrimary, schemes.dark.primary)
            assertTrue("$accent light button: ${"%.1f".format(light)}:1", light >= 4.5f)
            assertTrue("$accent dark button: ${"%.1f".format(dark)}:1", dark >= 4.5f)
        }
    }

    @Test
    fun `text inside a container is readable in both themes`() {
        for (accent in fixedAccents) {
            val schemes = accent.schemes()!!
            val light = contrast(schemes.light.onPrimaryContainer, schemes.light.primaryContainer)
            val dark = contrast(schemes.dark.onPrimaryContainer, schemes.dark.primaryContainer)
            assertTrue("$accent light container: ${"%.1f".format(light)}:1", light >= 4.5f)
            assertTrue("$accent dark container: ${"%.1f".format(dark)}:1", dark >= 4.5f)
        }
    }

    @Test
    fun `an outline is visible against its surface`() {
        for (accent in fixedAccents) {
            val scheme = accent.schemes()!!.light.withReadableLightNeutrals()
            val ratio = contrast(scheme.outline, scheme.surface)
            assertTrue("$accent outline: ${"%.1f".format(ratio)}:1", ratio >= 3f)
        }
    }

    @Test
    fun `the readable neutrals genuinely improve on the defaults`() {
        // The claim this change rests on: Material's tinted defaults are worse
        // for the smallest text in the app, and measurably so.
        val default = AccentColor.SENYERA.schemes()!!.light
        val readable = default.withReadableLightNeutrals()

        val before = contrast(default.onSurfaceVariant, default.surface)
        val after = contrast(readable.onSurfaceVariant, readable.surface)
        println("secondary text: %.2f:1 -> %.2f:1".format(before, after))
        assertTrue("expected an improvement, got $before -> $after", after > before)
        assertTrue("secondary text should clear 7:1, got $after", after >= 7f)
    }

    @Test
    fun `true black really is black, and stays readable on it`() {
        val amoled = AccentColor.SENYERA.schemes()!!.dark.toTrueBlack()
        assertTrue("background was not black", amoled.background == Color.Black)
        assertTrue("surface was not black", amoled.surface == Color.Black)

        val body = contrast(amoled.onSurface, amoled.surface)
        val secondary = contrast(amoled.onSurfaceVariant, amoled.surface)
        assertTrue("body text on black: %.1f:1".format(body), body >= 12f)
        assertTrue("secondary on black: %.1f:1".format(secondary), secondary >= 7f)
    }

    @Test
    fun `an elevated surface is still distinguishable from the black behind it`() {
        val amoled = AccentColor.SENYERA.schemes()!!.dark.toTrueBlack()
        // A sheet or a card has to have a visible edge, or the layout collapses
        // into one undifferentiated black field.
        assertTrue(
            "containers were indistinguishable from the background",
            amoled.surfaceContainerHigh != amoled.background,
        )
    }

    /**
     * The tag palette, on every surface it can land on.
     *
     * This is what the light-mode complaint turned out to be about: a fixed
     * darkening left a yellow tag's label at 3.06:1 on a light page.
     */
    @Test
    fun `every tag label is readable on every surface it can sit on`() {
        val surfaces = listOf(
            "light" to Color(0xFFFCFCFC),
            "dark" to Color(0xFF141218),
            "amoled" to Color.Black,
        )
        for ((themeName, surface) in surfaces) {
            for (argb in HighlightColors.palette) {
                val tag = Color(argb)
                val background = tagChipBackground(tag, surface)
                val label = tagLabelColor(tag, surface)
                val ratio = contrastAgainst(label, background)
                assertTrue(
                    "$themeName: ${HighlightColors.nameOf(argb)} label was " +
                        "${"%.2f".format(ratio)}:1",
                    ratio >= 4.5f,
                )
            }
        }
    }

    @Test
    fun `a tag keeps the hue it was given`() {
        // Readability must not turn a yellow tag grey.
        val yellow = Color(HighlightColors.Yellow)
        val label = tagLabelColor(yellow, Color(0xFFFCFCFC))
        assertTrue("the label lost its hue", label.red > label.blue)
    }
}
