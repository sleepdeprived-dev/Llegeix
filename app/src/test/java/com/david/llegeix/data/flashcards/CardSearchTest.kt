package com.david.llegeix.data.flashcards

import com.david.llegeix.data.db.entity.FlashcardEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CardSearchTest {

    private var nextId = 1L

    private fun card(catalan: String, romanian: String) =
        FlashcardEntity(id = nextId++, deckId = 1, catalan = catalan, romanian = romanian)

    private val cafe = card("cafè", "cafea")
    private val cotxe = card("cotxe", "mașină")
    private val collegi = card("col·legi", "școală")
    private val aigua = card("aigua", "apă")
    private val deck = listOf(cafe, cotxe, collegi, aigua)

    private fun search(query: String) = CardSearch.filter(deck, query)

    @Test
    fun `an empty search finds everything`() {
        assertEquals(deck, search(""))
        assertEquals(deck, search("   "))
    }

    @Test
    fun `a Catalan accent is not needed`() {
        assertEquals(listOf(cafe), search("cafe"))
    }

    @Test
    fun `a Romanian accent is not needed`() {
        assertEquals(listOf(cotxe), search("masina"))
        assertEquals(listOf(collegi), search("scoala"))
        assertEquals(listOf(aigua), search("apa"))
    }

    @Test
    fun `typing the accent still works`() {
        assertEquals(listOf(cafe), search("cafè"))
        assertEquals(listOf(cotxe), search("mașină"))
    }

    @Test
    fun `the punt volat can be left out or typed as a full stop`() {
        assertEquals(listOf(collegi), search("collegi"))
        assertEquals(listOf(collegi), search("col.legi"))
        assertEquals(listOf(collegi), search("col·legi"))
    }

    @Test
    fun `capitals do not matter`() {
        assertEquals(listOf(cotxe), search("COTXE"))
    }

    @Test
    fun `either side of the card is searched`() {
        assertEquals(listOf(cafe), search("cafea"))
        assertEquals(listOf(cafe), search("caf"))
    }

    @Test
    fun `several words must all be found`() {
        assertEquals(listOf(cafe), search("cafe cafea"))
        assertEquals(emptyList<FlashcardEntity>(), search("cafe apa"))
    }

    @Test
    fun `the order of the deck is kept`() {
        assertEquals(listOf(cotxe, collegi), search("co"))
    }

    @Test
    fun `nothing found is an empty list`() {
        assertEquals(emptyList<FlashcardEntity>(), search("zebra"))
    }
}
