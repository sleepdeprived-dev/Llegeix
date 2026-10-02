package com.david.llegeix.data

import com.david.llegeix.data.settings.SEARCH_HISTORY_LIMIT
import com.david.llegeix.data.settings.remembered
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The history is only useful if it stays short and stays in the order the
 * reader last used it, so those are the two things pinned down here.
 */
class SearchHistoryTest {

    @Test
    fun `the newest search goes to the front`() {
        assertEquals(
            listOf("balcons", "carrer"),
            remembered(listOf("carrer"), "balcons"),
        )
    }

    @Test
    fun `searching the same word again moves it up instead of repeating it`() {
        assertEquals(
            listOf("carrer", "placa", "balcons"),
            remembered(listOf("placa", "balcons", "carrer"), "carrer"),
        )
    }

    @Test
    fun `case is not a difference worth keeping two entries for`() {
        assertEquals(listOf("Carrer"), remembered(listOf("carrer"), "Carrer"))
    }

    @Test
    fun `surrounding space is not part of the word`() {
        assertEquals(listOf("carrer"), remembered(emptyList(), "  carrer  "))
    }

    @Test
    fun `a blank search is not a search`() {
        assertEquals(listOf("carrer"), remembered(listOf("carrer"), "   "))
    }

    @Test
    fun `the oldest falls off the end once the list is full`() {
        val full = (1..SEARCH_HISTORY_LIMIT).map { "word$it" }

        val updated = remembered(full, "newest")

        assertEquals(SEARCH_HISTORY_LIMIT, updated.size)
        assertEquals("newest", updated.first())
        assertEquals("word${SEARCH_HISTORY_LIMIT - 1}", updated.last())
    }
}
