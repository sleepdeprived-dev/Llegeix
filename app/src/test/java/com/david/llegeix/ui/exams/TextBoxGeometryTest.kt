package com.david.llegeix.ui.exams

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one property that matters: a new text box is always wide enough to type
 * in.
 *
 * A box that comes out narrow is the bug this file exists to prevent, and it is
 * a bug that hides — the field draws, takes the keyboard, and wraps after two
 * characters, which looks like the typing being broken rather than the box
 * being the width of a pencil.
 */
class TextBoxGeometryTest {

    @Test
    fun `a tap in the middle gets a full column`() {
        val placed = TextBoxGeometry.placeAt(0.2f)
        assertEquals(0.2f, placed.left, 0.001f)
        assertEquals(TextBoxGeometry.MAX_WIDTH, placed.width, 0.001f)
    }

    @Test
    fun `a tap at the right edge slides the box left rather than shrinking it`() {
        val placed = TextBoxGeometry.placeAt(0.99f)
        assertEquals(TextBoxGeometry.MIN_WIDTH, placed.width, 0.001f)
        assertTrue("it moved left to make room", placed.left < 0.99f)
        assertTrue(
            "and it still ends on the page",
            placed.left + placed.width <= 1f - TextBoxGeometry.RIGHT_MARGIN + 0.001f,
        )
    }

    @Test
    fun `no tap anywhere produces a box too narrow to type in`() {
        var x = 0f
        while (x <= 1f) {
            val placed = TextBoxGeometry.placeAt(x)
            assertTrue(
                "at $x the box was ${placed.width} wide",
                placed.width >= TextBoxGeometry.MIN_WIDTH - 0.001f,
            )
            assertTrue("at $x the box started off the page", placed.left >= 0f)
            assertTrue(
                "at $x the box ran off the page",
                placed.left + placed.width <= 1f + 0.001f,
            )
            x += 0.01f
        }
    }

    @Test
    fun `a tap off the left of the page is brought back on`() {
        val placed = TextBoxGeometry.placeAt(-0.5f)
        assertEquals(0f, placed.left, 0.001f)
    }
}
