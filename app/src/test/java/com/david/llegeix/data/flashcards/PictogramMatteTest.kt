package com.david.llegeix.data.flashcards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PictogramMatteTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val red = 0xFFDC1E1E.toInt()

    /**
     * A 7×7 picture: white paper, a black ring from (1,1) to (5,5), red inside
     * it, and one white pixel in the middle — a white of the drawing's own.
     */
    private fun drawing(): IntArray {
        val w = 7
        val px = IntArray(w * w) { white }
        for (y in 1..5) for (x in 1..5) {
            val ring = x == 1 || x == 5 || y == 1 || y == 5
            px[y * w + x] = if (ring) black else red
        }
        px[3 * w + 3] = white
        return px
    }

    @Test
    fun `the paper round the drawing becomes transparent`() {
        val out = PictogramMatte.cutPaper(drawing(), 7, 7)
        for (i in listOf(0, 3, 6, 7 * 3, 7 * 6 + 6)) assertEquals("pixel $i", 0, out[i])
    }

    @Test
    fun `a white the drawing encloses keeps its white`() {
        val out = PictogramMatte.cutPaper(drawing(), 7, 7)
        assertEquals(white, out[3 * 7 + 3])
    }

    @Test
    fun `the colours of the drawing are left exactly as they were`() {
        val input = drawing()
        val out = PictogramMatte.cutPaper(input, 7, 7)
        assertEquals(red, out[2 * 7 + 2])
        assertEquals(red, out[4 * 7 + 3])
        // A black line beside the paper is pure ink, so it comes out unchanged.
        assertEquals(black, out[1 * 7 + 3])
    }

    @Test
    fun `the input is not changed`() {
        val input = drawing()
        val copy = input.copyOf()
        PictogramMatte.cutPaper(input, 7, 7)
        assertTrue(input.contentEquals(copy))
    }

    @Test
    fun `a line's smoothed edge becomes ink at part strength, not a pale halo`() {
        // Mid-grey next to the paper: black laid over white at about half.
        val edge = PictogramMatte.unmixFromWhite(0xFF808080.toInt())
        val alpha = edge ushr 24
        assertTrue("about half strength: $alpha", alpha in 120..135)
        assertEquals("and the ink itself is black", 0, (edge shr 16) and 0xFF)
    }

    @Test
    fun `a coloured edge keeps its colour`() {
        // Red smoothed into white keeps a red ink, not a pink one.
        val edge = PictogramMatte.unmixFromWhite(0xFFF0A0A0.toInt())
        val r = (edge shr 16) and 0xFF
        val g = (edge shr 8) and 0xFF
        assertTrue("red ink: r=$r g=$g", r > 200 && g < 10)
    }

    @Test
    fun `slightly off-white paper from JPEG noise still counts as paper`() {
        val px = IntArray(9) { 0xFFF6F8F4.toInt() }
        px[4] = black
        val out = PictogramMatte.cutPaper(px, 3, 3)
        assertEquals(0, out[0])
        assertEquals(black, out[4])
    }

    @Test
    fun `an empty picture is handled`() {
        assertEquals(0, PictogramMatte.cutPaper(IntArray(0), 0, 0).size)
    }
}
