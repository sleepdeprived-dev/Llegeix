package com.david.llegeix.data.flashcards

import com.david.llegeix.data.db.entity.FlashcardEntity
import com.david.llegeix.data.practice.Leitner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FlashcardSessionTest {

    private val now = 1_700_000_000_000L
    private var nextId = 1L

    private fun card(
        dueAt: Long = 0,
        reverseDueAt: Long = 0,
        box: Int = 0,
        reverseBox: Int = 0,
        english: String? = null,
    ) =
        FlashcardEntity(
            id = nextId++,
            deckId = 1,
            catalan = "c$nextId",
            romanian = "r$nextId",
            english = english,
            box = box,
            dueAt = dueAt,
            reverseBox = reverseBox,
            reverseDueAt = reverseDueAt,
        )

    @Test
    fun `only cards due in the chosen direction are dealt`() {
        val dueForward = card(dueAt = now - 1, reverseDueAt = now + 1_000)
        val dueReverse = card(dueAt = now + 1_000, reverseDueAt = now - 1)
        val cards = listOf(dueForward, dueReverse)

        assertEquals(
            listOf(dueForward),
            FlashcardSession.deal(cards, StudyDirection.CATALAN_TO_MEANING, now),
        )
        assertEquals(
            listOf(dueReverse),
            FlashcardSession.deal(cards, StudyDirection.MEANING_TO_CATALAN, now),
        )
    }

    @Test
    fun `a card due exactly now is due`() {
        val card = card(dueAt = now)
        assertEquals(listOf(card), FlashcardSession.deal(listOf(card), StudyDirection.CATALAN_TO_MEANING, now))
    }

    @Test
    fun `the longest-waiting card comes first`() {
        val yesterday = card(dueAt = now - 86_400_000)
        val anHourAgo = card(dueAt = now - 3_600_000)
        val lastWeek = card(dueAt = now - 7 * 86_400_000L)
        val dealt = FlashcardSession.deal(
            listOf(yesterday, anHourAgo, lastWeek),
            StudyDirection.CATALAN_TO_MEANING,
            now,
        )
        assertEquals(listOf(lastWeek, yesterday, anHourAgo), dealt)
    }

    @Test
    fun `a session is capped`() {
        val cards = List(50) { card(dueAt = now - it) }
        assertEquals(20, FlashcardSession.deal(cards, StudyDirection.CATALAN_TO_MEANING, now).size)
        assertEquals(5, FlashcardSession.deal(cards, StudyDirection.CATALAN_TO_MEANING, now, limit = 5).size)
    }

    @Test
    fun `new cards are shuffled rather than asked in the order they were written`() {
        val fresh = List(12) { card() }
        val orders = (1..5).map { seed ->
            FlashcardSession.deal(fresh, StudyDirection.CATALAN_TO_MEANING, now, random = Random(seed))
                .map { it.id }
        }
        orders.forEach { assertEquals("every card is dealt once", fresh.map { it.id }.toSet(), it.toSet()) }
        assertTrue("the order is not simply the written order", orders.any { it != fresh.map { c -> c.id } })
    }

    @Test
    fun `the shuffle never jumps a card ahead of one that has waited longer`() {
        val old = card(dueAt = now - 10_000)
        val fresh = List(8) { card(dueAt = 0) }
        repeat(5) { seed ->
            val dealt = FlashcardSession.deal(
                fresh + old,
                StudyDirection.CATALAN_TO_MEANING,
                now,
                random = Random(seed),
            )
            // Zero is "due since the beginning", which is longer than anything.
            assertEquals(old, dealt.last())
        }
    }

    @Test
    fun `the same seed deals the same session`() {
        val fresh = List(10) { card() }
        val a = FlashcardSession.deal(fresh, StudyDirection.CATALAN_TO_MEANING, now, random = Random(7))
        val b = FlashcardSession.deal(fresh, StudyDirection.CATALAN_TO_MEANING, now, random = Random(7))
        assertEquals(a, b)
    }

    @Test
    fun `nothing due deals nothing and says when the next card is`() {
        val soon = card(dueAt = now + 600_000)
        val later = card(dueAt = now + 86_400_000)
        val cards = listOf(later, soon)
        assertTrue(FlashcardSession.deal(cards, StudyDirection.CATALAN_TO_MEANING, now).isEmpty())
        assertEquals(now + 600_000, FlashcardSession.nextDueAt(cards, StudyDirection.CATALAN_TO_MEANING, now))
        assertNull(FlashcardSession.nextDueAt(emptyList(), StudyDirection.CATALAN_TO_MEANING, now))
    }

    @Test
    fun `an answer moves only the box of the direction asked`() {
        val card = card(box = 2, reverseBox = 0)
        assertEquals(
            Leitner.answer(2, correct = true, now = now),
            StudyDirection.CATALAN_TO_MEANING.answer(card, correct = true, now = now),
        )
        assertEquals(
            Leitner.answer(0, correct = true, now = now),
            StudyDirection.MEANING_TO_CATALAN.answer(card, correct = true, now = now),
        )
    }

    @Test
    fun `a wrong answer sends that direction back to the start`() {
        val card = card(box = 4, reverseBox = 4)
        assertEquals(0, StudyDirection.MEANING_TO_CATALAN.answer(card, correct = false, now = now).box)
    }

    @Test
    fun `an unknown direction name falls back to the default`() {
        assertEquals(StudyDirection.MEANING_TO_CATALAN, StudyDirection.fromName("MEANING_TO_CATALAN"))
        assertEquals(StudyDirection.Default, StudyDirection.fromName("sideways"))
        assertEquals(StudyDirection.Default, StudyDirection.fromName(null))
    }

    @Test
    fun `an English session leaves out cards with no English meaning`() {
        val both = card(english = "bread")
        val romanianOnly = card()
        val blank = card(english = "  ")
        val cards = listOf(both, romanianOnly, blank)
        assertEquals(
            listOf(both),
            FlashcardSession.deal(cards, StudyDirection.CATALAN_TO_MEANING, now, language = MeaningLanguage.ENGLISH),
        )
        assertEquals(
            "a Romanian session has every card",
            3,
            FlashcardSession.deal(cards, StudyDirection.CATALAN_TO_MEANING, now).size,
        )
    }

    @Test
    fun `extra practice takes cards that are not due`() {
        val later = card(dueAt = now + 86_400_000)
        val due = card(dueAt = now - 1)
        val extra = FlashcardSession.extra(listOf(later, due), StudyDirection.CATALAN_TO_MEANING, random = Random(1))
        assertEquals(setOf(later, due), extra.toSet())
    }

    @Test
    fun `extra practice starts with the least known`() {
        val known = card(box = 4)
        val shaky = card(box = 0)
        val middling = card(box = 2)
        repeat(3) { seed ->
            val extra = FlashcardSession.extra(
                listOf(known, shaky, middling),
                StudyDirection.CATALAN_TO_MEANING,
                random = Random(seed),
            )
            assertEquals(listOf(shaky, middling, known), extra)
        }
    }

    @Test
    fun `extra practice goes by the box of the direction asked`() {
        val a = card(box = 5, reverseBox = 0)
        val b = card(box = 0, reverseBox = 5)
        assertEquals(listOf(b, a), FlashcardSession.extra(listOf(a, b), StudyDirection.CATALAN_TO_MEANING))
        assertEquals(listOf(a, b), FlashcardSession.extra(listOf(a, b), StudyDirection.MEANING_TO_CATALAN))
    }

    @Test
    fun `extra practice respects the language and its limit`() {
        val cards = List(80) { card(english = if (it % 2 == 0) "x" else null) }
        assertEquals(50, FlashcardSession.extra(cards, StudyDirection.CATALAN_TO_MEANING).size)
        assertEquals(
            40,
            FlashcardSession.extra(cards, StudyDirection.CATALAN_TO_MEANING, language = MeaningLanguage.ENGLISH).size,
        )
    }

    @Test
    fun `the next due time ignores cards the language leaves out`() {
        val soonNoEnglish = card(dueAt = now + 1_000)
        val laterEnglish = card(dueAt = now + 9_000, english = "milk")
        assertEquals(
            now + 9_000,
            FlashcardSession.nextDueAt(
                listOf(soonNoEnglish, laterEnglish),
                StudyDirection.CATALAN_TO_MEANING,
                now,
                MeaningLanguage.ENGLISH,
            ),
        )
    }
}
