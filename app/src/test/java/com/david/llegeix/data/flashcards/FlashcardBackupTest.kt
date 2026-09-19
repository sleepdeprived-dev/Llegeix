package com.david.llegeix.data.flashcards

import com.david.llegeix.data.db.entity.FlashcardEntity
import com.david.llegeix.data.flashcards.FlashcardBackup.Card
import com.david.llegeix.data.flashcards.FlashcardBackup.Deck
import com.david.llegeix.data.flashcards.FlashcardBackup.ExistingDeck
import com.david.llegeix.data.flashcards.FlashcardBackup.UnreadableException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class FlashcardBackupTest {

    private val pa = Card(
        catalan = "pa",
        romanian = "pâine",
        ipa = "ˈpa",
        image = "images/pa.jpg",
        imageCredit = "Sergio Palao · ARASAAC · CC BY-NC-SA",
        createdAt = 100,
        box = 3,
        dueAt = 5_000,
        reviewCount = 7,
        lastReviewedAt = 4_000,
        reverseBox = 1,
        reverseDueAt = 6_000,
        reverseReviewCount = 2,
        reverseLastReviewedAt = null,
    )
    private val cotxe = Card(catalan = "cotxe", romanian = "mașină", ipa = "ˈkɔtʃə", ipaApproximate = true)

    // ---- The file ----------------------------------------------------------

    @Test
    fun `a copy reads back exactly as it was written, schedules and all`() {
        val decks = listOf(Deck("Menjar", 10, listOf(pa, cotxe)), Deck("Buit", 20, emptyList()))
        val json = FlashcardBackup.encode(decks, exportedAt = 99)
        assertEquals(decks, FlashcardBackup.decode(json))
    }

    @Test
    fun `accents and symbols survive the round trip`() {
        val deck = Deck("Àpats i begudes", 1, listOf(Card("col·legi", "școală", ipa = "kuˈlɛʒi")))
        assertEquals(listOf(deck), FlashcardBackup.decode(FlashcardBackup.encode(listOf(deck), 0)))
    }

    @Test
    fun `a file that is not a copy is refused as such`() {
        expectUnreadable(UnreadableException.Reason.NOT_A_BACKUP, "not json at all")
        expectUnreadable(UnreadableException.Reason.NOT_A_BACKUP, """{"hello":"world"}""")
        expectUnreadable(
            UnreadableException.Reason.NOT_A_BACKUP,
            """{"format":"something-else","version":1,"decks":[]}""",
        )
        expectUnreadable(
            UnreadableException.Reason.NOT_A_BACKUP,
            """{"format":"llegeix-flashcards","decks":[]}""",
        )
    }

    @Test
    fun `a copy from a newer version is refused rather than misread`() {
        expectUnreadable(
            UnreadableException.Reason.TOO_NEW,
            """{"format":"llegeix-flashcards","version":2,"decks":[]}""",
        )
    }

    @Test
    fun `a card missing its schedule starts again rather than failing the file`() {
        val json = """
            {"format":"llegeix-flashcards","version":1,"decks":[
              {"name":"Menjar","cards":[{"catalan":"pa","romanian":"pâine"}]}
            ]}
        """.trimIndent()
        val card = FlashcardBackup.decode(json).single().cards.single()
        assertEquals(Card(catalan = "pa", romanian = "pâine"), card)
        assertNull(card.image)
    }

    @Test
    fun `half a card and a nameless deck are dropped, the rest kept`() {
        val json = """
            {"format":"llegeix-flashcards","version":1,"decks":[
              {"name":"  ","cards":[{"catalan":"x","romanian":"y"}]},
              {"name":"Menjar","cards":[
                {"catalan":"pa","romanian":""},
                {"catalan":"aigua","romanian":"apă","box":-4}
              ]}
            ]}
        """.trimIndent()
        val decks = FlashcardBackup.decode(json)
        assertEquals(listOf("Menjar"), decks.map { it.name })
        val card = decks.single().cards.single()
        assertEquals("aigua", card.catalan)
        assertEquals("a nonsense box is clamped", 0, card.box)
    }

    // ---- Bringing it back --------------------------------------------------

    private var nextId = 1L

    private fun onPhone(catalan: String, romanian: String) =
        FlashcardEntity(id = nextId++, deckId = 1, catalan = catalan, romanian = romanian)

    @Test
    fun `onto an empty phone, everything is added as new decks`() {
        val plan = FlashcardBackup.plan(listOf(Deck("Menjar", 10, listOf(pa, cotxe))), emptyList())
        assertEquals(1, plan.decks.size)
        assertNull(plan.decks.single().existingId)
        assertEquals(2, plan.cardCount)
        assertEquals(0, plan.skipped)
    }

    @Test
    fun `a deck with the same name is filled, ignoring case`() {
        val existing = ExistingDeck(7, "menjar", listOf(onPhone("aigua", "apă")))
        val plan = FlashcardBackup.plan(listOf(Deck("Menjar", 10, listOf(pa))), listOf(existing))
        assertEquals(7L, plan.decks.single().existingId)
        assertEquals(listOf(pa), plan.decks.single().cards)
    }

    @Test
    fun `a card already in the deck is skipped, ignoring case and spacing`() {
        val existing = ExistingDeck(7, "Menjar", listOf(onPhone("Pa ", "Pâine")))
        val plan = FlashcardBackup.plan(listOf(Deck("Menjar", 10, listOf(pa, cotxe))), listOf(existing))
        assertEquals(listOf(cotxe), plan.decks.single().cards)
        assertEquals(1, plan.skipped)
    }

    @Test
    fun `the same word with another meaning is a different card`() {
        val existing = ExistingDeck(7, "Menjar", listOf(onPhone("cap", "cap")))
        val other = Card("cap", "niciun")
        val plan = FlashcardBackup.plan(listOf(Deck("Menjar", 10, listOf(other))), listOf(existing))
        assertEquals(listOf(other), plan.decks.single().cards)
    }

    @Test
    fun `restoring the same copy twice adds nothing the second time`() {
        val copy = listOf(Deck("Menjar", 10, listOf(pa, cotxe)))
        val afterFirst = ExistingDeck(
            7,
            "Menjar",
            listOf(onPhone("pa", "pâine"), onPhone("cotxe", "mașină")),
        )
        val plan = FlashcardBackup.plan(copy, listOf(afterFirst))
        assertEquals(0, plan.cardCount)
        assertEquals(2, plan.skipped)
        assertEquals("no deck is touched", emptyList<FlashcardBackup.DeckPlan>(), plan.decks)
    }

    @Test
    fun `a card listed twice in the copy is added once`() {
        val plan = FlashcardBackup.plan(
            listOf(Deck("Menjar", 10, listOf(pa)), Deck("MENJAR", 11, listOf(pa, cotxe))),
            emptyList(),
        )
        assertEquals(1, plan.decks.size)
        assertEquals(listOf(pa, cotxe), plan.decks.single().cards)
        assertEquals(1, plan.skipped)
    }

    @Test
    fun `decks on the phone that the copy does not mention are left alone`() {
        val untouched = ExistingDeck(3, "Verbs", listOf(onPhone("anar", "a merge")))
        val plan = FlashcardBackup.plan(listOf(Deck("Menjar", 10, listOf(pa))), listOf(untouched))
        assertEquals(listOf("Menjar"), plan.decks.map { it.name })
    }

    private fun expectUnreadable(reason: UnreadableException.Reason, json: String) {
        try {
            FlashcardBackup.decode(json)
            fail("expected $reason for $json")
        } catch (error: UnreadableException) {
            assertEquals(reason, error.reason)
        }
    }
}
