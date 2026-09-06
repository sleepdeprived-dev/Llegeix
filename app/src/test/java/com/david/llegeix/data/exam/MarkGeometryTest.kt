package com.david.llegeix.data.exam

import com.david.llegeix.data.exam.MarkGeometry.Point
import com.david.llegeix.data.exam.MarkGeometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MarkGeometryTest {

    @Test
    fun `a stroke survives a round trip`() {
        val stroke = listOf(Point(0f, 0f), Point(0.25f, 0.5f), Point(1f, 1f))

        val decoded = MarkGeometry.decode(MarkGeometry.encode(stroke))

        assertEquals(3, decoded.size)
        assertEquals(0.25f, decoded[1].x, TOLERANCE)
        assertEquals(0.5f, decoded[1].y, TOLERANCE)
        assertEquals(1f, decoded[2].x, TOLERANCE)
    }

    /**
     * The reason this object formats numbers by hand.
     *
     * `String.format` follows the default locale, and Llegeix runs in Catalan,
     * where the decimal separator is a comma — which is also the character this
     * format uses to separate a point's two halves. Encoded under that locale,
     * "0,5,0,5" is not something any decoder could take apart, and every mark
     * written by a reader with a Catalan phone would come back as nothing.
     */
    @Test
    fun `encoding does not follow the phone's locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ca-ES"))
            val encoded = MarkGeometry.encode(listOf(Point(0.5f, 0.25f)))

            assertEquals("0.5,0.25", encoded)
            assertEquals(0.5f, MarkGeometry.decode(encoded).single().x, TOLERANCE)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `four decimal places are kept and trailing zeros are not`() {
        assertEquals("0.1234,0.5", MarkGeometry.encode(listOf(Point(0.12341f, 0.5000f))))
        assertEquals("0,1", MarkGeometry.encode(listOf(Point(0f, 1f))))
    }

    /** A page is a unit square, so four places is finer than a pixel on any screen. */
    @Test
    fun `rounding stays well under one pixel on a large page`() {
        val encoded = MarkGeometry.encode(listOf(Point(0.123456f, 0.987654f)))
        val decoded = MarkGeometry.decode(encoded).single()

        val errorInPixelsOnA4000pxPage = kotlin.math.abs(decoded.x - 0.123456f) * 4000
        assertTrue("within a pixel", errorInPixelsOnA4000pxPage < 1f)
    }

    @Test
    fun `highlight rectangles survive a round trip`() {
        val rects = listOf(Rect(0.1f, 0.2f, 0.9f, 0.25f), Rect(0.1f, 0.3f, 0.4f, 0.35f))

        val decoded = MarkGeometry.decodeRects(MarkGeometry.encodeRects(rects))

        assertEquals(2, decoded.size)
        assertEquals(0.9f, decoded[0].right, TOLERANCE)
        assertEquals(0.35f, decoded[1].bottom, TOLERANCE)
    }

    /**
     * Decoding is forgiving on purpose.
     *
     * These strings are read back months after they were written, and a stroke
     * with one unreadable point should lose that point rather than take the
     * whole page of answers down with it.
     */
    @Test
    fun `rubbish in a stroke costs only that point`() {
        val decoded = MarkGeometry.decode("0.1,0.1 nonsense 0.3,0.3 ,5 0.4,")

        assertEquals(2, decoded.size)
        assertEquals(0.1f, decoded[0].x, TOLERANCE)
        assertEquals(0.3f, decoded[1].x, TOLERANCE)
    }

    @Test
    fun `nothing at all decodes to nothing`() {
        assertTrue(MarkGeometry.decode(null).isEmpty())
        assertTrue(MarkGeometry.decode("").isEmpty())
        assertTrue(MarkGeometry.decode("   ").isEmpty())
    }

    /** An odd number of corners drops the dangling one rather than inventing a rectangle. */
    @Test
    fun `a half-written rectangle is dropped`() {
        assertEquals(1, MarkGeometry.decodeRects("0.1,0.1 0.2,0.2 0.3,0.3").size)
    }

    @Test
    fun `bounds cover every point`() {
        val bounds = MarkGeometry.boundsOf(
            listOf(Point(0.4f, 0.9f), Point(0.1f, 0.2f), Point(0.8f, 0.5f)),
        )

        assertEquals(0.1f, bounds.left, TOLERANCE)
        assertEquals(0.2f, bounds.top, TOLERANCE)
        assertEquals(0.8f, bounds.right, TOLERANCE)
        assertEquals(0.9f, bounds.bottom, TOLERANCE)
    }

    @Test
    fun `bounds of nothing are empty rather than infinite`() {
        val bounds = MarkGeometry.boundsOf(emptyList())

        assertEquals(0f, bounds.left, TOLERANCE)
        assertEquals(0f, bounds.right, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
