package com.david.llegeix.ui.flashcards

import com.david.llegeix.data.db.dao.DeckWithCount
import com.david.llegeix.data.db.entity.FlashcardCollectionEntity
import com.david.llegeix.data.flashcards.ListSort
import org.junit.Assert.assertEquals
import org.junit.Test

class DeckListOrderTest {

    private fun deck(id: Long, name: String, collection: Long? = null, pinned: Boolean = false) = DeckWithCount(
        id = id, name = name, createdAt = id, isPinned = pinned, collectionId = collection, cardCount = 1,
        imageCount = 0, coverImage = null, chosenCover = null, coverCredit = null,
        boxTotal = 0, reverseBoxTotal = 0,
    )

    private fun names(entries: List<ListEntry>) = entries.map {
        when (it) {
            is ListEntry.Shelf -> it.shelf.collection.name
            is ListEntry.Deck -> it.deck.name
        }
    }

    private val collections = listOf(
        FlashcardCollectionEntity(id = 1, name = "Menjar"),
        FlashcardCollectionEntity(id = 2, name = "Viatges"),
    )
    private val decks = listOf(deck(10, "Animals"), deck(11, "Verbs"), deck(12, "Fruita", collection = 1), deck(13, "Carn", collection = 1))

    @Test
    fun `A to Z runs across collections and decks together`() {
        val list = DeckList.build(decks, collections, ListSort.NAME)
        assertEquals(listOf("Animals", "Menjar", "Verbs", "Viatges"), names(list.entries))
        assertEquals(listOf("Carn", "Fruita"), names(list.shelf(1)!!.entries))
    }

    @Test
    fun `Z to A is the same list the other way, pinned still first`() {
        val pinned = decks.map { if (it.name == "Animals") it.copy(isPinned = true) else it }
        val list = DeckList.build(pinned, collections, ListSort.NAME_DESCENDING)
        assertEquals(listOf("Animals", "Viatges", "Verbs", "Menjar"), names(list.entries))
    }

    @Test
    fun `the path to a collection runs from the top down`() {
        val nested = collections + FlashcardCollectionEntity(id = 3, name = "Fruita seca", parentId = 1)
        val list = DeckList.build(decks, nested)
        assertEquals(listOf("Menjar", "Fruita seca"), list.pathTo(3).map { it.collection.name })
    }
}
