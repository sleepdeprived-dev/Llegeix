package com.david.llegeix.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pages drawn by hand, so the detector can be judged on JVM.
 *
 * The point of keeping [TickBoxes] free of Android types is exactly this: the
 * behaviour that matters — which shapes it accepts and which it rejects — can
 * be pinned down against pages built pixel by pixel, rather than only ever
 * being looked at on a phone with a real PDF.
 */
class TickBoxesTest {

    private val width = 400
    private val height = 500

    private fun blankPage() = IntArray(width * height) { WHITE }

    private fun IntArray.drawBox(left: Int, top: Int, side: Int, thickness: Int = 2) {
        drawRect(left, top, left + side, top + side, thickness)
    }

    private fun IntArray.drawRect(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        thickness: Int = 2,
    ) {
        for (t in 0 until thickness) {
            for (x in left..right) {
                ink(x, top + t)
                ink(x, bottom - t)
            }
            for (y in top..bottom) {
                ink(left + t, y)
                ink(right - t, y)
            }
        }
    }

    /**
     * Named `ink` rather than `set`, and that is not a style choice.
     *
     * `IntArray` already has an operator `set(index, value)`, and a member beats
     * an extension: an extension called `set` taking two Ints is silently never
     * called, and every drawing helper here quietly writes a coordinate into
     * element zero instead of marking a pixel. Every page then comes out blank,
     * which makes the tests that expect *no* detections pass for the wrong
     * reason — the worst possible failure in a test suite.
     */
    private fun IntArray.ink(x: Int, y: Int) {
        if (x in 0 until width && y in 0 until height) this[y * width + x] = BLACK
    }

    @Test
    fun `finds a single checkbox`() {
        val page = blankPage().apply { drawBox(left = 50, top = 60, side = 12) }

        val found = TickBoxes.detect(page, width, height)

        assertEquals(1, found.size)
        // Roughly where it was drawn, in fractions of the page.
        assertEquals(50f / width, found[0].left, 0.02f)
        assertEquals(60f / height, found[0].top, 0.02f)
    }

    @Test
    fun `finds several boxes down a page`() {
        val page = blankPage().apply {
            drawBox(left = 40, top = 60, side = 12)
            drawBox(left = 40, top = 120, side = 12)
            drawBox(left = 40, top = 180, side = 12)
        }

        assertEquals(3, TickBoxes.detect(page, width, height).size)
    }

    /**
     * The same square is met once per row of its top edge; it must be reported
     * once. Without the overlap check a two-pixel line yields two boxes, and
     * every tick would land on a duplicate.
     */
    @Test
    fun `a thick outline is still one box`() {
        val page = blankPage().apply { drawBox(left = 50, top = 60, side = 14, thickness = 3) }

        assertEquals(1, TickBoxes.detect(page, width, height).size)
    }

    @Test
    fun `ignores a shape too large to be a checkbox`() {
        // A frame around an answer area: right shape, wrong size.
        val page = blankPage().apply { drawBox(left = 40, top = 60, side = 120) }

        assertTrue(TickBoxes.detect(page, width, height).isEmpty())
    }

    @Test
    fun `ignores a shape too small to be a checkbox`() {
        val page = blankPage().apply { drawBox(left = 40, top = 60, side = 3) }

        assertTrue(TickBoxes.detect(page, width, height).isEmpty())
    }

    /** A long thin rectangle is a table cell or a rule to write on, not a box. */
    @Test
    fun `ignores a wide rectangle`() {
        val page = blankPage().apply { drawRect(left = 40, top = 60, right = 130, bottom = 74) }

        assertTrue(TickBoxes.detect(page, width, height).isEmpty())
    }

    @Test
    fun `ignores an open shape with no bottom`() {
        val page = blankPage().apply {
            // Three sides only.
            for (x in 50..62) ink(x, 60)
            for (y in 60..72) {
                ink(50, y)
                ink(62, y)
            }
        }

        assertTrue(TickBoxes.detect(page, width, height).isEmpty())
    }

    @Test
    fun `a blank page has no boxes`() {
        assertTrue(TickBoxes.detect(blankPage(), width, height).isEmpty())
    }

    /**
     * A box that has already been ticked is still a box.
     *
     * This is the case that matters for reopening a paper: the reader comes
     * back to a sitting where half the boxes are ticked, and the ticked ones
     * must still snap.
     */
    @Test
    fun `finds a box that already has a mark in it`() {
        val page = blankPage().apply {
            drawBox(left = 50, top = 60, side = 16)
            // A scrawl through the middle.
            for (i in 0..10) {
                ink(54 + i, 64 + i)
                ink(54 + i, 70 - i)
            }
        }

        assertEquals(1, TickBoxes.detect(page, width, height).size)
    }

    @Test
    fun `tolerates a small gap in an edge`() {
        val page = blankPage().apply { drawBox(left = 50, top = 60, side = 14) }
        // Rub out one pixel of the bottom edge, the way a scan does.
        page[74 * width + 57] = WHITE

        assertEquals(1, TickBoxes.detect(page, width, height).size)
    }

    @Test
    fun `grey scanned lines still count as ink`() {
        val page = blankPage()
        val grey = 0xFF6E6E6E.toInt()
        fun grey(x: Int, y: Int) {
            if (x in 0 until width && y in 0 until height) page[y * width + x] = grey
        }
        for (x in 50..64) {
            grey(x, 60)
            grey(x, 74)
        }
        for (y in 60..74) {
            grey(50, y)
            grey(64, y)
        }

        assertEquals(1, TickBoxes.detect(page, width, height).size)
    }

    @Test
    fun `a malformed page is refused rather than throwing`() {
        assertTrue(TickBoxes.detect(IntArray(10), width, height).isEmpty())
        assertTrue(TickBoxes.detect(IntArray(0), 0, 0).isEmpty())
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
    }
}
