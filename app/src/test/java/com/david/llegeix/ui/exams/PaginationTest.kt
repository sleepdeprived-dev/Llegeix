package com.david.llegeix.ui.exams

import com.david.llegeix.data.db.entity.ExamPartEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The translation between the paper the reader sees and the files behind it.
 *
 * Worth testing on its own because getting it wrong is silent and expensive: an
 * off-by-one here does not crash, it writes an answer onto the wrong page of the
 * wrong document, and the reader finds out when they come back to revise.
 */
class PaginationTest {

    private fun part(id: Long, pages: Int, name: String = "part$id") =
        ExamPartEntity(
            id = id,
            examId = 1,
            sourceName = name,
            fileName = "$id.pdf",
            pageCount = pages,
            position = (id - 1).toInt(),
        )

    @Test
    fun `a single document is its own paper`() {
        val paper = Pagination(listOf(part(1, 12)))

        assertEquals(12, paper.pageCount)
        assertEquals(1L, paper.addressOf(0)?.part?.id)
        assertEquals(0, paper.addressOf(0)?.pageInPart)
        assertEquals(11, paper.addressOf(11)?.pageInPart)
    }

    @Test
    fun `pages run straight through the join`() {
        val paper = Pagination(listOf(part(1, 3), part(2, 4), part(3, 2)))

        assertEquals(9, paper.pageCount)

        // Last page of the first document, then the first of the second.
        assertEquals(1L, paper.addressOf(2)?.part?.id)
        assertEquals(2, paper.addressOf(2)?.pageInPart)
        assertEquals(2L, paper.addressOf(3)?.part?.id)
        assertEquals(0, paper.addressOf(3)?.pageInPart)

        // And across the second join.
        assertEquals(2L, paper.addressOf(6)?.part?.id)
        assertEquals(3, paper.addressOf(6)?.pageInPart)
        assertEquals(3L, paper.addressOf(7)?.part?.id)
        assertEquals(0, paper.addressOf(7)?.pageInPart)
    }

    @Test
    fun `every page of every document is reachable exactly once`() {
        val parts = listOf(part(1, 3), part(2, 4), part(3, 2))
        val paper = Pagination(parts)

        val seen = (0 until paper.pageCount).map {
            val address = paper.addressOf(it)!!
            address.part.id to address.pageInPart
        }

        assertEquals("no page is visited twice", seen.size, seen.toSet().size)
        val expected = parts.flatMap { p -> (0 until p.pageCount).map { p.id to it } }
        assertEquals(expected, seen)
    }

    @Test
    fun `off the end is nothing rather than the last page`() {
        val paper = Pagination(listOf(part(1, 3), part(2, 4)))

        assertNull(paper.addressOf(7))
        assertNull(paper.addressOf(100))
        assertNull(paper.addressOf(-1))
    }

    @Test
    fun `a paper with no documents has no pages`() {
        assertEquals(0, Pagination.Empty.pageCount)
        assertNull(Pagination.Empty.addressOf(0))
    }

    /**
     * A document of no pages should not swallow the page after it.
     *
     * PDFium reports zero for a file it cannot parse, and although import
     * refuses those, a part could in principle reach here with none.
     */
    @Test
    fun `an empty document is stepped over`() {
        val paper = Pagination(listOf(part(1, 2), part(2, 0), part(3, 2)))

        assertEquals(4, paper.pageCount)
        assertEquals(1L, paper.addressOf(1)?.part?.id)
        assertEquals(3L, paper.addressOf(2)?.part?.id)
        assertEquals(0, paper.addressOf(2)?.pageInPart)
    }

    @Test
    fun `a document reports where it starts`() {
        val paper = Pagination(listOf(part(1, 3), part(2, 4), part(3, 2)))

        assertEquals(0, paper.startOf(1))
        assertEquals(3, paper.startOf(2))
        assertEquals(7, paper.startOf(3))
        assertEquals("an unknown document falls back to the front", 0, paper.startOf(99))
    }
}
