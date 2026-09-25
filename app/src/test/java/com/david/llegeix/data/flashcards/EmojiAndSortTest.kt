package com.david.llegeix.data.flashcards

import com.david.llegeix.data.db.entity.FlashcardEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiAndSortTest {

    private val english = """[
        {"group":4,"hexcode":"1F34E","label":"red apple","order":3661,"tags":["apple","fruit"],"unicode":"🍎"},
        {"group":4,"hexcode":"1F9FA","label":"basket","order":4000,"tags":["picnic"],"unicode":"🧺"},
        {"group":3,"hexcode":"1F415","label":"dog","order":3491,"tags":["animal","pet"],"unicode":"🐕️"},
        {"group":9,"hexcode":"1F1EA-1F1F8","label":"flag: Spain","order":5000,"unicode":"🇪🇸"}
    ]"""
    private val catalan = """{"annotations":{"annotations":{
        "🍎":{"default":["fruita","poma"],"tts":["poma vermella"]},
        "🧺":{"default":["cistella","poma"],"tts":["cistella"]},
        "🐕":{"default":["animal","gos"],"tts":["gos"]}
    }}}"""

    @Test
    fun `emoji are found by their Catalan name, named ones first, flags left out`() {
        val index = PictureResults.parseEmoji(english, catalan)
        assertEquals(3, index.size)
        val hits = PictureResults.emojiMatches(index, "poma", "apple")
        assertEquals(listOf("noto:1F34E", "noto:1F9FA"), hits.map { it.id })
        assertTrue(hits.first().fullUrl.endsWith("/512/emoji_u1f34e.png"))
        assertEquals(PictureKind.EMOJI, PictureResults.kindOf(hits.first().credit))
        assertEquals(listOf("noto:1F415"), PictureResults.emojiMatches(index, "gos", null).map { it.id })
    }

    @Test
    fun `a verb's English is searched without its to`() {
        assertEquals("eat", PictureResults.stripArticles("to eat"))
        assertEquals("apple", PictureResults.stripArticles("an apple"))
        assertEquals("toast", PictureResults.stripArticles("toast"))
    }

    private fun card(id: Long, catalan: String, created: Long, box: Int = 0, weak: Long? = null) =
        FlashcardEntity(id = id, deckId = 1, catalan = catalan, romanian = "x", createdAt = created, box = box, weakAt = weak)

    @Test
    fun `cards sort by name, date, or least known with weak words first`() {
        val cards = listOf(card(1, "zona", 3, box = 4), card(2, "àvia", 1, box = 1), card(3, "poma", 2, box = 0, weak = 9))
        assertEquals(listOf("àvia", "poma", "zona"), CardSort.ALPHABETICAL.sorted(cards).map { it.catalan })
        assertEquals(listOf("zona", "poma", "àvia"), CardSort.NEWEST.sorted(cards).map { it.catalan })
        assertEquals(listOf("poma", "àvia", "zona"), CardSort.WEAKEST.sorted(cards).map { it.catalan })
    }

    @Test
    fun `pinned stays first whatever the order`() {
        data class Row(val name: String, val pinned: Boolean, val cards: Int)
        val rows = listOf(Row("b", false, 9), Row("a", false, 1), Row("c", true, 0))
        val order = ListSort.MOST_CARDS.comparator<Row>({ it.name }, { 0L }, { it.pinned }, { it.cards })
        assertEquals(listOf("c", "b", "a"), rows.sortedWith(order).map { it.name })
    }
}
