package com.david.llegeix.ui.exams

import com.david.llegeix.data.db.entity.ExamMarkEntity
import com.david.llegeix.data.db.entity.MarkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which mark a blank page is written into.
 *
 * This is a one-line rule guarding a bug that cost a reader their essay, so it
 * is worth a test of its own. A blank page holds one text mark; two calls to
 * make sure of it, racing, once left a page holding two. The editor bound to
 * one of them and the app went on saving into it, but the words that were
 * already there lived in the other — so the page came up empty, and to the
 * reader "empty" and "nothing was saved" are the same thing.
 *
 * The rule is: the words win.
 */
class DocumentMarkTest {

    private fun text(id: Long, words: String?) = ExamMarkEntity(
        id = id,
        attemptId = 1,
        partId = 1,
        pageIndex = 0,
        kind = MarkKind.TEXT.name,
        x = 0.07f,
        y = 0.07f,
        width = 0.86f,
        height = 0.86f,
        colorArgb = ExamInk.Default,
        size = 0.026f,
        text = words,
    )

    private fun stroke(id: Long) = text(id, null).copy(kind = MarkKind.INK.name)

    @Test
    fun `a page with nothing typed on it has no document`() {
        assertNull(listOf(stroke(1)).documentMark())
        assertNull(emptyList<ExamMarkEntity>().documentMark())
    }

    @Test
    fun `the one text mark is the document`() {
        assertEquals(7L, listOf(stroke(1), text(7, "")).documentMark()?.id)
    }

    @Test
    fun `the written one wins over an empty one made after it`() {
        val page = listOf(text(1, ""), text(2, "The essay."))
        assertEquals(2L, page.documentMark()?.id)
    }

    @Test
    fun `the written one wins over an empty one made before it`() {
        val page = listOf(text(1, "The essay."), text(2, ""))
        assertEquals(1L, page.documentMark()?.id)
    }

    @Test
    fun `a page of whitespace counts as empty`() {
        val page = listOf(text(1, "   \n "), text(2, "The essay."))
        assertEquals(2L, page.documentMark()?.id)
    }

    @Test
    fun `when two have been written in, the older is the one kept`() {
        val page = listOf(text(1, "First."), text(2, "Second."))
        assertEquals(1L, page.documentMark()?.id)
    }
}
