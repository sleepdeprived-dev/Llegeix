package com.david.llegeix.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic behind "which word did they press".
 *
 * The case that matters is the cropped one. With the margins trimmed the
 * bitmap holds only the middle of the page, so a pixel stands for a different
 * point than it would otherwise — and for as long as it did not, every press
 * on a cropped page selected the word a line or two below the finger.
 */
class PageGeometryTest {

    private val widthPt = 595.0
    private val heightPt = 842.0
    private val widthPx = 1000
    private val heightPx = 1415

    /** A typical trim: a tenth off every side. */
    private val cropped = ContentBox(0.1f, 0.1f, 0.9f, 0.9f)

    @Test
    fun `a whole page maps corner to corner`() {
        val whole = ContentBox.Whole
        assertEquals(0.0, PageGeometry.xPoint(0f, widthPx, widthPt, whole), 0.001)
        assertEquals(widthPt, PageGeometry.xPoint(widthPx.toFloat(), widthPx, widthPt, whole), 0.001)
        // The top of the bitmap is the top of the page, which in PDF space is
        // the *highest* y.
        assertEquals(heightPt, PageGeometry.yPoint(0f, heightPx, heightPt, whole), 0.001)
        assertEquals(0.0, PageGeometry.yPoint(heightPx.toFloat(), heightPx, heightPt, whole), 0.001)
    }

    @Test
    fun `a cropped page starts at the crop, not at the page edge`() {
        assertEquals(
            "the left edge of the bitmap is the left edge of the ink",
            0.1 * widthPt,
            PageGeometry.xPoint(0f, widthPx, widthPt, cropped),
            0.001,
        )
        assertEquals(
            "and the top of the bitmap is the top of the ink",
            0.9 * heightPt,
            PageGeometry.yPoint(0f, heightPx, heightPt, cropped),
            0.001,
        )
    }

    /**
     * The bug itself, as a number.
     *
     * Reading the middle of a cropped bitmap as though it were a whole page
     * lands a tenth of the sheet — some seventy points, or three or four lines
     * of type — below where the finger actually was.
     */
    @Test
    fun `ignoring the crop lands below the finger`() {
        val middle = heightPx / 2f
        val correct = PageGeometry.yPoint(middle, heightPx, heightPt, cropped)
        val ifIgnored = PageGeometry.yPoint(middle, heightPx, heightPt, ContentBox.Whole)
        assertEquals("the middle of the ink is the middle of the page", 0.5 * heightPt, correct, 0.001)
        assertTrue(
            "and taking the crop for the whole page reads lower down it",
            ifIgnored < correct,
        )
    }

    @Test
    fun `pixels and points round-trip, cropped or not`() {
        for (shown in listOf(ContentBox.Whole, cropped, ContentBox(0f, 0.2f, 1f, 0.75f))) {
            for (px in listOf(0f, 1f, 250f, 999f)) {
                val pt = PageGeometry.xPoint(px, widthPx, widthPt, shown)
                assertEquals(px.toDouble(), PageGeometry.xPixel(pt, widthPx, widthPt, shown), 0.001)
            }
            for (py in listOf(0f, 1f, 700f, 1414f)) {
                val pt = PageGeometry.yPoint(py, heightPx, heightPt, shown)
                assertEquals(
                    py.toDouble(),
                    PageGeometry.yPixel(pt, heightPx, heightPt, shown),
                    0.001,
                )
            }
        }
    }

    /**
     * A word near the foot of the ink is near the foot of the bitmap.
     *
     * The highlight drawn over a selection goes the other way through the same
     * arithmetic, so it is worth one check of its own: a highlight that agreed
     * with the old lookup would have been wrong in the same direction and made
     * the bug look like correct behaviour.
     */
    @Test
    fun `a word at the foot of the ink is drawn at the foot of the crop`() {
        val lastLinePt = 0.12 * heightPt
        val y = PageGeometry.yPixel(lastLinePt, heightPx, heightPt, cropped)
        assertTrue("well down the bitmap", y > heightPx * 0.9)
        assertTrue("and still on it", y <= heightPx.toDouble())
    }
}
