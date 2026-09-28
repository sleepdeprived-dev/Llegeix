package com.david.llegeix.data.flashcards

import com.david.llegeix.data.db.entity.FlashcardEntity
import com.david.llegeix.data.practice.Leitner
import org.junit.Assert.assertEquals
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
    ) =
        FlashcardEntity(
            id = nextId++,
            deckId = 1,
            catalan = "c$nextId",
            romanian = "r$nextId",
            box = box,
            dueAt = dueAt,
            reverseBox = reverseBox,
            reverseDueAt = reverseDueAt,
        )

    @Test
    fun `every card is dealt, due or not`() {
        val long = card(dueAt = now + 86_400_000)
        val overdue = card(dueAt = now - 86_400_000)
        val fresh = card(dueAt = 0)
        val dealt = FlashcardSession.everything(
            listOf(long, overdue, fresh),
            StudyDirection.CATALAN_TO_MEANING,
            random = Random(1),
        )
        assertEquals(
            "pressing play on a deck goes through the deck",
            setOf(long, overdue, fresh),
            dealt.toSet(),
        )
    }

    @Test
    fun `a session is not capped`() {
        val cards = List(200) { card() }
        assertEquals(
            200,
            FlashcardSession.everything(cards, StudyDirection.CATALAN_TO_MEANING).size,
        )
    }

    @Test
    fun `the least known cards come first`() {
        val known = card(box = 4)
        val shaky = card(box = 0)
        val middling = card(box = 2)
        repeat(3) { seed ->
            assertEquals(
                listOf(shaky, middling, known),
                FlashcardSession.everything(
                    listOf(known, shaky, middling),
                    StudyDirection.CATALAN_TO_MEANING,
                    random = Random(seed),
                ),
            )
        }
    }

    @Test
    fun `the order goes by the box of the direction asked`() {
        val a = card(box = 5, reverseBox = 0)
        val b = card(box = 0, reverseBox = 5)
        assertEquals(
            listOf(b, a),
            FlashcardSession.everything(listOf(a, b), StudyDirection.CATALAN_TO_MEANING),
        )
        assertEquals(
            listOf(a, b),
            FlashcardSession.everything(listOf(a, b), StudyDirection.MEANING_TO_CATALAN),
        )
    }

    @Test
    fun `cards in the same box are shuffled rather than asked in the order written`() {
        val fresh = List(12) { card() }
        val orders = (1..6).map { seed ->
            FlashcardSession.everything(
                fresh,
                StudyDirection.CATALAN_TO_MEANING,
                random = Random(seed),
            ).map { it.id }
        }
        assertTrue("a deck should not become a recitation", orders.toSet().size > 1)
    }

    @Test
    fun `the same seed deals the same session`() {
        val fresh = List(10) { card() }
        val a = FlashcardSession.everything(fresh, StudyDirection.CATALAN_TO_MEANING, Random(7))
        val b = FlashcardSession.everything(fresh, StudyDirection.CATALAN_TO_MEANING, Random(7))
        assertEquals(a, b)
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
    fun `extra practice respects its limit`() {
        val cards = List(80) { card() }
        assertEquals(50, FlashcardSession.extra(cards, StudyDirection.CATALAN_TO_MEANING).size)
    }

}
