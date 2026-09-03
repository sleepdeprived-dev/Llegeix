package com.david.llegeix.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Finding the line a tap landed on.
 *
 * The line *number* is the part worth guarding: it is printed on the sheet and
 * saved with a bookmarked word, so a reader can come back to the word months
 * later and find it. Off-by-one here is a wrong reference in someone's notes,
 * which is worse than no reference, and nothing about it is visible until then.
 */
class PageLinesTest {

    private val page = "primera línia\nsegona línia\ntercera línia\nquarta línia\ncinquena línia"

    @Test
    fun `numbers lines from one`() {
        assertEquals(1, PageLines.locate(page, 0).number)
        assertEquals("primera línia", PageLines.locate(page, 0).text)

        val secondStart = page.indexOf("segona")
        assertEquals(2, PageLines.locate(page, secondStart).number)
        assertEquals("segona línia", PageLines.locate(page, secondStart).text)
    }

    @Test
    fun `puts a character at the very end of a line on that line`() {
        // The last character before the break, and the break itself, both
        // belong to the line they end rather than to the one after it.
        val endOfFirst = page.indexOf('\n') - 1
        assertEquals(1, PageLines.locate(page, endOfFirst).number)
        assertEquals(1, PageLines.locate(page, page.indexOf('\n')).number)
        assertEquals(2, PageLines.locate(page, page.indexOf('\n') + 1).number)
    }

    @Test
    fun `finds the last line`() {
        val located = PageLines.locate(page, page.length - 1)
        assertEquals(5, located.number)
        assertEquals("cinquena línia", located.text)
    }

    @Test
    fun `clamps an index past the end of the page`() {
        assertEquals(5, PageLines.locate(page, 9999).number)
        assertEquals(1, PageLines.locate(page, -3).number)
    }

    @Test
    fun `answers for a page with no line breaks at all`() {
        val located = PageLines.locate("una sola línia", 5)
        assertEquals(1, located.number)
        assertEquals("una sola línia", located.text)
        assertEquals("una sola línia", located.passage)
    }

    @Test
    fun `answers for an empty page`() {
        assertEquals(LocatedLine("", 1, ""), PageLines.locate("", 0))
    }

    @Test
    fun `gathers the lines either side into a passage`() {
        val located = PageLines.locate(page, page.indexOf("tercera"))
        assertEquals(
            "primera línia segona línia tercera línia quarta línia cinquena línia",
            located.passage,
        )
    }

    @Test
    fun `stops the passage at the edges of the page`() {
        assertEquals(
            "primera línia segona línia tercera línia",
            PageLines.locate(page, 0).passage,
        )
        assertEquals(
            "tercera línia quarta línia cinquena línia",
            PageLines.locate(page, page.length - 1).passage,
        )
    }

    @Test
    fun `tidies the stray breaks a text layer leaves behind`() {
        assertEquals("una línia partida", PageLines.clean("  una línia\r\npartida  "))
    }
}
