package com.david.llegeix.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cutting a readable extract around a search hit.
 *
 * The positions are what these guard. The extract is tidied on the way out —
 * line breaks and runs of spaces collapse — so the term almost never sits where
 * it sat in the page's own text, and the list underlines whatever the positions
 * point at. Get them wrong by one and every row marks the wrong letters, which
 * is the sort of thing that looks like a rendering glitch rather than like a
 * bug in arithmetic.
 */
class MatchSnippetTest {

    /** The substring the row will actually mark. */
    private fun marked(snippet: Snippet) = snippet.text.substring(snippet.start, snippet.end)

    @Test
    fun `marks the term inside the extract`() {
        val page = "El poble vivia de la pesca i del comerç amb els pobles del voltant."
        val at = page.indexOf("pesca")
        val snippet = MatchSnippet.around(page, at, "pesca".length)

        assertEquals("pesca", marked(snippet))
    }

    @Test
    fun `collapses the line breaks the page text is full of`() {
        val page = "la\npesca   del\ncorall"
        val snippet = MatchSnippet.around(page, page.indexOf("pesca"), 5)

        assertEquals("la pesca del corall", snippet.text)
        assertEquals("pesca", marked(snippet))
    }

    @Test
    fun `marks the occurrence that was found, not the first one`() {
        // The whole reason the positions are tracked rather than searched for:
        // a row for the second "cap" must not underline the first.
        val page = "cap de la corda, no en tinc cap de vermell"
        val second = page.lastIndexOf("cap")
        val snippet = MatchSnippet.around(page, second, 3)

        val offsetOfSecond = snippet.text.lastIndexOf("cap")
        assertEquals(offsetOfSecond, snippet.start)
        assertEquals("cap", marked(snippet))
    }

    @Test
    fun `ellipsis only where text was actually cut off`() {
        val short = "una pesca abundant"
        val whole = MatchSnippet.around(short, short.indexOf("pesca"), 5)
        assertEquals(short, whole.text)

        val long = "x".repeat(200) + " la pesca " + "y".repeat(200)
        val cut = MatchSnippet.around(long, long.indexOf("pesca"), 5)
        assertTrue(cut.text.startsWith("…"))
        assertTrue(cut.text.endsWith("…"))
        assertEquals("pesca", marked(cut))
    }

    @Test
    fun `no ellipsis piled on top of a full stop`() {
        val page = "x".repeat(80) + " La pesca del corall era dura. " + "y".repeat(80)
        val snippet = MatchSnippet.around(page, page.indexOf("pesca"), 5)

        assertTrue(snippet.text, snippet.text.endsWith("dura."))
        assertEquals("pesca", marked(snippet))
    }

    @Test
    fun `never opens or closes in the middle of a word`() {
        val page = "antidisestabliment ".repeat(3) +
            "la pesca del corall " +
            "incommensurablement ".repeat(3)
        val snippet = MatchSnippet.around(page, page.indexOf("pesca"), 5)

        // Both ends of the extract have to fall on a space in the page, or the
        // row shows half a word and reads as a spelling mistake.
        val body = snippet.text.trim('…')
        val collapsed = page.replace(Regex("\\s+"), " ").trim()
        val at = collapsed.indexOf(body)
        assertTrue(snippet.text, at >= 0)
        assertTrue(snippet.text, at == 0 || collapsed[at - 1] == ' ')
        val after = at + body.length
        assertTrue(snippet.text, after == collapsed.length || collapsed[after] == ' ')
        assertEquals("pesca", marked(snippet))
    }

    @Test
    fun `a hit at either end of the page is still marked correctly`() {
        val page = "pesca al matí i pesca al vespre"
        val first = MatchSnippet.around(page, 0, 5)
        assertEquals(0, first.start)
        assertEquals("pesca", marked(first))

        val tail = "vespre"
        val last = MatchSnippet.around(page, page.length - tail.length, tail.length)
        assertEquals("vespre", marked(last))
        assertEquals(last.text.length, last.end)
    }

    @Test
    fun `refuses nothing rather than throwing on a degenerate range`() {
        assertEquals("", MatchSnippet.around("", 0, 4).text)
        assertEquals("", MatchSnippet.around("una pesca", 3, 0).text)

        // A count running past the end of the page is clamped, not fatal.
        val overrun = MatchSnippet.around("una pesca", 4, 500)
        assertEquals("pesca", marked(overrun))

        // As is an index past it.
        val past = MatchSnippet.around("una pesca", 99, 5)
        assertTrue(past.start <= past.text.length)
        assertTrue(past.end <= past.text.length)
    }

    @Test
    fun `the marked range is always inside the text`() {
        val page = "  la\t\tpesca\n\n del corall  "
        for (index in page.indices) {
            for (count in 1..6) {
                val snippet = MatchSnippet.around(page, index, count)
                assertTrue(snippet.start in 0..snippet.text.length)
                assertTrue(snippet.end in snippet.start..snippet.text.length)
            }
        }
    }
}
