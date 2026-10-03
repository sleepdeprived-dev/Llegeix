package com.david.llegeix.pdf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the picture detector has to get right.
 *
 * The whole feature is a heuristic, so these are not tests of an algorithm's
 * output; they are tests of the two decisions it exists to make. A page of
 * printed text must come back with nothing protected — otherwise dark mode
 * leaves holes in the type — and a photograph pasted onto a page must come back
 * protected, otherwise dark mode turns it into a negative, which is the bug
 * being fixed.
 *
 * Pages are built as pixel arrays rather than rendered, so this runs on the JVM
 * without a device.
 */
class PageInvertTest {

    private val width = 320
    private val height = 320
    private val blocksAcross = (width + 15) / 16

    private fun page(fill: Int = WHITE) = IntArray(width * height) { fill }

    private fun IntArray.rect(left: Int, top: Int, right: Int, bottom: Int, colour: (Int, Int) -> Int) {
        for (y in top until bottom) {
            for (x in left until right) this[y * width + x] = colour(x, y)
        }
    }

    private fun protectedAt(mask: BooleanArray, x: Int, y: Int) =
        mask[(y / 16) * blocksAcross + (x / 16)]

    @Test
    fun `blank paper is never protected`() {
        val mask = PageInvert.pictureMask(page(), width, height)
        assertFalse("nothing on a blank page is a picture", mask.any { it })
    }

    @Test
    fun `lines of printed text are not protected`() {
        val pixels = page()
        // Rows of dark strokes on white, at roughly the density of body type:
        // a 3px-tall line every 12px, with ink on about a third of its width.
        for (line in 0 until 24) {
            val top = line * 12 + 2
            pixels.rect(20, top, 300, top + 3) { x, _ -> if (x % 3 == 0) WHITE else BLACK }
        }
        val mask = PageInvert.pictureMask(pixels, width, height)
        assertFalse("body text must invert, not be protected", mask.any { it })
    }

    @Test
    fun `a heading in large type is not protected`() {
        val pixels = page()
        // One thick black band, as a 40pt heading or a table header would be:
        // wide, and only a block or two tall.
        pixels.rect(20, 40, 300, 62) { _, _ -> BLACK }
        val mask = PageInvert.pictureMask(pixels, width, height)
        assertFalse("a heading is text and must invert", mask.any { it })
    }

    @Test
    fun `a photograph is protected`() {
        val pixels = page()
        // A mid-tone, varied, coloured rectangle: no paper in it anywhere.
        pixels.rect(80, 80, 240, 240) { x, y ->
            0xFF000000.toInt() or ((x * 3 % 200 + 30) shl 16) or
                ((y * 2 % 180 + 40) shl 8) or ((x + y) % 150 + 50
        )
        }
        val mask = PageInvert.pictureMask(pixels, width, height)
        assertTrue("the middle of the photograph is protected", protectedAt(mask, 160, 160))
        assertTrue("its corner is protected too", protectedAt(mask, 96, 96))
        assertFalse("the paper around it still inverts", protectedAt(mask, 8, 8))
    }

    @Test
    fun `a greyscale photograph is protected`() {
        val pixels = page()
        // No colour at all, so it can only be caught by the paper test.
        pixels.rect(60, 60, 200, 200) { x, y ->
            val level = (x * 7 + y * 5) % 170 + 20
            0xFF000000.toInt() or (level shl 16) or (level shl 8) or level
        }
        val mask = PageInvert.pictureMask(pixels, width, height)
        assertTrue("a black-and-white photograph is still a photograph", protectedAt(mask, 130, 130))
    }

    @Test
    fun `a small logo is not protected`() {
        val pixels = page()
        // 32x32: big enough to fail the paper test, too small to be a picture.
        pixels.rect(40, 40, 72, 72) { _, _ -> 0xFF204080.toInt() }
        val mask = PageInvert.pictureMask(pixels, width, height)
        assertFalse("a small mark inverts with the text around it", mask.any { it })
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF101010.toInt()
    }
}
