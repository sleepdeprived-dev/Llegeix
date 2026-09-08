package com.david.llegeix.ui.exams

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole sheet has to be on the screen.
 *
 * A page fitted to the width alone is a page whose top and bottom leave the
 * screen the moment anything above it makes the middle of the screen shorter —
 * which is what opening the recording does, and what a reader reported as the
 * player "stealing part of the top of the page".
 */
class PageFitTest {

    /** A4 portrait, as a rendered bitmap reports it: width over height. */
    private val a4 = 210f / 297f

    @Test
    fun `a tall room fits the page by its width`() {
        val size = PageFit.of(roomWide = 336.dp, roomTall = 900.dp, ratio = a4)
        assertEquals(336f, size.width.value, 0.5f)
        assertTrue("and it is not taller than the room", size.height <= 900.dp)
    }

    @Test
    fun `a short room fits the page by its height instead`() {
        val size = PageFit.of(roomWide = 336.dp, roomTall = 340.dp, ratio = a4)
        assertEquals(340f, size.height.value, 0.5f)
        assertTrue("and it narrows to keep its shape", size.width < 336.dp)
    }

    @Test
    fun `the page never overflows the room it was given`() {
        // Every room a phone can offer, portrait and landscape, with and
        // without the recording open above the page.
        for (wide in listOf(280, 336, 420, 700)) {
            for (tall in listOf(200, 340, 500, 900)) {
                val size = PageFit.of(wide.dp, tall.dp, a4)
                assertTrue("$wide x $tall was too wide", size.width <= wide.dp + 0.5f.dp)
                assertTrue("$wide x $tall was too tall", size.height <= tall.dp + 0.5f.dp)
            }
        }
    }

    @Test
    fun `the shape of the page is kept whichever way it is fitted`() {
        for (tall in listOf(200, 340, 900)) {
            val size = PageFit.of(336.dp, tall.dp, a4)
            assertEquals(a4, size.width.value / size.height.value, 0.01f)
        }
    }

    @Test
    fun `typing fills the width even when that overflows the room`() {
        val size = PageFit.of(roomWide = 336.dp, roomTall = 200.dp, ratio = a4, fillWidth = true)
        assertEquals(336f, size.width.value, 0.5f)
        assertTrue("it is meant to be taller than the screen and moved", size.height > 200.dp)
    }

    @Test
    fun `a room with no size, or a page with no shape, comes back to nothing`() {
        // Rather than to an infinity, which is what dividing by either of them
        // would put into the layout.
        val none = PageFit.of(0.dp, 0.dp, a4)
        assertEquals(0f, none.width.value, 0f)
        assertEquals(0f, none.height.value, 0f)
        val shapeless = PageFit.of(336.dp, 340.dp, ratio = 0f)
        assertTrue(shapeless.width.value.isFinite() && shapeless.height.value.isFinite())
    }
}
