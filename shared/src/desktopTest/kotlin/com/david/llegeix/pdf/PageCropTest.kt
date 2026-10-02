package com.david.llegeix.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finding the margins of a page by looking at its pixels.
 *
 * This is what "Trim margins" is: get it wrong and the reader's page is either
 * cut through the middle of a line or not cropped at all, and both look like
 * the setting is broken rather than like a measurement being off.
 */
class PageCropTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val cream = 0xFFF2EAD8.toInt()

    /** A page of [paper] with a solid block of [ink] in the given box. */
    private fun page(
        width: Int,
        height: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        paper: Int = white,
        ink: Int = black,
    ): IntArray {
        val pixels = IntArray(width * height) { paper }
        for (y in top until bottom) {
            for (x in left until right) {
                pixels[y * width + x] = ink
            }
        }
        return pixels
    }

    @Test
    fun `a blank page is not worth cropping`() {
        val blank = IntArray(100 * 100) { white }
        val box = contentBoxOf(blank, 100, 100)
        assertEquals(ContentBox.Whole, box)
    }

    @Test
    fun `the box lands on the block of ink`() {
        val pixels = page(width = 100, height = 100, left = 20, top = 30, right = 80, bottom = 70)
        val box = contentBoxOf(pixels, 100, 100)

        // Within the breathing room the crop deliberately leaves around the
        // content, which is why these are ranges rather than equalities.
        assertTrue("left edge near 0.20, was ${box.left}", box.left in 0.17f..0.20f)
        assertTrue("top edge near 0.30, was ${box.top}", box.top in 0.27f..0.30f)
        assertTrue("right edge near 0.80, was ${box.right}", box.right in 0.80f..0.83f)
        assertTrue("bottom edge near 0.70, was ${box.bottom}", box.bottom in 0.70f..0.73f)
    }

    @Test
    fun `a speck in the margin does not pin the crop to the edge`() {
        val pixels = page(width = 200, height = 200, left = 60, top = 60, right = 140, bottom = 140)
        // A scanning artefact in the top-left corner: two pixels of dirt.
        pixels[0] = black
        pixels[201] = black

        val box = contentBoxOf(pixels, 200, 200)
        assertTrue("the speck is ignored, was ${box.left}", box.left > 0.2f)
        assertTrue("the speck is ignored, was ${box.top}", box.top > 0.2f)
    }

    @Test
    fun `an aged page is judged against its own paper`() {
        // Cream paper is far from white. Against a fixed threshold the whole
        // page would read as ink and nothing would ever be cropped.
        val pixels = page(
            width = 100,
            height = 100,
            left = 25,
            top = 25,
            right = 75,
            bottom = 75,
            paper = cream,
        )
        val box = contentBoxOf(pixels, 100, 100)
        assertTrue("found the type on cream paper, was ${box.left}", box.left in 0.22f..0.25f)
        assertTrue(box.isWorthCropping)
    }

    @Test
    fun `a page filled edge to edge is left alone`() {
        val pixels = page(width = 100, height = 100, left = 0, top = 0, right = 100, bottom = 100)
        assertFalse("a full-bleed scan is not worth cropping", contentBoxOf(pixels, 100, 100).isWorthCropping)
    }

    @Test
    fun `an empty array is answered rather than crashed on`() {
        assertEquals(ContentBox.Whole, contentBoxOf(IntArray(0), 0, 0))
        assertEquals(ContentBox.Whole, contentBoxOf(IntArray(4), 100, 100))
    }
}
