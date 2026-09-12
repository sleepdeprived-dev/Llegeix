package com.david.llegeix.ui.exams

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page has to follow the cursor, and it has to do it quietly.
 *
 * Two failures to guard against, and neither of them looks like a crash. A page
 * that does not move leaves an essay being typed underneath the keyboard, which
 * is the complaint this exists to answer. A page that moves whenever it is
 * asked, rather than only when the cursor has actually gone somewhere, slides
 * under every character typed.
 */
class CaretFollowTest {

    /** A phone with a keyboard up: half the screen left, and a page inside it. */
    private val viewport = 300f
    private val slack = 150
    private val margin = 12f
    private val pageTop = margin + slack
    private val furthest = 900

    private fun follow(
        caretPx: Float,
        scrolled: Int,
        zoom: Float = 1f,
        alwaysPlace: Boolean = false,
    ): Int? = CaretFollow.scrollFor(
        caretTopPx = caretPx,
        caretBottomPx = caretPx + 14f,
        pageTopPx = pageTop,
        zoom = zoom,
        scrolledPx = scrolled,
        furthestPx = furthest,
        viewportPx = viewport,
        lowestPx = slack,
        alwaysPlace = alwaysPlace,
    )

    @Test
    fun `a cursor in the middle of what can be seen is left alone`() {
        // The page is scrolled to its own top edge and the cursor is a third of
        // the way down the screen, which is exactly where it wants to be.
        val scrolled = slack
        val caret = viewport * CaretFollow.REST - margin
        assertNull(follow(caret, scrolled))
    }

    @Test
    fun `a cursor typed past the bottom of the band brings the page up`() {
        val scrolled = slack
        // Two thirds of the way down the screen, which is past the band.
        val caret = viewport * 0.8f
        val target = follow(caret, scrolled)
        assertNotNull("the page should have moved", target)
        assertTrue("and it should have moved down the page, not up", target!! > scrolled)
        // Far enough that the cursor now sits at rest.
        val seen = pageTop + caret - target
        assertEquals(viewport * CaretFollow.REST, seen, 1f)
    }

    @Test
    fun `a cursor above the top of the screen brings the page back down`() {
        // The reader has pushed the page up by hand, leaving the line they are
        // typing off the top of the screen.
        val caret = 100f
        val target = follow(caret, scrolled = 600)
        assertNotNull(target)
        assertTrue("the page should have come back down", target!! < 600)
    }

    @Test
    fun `the first look places the cursor even when it can already be seen`() {
        val scrolled = slack
        val caret = viewport * CaretFollow.REST - margin
        assertNull("nothing to do on an ordinary keystroke", follow(caret, scrolled))
        // Coming back to a page, the same position is still worth scrolling to,
        // unless it happens to be exactly where the page already is.
        val elsewhere = follow(caret + 200f, scrolled, alwaysPlace = true)
        assertNotNull(elsewhere)
    }

    @Test
    fun `the page is never scrolled into the desk above it`() {
        // The cursor is on the first line, so the comfortable position would be
        // a third of a screen above the top of the page — which is empty desk.
        val target = follow(caretPx = 4f, scrolled = 400)
        assertEquals(slack, target)
    }

    @Test
    fun `the page is never scrolled past the end of what there is`() {
        val target = follow(caretPx = 5000f, scrolled = 0)
        assertEquals(furthest, target)
    }

    @Test
    fun `a magnified page is followed by the distance it is actually drawn at`() {
        // The same line of a page drawn at twice life size is a whole page's
        // worth further from the page's top edge, and the page has to be
        // scrolled by exactly that much more to bring it to the same place.
        val caret = 200f
        val once = follow(caret, scrolled = slack, alwaysPlace = true)!!
        val twice = follow(caret, scrolled = slack, zoom = 2f, alwaysPlace = true)!!
        assertEquals(caret.toInt(), twice - once)
    }

    @Test
    fun `a page shorter than its own slack does not ask for a range back to front`() {
        // Nothing sensible to scroll to, and the answer has to be a number
        // rather than an exception.
        val target = CaretFollow.scrollFor(
            caretTopPx = 10f,
            caretBottomPx = 24f,
            pageTopPx = pageTop,
            zoom = 1f,
            scrolledPx = 0,
            furthestPx = 40,
            viewportPx = viewport,
            lowestPx = slack,
            alwaysPlace = true,
        )
        assertEquals(40, target)
    }

    @Test
    fun `a cursor nobody has measured yet is not followed`() {
        assertNull(follow(Float.NaN, scrolled = 0, alwaysPlace = true))
        // And neither is anything at all on a screen with no room on it.
        assertNull(
            CaretFollow.scrollFor(
                caretTopPx = 10f,
                caretBottomPx = 24f,
                pageTopPx = 0f,
                zoom = 1f,
                scrolledPx = 0,
                furthestPx = 0,
                viewportPx = 0f,
                lowestPx = 0,
                alwaysPlace = true,
            ),
        )
    }
}
