package com.david.llegeix.data.flashcards

import com.david.llegeix.data.db.entity.FlashcardEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CardOrderTest {

    private var nextId = 1L

    private fun card(catalan: String, romanian: String = "x") =
        FlashcardEntity(id = nextId++, deckId = 1, catalan = catalan, romanian = romanian)

    @Test
    fun `accented words sort beside their plain letters, not after z`() {
        val sorted = CardOrder.sorted(
            listOf(card("zona"), card("àvia"), card("avi"), card("bé"), card("be")),
        ).map { it.catalan }
        assertEquals(listOf("avi", "àvia", "be", "bé", "zona"), sorted)
    }

    @Test
    fun `capitals do not jump the queue`() {
        val sorted = CardOrder.sorted(listOf(card("pa"), card("Barcelona"), card("aigua")))
            .map { it.catalan }
        assertEquals(listOf("aigua", "Barcelona", "pa"), sorted)
    }

    @Test
    fun `the same word twice is ordered by its meaning`() {
        val sorted = CardOrder.sorted(listOf(card("cap", "niciun"), card("cap", "cap")))
            .map { it.romanian }
        assertEquals(listOf("cap", "niciun"), sorted)
    }
}
